// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import android.util.Log;

import org.lumen.menu.FeatureId;
import org.lumen.menu.ProjectorFeatures.Note;
import org.lumen.menu.ProjectorFeatures.Result;
import org.lumen.projector.Hal;
import org.lumen.projector.ak.AkOverlay;
import org.lumen.projector.hal.KstPoint;

import java.util.function.Consumer;

import static org.lumen.projector.utils.KstFrames.isFullFrame;

/** Keystone switches, Auto keystone / fit now and the stock keystone reset. */
final class Z9xKeystoneRows extends Z9xRows {
    Z9xKeystoneRows(Z9xFeatures menu) { super(menu); }

    @Override boolean set(FeatureId f, String v, boolean confirmed, Consumer<Result> cb) {
        final boolean on = "on".equals(v);
        switch (f) {
            case KEYSTONE_REALTIME:                                       // QPC:980
                write("585 real-time keystone " + on, (g, g2) ->
                        Hal.setMoveAkChecked(g, on) ? null : Result.done(null, Note.of("toast_rt_kst_ceiling")), cb);
                return true;
            case KEYSTONE_ON_POWER:
                write("552 power-on keystone " + on, (g, g2) -> { g.setPowerOnAk(on); return null; }, cb);
                return true;
            case KEYSTONE_CURTAIN:                                        // fit ON also turns obstacle avoidance on
                write("290 fit to screen " + on, (g, g2) -> { Hal.setCurtainFitCoupled(g, on); return null; }, cb);
                return true;
            case KEYSTONE_OBSTACLE:
                write("292 obstacle avoidance " + on, (g, g2) -> { Hal.setObstacleAvoidCoupled(g, on); return null; }, cb);
                return true;
            default:
                return false;
        }
    }

    @Override boolean run(FeatureId a, Consumer<Result> cb) {
        switch (a) {
            case KEYSTONE_AK_NOW: Hal.requestKeystone(app, false); cb.accept(Result.done(null)); return true;
            case KEYSTONE_FIT_NOW: Hal.requestKeystone(app, true); cb.accept(Result.done(null)); return true;
            case KEYSTONE_RESTORE:
                resetKeystone(cb);
                return true;
            default:
                return false;
        }
    }

    /** QPC:1969 keystone reset, the stock sequence with the lens home and the full-frame safety net. */
    private void resetKeystone(Consumer<Result> cb) {
        write("keystone reset (stock)", (g, g2) -> {
            if (Hal.isKeystoneRunning() || AkOverlay.isActive()) return Result.refusedWith(Note.of("toast_kst_busy"), null);
            boolean paused = ManualKeystonePanel.pauseRealtime(app, g, "panel reset");
            try {
                int r181 = g.resetKeystoneStock();                                // 207, 130 (129), 181(50)
                boolean r46 = false;
                int r153 = Integer.MIN_VALUE;
                try {
                    r153 = g.zoomHome();                                          // 153(5,10), stock order: before 46
                    OpticalZoomPanel.onLensHome(app);
                } catch (Throwable t) { Log.w(TAG, "153(5,10): " + t); }
                try { r46 = g2.resetKeystoneRatio(); } catch (Throwable t) { Log.w(TAG, "IGmpf2 46: " + t); }
                Log.i(TAG, "reset: 207/130/181(50) -> " + r181 + ", 153(5,10) -> " + r153 + ", 46(3) -> " + r46);
                KstPoint after = null;
                try { after = g.getKeystonePoints(); } catch (Throwable t) { Log.w(TAG, "186 after reset: " + t); }
                if (after == null || !isFullFrame(after)) {
                    Log.w(TAG, "reset: 186 reads " + after + " after the stock sequence: full frame through 150 -> 185");
                    KstPoint full = KstPoint.fullFrame();
                    if (!g.checkKeystonePoints(full)) throw new IllegalStateException("150 rejected the full frame");
                    Log.i(TAG, "185 full frame -> " + g.applyKeystonePoints(full));
                }
                org.lumen.projector.game.GameProfile.noteKeystoneFullFrame(app, "quick panel reset");
                try { g.regenerateBootLogo(); } catch (Throwable t) { Log.w(TAG, "158: " + t); }
                return Result.done(null, Note.of("kst_restore_done"));
            } finally {
                if (paused) ManualKeystonePanel.resumeRealtime(app, g, "panel reset");
            }
        }, cb);
    }
}
