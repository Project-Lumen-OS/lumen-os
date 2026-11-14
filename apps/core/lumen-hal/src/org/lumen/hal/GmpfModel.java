package org.lumen.hal;

/**
 * The MT9681 models whose Lumen image carries the X50 family apps: the image codename (ro.lumen.device), the XGIMI
 * model (ro.boot.xgimi.modelname) and the stock xgimi.hardware.gmpf V1 interface hash rule of its firmwares. One row
 * per registry entry (devices/&lt;codename&gt;/device.conf models, hal_hash); tools/devices/test/gmpf_model_test.sh keeps
 * them equal. Plain Java, host-tested.
 */
public enum GmpfModel {
    X50_ULTRA("g0111", "G0111", GmpfHash.X50_RULE),
    X50_ULTRA_MAX("g0100", "G0100", GmpfHash.X50_RULE),
    RS30_ULTRA_MAX("g0155", "G0155", GmpfHash.RS30_RULE);

    public static final int VERSION = 1;

    public final String codename;
    public final String model;
    public final HashRule rule;

    GmpfModel(String codename, String model, HashRule rule) {
        this.codename = codename;
        this.model = model;
        this.rule = rule;
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
