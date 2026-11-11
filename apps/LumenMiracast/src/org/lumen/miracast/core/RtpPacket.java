// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

/** RTP fixed header (RFC 3550 5.1) of one received datagram; payload bounds point into the caller's buffer. */
public final class RtpPacket {
    public static final int PT_MP2T = 33;

    public int payloadType, seq, payloadOffset, payloadLength;
    public long timestamp, ssrc;
    public boolean marker;

    /**
     * Parses {@code buf[0..len)} into this object. False for anything that is not RTP version 2 or whose
     * CSRC list, extension or padding runs past the datagram.
     */
    public boolean parse(byte[] buf, int len) {
        if (len < 12) return false;
        int b0 = buf[0] & 0xff;
        if ((b0 >> 6) != 2) return false;
        boolean padding = (b0 & 0x20) != 0, ext = (b0 & 0x10) != 0;
        int cc = b0 & 0x0f;
        int b1 = buf[1] & 0xff;
        marker = (b1 & 0x80) != 0;
        payloadType = b1 & 0x7f;
        seq = ((buf[2] & 0xff) << 8) | (buf[3] & 0xff);
        timestamp = ((long) (buf[4] & 0xff) << 24) | ((buf[5] & 0xff) << 16) | ((buf[6] & 0xff) << 8) | (buf[7] & 0xff);
        ssrc = ((long) (buf[8] & 0xff) << 24) | ((buf[9] & 0xff) << 16) | ((buf[10] & 0xff) << 8) | (buf[11] & 0xff);
        int off = 12 + 4 * cc;
        if (off > len) return false;
        if (ext) {
            if (off + 4 > len) return false;
            int words = ((buf[off + 2] & 0xff) << 8) | (buf[off + 3] & 0xff);
            off += 4 + 4 * words;
            if (off > len) return false;
        }
        int end = len;
        if (padding) {
            int p = buf[len - 1] & 0xff;
            if (p == 0 || off + p > len) return false;
            end -= p;
        }
        payloadOffset = off;
        payloadLength = end - off;
        return true;
    }
}
