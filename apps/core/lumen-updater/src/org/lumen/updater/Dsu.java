// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;

/**
 * Lumen OS running as a DSU try on the stock system (ro.gsid.image_running=1): no update work at all (an OTA would
 * target the stock slots), and the updater screen hands over to "Install Lumen OS on this projector" (the app
 * org.lumen.dsuinstall, apps/LumenDsuInstall) when that app is in the image and the kill switch
 * ro.lumen.dsu_install=0 is not set. Inert on every normal boot.
 */
public final class Dsu {
    private Dsu() {}

    static final String ACTION = "org.lumen.dsuinstall.action.INSTALL", PKG = "org.lumen.dsuinstall";

    public static boolean running() { return "1".equals(SystemProperties.get("ro.gsid.image_running", "")); }

    /** true when the install screen was opened */
    public static boolean openInstaller(Context c) {
        if ("0".equals(SystemProperties.get("ro.lumen.dsu_install", ""))) return false;
        Intent i = new Intent(ACTION).setPackage(PKG).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (c.getPackageManager().resolveActivity(i, 0) == null) return false;
            c.startActivity(i);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
