package org.lumen.hal;

/**
 * The MT9681 models whose Lumen image carries the X50 family apps: the image codename (ro.lumen.device), the XGIMI
 * model (ro.boot.xgimi.modelname) and the stock xgimi.hardware.gmpf V1 interface hash rule of its firmwares. One row
 * per registry entry (devices/&lt;codename&gt;/device.conf models, hal_hash); tools/devices/test/gmpf_model_test.sh keeps
 * them equal. Plain Java, host-tested.
 */
public enum GmpfModel {
    X50_ULTRA("g0111", "G0111", GmpfHash.X50_RULE, "hd_server2"),
    X50_ULTRA_MAX("g0100", "G0100", GmpfHash.X50_RULE, "hd_server2"),
    RS30_ULTRA_MAX("g0155", "G0155", GmpfHash.RS30_RULE, ""),
    TITAN_NOIR_PRO("h0062", "H0062", GmpfHash.TITAN_RULE, "hd_server2"),
    TITAN_NOIR_MAX("h0063", "H0063", GmpfHash.TITAN_RULE, "hd_server2");

    public static final int VERSION = 1;

    public final String codename;
    public final String model;
    public final HashRule rule;
    /**
     * The vendor service whose steady run shows that the person detection behind eye protection is up (X50EyeGuard
     * Detector), "" when the detection has no service of its own. X50: hd_server2 (xgimiconfig G0111/xlab/
     * HumanDetect.ini SOURCE_MODE 5, every *DETECT_START 0, stock v1.2.64 runs hd_server2 while the switch is on).
     * RS30: none. Its HumanDetect.ini has SOURCE_MODE 4 and MULTTOFDETECT_START 1, the multi-point ToF detection runs
     * as threads of xgimi_middle (libxgimi HumDet_Multtof_Cutout, hd_read-multtof, hd_cut-multtof), and the stock
     * v1.1.42 dump has the switch on (Env HumanDetect_OnOff=1) with nativeui running and no hd_server2 at all.
     * TITAN Noir Pro and Max: hd_server2, their xgimiconfig H0062 / H0063 xlab/HumanDetect.ini are the G0111 / G0100
     * files but for the folder name in one path (SOURCE_MODE 5, every *DETECT_START 0) in the overseas V1.2.99 and
     * V1.2.54 OTAs (one build for both models, the model chosen at boot).
     */
    public final String detector;

    GmpfModel(String codename, String model, HashRule rule, String detector) {
        this.codename = codename;
        this.model = model;
        this.rule = rule;
        this.detector = detector;
    }

    /**
     * The model whose own image runs on it: (ro.lumen.device, ro.boot.xgimi.modelname) is one row, and
     * ro.xgimi.modelname, when set, names the same model. Null for any other pair (another model's image, Z9X, HB).
     */
    public static GmpfModel of(String lumenDevice, String bootModel, String model) {
        String d = trim(lumenDevice), b = trim(bootModel), m = trim(model);
        if (!m.isEmpty() && !m.equals(b)) return null;
        for (GmpfModel g : values()) {
            if (g.codename.equals(d) && g.model.equals(b)) return g;
        }
        return null;
    }

    /** The hash rule of the model ro.boot.xgimi.modelname names; {@link HashRule#NONE} (nothing passes) for any other. */
    public static HashRule ruleOf(String bootModel) {
        String b = trim(bootModel);
        for (GmpfModel g : values()) {
            if (g.model.equals(b)) return g.rule;
        }
        return HashRule.NONE;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    /** Whether ro.lumen.device names an image of this family, whatever model it runs on. */
    public static boolean isFamilyImage(String lumenDevice) {
        String d = trim(lumenDevice);
        for (GmpfModel g : values()) {
            if (g.codename.equals(d)) return true;
        }
        return false;
    }
}
