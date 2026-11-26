// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.signal;

/**
 * How an MStar receiver dump prints the bytes of one InfoFrame ("HDR packet Byte k: 0x..", "SPD packet Byte k: ..").
 * The receivers do not print every packet the same way (InfoFrames): a layout names where header byte HBi and data
 * byte PBk sit among the printed bytes, -1 = this layout does not print it.
 */
enum PacketLayout {
    /** 16-bit words from header byte 1, high byte first: HB2 HB1 PB1 PB0 PB3 PB2 .. (Z9X HB, its HDR packet) */
    SWAPPED,
    /** the three header bytes, one pad byte, then PB0 .. (Z9X, its VS packet) */
    HEADER_PAD,
    /** header bytes 1 and 2, then PB0 .. */
    FROM_HB1,
    /** PB1 first, no header, no checksum (Z9X, its AVI and SPD packets) */
    FROM_PB1;

    /** True when this layout prints the header (version, length) and PB0, so the checksum can be tested. */
    boolean checked() { return this != FROM_PB1; }

    /** Header byte {@code i} (0 type, 1 version, 2 length) of the printed bytes {@code b}. */
    int hb(int[] b, int i) {
        switch (this) {
            case SWAPPED: return i == 0 ? -1 : at(b, (i - 1) ^ 1);
            case HEADER_PAD: return at(b, i);
            case FROM_HB1: return i == 0 ? -1 : at(b, i - 1);
            default: return -1;
        }
    }

    /** Data byte PB{@code k} (PB0 = checksum) of the printed bytes {@code b}. */
    int pb(int[] b, int k) {
        switch (this) {
            case SWAPPED: return at(b, (k + 2) ^ 1);
            case HEADER_PAD: return at(b, k + 4);
            case FROM_HB1: return at(b, k + 2);
            default: return k == 0 ? -1 : at(b, k - 1);
        }
    }

    private static int at(int[] b, int i) { return i >= 0 && i < b.length ? b[i] : -1; }
}
