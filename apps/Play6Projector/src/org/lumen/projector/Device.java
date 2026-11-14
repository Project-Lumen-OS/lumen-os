package org.lumen.projector;

import android.content.Context;
import android.os.SystemProperties;
import android.util.Log;

/**
 * Device identity and capabilities of the XGIMI Play 6 build of org.lumen.projector
 *.
 *
 * {@link #isPlay6()} guards every Lumen module of this app that talks to the vendor HAL: all three
 * properties must say Play 6 (ro.boot.xgimi.modelname G0058 from the bootloader, ro.board.platform
 * mt5889 from the vendor, ro.lumen.device play6 from our own image). If this APK ever ran on another
 * projector (rule S1 broken, plan 2.1), Play6App starts nothing and the Hal facade stays closed. The
 * stock boot handshake (org.lumen.play6.App / BootHandshake) is not covered by this guard and may not
 * change (check K). Any thread; the properties are read once.
 */
public final class Device {
    private static final String TAG = "P6Hal";
    static final String MODEL = "G0058";
    static final String PLATFORM = "mt5889";
    static final String LUMEN_DEVICE = "play6";

    private static volatile Boolean sPlay6;

    private Device() {}

    /** True on the XGIMI Play 6 with a Lumen Play 6 image (all three properties). */
    public static boolean isPlay6() {
        Boolean v = sPlay6;
        if (v == null) {
            String model = prop("ro.boot.xgimi.modelname");
            String platform = prop("ro.board.platform");
            String lumen = prop("ro.lumen.device");
            v = MODEL.equals(model) && PLATFORM.equals(platform) && LUMEN_DEVICE.equals(lumen);
            if (!v) Log.w(TAG, "not a Play 6 (modelname=" + model + " platform=" + platform + " lumen=" + lumen + ")");
            sPlay6 = v;
        }
        return v;
    }

    /**
     * The G0058 has an internal battery (G58 feature_2.xml support_battery=true, plan 3.4); Diagnostics
     * cross-checks it against support_battery of IGmpf 404 getAllFeatureList.
     */
    public static boolean hasBattery() {
        return isPlay6();
    }

    /** A Play 6 test build (ro.lumen.version play6-t...): Diagnostics, the probe and the test hooks exist only here. */
    public static boolean isTestBuild() {
        return prop("ro.lumen.version").startsWith("play6-t");
    }

    /**
     * Eye protection can be offered: while the image's NUI fifo reader is up (z9x_nui, sys.z9x.nui=ready;
     * stock parity 2026-10-21). The stock Play 6 supports it (stock analysis 4), IGmpf 152
     * is sent only with that reader, and on the Play 6 nothing unsupported is shown, not even disabled: without
     * the reader the quick panel builds no Eye protection tile or page and Lumen Home hides its tile
     * ({@link #unsupportedHomeTiles}).
     */
    public static boolean hasEyeProtection() {
        return org.lumen.projector.hal.Play6Gmpf.nuiReaderReady();
    }

    /** Lumen Home's Projector tab tile of eye protection (apps/Z9xHome data/InputSource.tiles: its intent). */
    public static final String HOME_TILE_EYE = "section:eye";

    /** Lumen Home's Projector tab tile of screen mirroring (apps/Z9xHome data/InputSource.tiles: its intent). */
    public static final String HOME_TILE_MIRRORING = "miracast";
    /** The screen mirroring receiver's control provider (apps/LumenMiracast StateProvider). */
    static final String MIRRORING_AUTHORITY = "org.lumen.miracast";

    /**
     * The Lumen Home Projector tab tiles this projector does not support, by the tile's intent string of
     * apps/Z9xHome InputSource.tiles ("section:eye", "action:autofocus", ...; the Card id is "tile:" + it),
     * for device/DeviceStateProvider call("caps"): eye protection until the NUI reader exists, screen mirroring
     * where the receiver found no Wi-Fi Direct ({@link #screenMirroring}).
     */
    public static String[] unsupportedHomeTiles(Context c) {
        java.util.List<String> off = new java.util.ArrayList<>(2);
        if (!hasEyeProtection()) off.add(HOME_TILE_EYE);
        if (!screenMirroring(c)) off.add(HOME_TILE_MIRRORING);
        return off.toArray(new String[0]);
    }

    /**
     * Screen mirroring works on this unit: the receiver is in the image and its control provider resolves, which it
     * does only once the receiver found Wi-Fi Direct (LumenMiracast ControlGate; the quick menu tile asks the same).
     */
    public static boolean screenMirroring(Context c) {
        try {
            return c != null && c.getPackageManager().resolveContentProvider(MIRRORING_AUTHORITY, 0) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String prop(String name) {
        try {
            return SystemProperties.get(name, "").trim();
        } catch (Throwable t) {
            return "";
        }
    }
}
