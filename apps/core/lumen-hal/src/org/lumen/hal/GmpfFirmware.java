// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hal;

/**
 * Stock rows that only some XGIMI firmwares of a model line have: each names the gmpf V1 interface hashes whose
 * firmware offers it (the stock settings tree of that firmware shows the row and its code table has the calls,
 * gsi-stock analysis). Two checks use it: the menu offers the row only where the running firmware's hash
 * ({@link HashRule#hashOn}) is listed, and the app's transport sends a call of the row only after the guard accepted
 * a listed hash for its interface, so a v1.3.38 call never reaches a v1.2.64 vendor. Plain Java, host-tested.
 */
public enum GmpfFirmware {
    /**
     * The dynamic black page's level, low or high (DisplayManager 173 / 175, map 1 / 3). On v1.2.64 the vendor
     * dynamicBlack.ini forces the low level (force_level = 1) and the stock tree has no level row.
     */
    DYNAMIC_BLACK_LEVEL(GmpfHash.X50_V1338),
    /** The subtitle masks at the top and the bottom of the screen and their reset (DisplayManager 233 / 234 / 235). */
    SUBTITLE_MASK(GmpfHash.X50_V1338),
    /** The colour space line of the signal panel (GmTvManager 204 getHdmiColorSpaceInfo). */
    HDMI_COLOR_SPACE(GmpfHash.X50_V1338),
    /**
     * The colour uniformity adjustment of the keystone page (DisplayManager 217..223): the stock tree has it from
     * v1.3.38, the calls are in the X50 v1.2.64 table and the TITAN v1.2.99 one with the same codes and signatures
     * (devices/g0111/research/aidl_codes*.tsv, devices/h0062/research/aidl_codes_v1299.tsv), not in the v1.2.54 one.
     */
    COLOR_UNIFORMITY(GmpfHash.X50, GmpfHash.X50_V1338, GmpfHash.TITAN_V1299),
    /**
     * The calls every table has but the RS30 Ultra v1.2.5 one (devices/g0170/research/aidl_codes_v125.tsv):
     * DisplayManager 207 isKeystoneAdjusted, the lens memory ProjectorFocusManager 107..111 and 121, and the shift page
     * flag getUitiltShiftSwithStatus at 112.
     */
    KEYSTONE_ADJUSTED(GmpfHash.X50, GmpfHash.X50_V1254, GmpfHash.X50_V1338, GmpfHash.TITAN_V1299, GmpfHash.RS30),
    LENS_MEMORY(GmpfHash.X50, GmpfHash.X50_V1254, GmpfHash.X50_V1338, GmpfHash.TITAN_V1299, GmpfHash.RS30),
    SHIFT_PAGE(GmpfHash.X50, GmpfHash.X50_V1254, GmpfHash.X50_V1338, GmpfHash.TITAN_V1299, GmpfHash.RS30),
    /**
     * Rainbow reduction (DisplayManager 203 getRainbowReductionStatus, 205 setRainbowReductionStatus): every table but
     * the RS30 old line's v1.1.47 and v1.2.27 ones (devices/_family/rs30old/research/aidl_codes_v*.tsv), whose stock
     * tree has no picture_anti_rbe node either.
     */
    RAINBOW_REDUCTION(GmpfHash.X50, GmpfHash.X50_V1254, GmpfHash.X50_V1338, GmpfHash.TITAN_V1299, GmpfHash.RS30, GmpfHash.RS30_ULTRA),
    /** The shift page flag getUitiltShiftSwithStatus at ProjectorFocusManager 107 (RS30 Ultra v1.2.5, RS30 old line v1.2.27: its v1.1.47 table ends at 106). */
    SHIFT_PAGE_107(GmpfHash.RS30_ULTRA, GmpfHash.RS30_OLD);

    private final String[] hashes;

    GmpfFirmware(String... hashes) {
        this.hashes = hashes.clone();
    }

    /** Does the firmware whose gmpf interface answers {@code hash} offer this row? */
    public boolean on(String hash) {
        if (hash == null) return false;
        for (String h : hashes) if (h.equals(hash)) return true;
        return false;
    }

    /** Does the running firmware {@code fw} offer this row under {@code rule} (the menu's check, no vendor call)? */
    public boolean offered(HashRule rule, String fw) {
        return rule != null && on(rule.hashOn(fw));
    }
}
