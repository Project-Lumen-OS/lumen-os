// SPDX-License-Identifier: Apache-2.0
package org.lumen.system;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * The stock screensaver page's "start after" row (newsettings screen.json screen_waiting_time_close: Never, 1, 5, 10,
 * 30 min, 1 h) over Android's own idle time, Settings.System screen_off_timeout, as the Z9X HB DreamSettings stores it
 * ("Never" = Integer.MAX_VALUE). Tokens: never, or m + minutes. A stored time outside the stock list stays readable
 * as its own token, so the row never shows a value that is not there. Writing needs WRITE_SETTINGS (the projector
 * apps run as system or platform).
 */
public final class ScreensaverDelay {
    private static final String TAG = "LumenSystem";

    /** The stock list, in its order. */
    public static final String[] TOKENS = {"never", "m1", "m5", "m10", "m30", "m60"};
    public static final String NEVER = "never";
    /** The value Android reads as no idle time at all. */
    static final long NEVER_MS = Integer.MAX_VALUE;
    private static final long MINUTE_MS = 60_000L;
    /** The longest time the row writes: one day. */
    private static final int MAX_MINUTES = 24 * 60;

    private ScreensaverDelay() { }

    /** The token of a stored screen_off_timeout (ms): never for none or Integer.MAX_VALUE, else m + whole minutes. */
    public static String token(long ms) {
        if (ms <= 0 || ms >= NEVER_MS) return NEVER;
        return "m" + Math.max(1, Math.round(ms / (double) MINUTE_MS));
    }

    /** The minutes of an m token, -1 for never, null for anything else. */
    public static Integer minutes(String token) {
        if (NEVER.equals(token)) return -1;
        if (token == null || !token.matches("m[1-9][0-9]{0,3}")) return null;
        int m = Integer.parseInt(token.substring(1));
        return m <= MAX_MINUTES ? m : null;
    }

    /** The stock list, plus {@code current} at its place when it is not one of them. */
    public static List<String> options(String current) {
        List<String> out = new ArrayList<>(java.util.Arrays.asList(TOKENS));
        Integer cm = minutes(current);
        if (cm == null || out.contains(current)) return out;
        int at = 1;
        while (at < out.size() && minutes(out.get(at)) < cm) at++;
        out.add(at, current);
        return out;
    }

    public static String get(Context c) {
        try {
            return token(Settings.System.getLong(c.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, NEVER_MS));
        } catch (Throwable t) {
            Log.w(TAG, "screensaver delay: " + t);
            return null;
        }
    }

    /** Writes the time of a token, then the read; false when it is not a token or did not stick. */
    public static boolean set(Context c, String token) {
        Integer m = minutes(token);
        if (m == null) return false;
        try {
            Settings.System.putLong(c.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, m < 0 ? NEVER_MS : m * MINUTE_MS);
        } catch (Throwable t) {
            Log.w(TAG, "screensaver delay " + token + ": " + t);
            return false;
        }
        return token.equals(get(c));
    }
}
