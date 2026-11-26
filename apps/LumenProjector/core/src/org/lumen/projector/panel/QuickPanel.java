// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.util.Log;

import org.lumen.projector.Family;
import org.lumen.projector.ui.Page;

/**
 * The quick settings of every projector family: one API for the callers (KeyReceiver, Lumen Home's Projector tiles
 * through PanelRequestReceiver, the Source key, the voice commands, the Setup bridge), the menu behind it per family
 * (Family.menuToggle / menuShow / menuRefresh / menuRestorer / menuExtension / menuLocaleChanged).
 *
 * Fixed entry points (called on the MAIN thread, cheap, must not throw; the callers wrap them anyway):
 *  - {@link #toggle(Context)}: gear short press (KEYCODE_SETTINGS UP without a long press).
 *  - {@link #show(Context, String)}: open at a section page (IR KEYSTONE/LENS key ->
 *    SECTION_PROJECTION = the Keystone page). null / unknown = the tile grid or the card.
 *
 * For other modules: {@link #addExtension(String, Extension)} adds rows to a section page (e.g. the game module's
 * console options on SECTION_GAME, a screensaver or sleep-timer row on SECTION_GENERAL) without editing panel files.
 *
 * The gmpf families: the shared projector menu (apps/core/lumen-menu MenuController) behind
 * {@link QuickPanelOwner} and {@link QuickPanelController} (the Z9X adapter Z9xFeatures, its stock rules
 * Z9xMenuRules, its pages Z9xMenuPages); ManualKeystonePanel is the 4-corner keystone card. The Play 6: the same
 * menu behind Play6Menu, its corner card Play6KeystonePanel. The standby window of the panel's All settings page
 * (KEY_QUICK_WAKE_MIN) is stored and applied by PowerPolicy / StandbyController (prefs z9x_power).
 */
public final class QuickPanel {
    private static final String TAG = Family.TAG_PREFIX + "Panel";

    // ---- section pages (tile keys). The v6.1 names keep their callers working.
    public static final String SECTION_FOCUS = "focus";
    /** Keystone / image correction page (auto keystone, fit, manual keystone, keystone settings). */
    public static final String SECTION_KEYSTONE = "keystone";
    /**
     * v6.3: not a page. show(SECTION_MANUAL_KEYSTONE) opens {@link ManualKeystonePanel} directly (4 corners,
     * live vendor warp; SetupBridge kst_manual). Lumen OS 1.0: no tile of its own any more, the Keystone
     * page's "Manual keystone" row is its entry in the panel.
     */
    public static final String SECTION_MANUAL_KEYSTONE = "manual_keystone";
    /** v6.1 name used by the IR KEYSTONE/LENS key: the Keystone page. */
    public static final String SECTION_PROJECTION = "projection";
    /** Brightness page (lamp level 1..10 + Boost). */
    public static final String SECTION_LAMP = "lamp";
    public static final String SECTION_PICTURE = "picture";
    public static final String SECTION_SOUND = "sound";
    /** Not a page: the Input tile opens the Source overlay. */
    public static final String SECTION_INPUT = "input";
    /** Projection mode page (auto / table / ceiling / rear). */
    public static final String SECTION_PROJECTION_MODE = "projection_mode";
    /** Game mode page (HDMI only). SECTION_HDMI is the v6.1 name of the same page. */
    public static final String SECTION_GAME = "hdmi";
    public static final String SECTION_HDMI = SECTION_GAME;
    public static final String SECTION_EYE = "eye";
    /** "All settings" page: system settings, projector settings, HDMI-CEC, recent apps, home screen. */
    public static final String SECTION_GENERAL = "general";

    /** SharedPreferences file of the panel module (org.lumen.projector process). */
    public static final String PREFS = "z9x_panel";
    /**
     * int in prefs {@link #PREFS}: the game mode last set (0 off, 1 on, 2 auto; IGmpf2 55 is not read).
     * The panel shows it on the Game tile and page; the v6.2 console profile should write it too
     * when it switches game mode, so both show one state.
     */
    public static final String KEY_GAME_MODE = "game_mode";
    /** int minutes; see {@link #quickWakeMinutes}. */
    public static final String KEY_QUICK_WAKE_MIN = "quick_wake_min";
    /** v6.1 requirements decisions: "default 30 min, set in the panel". */
    public static final int DEFAULT_QUICK_WAKE_MIN = 30;

    /**
     * Rows another module adds to a section page. Both methods run on the MAIN thread and must be
     * cheap and must not block; HAL reads go through Hal.query, writes through Hal.run (v6.1 design 3).
     * Exceptions are caught and logged by the panel.
     */
    public interface Extension {
        /**
         * Called once when the page is built (again after a locale change, because the panel is
         * then rebuilt with new texts). Add rows with {@code page.add(...)}; keep references to
         * update them later.
         */
        void addRows(Context app, Page page);

        /** Each time the page becomes visible: load the values of your rows here. */
        default void onPageShown() {}
    }

    private QuickPanel() {}

    public static void toggle(Context ctx) {
        try {
            Family.menuToggle(ctx);
        } catch (Throwable t) {
            Log.e(TAG, "toggle failed", t);
        }
    }

    public static void show(Context ctx, String section) {
        try {
            Family.menuShow(ctx, section);
        } catch (Throwable t) {
            Log.e(TAG, "show failed", t);
        }
    }

    /** Re-reads the values of the menu on screen (no-op without one). */
    public static void refreshIfShowing() {
        try {
            Family.menuRefresh();
        } catch (Throwable t) {
            Log.e(TAG, "refreshIfShowing failed", t);
        }
    }

    /** What reopens the menu where it was (an overlay closing over it), or null. */
    public static Runnable restorer() {
        try {
            return Family.menuRestorer();
        } catch (Throwable t) {
            Log.e(TAG, "restorer failed", t);
            return null;
        }
    }

    public static void addExtension(String section, Extension ext) {
        if (section == null || ext == null) return;
        Family.menuExtension(section, ext);
    }

    /** The app's locale changed: the menu is built again in the new language on its next use. */
    public static void onLocaleChanged(Context ctx) {
        Family.menuLocaleChanged(ctx);
    }
}
