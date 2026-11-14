// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import android.os.SystemClock;
import android.util.Log;

import org.lumen.menu.FeatureId;
import org.lumen.menu.ProjectorFeatures.Confirm;
import org.lumen.menu.ProjectorFeatures.Note;
import org.lumen.menu.ProjectorFeatures.Result;
import org.lumen.projector.Hal;
import org.lumen.projector.Ui;
import org.lumen.projector.hal.GameModeOption;
import org.lumen.projector.hal.GameModeType;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.ui.Notify;

import java.util.function.Consumer;

import static org.lumen.projector.panel.Z9xHdmiRows.HDMI_ONLY;
import static org.lumen.projector.utils.KstFrames.isFullFrame;
import static org.lumen.projector.utils.Z9xHalReads.hdmiNow;
import static org.lumen.projector.utils.Z9xHalReads.threeD;
import static org.lumen.projector.utils.Z9xHalReads.waitGameOption;

/** Game mode, game speed (top speed asks first) and ALLM, with the values just set shown until the read agrees. */
final class Z9xGameRows extends Z9xRows {
    private static final long PENDING_SPEED_MS = 10_000, TOP_SPEED_POLL_MS = 300;

    // game mode / speed just set (QPC:1898): shown until the read agrees or the wait is over
    private volatile int pendingGame = -1;
    private volatile long pendingGameUntil, pendingSpeedUntil;
    private volatile GameModeOption pendingSpeed;

    Z9xGameRows(Z9xFeatures menu) { super(menu); }

    /** The read: a game mode or speed just set stays shown until the read agrees or the wait is over. */
    void addPending(Z9xMenuRules.In in) {
        long now = SystemClock.uptimeMillis();
        int pg = pendingGame;
        if (in.hdmiConfirmed() && in.gameMode55 != null) {
            if (pg >= 0 && pg != in.gameMode55 && now < pendingGameUntil) in.pendingGame = pg;
            else pendingGame = -1;
        }
        GameModeOption ps = pendingSpeed;
        if (in.gameOption != null) {
            if (ps != null && in.gameOption != ps.wire && now < pendingSpeedUntil) in.pendingSpeed = ps.wire;
            else pendingSpeed = null;
        }
    }

    @Override boolean set(FeatureId f, String v, boolean confirmed, Consumer<Result> cb) {
        switch (f) {
            case GAME_MODE:
                setGameMode(v, cb);
                return true;
            case GAME_SPEED:
                setGameSpeed(v, confirmed, cb);
                return true;
            case GAME_ALLM: {                                             // QPC:1430
                final boolean auto = "signal".equals(v);
                write("IGmpf2 312 allm " + auto, (g, g2) -> {
                    if (!hdmiNow(app, g, "312")) return HDMI_ONLY;
                    g2.setAllmAutoSwitch(auto);
                    return null;
                }, cb);
                return true;
            }
            default:
                return false;
        }
    }

    /** QPC:1802 game mode: 0 off = 56(0) + 57(1); 1 on = 648(current) + 56(0) + 57(0); 2 auto = 56(1). */
    private void setGameMode(String v, Consumer<Result> cb) {
        final int i = Z9xMenuRules.indexOf(Z9xMenuRules.GAME, v);
        if (i < 0) { cb.accept(Result.failed(null)); return; }
        write("game mode " + i, (g, g2) -> {
            if (i != 0) {                                                         // stock order: Dolby Vision, 3D, then HDMI
                if (g.getHdrType() == GmpfClient.HDR_DOLBY_VISION) return Result.refusedWith(Note.titledBy("panel_game_mode", "game_blocked_dv"), null);
                if (threeD(g)) return Result.refusedWith(Note.titledBy("panel_game_mode", "panel_boost_3d"), null);
            }
            if (!hdmiNow(app, g, "game mode")) return HDMI_ONLY;
            boolean ok;
            if (i == 0) {
                ok = g2.setGameModeType(GameModeType.MANUAL);
                ok &= g2.setGameModeState(false);
            } else if (i == 1) {
                GameModeOption o = g.getGameModeOption();
                if (o != null) g.setGameModeOption(o);
                ok = g2.setGameModeType(GameModeType.MANUAL);
                ok &= g2.setGameModeState(true);
            } else {
                ok = g2.setGameModeType(GameModeType.AUTO);
            }
            if (!ok) throw new IllegalStateException("56 / 57 returned false");
            prefs.edit().putInt(Z9xFeatures.KEY_GAME_MODE, i).apply();
            pendingGame = i;
            pendingGameUntil = SystemClock.uptimeMillis() + PENDING_SPEED_MS;
            return null;
        }, cb);
    }

    /**
     * QPC:1847 game speed: top speed asks first when the keystone is not the full frame (stock), then 648 and, for top
     * speed, 207 once 647 reads it.
     */
    private void setGameSpeed(String v, boolean confirmed, Consumer<Result> cb) {
        int w = Z9xMenuRules.wireOf(Z9xMenuRules.SPEED, Z9xMenuRules.SPEED_WIRE, v);
        final GameModeOption o = w < 0 ? null : GameModeOption.fromWire(w);
        Z9xMenuRules.In in = menu.last();
        if (o == null || in == null) { cb.accept(Result.done(null)); return; }
        if (confirmed || !o.isTopSpeed() || in.isTopSpeedGame()) {
            applyGameSpeed(o, cb);
            return;
        }
        Hal.query((g, g2) -> isFullFrame(g.getKeystonePoints()), full -> {
            if (Boolean.TRUE.equals(full)) applyGameSpeed(o, cb);
            else cb.accept(Result.ask(new Confirm("panel_speed_top", "game_top_speed_confirm", "panel_switch"), null));
        });
    }

    private void applyGameSpeed(GameModeOption o, Consumer<Result> cb) {
        Z9xMenuRules.In in = menu.last();
        final boolean modeOn = in != null && Z9xMenuRules.shownGameMode(in) == 1;  // shown mode On (QPC:1870)
        write("648 game option " + o, (g, g2) -> {
            if (!hdmiNow(app, g, "648")) return HDMI_ONLY;                       // 207 below would reset the normal picture
            boolean r = g.setGameModeOption(o);
            if (PanelRules.resendGameState(r, modeOn)) {
                try { g2.getGameModeProp(); } catch (Throwable t) { Log.d(TAG, "g2 55 before 57: " + t); }
                boolean r57 = false;
                try { r57 = g2.setGameModeState(true); } catch (Throwable t) { Log.w(TAG, "g2 57(0): " + t); }
                Log.i(TAG, "57(0) after 648 -> " + r57);
            }
            GameModeOption now = g.getGameModeOption();
            Log.i(TAG, "648(" + o + ") -> " + r + ", 647 reads " + now);
            if (!r && now != o) {
                now = waitGameOption(g, o);
                if (now != o) throw new IllegalStateException("648 -> false, 647 reads " + now);
            }
            pendingSpeed = o;
            pendingSpeedUntil = SystemClock.uptimeMillis() + PENDING_SPEED_MS;
            if (o.isTopSpeed()) topSpeedKeystone(o, SystemClock.uptimeMillis() + PENDING_SPEED_MS);
            return null;
        }, cb);
    }

    /** 207 for top speed once 647 reads it (re-polled every 300 ms on the HAL thread, never blocking it). */
    private void topSpeedKeystone(GameModeOption o, long deadline) {
        Hal.runDelayed((g, g2) -> {
            GameModeOption now = g.getGameModeOption();
            if (now == o) {
                g.resetKeystoneForTopSpeedGame();
                Log.i(TAG, "207 after 647 reads " + o);
                Ui.main().post(() -> Notify.show(app, text("panel_speed_top_note")));
            } else if (SystemClock.uptimeMillis() < deadline) {
                topSpeedKeystone(o, deadline);
            } else {
                Log.w(TAG, "207 skipped: 647 still reads " + now + " instead of " + o);
            }
        }, TOP_SPEED_POLL_MS);
    }
}
