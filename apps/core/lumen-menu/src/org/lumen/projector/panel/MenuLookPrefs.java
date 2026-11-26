// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.content.SharedPreferences;

import org.lumen.menu.FeatureId;
import org.lumen.menu.MenuLook;

/** Where the app keeps the menu's look (MenuLook): its own preferences, nothing stored until the first change. */
final class MenuLookPrefs {
    private MenuLookPrefs() {}

    static final String PREFS = "lumen_menu_look", K_SIDE = "side", K_DENSITY = "density";

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The token of {@code f} as stored (the default when nothing is). */
    static String get(Context c, FeatureId f) {
        try {
            if (f == FeatureId.SETTINGS_MENU_SIDE) return MenuLook.side(prefs(c).getString(K_SIDE, null));
            if (f == FeatureId.SETTINGS_MENU_DENSITY) return MenuLook.density(prefs(c).getString(K_DENSITY, null));
        } catch (RuntimeException e) {
            // the defaults below
        }
        return f == FeatureId.SETTINGS_MENU_SIDE ? MenuLook.LEFT : f == FeatureId.SETTINGS_MENU_DENSITY ? MenuLook.NORMAL : null;
    }

    /** Stores a token of {@code f}; false for a value the setting does not take or a failed write. */
    static boolean set(Context c, FeatureId f, String value) {
        if (!MenuLook.valid(f, value)) return false;
        try {
            return prefs(c).edit().putString(f == FeatureId.SETTINGS_MENU_SIDE ? K_SIDE : K_DENSITY, value).commit();
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean right(Context c) { return MenuLook.right(get(c, FeatureId.SETTINGS_MENU_SIDE)); }

    static float scale(Context c) { return MenuLook.scale(get(c, FeatureId.SETTINGS_MENU_DENSITY)); }
}
