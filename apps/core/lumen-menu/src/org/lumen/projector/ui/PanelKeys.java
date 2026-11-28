// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.ui;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import org.lumen.ui.KeySound;

/**
 * The key rule of a panel's root view, one for both panel hosts (the windows of OverlayHost, the activity of
 * {@link ActivityPanelHost}): every key resets the auto-hide timer and goes to Panel.onKeyEvent first; media keys go to
 * the active media session (AudioManager.dispatchMediaKeyEvent: a bare window has no PhoneFallbackEventHandler); BACK
 * goes to Panel.onBack on the UP of a DOWN this window saw and closes the panel when that returns false; the rest goes
 * to the views.
 *
 * <p>Key sounds (org.lumen.ui.KeySound, as the stock menus): an OK of this window clicks once on its release (the
 * focused view's own click is muted for that key, so a Row or a clickable row never clicks twice), and a D-pad key whose
 * handling moved the focus plays that direction's sound. A D-pad key nobody handled goes on to Android's focus search,
 * which sounds by itself. Owner report 2026-11-28: the menus clicked only on the sound output page, the one page whose
 * arrows the views leave to Android; every other page moves the focus in our code and was silent.
 *
 * <p>2026-10-31: the rest of an OK press that went DOWN in another window (the held repeats and the UP of the row press
 * that opened this panel, log 14:54:47) never reaches the panel: its OK toggles and confirmations that act on the UP
 * (optical zoom size / position, zoom shift, fit width) only see presses that began here.
 *
 * <p>One instance per root view. Main thread.
 */
final class PanelKeys {
    /** The host side of one root view. */
    interface Root {
        void resetAutoHide(Panel p);

        /** Closes {@code p} with its exit animation. */
        void dismiss(Panel p);

        /** The views' own dispatch (FrameLayout.dispatchKeyEvent). */
        boolean viewsDispatch(KeyEvent ev);

        /** The focused view of this root, null when none. */
        View focused();
    }

    private final String tag;
    private final Context app;
    private final Panel panel;
    private final Root root;
    private boolean backDown;
    /** downTime of the OK press whose DOWN reached this window, -1 when none. */
    private long okDownTime = -1;

    PanelKeys(String tag, Context app, Panel panel, Root root) {
        this.tag = tag;
        this.app = app;
        this.panel = panel;
        this.root = root;
    }

    boolean dispatch(KeyEvent ev) {
        if (!panel.showing) return true;
        root.resetAutoHide(panel);
        int code = ev.getKeyCode();
        if (KeyEvent.isMediaSessionKey(code)) {
            if (ev.getAction() == KeyEvent.ACTION_DOWN || ev.getAction() == KeyEvent.ACTION_UP) {
                try {
                    AudioManager am = app.getSystemService(AudioManager.class);
                    if (am != null) am.dispatchMediaKeyEvent(ev);
                } catch (Throwable t) {
                    Log.w(tag, "media key: " + t);
                }
            }
            return true;
        }
        if (isOkKey(code) && !okOfThisWindow(ev)) return true;
        View before = root.focused();
        boolean click = isOkKey(code) && ev.getAction() == KeyEvent.ACTION_UP && !ev.isCanceled();
        if (click) KeySound.click(before);
        boolean mute = click && before != null && before.isSoundEffectsEnabled();
        if (mute) before.setSoundEffectsEnabled(false);
        try {
            boolean used = route(ev, code);
            if (used && ev.getAction() == KeyEvent.ACTION_DOWN) {
                View after = root.focused();
                if (after != null && after != before) KeySound.move(after, code);
            }
            return used;
        } finally {
            if (mute) before.setSoundEffectsEnabled(true);
        }
    }

    /** Panel first, then BACK, then the views (the key rule above). */
    private boolean route(KeyEvent ev, int code) {
        try {
            if (panel.onKeyEvent(ev)) return true;
        } catch (Throwable t) {
            Log.w(tag, "onKeyEvent: " + t);
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (ev.getAction() == KeyEvent.ACTION_DOWN) {
                if (ev.getRepeatCount() == 0) backDown = true;
            } else if (ev.getAction() == KeyEvent.ACTION_UP) {
                if (backDown && !ev.isCanceled()) {
                    boolean consumed = false;
                    try { consumed = panel.onBack(); } catch (Throwable t) { Log.w(tag, "onBack: " + t); }
                    if (!consumed) root.dismiss(panel);
                }
                backDown = false;
            }
            return true;
        }
        try {
            return root.viewsDispatch(ev);
        } catch (Throwable t) {
            Log.w(tag, "dispatchKeyEvent: " + t);
            return true;
        }
    }

    /** The window lost the focus: an OK press that began before belongs to another window now. */
    void focusLost() {
        okDownTime = -1;
    }

    private boolean okOfThisWindow(KeyEvent ev) {
        int a = ev.getAction();
        if (a == KeyEvent.ACTION_DOWN) {
            if (ev.getRepeatCount() == 0) {
                okDownTime = ev.getDownTime();
                return true;
            }
            return ev.getDownTime() == okDownTime;
        }
        if (a == KeyEvent.ACTION_UP) {
            boolean ours = okDownTime != -1 && ev.getDownTime() == okDownTime;
            okDownTime = -1;
            if (!ours) Log.i(tag, "OK up of a press begun elsewhere: dropped (" + panel.getClass().getSimpleName() + ")");
            return ours;
        }
        return true;
    }

    static boolean isOkKey(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }
}
