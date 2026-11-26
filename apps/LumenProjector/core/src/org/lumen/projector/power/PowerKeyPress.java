// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.power;

import android.app.DreamManager;
import android.content.Context;
import android.util.Log;

import org.lumen.menu.PowerAction;
import org.lumen.projector.Family;
import org.lumen.projector.panel.PowerKeyPrefs;

/**
 * What a short press and the hold of the remote power key do where the key runs through PowerKey (gmpf) or
 * Play6PowerKey: one rule over the user's setting (General, "Short press of the power key", lumen-menu PowerKeyPrefs;
 * the stock power menu by default, the hold does the other action). The X50 family reads the same setting through
 * lumen-menu PowerPress. Both press machines call only {@link #shortPress} and {@link #hold}. Main thread.
 */
final class PowerKeyPress {
    private static final String TAG = "PowerKeyPress";

    private PowerKeyPress() {}

    /** The armed press was released before the hold. */
    static void shortPress(Context ctx) {
        run(ctx, PowerKeyPrefs.choice(ctx).shortPress(), "short press");
    }

    /** The armed press reached the hold while the key is still down. */
    static void hold(Context ctx) {
        run(ctx, PowerKeyPrefs.choice(ctx).hold(), "hold");
    }

    private static void run(Context ctx, PowerAction a, String press) {
        if (a == PowerAction.MENU) menu(ctx, press);
        else if (a == PowerAction.SLEEP) sleep(ctx, press);
    }

    private static void sleep(Context ctx, String press) {
        if (SleepTimer.isFading()) {
            // the timer is already taking the projector down: finish now
            SleepTimer.finishNow(ctx, "power key during the timer fade");
            return;
        }
        Log.i(TAG, press + ": sleep");
        Family.powerSleepWithFade(ctx, "power key");
    }

    private static void menu(Context ctx, String press) {
        if (org.lumen.projector.ak.AkOverlay.isActive()) {
            Log.i(TAG, press + " ignored: AK overlay active");
            return;
        }
        if (SleepTimer.isFading()) SleepTimer.abortFade(ctx, "power key " + press);
        // the curtain (TYPE_SYSTEM_OVERLAY) sits above our panels: never open the menu underneath it, not even while it
        // fades out; lift it and let this press end here, the menu needs a fresh press once the picture is visible
        if (WakeCurtain.isShowing()) {
            if (!WakeCurtain.isFadingToBlack()) WakeCurtain.fastReveal();
            Log.i(TAG, press + ": curtain up, lifted; the power menu needs a new press");
            return;
        }
        try {
            DreamManager dm = ctx.getSystemService(DreamManager.class);
            if (dm != null && dm.isDreaming()) dm.stopDream();          // the menu must not sit under a dream
        } catch (Throwable t) {
            Log.w(TAG, "stopDream: " + t);
        }
        Log.i(TAG, press + ": power menu");
        PowerMenu.show(ctx);
    }
}
