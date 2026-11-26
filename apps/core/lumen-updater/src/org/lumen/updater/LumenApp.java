// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * The one Application of org.lumen.updater. What it does at start is this model's data (UpdateTarget):
 * <ul>
 * <li>importBridgeData (Z9X, Play 6, Lumen OS 1.0.8 on): the bridge import of the pre-1.0.8 org.z9x.updater data
 *     (org.lumen.common.LumenImport) before any component reads its prefs;</li>
 * <li>hideWithoutDevice (the neutral updater of the HB and the X50 family): an image without a device wire id
 *     (ro.lumen.ota.device) takes no update, so the update screen is disabled and TvSettings About shows no
 *     "Lumen OS update" row (it lists the SYSTEM_UPDATE_SETTINGS activity only while one resolves); with a wire id
 *     it is back to its manifest state.</li>
 * </ul>
 */
public final class LumenApp extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (Ota.T.importBridgeData) org.lumen.common.LumenImport.run(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (!Ota.T.hideWithoutDevice) return;
        try {
            PackageManager pm = getPackageManager();
            ComponentName ui = new ComponentName(this, UpdaterActivity.class);
            int want = Ota.device().isEmpty() ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
            if (pm.getComponentEnabledSetting(ui) != want) {
                pm.setComponentEnabledSetting(ui, want, PackageManager.DONT_KILL_APP);
                Log.i(Ota.TAG, "update screen " + (want == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        ? "hidden: no ro.lumen.ota.device" : "shown"));
            }
        } catch (Throwable t) {
            Log.w(Ota.TAG, "update screen state: " + t);
        }
    }
}
