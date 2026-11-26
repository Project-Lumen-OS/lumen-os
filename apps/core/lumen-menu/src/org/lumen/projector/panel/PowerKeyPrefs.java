// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.content.SharedPreferences;

import org.lumen.menu.PowerKeyChoice;

/**
 * Where the app keeps "Short press of the power key" (General, FeatureId.SETTINGS_POWER_KEY): its own preferences,
 * nothing stored until the first change, so the profile's keys.power_short (the stock power menu) holds until then.
 * The power key of every family reads {@link #choice} at each press (lumen-menu PowerPress, the gmpf and Play 6
 * PowerKey).
 */
public final class PowerKeyPrefs {
    private PowerKeyPrefs() {}

    static final String PREFS = "lumen_power_key", K_SHORT = "short";
    /** The profile's default, read once (the profile is part of the APK). */
    private static volatile PowerKeyChoice sDefault;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** What a short press does now: the stored choice, else the profile's default. Any thread. */
    public static PowerKeyChoice choice(Context c) {
        try {
            PowerKeyChoice stored = PowerKeyChoice.of(prefs(c).getString(K_SHORT, null));
            if (stored != null) return stored;
            PowerKeyChoice d = sDefault;
            if (d == null) sDefault = d = MenuController.profile(c).powerShort();
            return d;
        } catch (RuntimeException e) {
            return PowerKeyChoice.POWER_MENU;
        }
    }

    /** The token of the setting as the menu shows it. */
    static String get(Context c) {
        return choice(c).token;
    }

    /** Stores a token; false for a value the setting does not take or a failed write. */
    static boolean set(Context c, String token) {
        if (PowerKeyChoice.of(token) == null) return false;
        try {
            return prefs(c).edit().putString(K_SHORT, token).commit();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
