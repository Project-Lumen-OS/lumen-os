// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hal;

/**
 * Stock xgimi.hardware.gmpf V1 interface hashes (devices/&lt;codename&gt;/research/aidl_codes*.tsv headers) and the
 * {@link HashRule} of each model line built from them.
 */
public final class GmpfHash {
    /** X50 Ultra and Ultra Max, stock v1.2.64 */
    public static final String X50 = "715affd032170b4d256c28c9bbc3c6cbaf5de492";
    /** X50 Ultra and Ultra Max, stock v1.2.54: the v1.2.64 table without DisplayManager 217..223 */
    public static final String X50_V1254 = "ecadd76e79d0cdf0ad9fd7237cba71c772d83729";
    public static final String X50_V1254_FIRMWARE = "v1.2.54";
    /**
     * X50 Ultra and Ultra Max, stock v1.3.38: the v1.2.64 table (all 1199 rows the same) plus 53 appended methods,
     * devices/g0111/research/aidl_codes_v1338.tsv (gsi-stock analysis)
     */
    public static final String X50_V1338 = "dda0c216eced6e8f14cbda50c8d2156a5de168f5";
    public static final String X50_V1338_FIRMWARE = "v1.3.38";
    /**
     * TITAN Noir Pro H0062 (the overseas X50 Ultra), stock v1.2.99: the X50 v1.2.64 table (all 1199 rows the same) plus
     * 20 appended methods (DisplayManager 224..232, ProjectorFocusManager 122..127, SystemManager 257..260,
     * TemperatureManager 21), devices/h0062/research/aidl_codes_v1299.tsv. Its v1.2.54 is the X50 v1.2.54 hash.
     */
    public static final String TITAN_V1299 = "ad74d059b11cf5c5816cff11e285f8ec3bdc9270";
    public static final String TITAN_V1299_FIRMWARE = "v1.2.99";
    /** RS30 Ultra Max G0155, v1.1.42: the X50's 1199 codes unchanged plus 29 new ones */
    public static final String RS30 = "3a8aa5e6c42eb994e5922a154ac5a0bae146628f";
    /**
     * RS30 Ultra G0170, v1.2.5 (devices/g0170/research/aidl_codes_v125.tsv): an older table, the X50 v1.2.64 one
     * without 47 rows at the ends of five services, and its ProjectorFocusManager 107 is getUitiltShiftSwithStatus (112
     * on every other table): no lens memory, no isKeystoneAdjusted (GmpfFirmware).
     */
    public static final String RS30_ULTRA = "09e5998d368df12cc6f1662b8a6f3d8a54a5775b";

    /**
     * RS30 old line (RS30 Pro G0125, Pro Max G0124, Ultra G0114, Ultra Max G0115: one XGIMI build, the model chosen at
     * boot), stock v1.2.27 (devices/_family/rs30old/research/aidl_codes_v1227.tsv, official FULL OTA): the RS30 Ultra
     * v1.2.5 table, its ProjectorFocusManager 107 is getUitiltShiftSwithStatus too (no lens memory, no isKeystoneAdjusted).
     */
    public static final String RS30_OLD = "c7654bc598ee91a1e750fbc0bf84697acb1a3690";
    /** RS30 old line, stock v1.1.47 (aidl_codes_v1147.tsv): the v1.2.27 table without the shift flag (its ProjectorFocusManager ends at 106). */
    public static final String RS30_OLD_V1147 = "850a1b32c89dc7a084b5513b54d0883a2d1e7107";
    public static final String RS30_OLD_V1147_FIRMWARE = "v1.1.47";

    /**
     * MIRA 4K G0109, MIRA 4K Pro G0110 and AURA 3 Max G0104 (bollywood, bollywoodpro, oscar: one XGIMI build, the model
     * chosen at boot), stock v1.3.1 (devices/_family/mira/research/aidl_codes_v131.tsv, official FULL OTA): the G0155
     * v1.1.42 table with no code moved (lens memory at ProjectorFocusManager 107..111, isKeystoneAdjusted, the shift
     * flag at 112), no rainbow reduction (DisplayManager 203 / 205).
     */
    public static final String MIRA = "20a6fcc512dc6f26121c70204d9f90ea90988680";

    public static final HashRule X50_RULE = new HashRule(X50, X50_V1254_FIRMWARE, X50_V1254, X50_V1338_FIRMWARE, X50_V1338);
    public static final HashRule RS30_RULE = new HashRule(RS30);
    public static final HashRule RS30_ULTRA_RULE = new HashRule(RS30_ULTRA);
    /** the v1.2.27 hash, the v1.1.47 one on exactly v1.1.47 (the two firmwares of the RS30 old line) */
    public static final HashRule RS30_OLD_RULE = new HashRule(RS30_OLD, RS30_OLD_V1147_FIRMWARE, RS30_OLD_V1147);
    /** the one v1.3.1 hash (the only firmware of the MIRA and AURA 3 line read so far) */
    public static final HashRule MIRA_RULE = new HashRule(MIRA);
    /**
     * The TITAN line (H0061, H0062, H0063: one overseas XGIMI build, the model chosen at boot): the X50 v1.2.64 hash, the
     * X50 v1.2.54 hash on v1.2.54, the v1.2.99 hash on v1.2.99, and on the overseas v1.3.38 (dianahy build of
     * 2026-11-16, gsi-stock analysis) the X50 v1.3.38 hash: its gmpf ndk library, com.xgimi.middle.jar
     * and code table are byte-equal to the X50 v1.3.38 ones (devices/g0111/research/aidl_codes_v1338.tsv).
     */
    public static final HashRule TITAN_RULE = new HashRule(X50, X50_V1254_FIRMWARE, X50_V1254, TITAN_V1299_FIRMWARE, TITAN_V1299,
            X50_V1338_FIRMWARE, X50_V1338);

    private GmpfHash() {}
}
