// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

import java.io.ByteArrayOutputStream;

/**
 * Test-only MPEG-TS writer: PAT + PMT + PES packetized into 188-byte packets with continuity
 * counters, the way a WFD source (AOSP TSPacketizer, Windows) sends them. Not shipped.
 */
final class TsWriter {
    static final int PMT_PID = 0x100, VIDEO_PID = 0x1011, AUDIO_PID = 0x1100;
    private final int[] cc = new int[8192];
    private final int audioType;

    TsWriter(int audioType) {
        this.audioType = audioType;
    }

    byte[] psi() {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        // PAT: program 1 -> PMT_PID
        byte[] pat = {0x00, (byte) 0xB0, 0x0D, 0x00, 0x01, (byte) 0xC1, 0x00, 0x00,
            0x00, 0x01, (byte) (0xE0 | (PMT_PID >> 8)), (byte) PMT_PID, 0, 0, 0, 0};
        o.write(section(0, pat), 0, 188);
        // PMT: PCR on video, video 0x1B, audio
        byte[] pmt = {0x02, (byte) 0xB0, 0x17, 0x00, 0x01, (byte) 0xC1, 0x00, 0x00,
            (byte) (0xE0 | (VIDEO_PID >> 8)), (byte) VIDEO_PID, (byte) 0xF0, 0x00,
            0x1B, (byte) (0xE0 | (VIDEO_PID >> 8)), (byte) VIDEO_PID, (byte) 0xF0, 0x00,
            (byte) audioType, (byte) (0xE0 | (AUDIO_PID >> 8)), (byte) AUDIO_PID, (byte) 0xF0, 0x00,
            0, 0, 0, 0};
        o.write(section(PMT_PID, pmt), 0, 188);
        return o.toByteArray();
    }

    private byte[] section(int pid, byte[] sec) {
        byte[] p = new byte[188];
        java.util.Arrays.fill(p, (byte) 0xFF);
        p[0] = 0x47;
        p[1] = (byte) (0x40 | (pid >> 8));
        p[2] = (byte) pid;
        p[3] = (byte) (0x10 | next(pid));
        p[4] = 0; // pointer field
        System.arraycopy(sec, 0, p, 5, sec.length);
        return p;
    }

    private int next(int pid) {
        int c = cc[pid];
        cc[pid] = (c + 1) & 0xf;
        return c;
    }

    /** PES header + payload; lengthField=false writes PES_packet_length 0 (video). */
    static byte[] pes(int streamId, long pts90k, byte[] payload, boolean lengthField) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        int len = 3 + 5 + payload.length;
        o.write(0);
        o.write(0);
        o.write(1);
        o.write(streamId);
        o.write(lengthField ? (len >> 8) & 0xff : 0);
        o.write(lengthField ? len & 0xff : 0);
        o.write(0x80);
        o.write(0x80); // PTS only
        o.write(5);
        o.write((int) (0x21 | ((pts90k >> 29) & 0x0e)));
        o.write((int) ((pts90k >> 22) & 0xff));
        o.write((int) (0x01 | ((pts90k >> 14) & 0xfe)));
        o.write((int) ((pts90k >> 7) & 0xff));
        o.write((int) (0x01 | ((pts90k << 1) & 0xfe)));
        o.write(payload, 0, payload.length);
        return o.toByteArray();
    }

    /** Splits a PES into TS packets on pid; the last packet is padded with adaptation stuffing. */
    byte[] packetize(int pid, byte[] pes) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        int pos = 0;
        boolean first = true;
        while (pos < pes.length) {
            byte[] p = new byte[188];
            p[0] = 0x47;
            p[1] = (byte) ((first ? 0x40 : 0) | (pid >> 8));
            p[2] = (byte) pid;
            int remain = pes.length - pos;
            if (remain >= 184) {
                p[3] = (byte) (0x10 | next(pid));
                System.arraycopy(pes, pos, p, 4, 184);
                pos += 184;
            } else {
                p[3] = (byte) (0x30 | next(pid));
                int af = 183 - remain; // adaptation_field_length
                p[4] = (byte) af;
                if (af > 0) {
                    p[5] = 0x00;
                    for (int i = 6; i < 5 + af; i++) p[i] = (byte) 0xFF;
                }
                System.arraycopy(pes, pos, p, 5 + af, remain);
                pos += remain;
            }
            o.write(p, 0, 188);
            first = false;
        }
        return o.toByteArray();
    }

    /** RTP packets (7 TS packets each, PT 33) from a TS byte stream. */
    static java.util.List<byte[]> rtp(byte[] ts, int firstSeq) {
        java.util.List<byte[]> out = new java.util.ArrayList<>();
        int seq = firstSeq;
        for (int pos = 0; pos < ts.length; pos += 7 * 188) {
            int n = Math.min(7 * 188, ts.length - pos);
            byte[] p = new byte[12 + n];
            p[0] = (byte) 0x80;
            p[1] = 33;
            p[2] = (byte) (seq >> 8);
            p[3] = (byte) seq;
            p[8] = 0x12;
            p[9] = 0x34;
            System.arraycopy(ts, pos, p, 12, n);
            out.add(p);
            seq = (seq + 1) & 0xffff;
        }
        return out;
    }
}
