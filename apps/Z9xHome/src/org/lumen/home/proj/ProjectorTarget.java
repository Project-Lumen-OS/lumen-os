package org.lumen.home.proj;

/**
 * Which projector app Lumen Home talks to (plain Java, host-tested by data/ProjectorTargetTest). Keyed on
 * ro.boot.xgimi.modelname only, never on product or device: the Z9X, the X50 and the RS30 all report horse_popular.
 *
 *  - G0111 (X50 Ultra): org.lumen.x50.projector (apps/X50Projector). The X50 image has no org.lumen.projector; the X50
 *    app serves the same SHOW_PANEL / SHOW_SOURCE broadcasts and the standby-state provider under the same signature
 *    permissions (it defines them there), so the actions, extras and permissions of ProjectorBridge stay as they are.
 *  - G0100 (X50 Ultra Max, its own image g0100 since 2026-11-11, the same X50Projector app): org.lumen.x50.projector.
 *  - every other model (Z9X G0082, Z9X HB G0130, Play 6 G0058, an unknown or unreadable one): org.lumen.projector,
 *    as before.
 */
public final class ProjectorTarget {
    private ProjectorTarget() {}

    /** The projector app of the Z9X, the HB and the Play 6 images. */
    public static final String DEFAULT = "org.lumen.projector";
    /** The projector app of the X50 image. */
    public static final String X50 = "org.lumen.x50.projector";
    /** ro.boot.xgimi.modelname of the X50 Ultra. */
    public static final String MODEL_X50 = "G0111";
    /** ro.boot.xgimi.modelname of the X50 Ultra Max. */
    public static final String MODEL_X50_MAX = "G0100";

    /** The projector app for this ro.boot.xgimi.modelname (exact model, surrounding blanks ignored). */
    public static String packageFor(String bootModel) {
        String m = bootModel == null ? "" : bootModel.trim();
        return MODEL_X50.equals(m) || MODEL_X50_MAX.equals(m) ? X50 : DEFAULT;
    }
}
