// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.power;

import android.content.Context;
import android.util.Log;

import org.lumen.menu.LampOff;
import org.lumen.projector.Hal;
import org.lumen.projector.Ui;
import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.ui.LampOffHost;

import java.util.function.Consumer;

/**
 * The Z9X and HB light calls of the shared "Turn off screen" ({@link LampOff}), as their stock sends them
 * (xgimi.api.version 500): off = setScreenOnOffWithAction(false, 1), and setScreenOnOff(false) when the vendor says no
 * (GmDisplayManager.setScreenOnOffWithAction). On = the stock kPPowerPress light-on through {@link LampControl}
 * (eye-protection light first where it blanked it, then setScreenOnOff(true), read back first). The one LampOff of the
 * app: the menu row, the key receiver and the sleep paths all use {@link #get}.
 */
public final class LampOffHal implements LampOff.Light {
    private static final String TAG = "Z9xLampOff";
    private static final LampOffHal LIGHT = new LampOffHal();

    private LampOffHal() {}

    /** The app's LampOff. The first call restores the state of this boot (PowerPolicy.install). */
    public static LampOff get(Context c) { return LampOffHost.get(c, LIGHT); }

    @Override public void off(Consumer<Boolean> done) {
        boolean queued = Hal.runDelayed((g, g2) -> {
            boolean off = false;
            try {
                boolean shut = shut(g2);
                if (!shut) g.setScreenOn(false);
                off = !g.getScreenOn();
                Log.i(TAG, "screen off: action call " + shut + ", reads off " + off);
            } catch (Exception e) {
                Log.w(TAG, "screen off: " + e);
            }
            final boolean result = off;
            Ui.main().post(() -> done.accept(result));
        }, 0, () -> Ui.main().post(() -> done.accept(false)));
        if (!queued) done.accept(false);
    }

    /** setScreenOnOffWithAction(false, 1), false when the vendor says no or it fails (stock then sends setScreenOnOff(false)). */
    private static boolean shut(Gmpf2Client g2) {
        try {
            return g2.shutScreen();
        } catch (Exception e) {
            Log.w(TAG, "setScreenOnOffWithAction(false, 1): " + e);
            return false;
        }
    }

    @Override public void on() {
        LampControl.lampOn("screen off ends", () -> true, 0, null);
    }
}
