package org.lumen.projector.panel;

import android.os.SystemClock;
import android.util.Log;

import org.lumen.menu.FeatureId;
import org.lumen.menu.MenuState;
import org.lumen.projector.Hal;
import org.lumen.projector.R;
import org.lumen.projector.Ui;
import org.lumen.projector.hal.GameModeOption;
import org.lumen.projector.hal.GameModeType;
import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.ui.Notify;

/** Game mode, game speed and ALLM, with the values just set shown until the read agrees. */
final class HbGameRows extends HbRows {
    /** 647 follows 648 after the picture is applied again (~4.3 s seen); the state shows the choice meanwhile. */
    private static final long PENDING_MS = 10_000, GAME_OPTION_WAIT_MS = 6_000, GAME_OPTION_POLL_MS = 100,
            TOP_SPEED_POLL_MS = 300;

    private volatile int pendingGame = -1;
    private volatile long pendingGameUntil;
    private volatile Integer pendingSpeed;
    private volatile long pendingSpeedUntil;

    HbGameRows(HbMenuFeatures menu) { super(menu); }

    /** Main thread: the input's mode from 148 when it fits 77, else the last value set; a pending change shows meanwhile. */
    void read(MenuState.Builder b, Snapshot sn, boolean hdmi) {
        int gm = prefs.getInt(QuickPanel.KEY_GAME_MODE, -1);
        if (hdmi && sn.gameMode55 != null) {
            int pg = pendingGame;
            if (pg >= 0 && pg != sn.gameMode55 && SystemClock.uptimeMillis() < pendingGameUntil) gm = pg;
            else { pendingGame = -1; gm = sn.gameMode55; }
        }
        b.put(FeatureId.GAME_MODE, gm < 0 ? null : HbMenuGates.GAME_MODE.token(gm));
        Integer opt = sn.gameOption;
        Integer pend = pendingSpeed;
        if (opt != null && pend != null && !opt.equals(pend) && SystemClock.uptimeMillis() < pendingSpeedUntil) opt = pend;
        else pendingSpeed = null;
        b.put(FeatureId.GAME_SPEED, HbMenuGates.gameSpeedToken(opt));
        b.put(FeatureId.GAME_ALLM, sn.allm == null ? null : HbMenuGates.ALLM.token(sn.allm ? 1 : 0));
    }

    @Override
    String writeHal(FeatureId feature, String value, boolean on, GmpfClient g, Gmpf2Client g2) throws Exception {
        switch (feature) {
            case GAME_MODE:
                return setGameMode(value, g, g2);
            case GAME_SPEED:
                return setGameSpeed(value, g);
            case GAME_ALLM: {
                Integer w = HbMenuGates.ALLM.wire(value);
                if (w == null) throw new IllegalArgumentException("allm " + value);
                if (!hdmiNow(g)) return "panel_game_hdmi_only";
                g2.setAllmAutoSwitch(w == 1);                                   // TV 129
                return null;
            }
            default:
                return OTHER;
        }
    }

    /** HAL thread: stock gmuiapi setGameMode: 0 = 147 manual + 146 off, 1 = 76 again + 147 manual + 146 on, 2 = 147 auto. */
    private String setGameMode(String value, GmpfClient g, Gmpf2Client g2) throws Exception {
        Integer i = HbMenuGates.GAME_MODE.wire(value);
        if (i == null) throw new IllegalArgumentException("game mode " + value);
        if (!hdmiNow(g)) return "panel_game_hdmi_only";
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
        if (!ok) throw new IllegalStateException("147 / 146 returned false");
        prefs.edit().putInt(QuickPanel.KEY_GAME_MODE, i).apply();
        pendingGame = i;
        pendingGameUntil = SystemClock.uptimeMillis() + PENDING_MS;
        return null;
    }

    /** HAL thread: DISPLAY 76; false is a success only when 77 reads it within 6 s; top speed then sends 38(0). */
    private String setGameSpeed(String value, GmpfClient g) throws Exception {
        Integer w = HbMenuGates.GAME_SPEED.wire(value);
        if (w == null) throw new IllegalArgumentException("game speed " + value);
        GameModeOption o = GameModeOption.fromWire(w);
        if (!hdmiNow(g)) return "panel_game_hdmi_only";
        boolean r = g.setGameModeOption(o);
        GameModeOption now = g.getGameModeOption();
        if (!r && now != o) {
            long end = SystemClock.uptimeMillis() + GAME_OPTION_WAIT_MS;
            while (now != o && SystemClock.uptimeMillis() < end) {
                SystemClock.sleep(GAME_OPTION_POLL_MS);
                try { now = g.getGameModeOption(); } catch (Throwable t) { Log.w(TAG, "77: " + t); }
            }
            if (now != o) throw new IllegalStateException("76 -> false, 77 reads " + now);
        }
        pendingSpeed = o.wire;
        pendingSpeedUntil = SystemClock.uptimeMillis() + PENDING_MS;
        if (o.isTopSpeed()) topSpeedKeystone(o, SystemClock.uptimeMillis() + PENDING_MS);
        return null;
    }

    /** DISPLAY 38(0) for top speed once 77 reads it (re-polled every 300 ms, never blocking the HAL thread). */
    private void topSpeedKeystone(GameModeOption o, long deadline) {
        Hal.runDelayed((g, g2) -> {
            GameModeOption now = g.getGameModeOption();
            if (now == o) {
                g.resetKeystoneForTopSpeedGame();
                Ui.main().post(() -> Notify.show(app, app.getString(R.string.panel_speed_top_note)));
            } else if (SystemClock.uptimeMillis() < deadline) {
                topSpeedKeystone(o, deadline);
            } else {
                Log.w(TAG, "38(0) skipped: 77 still reads " + now + " instead of " + o);
            }
        }, TOP_SPEED_POLL_MS);
    }
}
