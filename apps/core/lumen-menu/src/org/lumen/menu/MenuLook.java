// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The menu's own look the user picks in General (TITAN testers 2026-11-19): which side of the screen the menu sits on
 * and a compact size that fits more rows on a page. The same two settings on every projector, stored by the app
 * (module settings, no device call). Plain Java: the tokens and what they mean, the app keeps the stored text.
 */
public final class MenuLook {
    private MenuLook() {}

    public static final String LEFT = "left", RIGHT = "right", NORMAL = "normal", COMPACT = "compact";
    /** The options of settings.menu_side and settings.menu_density, in menu order (the default first). */
    public static final List<String> SIDES = Collections.unmodifiableList(Arrays.asList(LEFT, RIGHT)),
            DENSITIES = Collections.unmodifiableList(Arrays.asList(NORMAL, COMPACT));
    /** The size of everything in the compact menu: the page gets taller by the same factor, so more rows fit. */
    public static final float COMPACT_SCALE = 0.85f;

    /** A stored side as a token; anything unknown (nothing stored yet) is the left side, the menu's place so far. */
    public static String side(String stored) { return RIGHT.equals(stored) ? RIGHT : LEFT; }

    /** A stored size as a token; anything unknown is the normal size. */
    public static String density(String stored) { return COMPACT.equals(stored) ? COMPACT : NORMAL; }

    /** True when the menu sits on the right side. */
    public static boolean right(String stored) { return RIGHT.equals(side(stored)); }

    /** The drawing scale of the menu for a stored size (1 for normal). */
    public static float scale(String stored) { return COMPACT.equals(density(stored)) ? COMPACT_SCALE : 1f; }

    /** True for a value the setting {@code f} takes. */
    public static boolean valid(FeatureId f, String value) {
        if (f == FeatureId.SETTINGS_MENU_SIDE) return SIDES.contains(value);
        if (f == FeatureId.SETTINGS_MENU_DENSITY) return DENSITIES.contains(value);
        return false;
    }
}
