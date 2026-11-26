// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.system;

import android.app.DreamManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * The stock screensaver page's "start now" row (enter_screen_saver_text): Android's own screensaver, a moment after
 * the menu closed (as the Z9X HB DreamSettings.startNow). DreamManager when the app holds WRITE_DREAM_STATE, else
 * SystemUI's Somnambulator, the activity the screensaver key of the X50 family starts.
 */
public final class ScreensaverStart {
    private static final String TAG = "LumenSystem";
    private static final long AFTER_MENU_MS = 350;

    private ScreensaverStart() { }

    public static boolean now(Context ctx) {
        final Context app = ctx.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(() -> start(app), AFTER_MENU_MS);
        return true;
    }

    private static void start(Context app) {
        try {
            DreamManager dm = app.getSystemService(DreamManager.class);
            if (dm != null && dm.isDreaming()) return;
            if (dm != null) {
                dm.startDream();
                return;
            }
        } catch (SecurityException e) {
            Log.i(TAG, "screensaver: no dream permission, SystemUI starts it");
        } catch (Throwable t) {
            Log.w(TAG, "screensaver: " + t);
        }
        try {
            app.startActivity(new Intent(Intent.ACTION_MAIN)
                    .setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) {
            Log.w(TAG, "screensaver start: " + t);
        }
    }
}
