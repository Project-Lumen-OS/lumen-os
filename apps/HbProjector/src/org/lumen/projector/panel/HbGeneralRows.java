// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.util.Log;

import org.lumen.menu.ElderColorRule;
import org.lumen.menu.FeatureId;
import org.lumen.menu.ProjectorFeatures.Result;
import org.lumen.projector.KeyReceiver;
import org.lumen.projector.Ui;
import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.power.LampOffHal;

import java.util.function.Consumer;

/** Eye protection (DISPLAY 61), senior colour (TV 179..182), Power On Auto Boot (FACTORY 36 / 37) and the home screen choice. */
final class HbGeneralRows extends HbRows {
    HbGeneralRows(HbMenuFeatures menu) { super(menu); }

    @Override
    String writeHal(FeatureId feature, String value, boolean on, GmpfClient g, Gmpf2Client g2) throws Exception {
        switch (feature) {
            case EYE: {
                if (!"on".equals(value) && !"off".equals(value)) throw new IllegalArgumentException("eye " + value);
                if (!GmpfClient.nuiReaderReady()) return "eye_unavailable";   // no NUI fifo reader: nothing sent
                if (!g.setEyeProtection(on)) throw new IllegalStateException("61 returned false");
                return null;
            }
            case EYE_ELDER_COLOR: {                                       // stock ElderlyColorDialog, shared rule
                Integer mode = g.getPictureModeRaw();
                if (mode != null && !ElderColorRule.allowed(mode)) return ElderColorRule.NOTE;
                if (!value.equals(ElderColorRule.set(elderPort(g), value))) throw new IllegalStateException("179 / 181 read back differs");
                return null;
            }
            case POWER_ON_AC: {                                           // stock HideRepository.setElectrify
                g.setPowerOnMode(on);
                if (g.getPowerOnMode() != on) throw new IllegalStateException("37 read back differs");
                return null;
            }
            default:
                return OTHER;
        }
    }

    /** Home screen: the menu confirmed it; LauncherSwitcher applies (after the first-run setup only). */
    void setLauncher(String value, Consumer<Result> cb) {
        Integer w = HbMenuGates.LAUNCHER.wire(value);
        if (w == null || !KeyReceiver.isSetupComplete(app)) { refuse(cb, w == null ? null : "toast_setup_running"); return; }
        String mode = w == 1 ? org.lumen.projector.home.LauncherSwitcher.MODE_CLASSIC
                : org.lumen.projector.home.LauncherSwitcher.MODE_LUMEN;
        org.lumen.projector.home.LauncherSwitcher.apply(app, mode, true, "quick menu",
                (ok, holder, err) -> Log.i(TAG, "home screen " + mode + " -> " + ok + " " + holder));
        Ui.main().post(() -> cb.accept(Result.done(menu.state(last()))));
    }

    /** The stock power menu's "Turn off screen" (org.lumen.menu.LampOff, the one rule of every projector). */
    @Override boolean run(FeatureId a, Consumer<Result> cb) {
        if (a != FeatureId.LAMP_OFF) return false;
        LampOffHal.get(app).run(cb);
        return true;
    }

    /** The senior colour row's vendor calls on the HB: GmTvManager 179..182. */
    static ElderColorRule.Port elderPort(GmpfClient g) {
        return new ElderColorRule.Port() {
            @Override public int elderTone() throws Exception { return g.getElderColorTone(); }
            @Override public boolean elderToneSet(boolean on) throws Exception { return g.setElderColorTone(on); }
            @Override public int elderLevel() throws Exception { return g.getElderColorLevel(); }
            @Override public boolean elderLevelSet(int level) throws Exception { return g.setElderColorLevel(level); }
        };
    }
}
