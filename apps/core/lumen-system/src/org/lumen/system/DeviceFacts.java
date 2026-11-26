// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.system;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;

import java.util.Locale;

/** The short facts General shows next to the Android screens it opens: language, time source, free space, version. */
public final class DeviceFacts {
    /** The Lumen OS version every image writes (build.prop). */
    static final String VERSION_PROP = "ro.lumen.version";

    private DeviceFacts() { }

    /** The system language in itself, first letter upper case ("Русский", "English"). */
    public static String language() {
        Locale l = Locale.getDefault();
        String n = l.getDisplayLanguage(l);
        return n.isEmpty() ? n : n.substring(0, 1).toUpperCase(l) + n.substring(1);
    }

    /** True when the time comes from the network. */
    public static boolean autoTime(Context c) {
        return Settings.Global.getInt(c.getContentResolver(), Settings.Global.AUTO_TIME, 1) != 0;
    }

    /** Free bytes of the user data. */
    public static long freeBytes() {
        try {
            return new StatFs(Environment.getDataDirectory().getPath()).getAvailableBytes();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** The Lumen OS version, null on an image without it. */
    public static String version() {
        String v = Props.get(VERSION_PROP);
        return v == null || v.isEmpty() ? null : v;
    }

    public static String model() { return Build.MODEL; }
}
