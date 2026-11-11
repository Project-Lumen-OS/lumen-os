package org.lumen.projector.panel;

import android.content.Context;
import android.util.Log;

import org.lumen.menu.MenuSpec;
import org.lumen.projector.KeyReceiver;
import org.lumen.projector.R;
import org.lumen.projector.Ui;
import org.lumen.projector.source.SourceOverlay;
import org.lumen.projector.ui.Page;

/**
 * The quick menu of the XGIMI Play 6. A fork of Z9xProjector panel/QuickPanel.java (tools/play6/forks.tsv) that keeps
 * its public API (SECTION_*, show, toggle, refreshIfShowing, addExtension), so the shared PanelRequestReceiver (Lumen
 * Home's Projector tiles), SourceOverlay, the voice commands and KeyReceiver work unchanged. Since the shared menu
 * (apps/core/lumen-menu) the menu itself is the quick panel D of every Lumen projector: this class only
 * maps the old section names onto it and forwards extension rows (Play6Menu: the controller, the Play 6 adapter
 * Play6Features and pages Play6MenuPages). The old Play 6 panel (Play6PanelController, its tile grid and pages) is
 * gone.
 *
 * Fixed entry points (MAIN thread, cheap, must not throw; the callers wrap them anyway):
 *  - {@link #toggle(Context)}: gear short press;
 *  - {@link #show(Context, String)}: a section (IR KEYSTONE/LENS key -> SECTION_PROJECTION = the keystone popup);
 *    null / unknown = the card.
 */
public final class QuickPanel {
    private static final String TAG = "P6Panel";

    // ---- section names of the callers (v6.1 names, kept)
    public static final String SECTION_FOCUS = "focus";
    /** Keystone popup (auto keystone, manual keystone, keystone settings). */
    public static final String SECTION_KEYSTONE = "keystone";
    /** Not a page: show(SECTION_MANUAL_KEYSTONE) opens {@link ManualKeystonePanel} directly. */
    public static final String SECTION_MANUAL_KEYSTONE = "manual_keystone";
    /** The name used by the IR KEYSTONE/LENS key: the keystone popup. */
    public static final String SECTION_PROJECTION = "projection";
    /** The light bar (level 1..10). */
    public static final String SECTION_LAMP = "lamp";
    public static final String SECTION_PICTURE = "picture";
    public static final String SECTION_SOUND = "sound";
    /** Not a page: the Input list (Source overlay). */
    public static final String SECTION_INPUT = "input";
    /** Projection mode popup (auto / table / ceiling / rear). */
    public static final String SECTION_PROJECTION_MODE = "projection_mode";
    /** Game mode popup (HDMI only). SECTION_HDMI is the old name of the same page. */
    public static final String SECTION_GAME = "hdmi";
    public static final String SECTION_HDMI = SECTION_GAME;
    /** Eye protection: the card when the unit has no NUI reader (no eye protection in the menu then). */
    public static final String SECTION_EYE = "eye";
    /** "All settings". */
    public static final String SECTION_GENERAL = "general";

    /** SharedPreferences file of the panel module (org.lumen.projector process). */
    public static final String PREFS = "z9x_panel";

    /** Rows another module adds to a section page: the shared menu's extension rows (R4). */
    public interface Extension extends MenuController.Extension {}

    private QuickPanel() {}

    /** Gear short press: show the menu, or close it if it is on screen. */
    public static void toggle(Context ctx) {
        try {
            Play6Menu.toggle(ctx);
        } catch (Throwable t) {
            Log.e(TAG, "toggle failed", t);
        }
    }

    /** Shows the menu at {@code section} (SECTION_*); unknown/null = the card. */
    public static void show(Context ctx, String section) {
        try {
            Context app = ctx.getApplicationContext();
            if (SECTION_MANUAL_KEYSTONE.equals(section)) {
                ManualKeystonePanel.open(app, () -> Play6Menu.show(app, MenuSpec.KEYSTONE));
                return;
            }
            if (SECTION_INPUT.equals(section)) {
                if (KeyReceiver.isSetupComplete(app)) SourceOverlay.toggle(app);
                else Ui.toast(app, R.string.toast_setup_running);
                return;
            }
            Play6Menu.show(app, sectionOf(section));
        } catch (Throwable t) {
            Log.e(TAG, "show failed", t);
        }
    }

    /** The menu section of a QuickPanel section name; null = the card. */
    static String sectionOf(String section) {
        if (section == null) return null;
        switch (section) {
            case SECTION_PROJECTION: return MenuSpec.KEYSTONE;
            case SECTION_PROJECTION_MODE: return MenuSpec.PROJECTION;
            case SECTION_GAME: return MenuSpec.GAME;
            case SECTION_FOCUS: return MenuSpec.FOCUS;
            case SECTION_KEYSTONE: return MenuSpec.KEYSTONE;
            case SECTION_LAMP: return MenuSpec.LAMP;
            case SECTION_PICTURE: return MenuSpec.PICTURE;
            case SECTION_SOUND: return MenuSpec.SOUND;
            case SECTION_EYE: return MenuSpec.EYE;
            case SECTION_GENERAL: return MenuSpec.GENERAL;
            default:
                Log.i(TAG, "section " + section + ": not a menu section, the card opens");
                return null;
        }
    }

    /**
     * Re-reads the values of an open menu (no-op when it is closed): battery/Battery calls it on the vendor power
     * events 3 and 5, so the light bar shows the level the vendor just set. MAIN thread.
     */
    public static void refreshIfShowing() {
        try {
            Play6Menu.refreshShown();
        } catch (Throwable t) {
            Log.e(TAG, "refreshIfShowing failed", t);
        }
    }

    /**
     * Rows another module adds to {@code section} (a SECTION_* name): kept by the shared menu for every menu built
     * later and added to the one built now (MenuController.addExtension). No module of the Play 6 app adds rows yet.
     */
    public static void addExtension(String section, Extension ext) {
        if (section == null || ext == null) return;
        String sec = sectionOf(section);
        if (sec == null) {
            Log.w(TAG, "extension for " + section + " (" + ext.getClass().getName() + "): not a menu section");
            return;
        }
        MenuController.addExtension(sec, ext, Play6Menu.built());
    }
}
