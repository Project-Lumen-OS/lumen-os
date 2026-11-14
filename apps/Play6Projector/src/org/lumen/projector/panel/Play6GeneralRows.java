package org.lumen.projector.panel;

import android.util.Log;

import org.lumen.menu.FeatureId;
import org.lumen.menu.ProjectorFeatures.Result;
import org.lumen.projector.Device;
import org.lumen.projector.KeyReceiver;
import org.lumen.projector.Ui;
import org.lumen.projector.hal.Play6Gmpf;
import org.lumen.projector.hal.Play6Gmpf2;
import org.lumen.projector.home.LauncherSwitcher;
import org.lumen.projector.power.LampOffHal;

import java.util.function.Consumer;

/** Eye protection (152), power saving and the home screen choice. */
final class Play6GeneralRows extends Play6Rows {
    Play6GeneralRows(Play6Features menu) { super(menu); }

    @Override
    String writeHal(FeatureId feature, String value, boolean on, Play6Gmpf g, Play6Gmpf2 g2) throws Exception {
        switch (feature) {
            case EYE: {
                if (!"on".equals(value) && !"off".equals(value)) throw new IllegalArgumentException("eye " + value);
                if (!Device.hasEyeProtection()) return "";                         // no NUI reader: nothing sent
                if (!g.setEyeProtection(on)) throw new IllegalStateException("152 returned false");
                return null;
            }
            default:
                return OTHER;
        }
    }

    /** Power saving (stock eco_mode_switch, IGmpf 327 / 328), battery model only; the vendor changes the light with it. */
    void setEco(boolean on, Consumer<Result> cb) {
        if (!Device.hasBattery()) { refuse(cb, null); return; }
        write("328 power saving " + on, cb, (g, g2) -> { g.setEcoState(on); return null; });
    }

    /** Home screen (asked, the menu closed first): LauncherSwitcher, after the first-run setup only. */
    void setLauncher(String value, Consumer<Result> cb) {
        Integer w = Play6MenuGates.LAUNCHER.wire(value);
        if (w == null || !KeyReceiver.isSetupComplete(app)) { refuse(cb, w == null ? null : "toast_setup_running"); return; }
        final String mode = w == 1 ? LauncherSwitcher.MODE_CLASSIC : LauncherSwitcher.MODE_LUMEN;
        if (mode.equals(LauncherSwitcher.currentMode(app))) { refuse(cb, null); return; }
        LauncherSwitcher.apply(app, mode, true, "quick menu",
                (ok, holder, err) -> Log.i(TAG, "home screen " + mode + " -> " + ok + " " + holder));
        Ui.main().post(() -> cb.accept(Result.done(menu.state())));
    }

    /** The stock power menu's "Turn off screen" (org.lumen.menu.LampOff, the one rule of every projector). */
    @Override boolean run(FeatureId a, Consumer<Result> cb) {
        if (a != FeatureId.LAMP_OFF) return false;
        LampOffHal.get(app).run(cb);
        return true;
    }
}
