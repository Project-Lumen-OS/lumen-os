package org.lumen.projector.panel;

import android.util.Log;

import org.lumen.menu.FeatureId;
import org.lumen.menu.ProjectorFeatures.Result;
import org.lumen.projector.Hal;
import org.lumen.projector.R;
import org.lumen.projector.Ui;
import org.lumen.projector.ak.AkOverlay;
import org.lumen.projector.hal.GameModeOption;
import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.hal.HbFeatures;
import org.lumen.projector.ui.Notify;

import java.util.function.Consumer;

import static org.lumen.projector.utils.HbHalReads.topSpeedGame;

/** Keystone switches, Auto keystone / fit now and the stock keystone restore. */
final class HbKeystoneRows extends HbRows {
    HbKeystoneRows(HbMenuFeatures menu) { super(menu); }

    @Override
    String writeHal(FeatureId feature, String value, boolean on, GmpfClient g, Gmpf2Client g2) throws Exception {
        switch (feature) {
            case KEYSTONE_ON_POWER:
                g.setPowerOnAk(on);                                             // FACT 45
                return null;
            case KEYSTONE_CURTAIN:
                Hal.setCurtainFitCoupled(g, on);                                // FOCUS 35 (+ 33 on)
                return null;
            case KEYSTONE_OBSTACLE:
                Hal.setObstacleAvoidCoupled(g, on);                             // FOCUS 33 (+ 35 off)
                return null;
            default:
                return OTHER;
        }
    }

    @Override
    boolean run(FeatureId action, Consumer<Result> cb) {
        switch (action) {
            case KEYSTONE_AK_NOW:
                write("auto keystone", cb, (g, g2) -> {
                    if (topSpeedGame(g, g2)) return "panel_kst_game_mode";     // stock autoKeystone: gameModeFast
                    Ui.main().post(() -> Hal.requestKeystone(app, false));     // FOCUS 27
                    return null;
                });
                return true;
            case KEYSTONE_FIT_NOW:
                Hal.requestKeystone(app, true);                                 // FOCUS 53(11)
                Ui.main().post(() -> cb.accept(Result.done(menu.state(last()))));
                return true;
            case KEYSTONE_RESTORE:
                if (Hal.isKeystoneRunning() || AkOverlay.isActive()) { refuse(cb, "toast_kst_busy"); return true; }
                write("restore default picture", cb, this::restore);
                return true;
            default:
                return false;
        }
    }

    /**
     * HAL thread, stock HB KeyStoneManager.resetKeyStone: 38(0), 84, 85(0,0) only if 84 read 6, then 63(5,10) lens home
     * with the optical zoom; real-time keystone paused around it; refused in top speed and while an AK runs.
     */
    private String restore(GmpfClient g, Gmpf2Client g2) throws Exception {
        GameModeOption o = g.getGameModeOption();
        if (o != null && o.isTopSpeed()) return "panel_kst_game_mode";
        if (Hal.isKeystoneRunning() || AkOverlay.isActive()) return "toast_kst_busy";
        boolean paused = ManualKeystonePanel.pauseRealtime(app, g, "menu reset");
        try {
            int mode = g.resetKeystoneStock();
            Log.i(TAG, "restore: 38(0) sent, 84 -> " + mode);
            if (HbFeatures.on(HbFeatures.F.OPTICAL_ZOOM)) {
                try { Log.i(TAG, "restore: 63(5,10) lens home -> " + g.zoomHome()); } catch (Throwable t) { Log.w(TAG, "63: " + t); }
            }
            org.lumen.projector.game.GameProfile.noteKeystoneFullFrame(app, "quick menu restore");
            Ui.main().post(() -> Notify.show(app, app.getString(R.string.kst_restore_done_hb)));
        } finally {
            if (paused) ManualKeystonePanel.resumeRealtime(app, g, "menu reset");
        }
        return null;
    }
}
