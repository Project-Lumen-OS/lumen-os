// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

import java.util.List;

/**
 * Received media path without Android: RTP datagrams in, decodable access units out.
 * RTP header check, reorder ({@link RtpReorderer}), MPEG-TS demux ({@link TsDemuxer}), then per stream:
 * H.264 access units (with parameter sets kept and put in front of a key frame that lacks them),
 * LPCM converted to little-endian PCM, AAC ADTS split into raw frames.
 *
 * Video before the first key frame cannot be decoded and is dropped (and a key frame is asked for).
 * After a loss the damaged frame still goes to the decoder (it conceals; a freeze looks worse than a
 * smear for a mirrored screen) and a key frame is asked for, unless {@link #dropUntilKeyOnDamage}.
 * Buffers handed to {@link Output} are only valid during the call.
 */
public final class MediaPipeline implements RtpReorderer.Sink, TsDemuxer.Listener {
    public interface Output {
        void onVideo(byte[] d, int off, int len, long ptsUs, boolean keyFrame);

        void onPcm(byte[] pcm, int len, int sampleRate, int channels, long ptsUs);

        /** One raw AAC frame (ADTS header removed); {@code hdr} describes it. */
        void onAac(byte[] d, int off, int len, AudioPayload.Adts hdr, long ptsUs);

        void onNeedKeyFrame(String why);

        void onStreams(List<TsDemuxer.Stream> streams);
    }

    private final Output out;
    private final RtpReorderer reorder;
    private final TsDemuxer demux;
    private final RtpPacket rtp = new RtpPacket();
    private byte[] paramSets;           // last SPS+PPS seen on their own
    private byte[] joined = new byte[0]; // paramSets + key frame
    private byte[] pcm = new byte[16384];
    private volatile boolean haveKey;
    public volatile boolean dropUntilKeyOnDamage;
    private long badRtp, videoUnits, droppedVideo, audioUnits;

    public MediaPipeline(Output out) {
        this.out = out;
        this.reorder = new RtpReorderer(24, 40, this);
        this.demux = new TsDemuxer(this);
    }

    /** One UDP datagram from the RTP port. */
    public void onDatagram(byte[] buf, int len, long nowMs) {
        if (!rtp.parse(buf, len) || rtp.payloadType != RtpPacket.PT_MP2T) {
            badRtp++;
            return;
        }
        reorder.push(rtp.seq, buf, rtp.payloadOffset, rtp.payloadLength, nowMs);
    }

    /** Call on receive timeouts so a held gap is released. */
    public void tick(long nowMs) {
        reorder.flushStale(nowMs);
    }

    /** A new decoder (surface change, codec error): the next video must start at a key frame. */
    public void requireKeyFrame() {
        haveKey = false;
    }

    // ------------------------------------------------------------------ RTP -> TS

    @Override
    public void onPayload(byte[] data, int off, int len) {
        demux.feed(data, off, len);
    }

    @Override
    public void onLoss(int packets) {
        // the demuxer sees the gap as a continuity error on the PIDs it hit
    }

    // ------------------------------------------------------------------ TS -> units

    @Override
    public void onStreams(List<TsDemuxer.Stream> streams) {
        out.onStreams(streams);
    }

    @Override
    public void onDiscontinuity(int pid, int streamType) {
        if (streamType == TsDemuxer.ST_H264) {
            out.onNeedKeyFrame("loss");
            if (dropUntilKeyOnDamage) haveKey = false;
        }
    }

    @Override
    public void onPes(int pid, int type, long ptsUs, byte[] d, int off, int len, boolean damaged) {
        switch (type) {
            case TsDemuxer.ST_H264: video(d, off, len, ptsUs, damaged); break;
            case TsDemuxer.ST_LPCM: lpcm(d, off, len, ptsUs); break;
            case TsDemuxer.ST_AAC_ADTS: aac(d, off, len, ptsUs); break;
            default: break;
        }
    }

    private void video(byte[] d, int off, int len, long ptsUs, boolean damaged) {
        int nal = H264.nalTypes(d, off, len);
        boolean slices = (nal & ((1 << 1) | (1 << H264.NAL_IDR))) != 0;
        if (!slices) {
            if (H264.hasParameterSets(nal)) {
                paramSets = new byte[len];
                System.arraycopy(d, off, paramSets, 0, len);
            }
            return;
        }
        boolean key = H264.isKeyFrame(nal);
        if (!haveKey) {
            if (!key || damaged) {
                droppedVideo++;
                out.onNeedKeyFrame("waiting for key frame");
                return;
            }
            haveKey = true;
        }
        if (key && !H264.hasParameterSets(nal) && paramSets != null) {
            int n = paramSets.length + len;
            if (joined.length < n) joined = new byte[n];
            System.arraycopy(paramSets, 0, joined, 0, paramSets.length);
            System.arraycopy(d, off, joined, paramSets.length, len);
            d = joined;
            off = 0;
            len = n;
        }
        if (key && H264.hasParameterSets(nal)) {
            // keep the newest parameter sets: a later key frame may come without them
            int end = firstSliceOffset(d, off, len);
            if (end > off) {
                paramSets = new byte[end - off];
                System.arraycopy(d, off, paramSets, 0, end - off);
            }
        }
        videoUnits++;
        out.onVideo(d, off, len, ptsUs, key);
    }

    /** Offset of the start code of the first slice NAL (type 1 or 5), or off when none. */
    static int firstSliceOffset(byte[] d, int off, int len) {
        int end = off + len;
        for (int i = off; i + 3 < end; i++) {
            if (d[i] == 0 && d[i + 1] == 0 && d[i + 2] == 1) {
                int t = d[i + 3] & 0x1f;
                if (t == 1 || t == H264.NAL_IDR) {
                    return i > off && d[i - 1] == 0 ? i - 1 : i;
                }
                i += 3;
            }
        }
        return off;
    }

    private void lpcm(byte[] d, int off, int len, long ptsUs) {
        AudioPayload.LpcmHeader h = AudioPayload.lpcmHeader(d, off, len);
        if (h == null) return;
        if (pcm.length < len) pcm = new byte[len];
        int n = AudioPayload.lpcmToLittleEndian(d, off, len, pcm);
        if (n <= 0) return;
        audioUnits++;
        out.onPcm(pcm, n, h.sampleRate, h.channels, ptsUs);
    }

    private void aac(byte[] d, int off, int len, long ptsUs) {
        int p = off, end = off + len;
        long t = ptsUs;
        while (p < end) {
            AudioPayload.Adts h = AudioPayload.adts(d, p, end - p);
            if (h == null || p + h.frameLen > end) return;
            audioUnits++;
            out.onAac(d, p + h.headerLen, h.frameLen - h.headerLen, h, t);
            if (t >= 0) t += 1024L * 1_000_000L / h.sampleRate;
            p += h.frameLen;
        }
    }

    // ------------------------------------------------------------------ stats

    public String stats() {
        return "rtp delivered=" + reorder.delivered() + " lost=" + reorder.lost() + " dup/late=" + reorder.dropped()
                + " bad=" + badRtp + " ts=" + demux.packets() + " sync=" + demux.syncLoss()
                + " video=" + videoUnits + " videoDropped=" + droppedVideo + " audio=" + audioUnits;
    }

    public long lostPackets() {
        return reorder.lost();
    }
}
