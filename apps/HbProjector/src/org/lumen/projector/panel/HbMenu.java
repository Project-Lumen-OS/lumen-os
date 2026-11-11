package org.lumen.projector.panel;

import android.content.Context;
import android.util.Log;

import org.lumen.menu.MenuProfile;

/**
 * Z9X HB (goat_elegant): the owner of the shared projector menu on the HB. One MenuController
 * (quick panel D) with the HB adapter (HbMenuFeatures) and pages (HbMenuPages); the shared game menu with the HB io
 * (HbGameIo) when the profile enables it and this unit has game mode and the game menu (HbFeatures GAME_MODE +
 * GAME_BAR). Main thread only.
 *
 * Keys (profile keys): gear short = the quick menu (QuickPanel.toggle), gear long = the game menu while game mode runs
 * on the HDMI input on screen, else the fallback (TV settings) - KeyReceiver calls {@link #gearLong}.
 */
public final class HbMenu {
    private static final String TAG = "HbMenu";
    private static MenuController sMenu;

    private HbMenu() {}

    /** The menu, built on first use (texts follow the locale it was built in). */
    static MenuController get(Context c) {
        if (sMenu == null) sMenu = new MenuController(c.getApplicationContext(), new HbMenuFeatures(c), new HbMenuPages(c));
        return sMenu;
    }

    /** The menu if it was built, else null (no build just to refresh). */
    static MenuController built() { return sMenu; }

    /** Locale changed: the next use builds the menu again with the new texts. */
    static void reset() { sMenu = null; }

    /** App.onCreate: the game menu watcher with the HB io, or none (no game menu on this unit / profile). */
    public static void install(Context c) {
        try {
            MenuProfile p = MenuController.profile(c);
            boolean on = p.gameMenu() && "game_menu".equals(p.gearLong()) && !HbMenuFeatures.gameItems().isEmpty();
            Log.i(TAG, "game menu " + (on ? "on, items " + HbMenuFeatures.gameItems() : "off on this unit"));
            GameMenu.install(c, on ? new HbGameIo(c) : null);
        } catch (Throwable t) {
            Log.e(TAG, "install", t);
        }
    }

    /** Gear key held (KeyReceiver): the profile's gear long action, else {@code fallback}. */
    public static void gearLong(Context c, Runnable fallback) {
        try {
            get(c).onGearLong(fallback);
        } catch (Throwable t) {
            Log.e(TAG, "gear long press", t);
            if (fallback != null) fallback.run();
        }
    }
}
