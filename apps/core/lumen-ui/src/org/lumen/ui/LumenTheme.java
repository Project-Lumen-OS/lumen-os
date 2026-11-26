// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import java.util.TimeZone;

/**
 * The ONE theme switch of Lumen (Home, Setup, the menus, any platform app): the choice lives in Settings.Secure
 * {@value #KEY_MODE} ("light", "dark", "auto"; missing = light), the last known place for Авто in {@value #KEY_GEO}
 * ("lat,lon"). Writing needs WRITE_SECURE_SETTINGS (every platform-signed Lumen app holds it); reading works for every
 * system app. The rule itself is {@link ThemeRule}; follow changes live with {@link ThemeWatcher}.
 */
public final class LumenTheme {
    public static final String KEY_MODE = "lumen_ui_theme";
    public static final String KEY_GEO = "lumen_ui_geo";
    public static final Palette LIGHT = Palette.light(), DARK = Palette.dark();
    private static final String TAG = "LumenTheme";

    private LumenTheme() {}

    public static ThemeMode mode(Context c) {
        return ThemeMode.parse(read(c, KEY_MODE));
    }

    /** Stores the user's choice; false when this app may not write secure settings. */
    public static boolean setMode(Context c, ThemeMode m) {
        return write(c, KEY_MODE, m.id);
    }

    /** The last known place (null: none yet). */
    public static double[] geo(Context c) {
        return ThemeRule.parseGeo(read(c, KEY_GEO));
    }

    /** Remembers the place for Авто; a write only when it moved (two decimals). */
    public static void setGeo(Context c, double lat, double lon) {
        String v = ThemeRule.geo(lat, lon);
        if (!v.equals(read(c, KEY_GEO))) write(c, KEY_GEO, v);
    }

    public static boolean dark(Context c) {
        return ThemeRule.dark(mode(c), System.currentTimeMillis(), TimeZone.getDefault(), geo(c));
    }

    public static Palette palette(Context c) {
        return dark(c) ? DARK : LIGHT;
    }

    /** When Авто flips next (UTC ms), Long.MAX_VALUE for a fixed choice. */
    public static long nextChange(Context c) {
        return ThemeRule.nextChange(mode(c), System.currentTimeMillis(), TimeZone.getDefault(), geo(c));
    }

    private static String read(Context c, String key) {
        try {
            return Settings.Secure.getString(c.getContentResolver(), key);
        } catch (RuntimeException e) {
            Log.w(TAG, "read " + key + ": " + e);
            return null;
        }
    }

    private static boolean write(Context c, String key, String v) {
        try {
            ContentResolver cr = c.getContentResolver();
            return Settings.Secure.putString(cr, key, v);
        } catch (RuntimeException e) {
            Log.w(TAG, "write " + key + ": " + e);
            return false;
        }
    }
}
