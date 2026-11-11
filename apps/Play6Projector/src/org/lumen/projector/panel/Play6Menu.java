package org.lumen.projector.panel;

import android.content.Context;
import android.util.Log;
import android.view.View;

import org.lumen.menu.MenuProfile;
import org.lumen.projector.Device;
import org.lumen.projector.Ui;
import org.lumen.projector.ui.OverlayHost;
import org.lumen.projector.ui.Panel;

import java.util.Locale;

/**
 * XGIMI Play 6 (dragon_healthy): the owner of the shared projector menu (apps/core/lumen-menu sections
 * 8 and 10). One MenuController (quick panel D) with the Play 6 adapter (Play6Features) and pages (Play6MenuPages); no
 * game menu (the stock Play 6 has none: menu.json game_menu off, gear long = the fallback, Android TV settings).
 *
 * Low memory: the controller is built when the
 * menu opens and dropped as soon as no menu window of it is left on screen (the root view of its window left the
 * window manager), unless a device screen opened from the menu will come back to it on BACK ({@link #holdFor}). The
 * menu is also built again when the language (the old one closed first, R7) or the eye protection presence (NUI
 * reader) changed. Main thread only.
 */
public final class Play6Menu {
    private static final String TAG = "P6Menu";
    private static MenuController sMenu;
    private static Locale sLocale;
    private static boolean sEye;
    /** A device screen opened from the menu is up and comes back to it on BACK: keep the controller. */
    private static boolean sHeld;

    private Play6Menu() {}

    /** The menu, built on first use (texts follow the locale it was built in). */
    static MenuController get(Context c) {
        Context app = c.getApplicationContext();
        Locale now = app.getResources().getConfiguration().getLocales().get(0);
        boolean eye = Device.hasEyeProtection();
        if (sMenu != null && (!now.equals(sLocale) || eye != sEye)) {
            Log.i(TAG, "menu built again: " + (eye != sEye ? "eye protection " + (eye ? "available" : "gone") : "language " + now));
            sMenu.close();
            sMenu = null;
        }
        if (sMenu == null) {
            sMenu = new MenuController(app, new Play6Features(app, eye), new Play6MenuPages(app));
            sLocale = now;
            sEye = eye;
        }
        return sMenu;
    }

    /** The menu built now, or null (QuickPanel.addExtension: rows reach the built menu too). */
    static MenuController built() { return sMenu; }

    /** Gear short press: the card, or close it. */
    static void toggle(Context c) {
        sHeld = false;
        get(c).toggle();
        watch(c.getApplicationContext());
    }

    /** The card (null) or a section popup ("general" = All settings, "lamp" = the light bar). */
    static void show(Context c, String section) {
        sHeld = false;
        get(c).show(section);
        watch(c.getApplicationContext());
    }

    /** Re-reads the values of the menu on screen (no-op without one). */
    static void refreshShown() {
        MenuController m = sMenu;
        if (m != null) m.refreshIfShowing();
    }

    /**
     * BACK of a device screen opened from the menu (Play6Features.open): the controller is kept while the screen is
     * up; {@code back} (the module's way back to the row that opened it) runs on the same controller.
     */
    static Runnable holdFor(Context c, Runnable back) {
        final Context app = c.getApplicationContext();
        sHeld = true;
        final MenuController m = sMenu;
        return () -> {
            sHeld = false;
            if (m != null && sMenu == null) sMenu = m;               // released meanwhile: the same one comes back
            if (back != null) back.run();
            watch(app);
        };
    }

    /**
     * Gear key held (KeyReceiver): the profile's gear long action, else {@code fallback}. The Play 6 profile says none
     * (no game menu): then only the profile is read, the menu is not built for it.
     */
    public static void gearLong(Context c, Runnable fallback) {
        try {
            MenuProfile p = MenuController.profile(c.getApplicationContext());
            if (!p.ok() || "none".equals(p.gearLong())) {
                if (fallback != null) fallback.run();
                return;
            }
            get(c).onGearLong(fallback);
        } catch (Throwable t) {
            Log.e(TAG, "gear long press", t);
            if (fallback != null) fallback.run();
        }
    }

    /** The controller exists right now (tests of the release rule, diagnostics). */
    static boolean isBuilt() { return sMenu != null; }

    /** After a show: drop the controller once its window is gone and no other menu window of ours is up. */
    private static void watch(Context app) {
        Panel p = OverlayHost.get(app).current();
        View root = p instanceof QpdPanel ? p.rootView() : null;
        if (root == null) {
            Ui.main().post(() -> releaseIfClosed(app));
            return;
        }
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}

            @Override public void onViewDetachedFromWindow(View v) {
                v.removeOnAttachStateChangeListener(this);
                Ui.main().post(() -> releaseIfClosed(app));
            }
        });
    }

    private static void releaseIfClosed(Context app) {
        if (sMenu == null || sHeld) return;
        Panel p = OverlayHost.get(app).current();
        if (p instanceof QpdPanel && p.isShowing()) return;             // a newer menu window of the same controller
        Log.i(TAG, "menu window closed: controller released");
        sMenu = null;
    }
}
