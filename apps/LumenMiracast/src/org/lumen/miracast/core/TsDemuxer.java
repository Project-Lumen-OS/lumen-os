// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

import java.util.ArrayList;
import java.util.List;

/**
 * MPEG-2 transport stream demuxer for the WFD media stream (ISO/IEC 13818-1): PAT, PMT, then PES
 * packets of the elementary streams the sink plays. WFD sources send one program with an H.264
 * stream (type 0x1B), and AAC ADTS (0x0F) or LPCM (0x83, private stream 1) audio.
 *
 * A PES is handed out as soon as it is complete: when its declared length is reached (audio), or
 * when the next PES of that PID starts (video PES usually have length 0). Continuity-counter gaps
 * mark the PES being assembled as damaged; the listener still gets it (flagged) so it can decide
 * (video: wait for the next key frame and ask for one).
 */
public final class TsDemuxer {
    public static final int PACKET = 188;
    public static final int ST_H264 = 0x1B, ST_HEVC = 0x24, ST_AAC_ADTS = 0x0F, ST_LPCM = 0x83, ST_AC3 = 0x81;

    public interface Listener {
        /** A complete PES payload. ptsUs = -1 when the PES had no PTS. */
        void onPes(int pid, int streamType, long ptsUs, byte[] data, int off, int len, boolean damaged);

        /** The program's stream list changed (first PMT or a new version). */
        void onStreams(List<Stream> streams);

        /** Continuity error on an elementary stream PID. */
        void onDiscontinuity(int pid, int streamType);
    }

    public static final class Stream {
        public final int pid, type;

        Stream(int pid, int type) {
            this.pid = pid;
            this.type = type;
        }

        @Override
        public String toString() {
            return "pid=" + pid + " type=0x" + Integer.toHexString(type);
        }
    }

    private static final class Pes {
        final int type;
        byte[] buf = new byte[64 * 1024];
        int len;
        boolean started, damaged;
        int cc = -1;

        Pes(int type) {
            this.type = type;
        }
    }

    private final Listener listener;
    private final byte[] carry = new byte[PACKET];
    private int carryLen;
    private int pmtPid = -1;
    private int pmtVersion = -1;
    private final Pes[] pes = new Pes[8192];
    private final byte[] section = new byte[4096];
    private int sectionLen, sectionPid = -1;
    private long packets, syncLoss;

    public TsDemuxer(Listener listener) {
        this.listener = listener;
    }

    /** Feeds any number of bytes (normally a multiple of 188 from one RTP payload). */
    public void feed(byte[] b, int off, int len) {
        int end = off + len;
        if (carryLen > 0) {
            int need = PACKET - carryLen;
            int n = Math.min(need, len);
            System.arraycopy(b, off, carry, carryLen, n);
            carryLen += n;
            off += n;
            if (carryLen < PACKET) return;
            packet(carry, 0);
            carryLen = 0;
        }
        while (off < end) {
            if (b[off] != 0x47) { // resync on the next 0x47
                syncLoss++;
                off++;
                continue;
            }
            if (end - off < PACKET) {
                System.arraycopy(b, off, carry, 0, end - off);
                carryLen = end - off;
                return;
            }
            packet(b, off);
            off += PACKET;
        }
    }

    /** Ends the stream: emits whatever PES is in progress (on teardown). */
    public void flush() {
        for (int pid = 0; pid < pes.length; pid++) {
            Pes p = pes[pid];
            if (p != null && p.started) emit(pid, p);
        }
    }

    public long packets() {
        return packets;
    }

    public long syncLoss() {
        return syncLoss;
    }

    private void packet(byte[] b, int o) {
        packets++;
        int b1 = b[o + 1] & 0xff, b2 = b[o + 2] & 0xff, b3 = b[o + 3] & 0xff;
        if ((b1 & 0x80) != 0) return; // transport error indicator
        boolean pusi = (b1 & 0x40) != 0;
        int pid = ((b1 & 0x1f) << 8) | b2;
        int afc = (b3 >> 4) & 3;
        int cc = b3 & 0x0f;
        int p = o + 4;
        int end = o + PACKET;
        if (afc == 2 || afc == 3) {
            int al = b[p] & 0xff;
            p += 1 + al;
            if (p > end) return;
        }
        boolean hasPayload = afc == 1 || afc == 3;
        if (!hasPayload || p >= end) return;
        if (pid == 0 || pid == pmtPid) {
            psi(pid, pusi, b, p, end);
            return;
        }
        Pes s = pes[pid];
        if (s == null) return;
        if (s.cc >= 0 && cc != ((s.cc + 1) & 0xf)) {
            if (cc == s.cc) return; // duplicate packet
            s.damaged = true;
            listener.onDiscontinuity(pid, s.type);
        }
        s.cc = cc;
        if (pusi) {
            if (s.started) emit(pid, s);
            s.started = true;
            s.len = 0;
        } else if (!s.started) {
            return; // wait for the start of a PES
        }
        append(s, b, p, end - p);
        // complete by declared length: emit at once (lowest latency for audio)
        if (s.len >= 6) {
            int declared = ((s.buf[4] & 0xff) << 8) | (s.buf[5] & 0xff);
            if (declared > 0 && s.len >= 6 + declared) emit(pid, s);
        }
    }

    private static void append(Pes s, byte[] b, int off, int n) {
        if (s.len + n > s.buf.length) {
            int nc = s.buf.length;
            while (nc < s.len + n) nc *= 2;
            if (nc > 8 * 1024 * 1024) { // a broken stream: drop the PES rather than grow without bound
                s.len = 0;
                s.started = false;
                return;
            }
            byte[] nb = new byte[nc];
            System.arraycopy(s.buf, 0, nb, 0, s.len);
            s.buf = nb;
        }
        System.arraycopy(b, off, s.buf, s.len, n);
        s.len += n;
    }

    private void emit(int pid, Pes s) {
        boolean damaged = s.damaged;
        s.started = false;
        s.damaged = false;
        byte[] d = s.buf;
        int n = s.len;
        s.len = 0;
        if (n < 9 || d[0] != 0 || d[1] != 0 || d[2] != 1) return;
        int flags2 = d[7] & 0xff;
        int hdrLen = d[8] & 0xff;
        int payload = 9 + hdrLen;
        if (payload > n) return;
        long ptsUs = -1;
        if ((flags2 & 0x80) != 0 && hdrLen >= 5) {
            long pts = ((long) (d[9] & 0x0e) << 29) | ((d[10] & 0xff) << 22) | ((d[11] & 0xfe) << 14)
                    | ((d[12] & 0xff) << 7) | ((d[13] & 0xfe) >> 1);
            ptsUs = pts * 100 / 9;
        }
        int declared = ((d[4] & 0xff) << 8) | (d[5] & 0xff);
        int end = declared > 0 ? Math.min(n, 6 + declared) : n;
        listener.onPes(pid, s.type, ptsUs, d, payload, end - payload, damaged);
    }

    // ------------------------------------------------------------------ PSI (PAT, PMT)

    private void psi(int pid, boolean pusi, byte[] b, int p, int end) {
        if (pusi) {
            int pointer = b[p] & 0xff;
            p += 1 + pointer;
            if (p >= end) return;
            sectionLen = 0;
            sectionPid = pid;
        } else if (sectionPid != pid || sectionLen == 0) {
            return;
        }
        int n = Math.min(end - p, section.length - sectionLen);
        System.arraycopy(b, p, section, sectionLen, n);
        sectionLen += n;
        if (sectionLen < 3) return;
        int total = 3 + (((section[1] & 0x0f) << 8) | (section[2] & 0xff));
        if (sectionLen < total || total > section.length) return;
        if (pid == 0) pat(section, total);
        else pmt(section, total);
        sectionLen = 0;
    }

    private void pat(byte[] s, int total) {
        if ((s[0] & 0xff) != 0x00 || total < 12) return;
        for (int i = 8; i + 4 <= total - 4; i += 4) {
            int program = ((s[i] & 0xff) << 8) | (s[i + 1] & 0xff);
            int pid = ((s[i + 2] & 0x1f) << 8) | (s[i + 3] & 0xff);
            if (program != 0) {
                if (pid != pmtPid) {
                    pmtPid = pid;
                    pmtVersion = -1;
                }
                return;
            }
        }
    }

    private void pmt(byte[] s, int total) {
        if ((s[0] & 0xff) != 0x02 || total < 16) return;
        int version = (s[5] >> 1) & 0x1f;
        if (version == pmtVersion) return;
        pmtVersion = version;
        int progInfo = ((s[10] & 0x0f) << 8) | (s[11] & 0xff);
        int i = 12 + progInfo;
        List<Stream> streams = new ArrayList<>();
        for (int pid = 0; pid < pes.length; pid++) pes[pid] = null;
        while (i + 5 <= total - 4) {
            int type = s[i] & 0xff;
            int pid = ((s[i + 1] & 0x1f) << 8) | (s[i + 2] & 0xff);
            int esInfo = ((s[i + 3] & 0x0f) << 8) | (s[i + 4] & 0xff);
            streams.add(new Stream(pid, type));
            if (pid != 0 && pid != pmtPid) pes[pid] = new Pes(type);
            i += 5 + esInfo;
        }
        listener.onStreams(streams);
    }
}
