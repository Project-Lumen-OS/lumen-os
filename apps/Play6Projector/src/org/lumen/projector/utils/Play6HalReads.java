package org.lumen.projector.utils;

import android.util.Log;

import org.lumen.projector.hal.Play6Gmpf;
import org.lumen.projector.panel.Play6MenuGates;

/** Play 6 HAL reads the menu sections check right before a write (p6-hal). */
public final class Play6HalReads {
    private static final String TAG = "P6Menu";

    private Play6HalReads() {}

    /** The HDMI port is the current source right now (IGmpf 696, the vendor keeps these per source). */
    public static boolean hdmiNow(Play6Gmpf g, String what) {
        int src = -1;
        try { src = g.getCurrentInputSource(); } catch (Throwable t) { Log.w(TAG, what + ": 696 " + t); }
        if (src == Play6MenuGates.SOURCE_HDMI1) return true;
        Log.i(TAG, what + " refused: HDMI is not the current source (696=" + src + ")");
        return false;
    }
}
