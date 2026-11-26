// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

/**
 * The Z9X HB colour space row (stock picture_gamut_athenaultra of the dapengse / dapenghm / dapengfhm trees) as stock
 * shows it: newsettings PictureFactory binds the row with no HDR condition and PictureViewModel sends 102 for any
 * signal, so the row stays while an HDR10 / HLG / Dolby Vision source plays (a tester on HDMI 2 forced to 4K 60 Hz
 * HDR10 found it missing). Lumen hides it only in the AI picture modes 10..17, where the vendor refuses 102
 * ("cs not support change for 0-0-16-0", hb-t6 device log) and the row would jump back after every choice. While HDR
 * plays and the read-back after a write disagrees, the row says why (the stock picture_gamut_athenaultra_mutex case).
 * Plain Java, host tested (test/picture).
 */
final class ColorSpaceRule {
    static final int AI_FIRST = 10, AI_LAST = 17;

    private ColorSpaceRule() {}

    /** The row shows when the vendor answered the read and the picture mode is not an AI one. */
    static boolean shown(Integer pictureMode, Integer colorSpace) {
        return colorSpace != null && !aiMode(pictureMode);
    }

    static boolean aiMode(Integer pictureMode) {
        return pictureMode != null && pictureMode >= AI_FIRST && pictureMode <= AI_LAST;
    }

    /** A write the vendor did not keep while an HDR signal plays: the row tells the user (a toast), the read-back stays. */
    static boolean hdrRefused(boolean hdr, int wanted, Integer readBack) {
        return hdr && readBack != null && readBack != wanted;
    }
}
