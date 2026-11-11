// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import org.lumen.miracast.core.AudioPayload;

import java.nio.ByteBuffer;

/**
 * Sound of the mirrored screen: LPCM straight into an AudioTrack, AAC through MediaCodec first.
 * Everything runs on one audio thread. The AudioTrack is kept short (about 100 ms buffer) and a
 * chunk is dropped when more than {@link #MAX_QUEUED_MS} are already waiting, so audio never drifts
 * far behind the picture on a slow link.
 */
final class AudioRenderer {
    private static final String TAG = "LumenMiracast";
    static final int MAX_QUEUED_MS = 250;

    private final HandlerThread thread = new HandlerThread("miracast-audio");
    private final Handler h;
    private AudioTrack track;
    private int trackRate, trackChannels;
    private long framesWritten;
    private MediaCodec aac;
    private int aacRate, aacChannels;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private byte[] out = new byte[16384];
    private long dropped;
    private volatile boolean released;

    AudioRenderer() {
        thread.start();
        h = new Handler(thread.getLooper());
    }

    /** RTP thread: little-endian 16-bit PCM. Copied. */
    void pcm(byte[] d, int len, int rate, int channels) {
        if (released) return;
        byte[] copy = new byte[len];
        System.arraycopy(d, 0, copy, 0, len);
        h.post(() -> write(copy, len, rate, channels));
    }

    /** RTP thread: one raw AAC frame. Copied. */
    void aac(byte[] d, int off, int len, AudioPayload.Adts hdr, long ptsUs) {
        if (released) return;
        byte[] copy = new byte[len];
        System.arraycopy(d, off, copy, 0, len);
        int rate = hdr.sampleRate, ch = hdr.channels;
        byte[] asc = hdr.audioSpecificConfig();
        h.post(() -> decodeAac(copy, rate, ch, asc, ptsUs));
    }

    void release() {
        released = true;
        h.post(() -> {
            stopAac();
            stopTrack();
            thread.quitSafely();
        });
    }

    String stats() {
        return "audio dropped=" + dropped + " written=" + framesWritten;
    }

    // ------------------------------------------------------------------ audio thread

    private void write(byte[] pcm, int len, int rate, int channels) {
        if (!ensureTrack(rate, channels)) return;
        int frameBytes = 2 * channels;
        long queued = framesWritten - (track.getPlaybackHeadPosition() & 0xffffffffL);
        if (queued * 1000 / rate > MAX_QUEUED_MS) {
            dropped += len / frameBytes;
            return;
        }
        int n = track.write(pcm, 0, len, AudioTrack.WRITE_NON_BLOCKING);
        if (n > 0) framesWritten += n / frameBytes;
    }

    private boolean ensureTrack(int rate, int channels) {
        if (track != null && rate == trackRate && channels == trackChannels) return true;
        stopTrack();
        int mask = channels == 1 ? AudioFormat.CHANNEL_OUT_MONO
                : channels == 6 ? AudioFormat.CHANNEL_OUT_5POINT1
                : channels == 8 ? AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
                : AudioFormat.CHANNEL_OUT_STEREO;
        int min = AudioTrack.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_16BIT);
        int size = Math.max(min * 2, rate * 2 * channels / 10);   // about 100 ms
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(rate)
                            .setChannelMask(mask)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build())
                    .setBufferSizeInBytes(size)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            track.play();
            trackRate = rate;
            trackChannels = channels;
            framesWritten = 0;
            Log.i(TAG, "audio track " + rate + " Hz " + channels + " ch, buffer " + size);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "audio track failed", e);
            track = null;
            return false;
        }
    }

    private void stopTrack() {
        if (track == null) return;
        try {
            track.pause();
            track.flush();
        } catch (IllegalStateException ignored) {
            // releasing anyway
        }
        track.release();
        track = null;
    }

    private void decodeAac(byte[] frame, int rate, int channels, byte[] asc, long ptsUs) {
        if (aac == null || rate != aacRate || channels != aacChannels) {
            stopAac();
            try {
                MediaFormat f = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, channels);
                f.setByteBuffer("csd-0", ByteBuffer.wrap(asc));
                f.setInteger(MediaFormat.KEY_IS_ADTS, 0);
                aac = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
                aac.configure(f, null, null, 0);
                aac.start();
                aacRate = rate;
                aacChannels = channels;
                Log.i(TAG, "aac decoder " + aac.getName() + " " + rate + "/" + channels);
            } catch (Exception e) {
                Log.e(TAG, "aac decoder failed", e);
                stopAac();
                return;
            }
        }
        try {
            int in = aac.dequeueInputBuffer(10_000);
            if (in >= 0) {
                ByteBuffer b = aac.getInputBuffer(in);
                if (b != null && b.capacity() >= frame.length) {
                    b.clear();
                    b.put(frame);
                    aac.queueInputBuffer(in, 0, frame.length, Math.max(ptsUs, 0), 0);
                } else {
                    aac.queueInputBuffer(in, 0, 0, 0, 0);
                }
            } else {
                dropped += 1024;
            }
            while (true) {
                int o = aac.dequeueOutputBuffer(info, 0);
                if (o < 0) break;
                ByteBuffer ob = aac.getOutputBuffer(o);
                if (ob != null && info.size > 0) {
                    if (out.length < info.size) out = new byte[info.size];
                    ob.position(info.offset);
                    ob.get(out, 0, info.size);
                    MediaFormat of = aac.getOutputFormat();
                    int r = of.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? of.getInteger(MediaFormat.KEY_SAMPLE_RATE) : rate;
                    int ch = of.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? of.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : channels;
                    write(out, info.size, r, ch);
                }
                aac.releaseOutputBuffer(o, false);
            }
        } catch (IllegalStateException e) {
            Log.w(TAG, "aac: " + e);
            stopAac();
        }
    }

    private void stopAac() {
        if (aac == null) return;
        try {
            aac.stop();
        } catch (Exception ignored) {
            // releasing anyway
        }
        aac.release();
        aac = null;
    }
}
