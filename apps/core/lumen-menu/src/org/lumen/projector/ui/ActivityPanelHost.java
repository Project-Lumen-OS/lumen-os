// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * The activity host of the shared menu (PanelHost): the same {@link Panel} views the overlay
 * host puts into TYPE_APPLICATION_OVERLAY windows, here inside ONE translucent activity of the app
 * ({@link PanelActivity}, declared by the device app with a translucent theme). For a projector app that adds no
 * overlay windows: X50Projector (no overlay permission by rule; X50EyeGuard's black cover must stay the top
 * window, so the menu is an ordinary activity the cover can come over).
 *
 * <p>What stays as in {@link OverlayHost}: one panel at a time (a new one replaces the shown one), the panel's own
 * layout params give its size, gravity and offset inside the full-screen activity window, every key resets the
 * auto-hide timer and goes to Panel.onKeyEvent first, BACK (UP) goes to Panel.onBack and closes the panel when that
 * returns false, media keys go to the active media session, an OK press that began in another window is dropped, and
 * the panel closes when another window takes the focus (300 ms grace), on screen off / HOME (the activity stops) and
 * on the auto-hide timeout. What differs: the activity opens on the first show and finishes when the last panel is
 * gone; the app below is paused while the menu is up (an activity, not an overlay); there are no static layers
 * ({@link #addStatic} answers false: no game crosshair or frame counter in this host); notes go to the app's own
 * {@link Notes} (the overlay Notify card is an overlay window too).
 *
 * <p>Main thread only. One instance per process ({@link #install}); PanelActivity finds it through {@link #instance}.
 */
public final class ActivityPanelHost implements PanelHost {
    private static final String TAG = "LumenMenuHost";
    /** A started PanelActivity that has not attached by then is given up (the show failed). */
    static final long ATTACH_TIMEOUT_MS = 3_000;
    /** Same grace as OverlayHost.PanelRoot: transient focus changes do not close the panel. */
    static final long FOCUS_LOST_MS = 300;

    /** How the app shows a short note and a toast (X50: its own TvFocus toast; never an overlay window). */
    public interface Notes {
        void note(Context c, CharSequence text);
    }

    /**
     * The device's check before a NEW panel comes on screen (main thread, cheap). X50: never over X50EyeGuard's
     * cover, nothing when the activity in front is unknown. False, or a throw, = not shown (fail closed).
     */
    public interface Gate {
        boolean mayShow();
    }

    private static ActivityPanelHost sInstance;

    private final Context app;
    private final Notes notes;
    private final Gate gate;
    private final Handler main = new Handler(Looper.getMainLooper());
    private PanelActivity activity;
    private FrameLayout frame;
    private Panel current;
    /** Shown but not on screen yet: waits for PanelActivity to start. */
    private Panel pending;
    private final Runnable autoHide = () -> {
        Panel p = current;
        if (p != null) {
            Log.i(TAG, "auto-hide " + p.getClass().getSimpleName());
            dismiss(p, true);
        }
    };
    private final Runnable attachTimeout = () -> {
        Panel p = pending;
        if (p != null) {
            Log.w(TAG, "the menu activity did not start: " + p.getClass().getSimpleName() + " not shown");
            pending = null;
            p.showing = false;
        }
    };

    /** The process's activity host without a gate (built once; later calls return the same one). */
    public static synchronized ActivityPanelHost install(Context c, Notes notes) {
        return install(c, notes, null);
    }

    /** The process's activity host; {@code gate} is asked before every new panel (null: always). */
    public static synchronized ActivityPanelHost install(Context c, Notes notes, Gate gate) {
        if (sInstance == null) sInstance = new ActivityPanelHost(c.getApplicationContext(), notes, gate);
        return sInstance;
    }

    /** The host PanelActivity attaches to, null when the app never installed one. */
    static synchronized ActivityPanelHost instance() { return sInstance; }

    private ActivityPanelHost(Context app, Notes notes, Gate gate) {
        this.app = app;
        this.notes = notes;
        this.gate = gate;
    }

    // ================================================================== PanelHost

    @Override
    public boolean show(Panel p) {
        if (!onMain("show")) { main.post(() -> show(p)); return true; }
        if (p == null) return false;
        if (p == current && p.showing) { resetAutoHide(p); return true; }
        if (p == pending && p.showing) return true;                  // its activity is on the way
        if (!allowed()) {
            Log.i(TAG, "not shown now (the device's gate): " + p.getClass().getSimpleName());
            return false;
        }
        if (current != null) dismiss(current, false);
        if (pending != null && pending != p) dropPending();
        p.showSerial++;
        p.showing = true;
        if (usable()) {
            pending = null;
            return mount(p);
        }
        pending = p;
        if (!launch()) {
            dropPending();
            return false;
        }
        return true;
    }

    /** The device's gate; a failing gate answers no. */
    private boolean allowed() {
        if (gate == null) return true;
        try {
            return gate.mayShow();
        } catch (Throwable t) {
            Log.w(TAG, "gate: " + t);
            return false;
        }
    }

    @Override public void dismiss(Panel p) { dismiss(p, true); }

    @Override public void dismissAll() {
        if (!onMain("dismissAll")) { main.post(this::dismissAll); return; }
        if (pending != null) dropPending();
        if (current != null) dismiss(current, true);
    }

    @Override public Panel current() { return current != null ? current : pending; }

    /** No layer over other apps in this host (it would need an overlay window). */
    @Override public boolean addStatic(View v, WindowManager.LayoutParams lp) {
        Log.i(TAG, "no static layer in the activity host: " + (lp == null ? "" : lp.getTitle()) + " not shown");
        return false;
    }

    @Override public void removeStatic(View v) { }

    /** No layers over the game here: the game menu leaves out its crosshair and frame counter tiles. */
    @Override public boolean hasStaticLayers() { return false; }

    @Override public void note(CharSequence text) {
        if (text == null || text.length() == 0) return;
        try {
            if (notes != null) notes.note(app, text);
        } catch (Throwable t) {
            Log.w(TAG, "note: " + t);
        }
    }

    /** The title and the text in one note ("Boost: ..."): the app's Notes show one line. */
    @Override public void note(CharSequence title, CharSequence text) {
        if (title == null || title.length() == 0) note(text);
        else if (text == null || text.length() == 0) note(title);
        else note(title + ": " + text);
    }

    @Override public void toast(int res) {
        if (res == 0) return;
        note(app.getString(res));
    }

    // ================================================================== the activity

    /** PanelActivity.onStart: from now on panels go into its window; a waiting panel goes in at once. */
    void attach(PanelActivity a, FrameLayout content) {
        if (activity != null && activity != a && current != null) dismiss(current, false);   // a panel of an older window
        activity = a;
        frame = content;
        main.removeCallbacks(attachTimeout);
        Panel p = pending;
        pending = null;
        if (p != null && p.showing) {
            mount(p);
        } else if (current == null) {
            Log.i(TAG, "menu activity started with nothing to show: finished");
            a.finish();
        }
    }

    /** PanelActivity.onStop: the menu leaves with its window (HOME, screen off, another app in front). */
    void detach(PanelActivity a) {
        if (activity != a) return;
        if (current != null) dismiss(current, false);
        activity = null;
        frame = null;
    }

    private boolean usable() {
        return activity != null && frame != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private boolean launch() {
        try {
            app.startActivity(new Intent(app, PanelActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION));
            main.removeCallbacks(attachTimeout);
            main.postDelayed(attachTimeout, ATTACH_TIMEOUT_MS);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "cannot start the menu activity (PanelActivity declared in the manifest?)", t);
            return false;
        }
    }

    private void dropPending() {
        Panel p = pending;
        pending = null;
        main.removeCallbacks(attachTimeout);
        if (p != null) p.showing = false;
        finishIfIdle();
    }

    private boolean mount(Panel p) {
        try {
            View content = p.onCreateView(app);
            HostRoot root = new HostRoot(activity, p);
            root.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            p.root = root;
            current = p;
            frame.addView(root, place(p.onCreateLayoutParams(app)));
            root.post(() -> {
                if (p.showing && current == p) {
                    try { p.onShown(root); } catch (Throwable t) { Log.w(TAG, "onShown: " + t); }
                }
            });
            resetAutoHide(p);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "cannot show " + p.getClass().getSimpleName(), t);
            p.showing = false;
            if (current == p) current = null;
            finishIfIdle();
            return false;
        }
    }

    /**
     * The panel's window params as a place inside the full-screen activity window: the same width, height and
     * gravity; the x / y offset becomes the margin on the gravity's side (as WindowManager applies it).
     */
    static FrameLayout.LayoutParams place(WindowManager.LayoutParams lp) {
        int g = lp.gravity == 0 ? Gravity.TOP | Gravity.START : lp.gravity;
        FrameLayout.LayoutParams f = new FrameLayout.LayoutParams(lp.width, lp.height, g);
        int h = Gravity.getAbsoluteGravity(g, View.LAYOUT_DIRECTION_LTR) & Gravity.HORIZONTAL_GRAVITY_MASK;
        int v = g & Gravity.VERTICAL_GRAVITY_MASK;
        if (h == Gravity.RIGHT) f.rightMargin = lp.x; else f.leftMargin = lp.x;
        if (v == Gravity.BOTTOM) f.bottomMargin = lp.y; else f.topMargin = lp.y;
        return f;
    }

    void dismiss(Panel p, boolean animate) {
        if (!onMain("dismiss")) { main.post(() -> dismiss(p, animate)); return; }
        if (p == null || !p.showing) return;
        if (p == pending) { dropPending(); return; }
        p.showing = false;
        if (current == p) {
            current = null;
            main.removeCallbacks(autoHide);
        }
        final View root = p.root;
        final int serial = p.showSerial;
        Runnable remove = () -> {
            try {
                if (root != null && root.getParent() instanceof ViewGroup) ((ViewGroup) root.getParent()).removeView(root);
            } catch (Throwable t) {
                Log.w(TAG, "remove: " + t);
            }
            // shown again while this exit animation ran: the old show's onDismissed must not tear the new one down
            if (p.showSerial == serial) {
                try { p.onDismissed(); } catch (Throwable t) { Log.w(TAG, "onDismissed: " + t); }
            }
            finishIfIdle();
        };
        if (animate && root != null && root.isAttachedToWindow()) {
            final boolean[] done = {false};
            Runnable once = () -> { if (!done[0]) { done[0] = true; remove.run(); } };
            try {
                p.animateOut(root, once);
            } catch (Throwable t) {
                once.run();
                return;
            }
            main.postDelayed(once, Theme.PANEL_EXIT_MS + 300);   // safety net
        } else {
            remove.run();
        }
    }

    /** No panel left (none shown, none waiting): the activity goes, the app below gets its window back. */
    private void finishIfIdle() {
        if (current != null || pending != null) return;
        PanelActivity a = activity;
        if (a != null && !a.isFinishing()) a.finish();
    }

    void resetAutoHide(Panel p) {
        if (p != current) return;
        main.removeCallbacks(autoHide);
        long ms = p.autoHideMs();
        if (ms > 0) main.postDelayed(autoHide, ms);
    }

    private static boolean onMain(String what) {
        if (Looper.myLooper() == Looper.getMainLooper()) return true;
        Log.w(TAG, what + " called off the main thread");
        return false;
    }

    private static boolean isOkKey(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    // ================================================================== root view of a panel
    /** Wraps every panel in the activity: key routing, timer reset, BACK, media keys, focus loss (as PanelRoot). */
    private final class HostRoot extends FrameLayout {
        private final Panel panel;
        private boolean backDown;
        /** downTime of the OK press whose DOWN reached this window, -1 when none. */
        private long okDownTime = -1;
        private boolean hadFocus;
        private final Runnable focusLostCheck = new Runnable() {
            @Override public void run() {
                if (panel.showing && !hasWindowFocus()) {
                    Log.i(TAG, "focus lost: closing " + panel.getClass().getSimpleName());
                    dismiss(panel, true);
                }
            }
        };

        HostRoot(Activity a, Panel panel) {
            super(a);
            this.panel = panel;
            setClipChildren(false);
            setClipToPadding(false);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent ev) {
            if (!panel.showing) return true;
            resetAutoHide(panel);
            int code = ev.getKeyCode();
            if (KeyEvent.isMediaSessionKey(code)) {
                if (ev.getAction() == KeyEvent.ACTION_DOWN || ev.getAction() == KeyEvent.ACTION_UP) {
                    try {
                        AudioManager am = app.getSystemService(AudioManager.class);
                        if (am != null) am.dispatchMediaKeyEvent(ev);
                    } catch (Throwable t) {
                        Log.w(TAG, "media key: " + t);
                    }
                }
                return true;
            }
            if (isOkKey(code) && !okOfThisWindow(ev)) return true;
            try {
                if (panel.onKeyEvent(ev)) return true;
            } catch (Throwable t) {
                Log.w(TAG, "onKeyEvent: " + t);
                return true;
            }
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                if (ev.getAction() == KeyEvent.ACTION_DOWN) {
                    if (ev.getRepeatCount() == 0) backDown = true;
                } else if (ev.getAction() == KeyEvent.ACTION_UP) {
                    if (backDown && !ev.isCanceled()) {
                        boolean consumed = false;
                        try { consumed = panel.onBack(); } catch (Throwable t) { Log.w(TAG, "onBack: " + t); }
                        if (!consumed) dismiss(panel, true);
                    }
                    backDown = false;
                }
                return true;
            }
            try {
                return super.dispatchKeyEvent(ev);
            } catch (Throwable t) {
                Log.w(TAG, "dispatchKeyEvent: " + t);
                return true;
            }
        }

        /** The rest of an OK press that went DOWN in another window never reaches the panel (OverlayHost rule). */
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
                if (!ours) Log.i(TAG, "OK up of a press begun elsewhere: dropped (" + panel.getClass().getSimpleName() + ")");
                return ours;
            }
            return true;
        }

        @Override
        public void onWindowFocusChanged(boolean hasFocus) {
            super.onWindowFocusChanged(hasFocus);
            if (!hasFocus) okDownTime = -1;
            if (hasFocus) {
                hadFocus = true;
                removeCallbacks(focusLostCheck);
            } else if (hadFocus) {
                removeCallbacks(focusLostCheck);
                postDelayed(focusLostCheck, FOCUS_LOST_MS);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            // added to a window that already has the focus (a panel replacing another): no focus change comes
            if (hasWindowFocus()) hadFocus = true;
        }

        @Override
        protected void onDetachedFromWindow() {
            removeCallbacks(focusLostCheck);
            super.onDetachedFromWindow();
        }
    }
}
