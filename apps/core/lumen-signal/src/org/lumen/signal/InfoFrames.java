// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.signal;

import java.util.function.Predicate;

/**
 * Two InfoFrames of the MStar receiver dump beside the AVI one (HdmiRxInfo): the HDR static metadata (CTA-861.3
 * Dynamic Range and Mastering, "HDR packet") and the source's own name (CTA-861 Source Product Descriptor, "SPD
 * packet"). Pure, host tested.
 *
 * <p>The receivers print packets in different layouts (PacketLayout). The Z9X HB capture prints its HDR packet as
 * byte-swapped 16-bit words from header byte 1 (1a 01 02 0d 34 00 aa 21 ..: length 26, version 1, EOTF 2 = PQ, checksum
 * 0x0d, BT.2020 primaries, D65 white, 1000 and 0.005 cd/m2); the Z9X capture prints its SPD packet from PB1 ("SCEI",
 * "PS5", source kind 8 = game) and its VS packet with the header and a pad byte in front. No HDR capture of the Z9X or
 * of the X50 family (the Z9X's platform, the same receiver driver) exists yet, so a layout counts only when the packet
 * fits it: the header bytes (version 1, length 26) where the layout prints them, the checksum when every byte is printed
 * (the HB's 28 bytes end one byte early: that byte, MaxFALL's high byte, follows from the checksum), and every field
 * inside the standard's ranges. A packet that no layout explains shows nothing.
 */
final class InfoFrames {
    static final int DRM_TYPE = 0x87, DRM_VERSION = 1, DRM_LENGTH = 26;
    static final int SPD_TYPE = 0x83, SPD_VERSION = 1, SPD_LENGTH = 25;
    /** CTA-861.3 EOTF 2: SMPTE ST 2084 (PQ), the only one whose static metadata the panel shows */
    static final int EOTF_PQ = 2, EOTF_MAX = 3;
    /** chromaticity unit 0.00002, white point window around D65 (0.3127, 0.3290), match tolerance 0.005 */
    private static final int UNIT_MAX = 50000, WHITE_MIN = 12500, WHITE_MAX = 21000, NEAR = 250;
    /** green, blue, red of BT.2020, DCI-P3 (D65) and BT.709, in 0.00002 */
    private static final int[][] BT2020 = {{8500, 39850}, {6550, 2300}, {35400, 14600}},
            P3 = {{13250, 34500}, {7500, 3000}, {34000, 16000}},
            BT709 = {{15000, 30000}, {7500, 3000}, {32000, 16500}};
    private static final int VENDOR = 8, PRODUCT = 16, ASCII_MIN = 0x20, ASCII_MAX = 0x7E;

    private InfoFrames() {}

    /** Fills {@code into} with the HDR static metadata of the printed HDR packet bytes, when a layout proves them. */
    static void hdr(int[] printed, HdmiRxInfo into) {
        int[] pb = data(printed, DRM_TYPE, DRM_VERSION, DRM_LENGTH, InfoFrames::drmFields);
        if (pb == null) return;
        into.eotf = pb[1] & 7;
        into.masterPrimaries = primaries(pb);
        into.masterMax = Math.max(0, u16(pb, 19));
        into.masterMin = Math.max(0, u16(pb, 21));
        into.maxCll = Math.max(0, u16(pb, 23));
        into.maxFall = Math.max(0, u16(pb, 25));
    }

    /** Fills {@code into} with the source's vendor and product name and kind of the printed SPD packet bytes. */
    static void spd(int[] printed, HdmiRxInfo into) {
        int[] pb = data(printed, SPD_TYPE, SPD_VERSION, SPD_LENGTH, InfoFrames::spdFields);
        if (pb == null || pb[1 + VENDOR + PRODUCT] < 0) return;
        into.sourceVendor = text(pb, 1, VENDOR);
        into.sourceProduct = text(pb, 1 + VENDOR, PRODUCT);
        into.sourceKind = pb[1 + VENDOR + PRODUCT];
    }

    /**
     * PB0..PB{@code length} in the first layout the packet proves, -1 for a byte past the printed ones: the header and
     * the checksum where the layout prints them (one missing data byte is solved from the checksum; with more missing,
     * as the Z9X VS layout would leave for a 26-byte packet in 28 printed bytes, the header alone), and {@code fields}
     * in every layout.
     */
    static int[] data(int[] printed, int type, int version, int length, Predicate<int[]> fields) {
        for (PacketLayout l : PacketLayout.values()) {
            int[] pb = new int[length + 1];
            int missing = -1, unknown = 0;
            for (int k = 0; k <= length; k++) {
                pb[k] = l.pb(printed, k);
                if (pb[k] < 0 && k > 0) { missing = k; unknown++; }
            }
            if (l.checked()) {
                if (l.hb(printed, 1) != version || l.hb(printed, 2) != length || pb[0] < 0) continue;
                if (l == PacketLayout.HEADER_PAD && l.hb(printed, 0) != type) continue;
                if (unknown <= 1) {
                    int sum = type + version + length;
                    for (int k = 0; k <= length; k++) if (k != missing) sum += pb[k];
                    if (unknown == 1) pb[missing] = (256 - (sum & 0xFF)) & 0xFF;
                    else if ((sum & 0xFF) != 0) continue;
                }
            } else if (unknown > 0 || !strict(pb)) {
                continue;
            }
            if (fields.test(pb)) return pb;
        }
        return null;
    }

    /** A layout without header and checksum must not read an empty or foreign buffer: some byte set, none above 0xFF. */
    private static boolean strict(int[] pb) {
        boolean any = false;
        for (int k = 1; k < pb.length; k++) {
            if (pb[k] > 0xFF) return false;
            any |= pb[k] != 0;
        }
        return any;
    }

    private static boolean drmFields(int[] pb) {
        if ((pb[1] & 7) > EOTF_MAX || pb[1] > 7 || pb[2] != 0) return false;
        boolean none = true;
        for (int k = 3; k <= 18; k++) {
            if (pb[k] < 0) return false;
            none &= pb[k] == 0;
        }
        if (none) return true;                         // "not told": the standard's zero metadata
        for (int k = 3; k <= 13; k += 2) if (u16(pb, k) > UNIT_MAX) return false;
        int wx = u16(pb, 15), wy = u16(pb, 17);
        return wx >= WHITE_MIN && wx <= WHITE_MAX && wy >= WHITE_MIN && wy <= WHITE_MAX;
    }

    private static boolean spdFields(int[] pb) {
        boolean name = false;
        for (int k = 1; k <= VENDOR + PRODUCT; k++) {
            int c = pb[k];
            if (c < 0 || (c != 0 && (c < ASCII_MIN || c > ASCII_MAX))) return false;
            if (k <= VENDOR && c > ASCII_MIN) name = true;
        }
        return name && pb[1 + VENDOR + PRODUCT] <= 0x0D;
    }

    /** "bt2020", "p3" or "bt709" when the three primaries (any order) are those of the standard, else null. */
    private static String primaries(int[] pb) {
        int[][] got = new int[3][];
        for (int i = 0; i < 3; i++) got[i] = new int[] {u16(pb, 3 + 4 * i), u16(pb, 5 + 4 * i)};
        if (same(got, BT2020)) return HdmiRxInfo.BT2020;
        if (same(got, P3)) return HdmiRxInfo.P3;
        if (same(got, BT709)) return HdmiRxInfo.BT709;
        return null;
    }

    private static boolean same(int[][] got, int[][] ref) {
        for (int[] r : ref) {
            boolean found = false;
            for (int[] g : got) found |= Math.abs(g[0] - r[0]) <= NEAR && Math.abs(g[1] - r[1]) <= NEAR;
            if (!found) return false;
        }
        return true;
    }

    /** The little-endian 16-bit field at PB{@code k}, -1 when a byte of it was not printed. */
    private static int u16(int[] pb, int k) { return pb[k] < 0 || pb[k + 1] < 0 ? -1 : pb[k] | (pb[k + 1] << 8); }

    private static String text(int[] pb, int from, int n) {
        StringBuilder b = new StringBuilder();
        for (int k = from; k < from + n && pb[k] != 0; k++) b.append((char) pb[k]);
        String s = b.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
