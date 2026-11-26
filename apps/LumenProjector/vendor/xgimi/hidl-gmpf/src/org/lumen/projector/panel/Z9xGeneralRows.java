// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.util.Log;

import org.lumen.menu.FeatureId;
import org.lumen.menu.ProjectorFeatures.Result;
import org.lumen.projector.home.LauncherSwitcher;
import org.lumen.projector.power.LampOffHal;

import java.util.function.Consumer;

/** Eye protection (152), Power On Auto Boot (543 / 544) and the home screen choice. */
final class Z9xGeneralRows extends Z9xRows {
    Z9xGeneralRows(Z9xFeatures menu) { super(menu); }

    @Override boolean set(FeatureId f, String v, boolean confirmed, Consumer<Result> cb) {
        switch (f) {
            case EYE: {                                                   // QPC:1944
                final boolean on = "on".equals(v);
                write("152 eye protection " + on, (g, g2) -> {
                    if (!g.setEyeProtection(on)) throw new IllegalStateException("152 returned false");
                    return null;
                }, cb);
                return true;
            }
            case POWER_ON_AC: {                                           // stock HideRepository.setElectrify
                final boolean on = "on".equals(v);
                write("543 power on auto boot " + on, (g, g2) -> {
                    g.setPowerOnMode(on);
                    return g.getPowerOnMode() == on ? null : Result.failed(null);
                }, cb);
                return true;
            }
            case SETTINGS_LAUNCHER: {                                     // QPC:1488 (the menu closed first)
                final String mode = "classic".equals(v) ? LauncherSwitcher.MODE_CLASSIC : LauncherSwitcher.MODE_LUMEN;
                LauncherSwitcher.apply(app, mode, true, "quick panel",
                        (ok, holder, err) -> Log.i(TAG, "home screen " + mode + " -> " + ok + " " + holder));
                cb.accept(Result.done(null));
                return true;
            }
            default:
                return false;
        }
    }

    /** The stock power menu's "Turn off screen" (org.lumen.menu.LampOff, the one rule of every projector). */
    @Override boolean run(FeatureId a, Consumer<Result> cb) {
        if (a != FeatureId.LAMP_OFF) return false;
        LampOffHal.get(app).run(cb);
        return true;
    }
}
