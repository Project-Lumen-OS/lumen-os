// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.signal;

import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * BACK held on a small developer card (2026-11-21): the card's window takes the keys, so the app in front gets
 * that press cancelled (as when a held OK opens the full panel) and never acts on its release; the card goes on the
 * press's UP, which then reaches the card. The filter never takes a BACK itself: a taken UP would leave Android
 * repeating the key to the app. Main thread.
 */
public final class CardKeys {
    /** The card goes by then even when the UP went elsewhere (released before the focus came). */
    static final long GONE_MS = 1_500L;

    private CardKeys() {}

    /** {@code root}: the card's window root (not focusable until now); {@code gone} runs once. */
    public static void take(WindowManager wm, View root, Runnable gone) {
        Runnable once = new Runnable() {
            private boolean done;

            @Override public void run() {
                if (done) return;
                done = true;
                gone.run();
            }
        };
        if (root == null || root.getParent() == null) {
            once.run();
            return;
        }
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.setDefaultFocusHighlightEnabled(false);
        root.setOnKeyListener((v, code, e) -> {
            if (e.getAction() == KeyEvent.ACTION_UP) once.run();
            return true;
        });
        try {
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) root.getLayoutParams();
            p.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            wm.updateViewLayout(root, p);
            root.requestFocus();
        } catch (Throwable t) {
            Log.w(SignalPanel.TAG, "card keys: " + t);
            once.run();
            return;
        }
        root.postDelayed(once, GONE_MS);
    }
}
