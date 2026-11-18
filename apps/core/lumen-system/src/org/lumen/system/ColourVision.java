// SPDX-License-Identifier: Apache-2.0
package org.lumen.system;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

/**
 * Android's colour correction (Settings.Secure accessibility_display_daltonizer_enabled and accessibility_display_daltonizer):
 * the stock General page's colour vision row (newsettings VisionManager writes the same two settings, filter 0 grayscale,
 * 11 red-green, 12 green-red, 13 blue-yellow). One choice: off, or the filter that is on. The stock strength slider writes
 * accessibility_display_daltonizer_saturation_level, which the Android 14 ColorDisplayService never reads, so it has no row.
 * Writing needs WRITE_SECURE_SETTINGS (the projector apps run as system).
 */
public final class ColourVision {
    private static final String TAG = "LumenSystem";
    static final String ENABLED = "accessibility_display_daltonizer_enabled";
    static final String MODE = "accessibility_display_daltonizer";
    public static final String OFF = "off";
    /** The stock filter tokens and their daltonizer modes, in the stock list order. */
    public static final String[] TOKENS = {"gray", "red_green", "green_red", "blue_yellow"};
    static final int[] MODES = {0, 11, 12, 13};

    private ColourVision() { }

    /** The current choice: OFF, or the token of the filter that is on (OFF also for a mode the stock list lacks). */
    public static String get(Context c) {
        try {
            if (Settings.Secure.getInt(c.getContentResolver(), ENABLED, 0) != 1) return OFF;
            return token(Settings.Secure.getInt(c.getContentResolver(), MODE, MODES[2]));
        } catch (Throwable t) {
            Log.w(TAG, "colour vision read: " + t);
            return OFF;
        }
    }

    /** OFF turns the correction off and keeps the filter, a filter token sets it and turns the correction on. */
    public static boolean set(Context c, String token) {
        int mode = mode(token);
        if (mode < 0 && !OFF.equals(token)) return false;
        try {
            if (mode >= 0) Settings.Secure.putInt(c.getContentResolver(), MODE, mode);
            Settings.Secure.putInt(c.getContentResolver(), ENABLED, mode >= 0 ? 1 : 0);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "colour vision " + token + ": " + t);
            return false;
        }
    }

    static String token(int mode) {
        for (int i = 0; i < MODES.length; i++) if (MODES[i] == mode) return TOKENS[i];
        return OFF;
    }

    static int mode(String token) {
        for (int i = 0; i < TOKENS.length; i++) if (TOKENS[i].equals(token)) return MODES[i];
        return -1;
    }
}
