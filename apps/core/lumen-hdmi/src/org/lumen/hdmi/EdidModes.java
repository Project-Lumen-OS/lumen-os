package org.lumen.hdmi;

/**
 * The stock "HDMI mode" row of every model and the vendor value behind each choice. Plain Java, host tested
 * (apps/core/lumen-hdmi/test). Evidence: devices/g0111/stock analysis
 *
 * <p>Stock rows (newsettings settings tree, page signal_setting_hdmi_version): Compatible (HDMI 1.4) and Enhanced
 * (HDMI 2.0), nothing else. The chain is the same on the Z9X, the HB and the X50 family (X50 Ultra and Ultra Max, Titan,
 * RS30): SignalManager.setHdmiVersion sends SignalEnum HDMI1_4 = 1 or HDMI2_0 = 2 (4 when the model's feature
 * edid_2_version_val is "3") to gmuiapi GmTvManager.setEDIDVersion, which writes {@code version - 1} to the vendor
 * (getEdidVersion / setEdidVersion: X50 family and HB TV 39 / 40, Z9X IGmpf 661 / 660). The read adds 1 back and
 * getHdmiVersion shows Enhanced only for the Enhanced value, Compatible for every other one.
 *
 * <p>The vendor (libxgimi Msrv_Tv_Control::_setEdidVersion) turns the raw value of the input on screen into a file
 * xgimiconfig/{model}/tv/{name}_{port}.bin: 0 edid14, 1 edid20 (edid20_non_hrr with support_hfr and
 * game mode 3.0), 2 edid14 outside game mode, 3 edid21. In game mode it picks the game files itself (edid21,
 * edid21_freesync, edid20 with 120 Hz, their _120hz variants) whenever game mode, its speed, VRR or FreeSync change:
 * no menu row writes those.
 *
 * <p>Model data: the model's edid_2_version_val (feature.xml): 1 (HDMI 2.0) on the Z9X G0082, the HB G0130 and the
 * X50 family (G0111, G0100, H0062, H0063, G0155).
 */
public final class EdidModes {
    /** The rows, in the stock order. */
    public static final int COMPATIBLE = 0, ENHANCED = 1;
    /** Vendor values (libxgimi EN_EDID_VERSION): 1.4, 2.0, the game mode request, 2.1. */
    public static final int RAW_14 = 0, RAW_20 = 1, RAW_GAME = 2, RAW_21 = 3;
    /** feature.xml edid_2_version_val: "1" = Enhanced is HDMI 2.0, "3" = Enhanced is HDMI 2.1. */
    public static final int FEATURE_HDMI_20 = 1, FEATURE_HDMI_21 = 3;

    /** SignalEnum HDMI1_4 / HDMI2_0 and the gmuiapi step from it to the vendor value. */
    private static final int SIGNAL_14 = 1, SIGNAL_20 = 2, SIGNAL_21 = 4, GMUIAPI_STEP = 1;

    private final int enhanced;

    private EdidModes(int enhanced) {
        this.enhanced = enhanced;
    }

    /** The rows of a model with this edid_2_version_val. */
    public static EdidModes of(int edid2VersionVal) {
        if (edid2VersionVal != FEATURE_HDMI_20 && edid2VersionVal != FEATURE_HDMI_21) {
            throw new IllegalArgumentException("edid_2_version_val " + edid2VersionVal);
        }
        return new EdidModes((edid2VersionVal == FEATURE_HDMI_21 ? SIGNAL_21 : SIGNAL_20) - GMUIAPI_STEP);
    }

    /** The vendor value setEdidVersion gets for a row. */
    public int wire(int row) {
        if (row == COMPATIBLE) return SIGNAL_14 - GMUIAPI_STEP;
        if (row == ENHANCED) return enhanced;
        throw new IllegalArgumentException("HDMI mode row " + row);
    }

    /** The row a getEdidVersion read shows (stock getHdmiVersion): Enhanced for its own value only. */
    public int row(int raw) {
        return raw == enhanced ? ENHANCED : COMPATIBLE;
    }

    /** The row's own value is not stored yet (a value left by an older Lumen also counts): write it. */
    public boolean needsWrite(int raw, int row) {
        return raw != wire(row);
    }

    /** The other row's value: the HDMI mode switch of the old X50 menu and the relink flip. */
    public int other(int raw) {
        return wire(row(raw) == ENHANCED ? COMPATIBLE : ENHANCED);
    }

    /** A value one of the rows writes: the only ones setEdidVersion may get. */
    public boolean writable(int raw) {
        return raw == wire(COMPATIBLE) || raw == wire(ENHANCED);
    }

    /** Every value a row may write, Compatible first (the relink writes back only these). */
    public int[] wires() {
        return new int[] {wire(COMPATIBLE), wire(ENHANCED)};
    }
}
