// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

/** Small H.264 Annex B helpers: which NAL unit types an access unit carries. */
public final class H264 {
    private H264() {}

    public static final int NAL_IDR = 5, NAL_SEI = 6, NAL_SPS = 7, NAL_PPS = 8, NAL_AUD = 9;

    /** Bit set of the NAL unit types (1 << type) found after 00 00 01 start codes in d[off..off+len). */
    public static int nalTypes(byte[] d, int off, int len) {
        int mask = 0;
        int end = off + len;
        for (int i = off; i + 3 < end; i++) {
            if (d[i] == 0 && d[i + 1] == 0 && d[i + 2] == 1) {
                mask |= 1 << (d[i + 3] & 0x1f);
                i += 3;
            }
        }
        return mask;
    }

    public static boolean isKeyFrame(int nalMask) {
        return (nalMask & (1 << NAL_IDR)) != 0;
    }

    public static boolean hasParameterSets(int nalMask) {
        return (nalMask & (1 << NAL_SPS)) != 0 && (nalMask & (1 << NAL_PPS)) != 0;
    }
}
