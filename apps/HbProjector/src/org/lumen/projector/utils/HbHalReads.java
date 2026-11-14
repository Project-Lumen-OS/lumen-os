package org.lumen.projector.utils;

import android.content.Context;
import android.util.Log;

import org.lumen.projector.hal.GameModeOption;
import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.source.TifHdmiState;

/** HB HAL reads the menu sections check right before a write (HAL thread); a failed read is null or false. */
public final class HbHalReads {
    private static final String TAG = "HbMenu";

    private HbHalReads() {}

    /** HDMI 1 / 2 on screen now (TV 4), or an open org.lumen.tvinput HDMI session. */
    public static boolean hdmiNow(Context app, GmpfClient g) {
        int src = -1;
        try { src = g.getCurrentInputSource(); } catch (Throwable t) { Log.w(TAG, "TV 4: " + t); }
        if (src == GmpfClient.SOURCE_HDMI1 || src == GmpfClient.SOURCE_HDMI2) return true;
        return Boolean.TRUE.equals(TifHdmiState.read(app));
    }

    /** Top-speed game mode running (77 top speed and 148 state on), stock gameModeFast. */
    public static boolean topSpeedGame(GmpfClient g, Gmpf2Client g2) {
        try {
            GameModeOption o = g.getGameModeOption();
            if (o == null || !o.isTopSpeed()) return false;
            int[] p = g2.getGameModeProp();
            return p == null || Gmpf2Client.gameModeOf(p, o.wire) < 0 || p[0] == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public static Boolean safeBoost(Gmpf2Client g2) {
        try { return g2.isBoost(); } catch (Throwable t) { return null; }
    }

    /** DISPLAY 174 getDynamicBlackType() == 1, null when unreadable. */
    public static Boolean dynamicBlack(Gmpf2Client g2) {
        try { return g2.getDynamicBlack(); } catch (Throwable t) { return null; }
    }
}
