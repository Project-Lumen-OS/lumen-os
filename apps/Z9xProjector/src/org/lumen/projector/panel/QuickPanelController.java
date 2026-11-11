// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import android.content.Context;
import android.util.Log;

import org.lumen.menu.MenuSpec;
import org.lumen.projector.KeyReceiver;
import org.lumen.projector.R;
import org.lumen.projector.Ui;
import org.lumen.projector.source.SourceOverlay;

/**
 * Z9X: QuickPanel's controller is the shared projector menu (apps/core/lumen-menu MenuController with Z9xFeatures,
 * the design notes); the Z9X quick panel code of 1.0.10 lives on in the module and in the adapter. This
 * class only maps QuickPanel's calls (keys, Home tiles, SHOW_PANEL, voice, setup) onto Z9xMenu. Main thread.
 */
final class QuickPanelController {
    private static final String TAG = "Z9xPanel";
    /** boolean in QuickPanel.PREFS: the Ultra 120 Hz value set last (synced from 157 on every read; ProPicture too). */
    static final String KEY_ULTRA_120 = "ultra_120";

    private final Context app;

    QuickPanelController(Context c) { app = c.getApplicationContext(); }

    void toggle() { Z9xMenu.get(app).toggle(); }

    /** QuickPanel.SECTION_* (the v6.1 names its callers keep) onto the menu's sections and screens. */
    void show(String section) {
        if (QuickPanel.SECTION_MANUAL_KEYSTONE.equals(section)) {
            ManualKeystonePanel.open(app, null);                  // not from the panel: BACK just closes
            return;
        }
        if (QuickPanel.SECTION_INPUT.equals(section)) {             // the Source key's overlay, as 1.0.10
            if (KeyReceiver.isSetupComplete(app)) SourceOverlay.toggle(app);
            else Ui.toast(app, R.string.toast_setup_running);
            return;
        }
        Z9xMenu.get(app).show(sectionOf(section));
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
        MenuController m = Z9xMenu.built();
        if (m != null) m.refreshIfShowing();
    }

    /** After the AF card: the menu reopens where it was, null when it is not the panel shown. */
    Runnable restorer() {
        MenuController m = Z9xMenu.built();
        return m == null ? null : m.restorer();
    }

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
        }, Z9xMenu.built());
    }


    /** Locale change: the shown menu closes; the next use builds it again with the new texts. */
    void close() {
        MenuController m = Z9xMenu.built();
        if (m != null) m.close();
        Z9xMenu.reset();
    }
}
