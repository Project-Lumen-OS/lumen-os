// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.util.Log;

import org.lumen.menu.MenuProfile;
import org.lumen.menu.ProjectorFeatures;
import org.lumen.projector.Family;
import org.lumen.projector.ui.PanelHost;

import java.util.Locale;

/**
 * The owner of the shared projector menu on every projector (apps/core/lumen-menu): one
 * MenuController (quick panel D) with the family's adapter and pages (Family.menuFeatures, Family.menuPages), built
 * on first use and again after a locale change, and the game menu with the family's io when the profile and this
 * unit have it (Family.gameIo). The menu's windows live in the family's window host (Family.menuHost: null = the
 * overlay host of the menu; the MT9681 family: its activity host, never over X50EyeGuard's cover), in the same host
 * the game menu's; a family whose menu is not rebuilt by its owner on a locale change (Family.MENU_LOCALE_REBUILD)
 * gets a new menu here on the first use in the new locale. Main thread only.
 *
 * Keys (profile keys): gear short = the quick menu (QuickPanel.toggle), gear long = the profile's gear long action
 * (the game menu while game mode runs on the HDMI input on screen), else the fallback (TV settings): KeyReceiver
 * calls {@link #gearLong}.
 */
public final class ProjectorMenu {
    private static final String TAG = "ProjectorMenu";
    private static MenuController sMenu;
    private static Locale sLocale;

    private ProjectorMenu() {}

    /** The menu, built on first use (texts follow the locale it was built in). */
    public static MenuController get(Context c) {
        Context app = c.getApplicationContext();
        if (Family.MENU_LOCALE_REBUILD && sMenu != null
                && !app.getResources().getConfiguration().getLocales().get(0).equals(sLocale)) {
            sMenu.close();                                           // v3 (R7): the owner builds a new menu
            sMenu = null;
        }
        if (sMenu == null) {
            PanelHost host = Family.menuHost(app);
            ProjectorFeatures f = Family.menuFeatures(app);
            sMenu = host == null ? new MenuController(app, f, Family.menuPages(app, f))
                    : new MenuController(app, f, Family.menuPages(app, f), host);
            if (Family.MENU_LOCALE_REBUILD) sLocale = app.getResources().getConfiguration().getLocales().get(0);
        }
        return sMenu;
    }

    /** The menu if it was built, else null (no build just to refresh). */
    public static MenuController built() { return sMenu; }

    /** Locale changed: the next use builds the menu again with the new texts. */
    static void reset() { sMenu = null; }

    /** App.onCreate: the game menu watcher with the family's io, or none (no game menu in the profile or on this unit). */
    public static void installGameMenu(Context c) {
        try {
            MenuProfile p = MenuController.profile(c);
            GameMenu.Io io = p.gameMenu() && "game_menu".equals(p.gearLong()) ? Family.gameIo(c) : null;
            Log.i(TAG, "game menu " + (io != null ? "on" : "off on this unit"));
            GameMenu.install(c, io, Family.menuHost(c));
        } catch (Throwable t) {
            Log.e(TAG, "game menu install", t);
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
