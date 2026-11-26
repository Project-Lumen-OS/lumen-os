// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.util.Log;

/**
 * The quick menu of the gmpf families behind QuickPanel (their Family.menu* members): one QuickPanelController, built
 * on first use and again after a locale change. Main thread only (KeyReceiver), so no locking beyond this.
 */
public final class QuickPanelOwner {
    private static final String TAG = "Z9xPanel";
    private static QuickPanelController sCtl;

    private QuickPanelOwner() {}

    public static void toggle(Context ctx) {
        ctl(ctx).toggle();
    }

    public static void show(Context ctx, String section) {
        ctl(ctx).show(section);
    }

    public static void refreshIfShowing() {
        QuickPanelController c = sCtl;
        if (c != null) c.refreshIfShowing();
    }

    public static Runnable restorer() {
        QuickPanelController c = sCtl;
        return c == null ? null : c.restorer();
    }

    /** SECTION_PROJECTION rows go to the Keystone page; the shared menu keeps them for every menu it builds. */
    public static void addExtension(String section, QuickPanel.Extension ext) {
        String key = QuickPanel.SECTION_PROJECTION.equals(section) ? QuickPanel.SECTION_KEYSTONE : section;
        try {
            QuickPanelController.extensionAdded(key, ext);
        } catch (Throwable t) {
            Log.e(TAG, "extension for " + key + " failed", t);
        }
    }

    public static void onLocaleChanged(Context ctx) {
        QuickPanelController c = sCtl;
        sCtl = null;
        if (c != null) c.close();
    }

    private static QuickPanelController ctl(Context ctx) {
        if (sCtl == null) sCtl = new QuickPanelController(ctx.getApplicationContext());
        return sCtl;
    }
}
