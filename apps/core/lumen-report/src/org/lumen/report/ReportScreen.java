// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.report;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * The entry of the shared menu row "Send a problem report" (lumen-menu FeatureId.SETTINGS_REPORT, General) and of
 * any settings screen of a device app: the row exists while the image sends reports and the app declares the screen.
 */
public final class ReportScreen {
    private ReportScreen() {}

    public static boolean available(Context c) {
        if (!ReportConfig.enabled()) return false;
        try {
            c.getPackageManager().getActivityInfo(new ComponentName(c, ReportActivity.class), 0);
            return true;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return false;
        }
    }

    /** Opens the report screen (any context); false when it is not available. */
    public static boolean open(Context c) {
        if (!available(c)) return false;
        try {
            Intent i = new Intent(c, ReportActivity.class);
            if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (RuntimeException e) {
            Log.w(ReportConfig.TAG, "open: " + e);
            return false;
        }
    }
}
