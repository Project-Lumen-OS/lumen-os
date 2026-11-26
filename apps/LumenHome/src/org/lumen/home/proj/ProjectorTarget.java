// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.proj;

/**
 * Which projector app Lumen Home talks to (plain Java, host-tested by data/ProjectorTargetTest). Keyed on the image,
 * never on the model, the product or the device: the Z9X, the X50 and the RS30 all report horse_popular.
 *
 *  - an X50 family image (ro.z9x.device=x50: the X50 recipe devices/g0111/image/steps.tsv and its prop files, the same
 *    value LumenDsuInstall's X50 profiles and verify_tree_x50.py require): org.lumen.x50.projector (apps/X50Projector).
 *    Those images have no org.lumen.projector; the X50 app serves the same SHOW_PANEL / SHOW_SOURCE broadcasts and the
 *    standby-state provider under the same signature permissions (it defines them there), so the actions, extras and
 *    permissions of ProjectorBridge stay as they are. A new model of the family needs no change here.
 *  - the RS30 images (ro.lumen.device=g0155 RS30 Ultra Max, g0170 RS30 Ultra: their recipe on the bare Lineage base, no
 *    ro.z9x.device, the same X50Projector app): org.lumen.x50.projector.
 *  - every other image (Z9X ro.z9x.device=z9x, the HB, the Play 6, an unset or unreadable value): org.lumen.projector,
 *    as before.
 */
public final class ProjectorTarget {
    private ProjectorTarget() {}

    /** The projector app of the Z9X, the HB and the Play 6 images. */
    public static final String DEFAULT = "org.lumen.projector";
    /** The projector app of the X50 family images. */
    public static final String X50 = "org.lumen.x50.projector";
    /** ro.z9x.device of every X50 family image. */
    public static final String X50_IMAGE = "x50";
    /** ro.lumen.device of the RS30 Ultra Max image, which carries the X50 family app without ro.z9x.device. */
    public static final String RS30_IMAGE = "g0155";
    /** ro.lumen.device of the RS30 Ultra image, the same recipe as the RS30 Ultra Max. */
    public static final String RS30_ULTRA_IMAGE = "g0170";
    /** ro.lumen.device of every RS30 family image (the same recipe, the X50 family app), the old line's models and the MIRA line too. */
    private static final java.util.Set<String> RS30_IMAGES = new java.util.HashSet<>(java.util.Arrays.asList(
            RS30_IMAGE, RS30_ULTRA_IMAGE, "g0114", "g0115", "g0124", "g0125", "g0104", "g0109", "g0110"));

    /** The projector app for this image's ro.z9x.device and ro.lumen.device (exact values, surrounding blanks ignored). */
    public static String packageFor(String imageDevice, String lumenDevice) {
        String d = imageDevice == null ? "" : imageDevice.trim();
        String l = lumenDevice == null ? "" : lumenDevice.trim();
        return X50_IMAGE.equals(d) || RS30_IMAGES.contains(l) ? X50 : DEFAULT;
    }
}
