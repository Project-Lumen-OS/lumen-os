package org.lumen.projector.panel;

import android.content.Context;
import android.util.Log;

import org.lumen.menu.MenuSpec;
import org.lumen.projector.KeyReceiver;
import org.lumen.projector.R;
import org.lumen.projector.Ui;
import org.lumen.projector.source.SourceOverlay;

/**
 * Z9X HB (goat_elegant): QuickPanel's controller on the HB is the shared projector menu (apps/core/lumen-menu
 * MenuController), not a quick panel of its own. The recipe drops the Z9X controller of this
 * name; this class only maps QuickPanel's calls (keys, Home tiles, SHOW_PANEL, voice, setup) onto HbMenu. Main thread.
 */
final class QuickPanelController {
    private static final String TAG = "HbMenu";
    /** Last Ultra 120 Hz value set (157 not readable), shared with ProPicture. */
    static final String KEY_ULTRA_120 = "ultra_120";

    private final Context app;

    QuickPanelController(Context c) { app = c.getApplicationContext(); }

    void toggle() { HbMenu.get(app).toggle(); }

    /** QuickPanel.SECTION_* (v6.1 names kept by their callers) onto the menu's sections and screens. */
    void show(String section) {
        if (QuickPanel.SECTION_MANUAL_KEYSTONE.equals(section)) {
            ManualKeystonePanel.open(app, () -> HbMenu.get(app).show(MenuSpec.KEYSTONE));
            return;
        }
        if (QuickPanel.SECTION_INPUT.equals(section)) {
            if (KeyReceiver.isSetupComplete(app)) SourceOverlay.showFromQuickPanel(app);
            else Ui.toast(app, R.string.toast_setup_running);
            return;
        }
        HbMenu.get(app).show(sectionOf(section));
    }

    /** The menu section of a QuickPanel section name; null = the card. */
    static String sectionOf(String section) {
        if (section == null) return null;
        switch (section) {
            case QuickPanel.SECTION_PROJECTION: return MenuSpec.KEYSTONE;          // the v6.1 keystone page name
            case QuickPanel.SECTION_PROJECTION_MODE: return MenuSpec.PROJECTION;
            case QuickPanel.SECTION_GAME: return MenuSpec.GAME;                    // "hdmi"
            case QuickPanel.SECTION_FOCUS: return MenuSpec.FOCUS;
            case QuickPanel.SECTION_KEYSTONE: return MenuSpec.KEYSTONE;
            case QuickPanel.SECTION_LAMP: return MenuSpec.LAMP;
            case QuickPanel.SECTION_PICTURE: return MenuSpec.PICTURE;
            case QuickPanel.SECTION_SOUND: return MenuSpec.SOUND;
            case QuickPanel.SECTION_EYE: return MenuSpec.EYE;
            case QuickPanel.SECTION_GENERAL: return MenuSpec.GENERAL;
            default:
                Log.i(TAG, "section " + section + ": not a menu section, the card opens");
                return null;
        }
    }

    void refreshIfShowing() {
        MenuController m = HbMenu.built();
        if (m != null) m.refreshIfShowing();
    }

    /** After the AF card: the menu reopens where it was (MenuController.restorer), null when it is not shown. */
    Runnable restorer() {
        MenuController m = HbMenu.built();
        return m == null ? null : m.restorer();
    }

    /** QuickPanel's instance call of older bases: the same as {@link #extensionAdded}. */
    void addExtension(String key, QuickPanel.Extension ext) { extensionAdded(key, ext); }

    /** Rows other modules add to a section page (QuickPanel.addExtension: game profile, screensaver, UI resolution). */
    static void extensionAdded(String key, QuickPanel.Extension ext) {
        String sec = sectionOf(key);
        if (sec == null) {
            Log.w(TAG, "extension for " + key + " ignored: not a menu section");
            return;
        }
        MenuController.addExtension(sec, new MenuController.Extension() {
            @Override public void addRows(Context a, org.lumen.projector.ui.Page page) { ext.addRows(a, page); }
            @Override public void onPageShown() { ext.onPageShown(); }
        }, HbMenu.built());
    }

    /** Locale change: QuickPanel drops this controller; the shown menu closes and is built again with the new texts. */
    void close() {
        MenuController m = HbMenu.built();
        if (m != null) m.close();
        HbMenu.reset();
    }
}
