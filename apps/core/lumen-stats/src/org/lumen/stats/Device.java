// SPDX-License-Identifier: Apache-2.0
package org.lumen.stats;

import android.content.Context;
import android.os.Build;
import android.os.SystemProperties;
import android.provider.Settings;

/** What a ping reads from the running system. */
public final class Device {
    private Device() {}

    static String prop(String key) {
        try {
            String v = SystemProperties.get(key, "");
            return v == null ? "" : v.trim();
        } catch (Throwable t) {
            return "";
        }
    }

    /** ro.boot.xgimi.modelname (G0082, G0111...). */
    public static String xgimiModel() { return prop("ro.boot.xgimi.modelname"); }

    /**
     * The Lumen codename of this projector: the device registry entry of its XGIMI model (Models), else the image's
     * ro.lumen.device, else the vendor device name (the server maps the known aliases).
     */
    public static String model() { return model(xgimiModel(), prop("ro.lumen.device"), prop("ro.product.vendor.device")); }

    static String model(String xgimiModel, String lumenDevice, String vendorDevice) {
        String c = Models.codename(xgimiModel);
        if (!c.isEmpty()) return c;
        return lumenDevice.isEmpty() ? vendorDevice : lumenDevice;
    }

    /** A DSU trial run of Lumen OS on the stock system (its own data, so its own install id). */
    public static boolean trial() { return "1".equals(prop("ro.gsid.image_running")); }

    /**
     * The projector serial: Build.getSerial() (READ_PRIVILEGED_PHONE_STATE: the platform signature or the system uid),
     * else ro.serialno (an app domain may not read it under an enforcing policy), else "".
     */
    @SuppressWarnings("MissingPermission")
    public static String serial() {
        String s;
        try {
            s = Build.getSerial();
        } catch (Throwable t) {
            s = "";
        }
        if (s == null || s.trim().isEmpty() || Build.UNKNOWN.equals(s)) s = prop("ro.serialno");
        s = s == null ? "" : s.trim();
        return Build.UNKNOWN.equals(s) ? "" : s;
    }

    /**
     * The release of an image without an updater: ro.lumen.version (x50-1.0.10-beta3), its version code
     * (ro.lumen.version_code, else Ping.versionCode of the version: the Z9X base of the X50 image keeps the Z9X's
     * ro.z9x.version_code), ro.lumen.build_id; the ro.z9x names only on an image without any ro.lumen version.
     */
    public static Release release() {
        String v = prop("ro.lumen.version");
        if (v.isEmpty()) return new Release(prop("ro.z9x.version"), parse(prop("ro.z9x.version_code"), 0), prop("ro.z9x.build_id"));
        String b = prop("ro.lumen.build_id");
        return new Release(v, parse(prop("ro.lumen.version_code"), Ping.versionCode(v)), b.isEmpty() ? prop("ro.z9x.build_id") : b);
    }

    static int parse(String s, int fallback) {
        try {
            return s.isEmpty() ? fallback : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Settings.Global.BOOT_COUNT: one more at every start of Android. */
    static int bootCount(Context c) {
        try {
            return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, 0);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Nothing is sent before the first-run setup ends (user_setup_complete is set at its end). */
    public static boolean setupDone(Context c) {
        try {
            return Settings.Secure.getInt(c.getContentResolver(), Settings.Secure.USER_SETUP_COMPLETE, 0) == 1;
        } catch (Throwable t) {
            return false;
        }
    }
}
