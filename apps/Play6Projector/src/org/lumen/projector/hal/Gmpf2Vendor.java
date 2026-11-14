package org.lumen.projector.hal;

/**
 * The Play 6 vendor builds whose IGmpf2 the guard accepts: the hash recorded offline from each stock vendor library
 * (stock analysis) and its code table. The constants of HalCodes are the v1.7.116 numbers; v1.1.67 has
 * the same methods minus five (getPowerSupplyVoltage and the four MIoT calls), so every later code is one or five lower
 * (stock analysis). A method without a v1.1.67 row does not exist there.
 */
enum Gmpf2Vendor {
    V1_7_116("v1.7.116", "6816d9efd3c7da9c522bfa05b090f7a0795081319ee20396d1343c81a6380157", null),
    V1_1_67("v1.1.67", "fa2afea7cb29be93bba20d99a3d60adadbaebe32a9cfa14964b074c781910b37", new int[][] {
            {HalCodes.GMPF2_GET_GAME_MODE_PROP, 33},
            {HalCodes.GMPF2_SET_GAME_MODE_TYPE, 34},
            {HalCodes.GMPF2_SET_GAME_MODE_STATE, 35},
            {HalCodes.GMPF2_GET_AUTO_ECO_MODE, 46},
            {HalCodes.GMPF2_GET_COPOWER_BATTERY_LEVEL, 50},
            {HalCodes.GMPF2_GET_COPOWER_DEVICE_TYPE, 51},
            {HalCodes.GMPF2_GET_POWER_ON_AF_USE_DISTANCE, 62},
            {HalCodes.GMPF2_SET_POWER_ON_AF_USE_DISTANCE, 63},
            {HalCodes.GMPF2_SET_SLIDE_FUNC, 68},
            {HalCodes.GMPF2_GET_SLIDE_FUNC, 69},
            {HalCodes.GMPF2_GET_IS_AUTO_ATMOSPHERE_LIGHT, 70},
            {HalCodes.GMPF2_GET_CONVEX_LENS_TYPE, 74},
            {HalCodes.GMPF2_GET_STAND_VERTICAL_ANGLE, 80},
            {HalCodes.GMPF2_IS_CONVEX_LENS_IN, 83},
            {HalCodes.GMPF2_SET_PUSH_PULL_SCREEN_STATE, 124},
            {HalCodes.GMPF2_GET_SCREEN_ROTATION_INFO, 129},
            {HalCodes.GMPF2_SET_SCREEN_ROTATION, 137},
            {HalCodes.GMPF2_GET_ALLM_STATUS, 207},
            {HalCodes.GMPF2_GET_ALLM_ENABLE_STATE, 208},
            {HalCodes.GMPF2_GET_TV_GAMMA_LEVEL, 223},
            {HalCodes.GMPF2_SET_TV_GAMMA_LEVEL, 224},
            {HalCodes.GMPF2_GET_TV_DYNAMIC_CONTRAST_ENABLE, 225},
            {HalCodes.GMPF2_SET_TV_DYNAMIC_CONTRAST_ENABLE, 226},
    });

    static final int ABSENT = -1;

    final String label;
    final String hash;
    private final int[][] codes;

    Gmpf2Vendor(String label, String hash, int[][] codes) {
        this.label = label;
        this.hash = hash;
        this.codes = codes;
    }

    /** The build whose recorded hash this is, or null. */
    static Gmpf2Vendor of(String hash) {
        for (Gmpf2Vendor v : values()) if (v.hash.equals(hash)) return v;
        return null;
    }

    /** The code of this build for a v1.7.116 code, or ABSENT. */
    int code(int v1_7_116) {
        if (codes == null) return v1_7_116;
        for (int[] row : codes) if (row[0] == v1_7_116) return row[1];
        return ABSENT;
    }
}
