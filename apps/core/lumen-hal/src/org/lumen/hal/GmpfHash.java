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
    /** RS30 Ultra Max G0155, v1.1.42: the X50's 1199 codes unchanged plus 29 new ones */
    public static final String RS30 = "3a8aa5e6c42eb994e5922a154ac5a0bae146628f";

    public static final HashRule X50_RULE = new HashRule(X50, X50_V1254_FIRMWARE, X50_V1254);
    public static final HashRule RS30_RULE = new HashRule(RS30);

    private GmpfHash() {}
}
