// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.ui;

import android.content.Context;
import android.view.View;
import android.view.WindowManager;

import org.lumen.projector.Ui;

/**
 * The overlay host of the shared menu (PanelHost): every call goes to the projector's {@link OverlayHost} as the menu
 * made it before the host existed (show, Panel.dismiss, dismissAll(true), current, addStatic / removeStatic,
 * Notify.show, Ui.toast). The default host of MenuController and GameMenu, so the Z9X and the HB run unchanged.
 */
public final class OverlayPanelHost implements PanelHost {
    private static OverlayPanelHost sInstance;
    private final Context app;

    public static synchronized OverlayPanelHost get(Context c) {
        if (sInstance == null) sInstance = new OverlayPanelHost(c.getApplicationContext());
        return sInstance;
    }

    private OverlayPanelHost(Context app) {
        this.app = app;
    }

    private OverlayHost overlay() { return OverlayHost.get(app); }

    @Override public boolean show(Panel p) { return overlay().show(p); }

    @Override public void dismiss(Panel p) { if (p != null) p.dismiss(); }

    @Override public void dismissAll() { overlay().dismissAll(true); }

    @Override public Panel current() { return overlay().current(); }

    @Override public boolean addStatic(View v, WindowManager.LayoutParams lp) { return overlay().addStatic(v, lp); }

    @Override public void removeStatic(View v) { overlay().removeStatic(v); }

    @Override public void note(CharSequence text) { Notify.show(app, text); }

    @Override public void note(CharSequence title, CharSequence text) { Notify.show(app, title, text); }

    @Override public void toast(int res) { Ui.toast(app, res); }
}
