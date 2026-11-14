// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.utils;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import org.lumen.projector.hal.GameModeOption;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.source.TifHdmiState;

/** Z9X HAL reads the menu sections check right before a write (HAL thread). */
public final class Z9xHalReads {
    private static final String TAG = "Z9xFeatures";
    private static final long GAME_OPTION_WAIT_MS = 6_000, GAME_OPTION_POLL_MS = 100;

    private Z9xHalReads() {}

    /** QPC:771 hdmiNow: HDMI 1/2 on screen (696) or an open HDMI TIF session. */
    public static boolean hdmiNow(Context app, GmpfClient g, String what) {
        int src = -1;
        try { src = g.getCurrentInputSource(); } catch (Throwable t) { Log.w(TAG, what + ": 696 " + t); }
        if (src == GmpfClient.SOURCE_HDMI1 || src == GmpfClient.SOURCE_HDMI2) return true;
        Boolean tif = TifHdmiState.read(app);
        if (Boolean.TRUE.equals(tif)) return true;
        Log.i(TAG, what + " refused: not on HDMI (696=" + src + ", tvinput session=" + tif + ")");
        return false;
    }

    public static boolean dolbyVision(GmpfClient g) {
        try { return g.getHdrType() == GmpfClient.HDR_DOLBY_VISION; } catch (Throwable t) { return false; }
    }

    public static boolean threeD(GmpfClient g) throws Exception {
        return g.getCurrent3DFormat() != 1 || g.is3DTo2DEnabled();
    }

    /** 647 until it reads {@code want} or the wait is over. */
    public static GameModeOption waitGameOption(GmpfClient g, GameModeOption want) {
        long end = SystemClock.uptimeMillis() + GAME_OPTION_WAIT_MS;
        GameModeOption now = null;
        while (true) {
            try { now = g.getGameModeOption(); } catch (Throwable t) { Log.w(TAG, "647: " + t); }
            if (now == want || SystemClock.uptimeMillis() >= end) return now;
            SystemClock.sleep(GAME_OPTION_POLL_MS);
        }
    }
}
