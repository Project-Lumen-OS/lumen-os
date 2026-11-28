// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.lumen.menu.KeyTools;
import org.lumen.menu.Rehost;

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
 * <p>Tool screens (org.lumen.menu.KeyTools): while a tool activity is in front (the X50 manual focus) the panel stays
 * under it instead of closing on the focus loss, its timer waiting, and has the keys again when the tool goes; the
 * remote's menu key goes to a tool panel ({@link #menuKey}), as in the overlay host.
 *
 * <p>Main thread only. One instance per process ({@link #install}); PanelActivity finds it through {@link #instance}.
 */
public final class ActivityPanelHost implements PanelHost, KeyTools.Host {
    private static final String TAG = "LumenMenuHost";
    /** A started PanelActivity that has not attached by then is given up (the show failed). */
    static final long ATTACH_TIMEOUT_MS = 3_000;
    /** Same grace as OverlayHost.PanelRoot: transient focus changes do not close the panel. */
    static final long FOCUS_LOST_MS = 300;
    /** A panel back from aside that has not got the window focus again by then closes (another window keeps it). */
    static final long BACK_FOCUS_MS = 1_000;

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

        /**
         * The package in front now (main thread, cheap), null when the device does not know it: then a menu whose window
         * stops simply closes (lumen-menu Rehost).
         */
        default String front() { return null; }
    }

    private static ActivityPanelHost sInstance;

    private final Context app;
    private final Notes notes;
    private final Gate gate;
    private final Handler main = new Handler(Looper.getMainLooper());
    private PanelActivity activity;
    private FrameLayout frame;
    private Panel current;
    /** The panel that waits under a tool screen (no focus-loss close, timer waiting), null when none. */
    private Panel aside;
    /** Shown but not on screen yet: waits for PanelActivity to start. */
    private Panel pending;
    /** The package in front when the shown panel opened (Gate.front), for Rehost. */
    private String frontAtShow;
    /** A panel whose window stopped while the same app stayed in front: it comes back after Rehost.WAIT_MS. */
    private final Runnable rehost = () -> {
        Panel p = pending;
        if (p == null || !p.showing || usable()) return;
        if (keepOnScreen() && allowed()) {
            Log.i(TAG, p.getClass().getSimpleName() + " back over the restarted screen of " + frontAtShow);
            if (!launch()) dropPending();
        } else {
            Log.i(TAG, "not back (screen off or another app in front): " + p.getClass().getSimpleName());
            dropPending();
        }
    };
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
        KeyTools.addHost(this);
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
        main.removeCallbacks(rehost);
        frontAtShow = front();
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

    // ================================================================== tool screens (org.lumen.menu.KeyTools)

    /**
     * {@code on}: a tool activity came over the menu activity (the X50 manual focus). The panel stays under it, seen as
     * the stock X50 menu under its focus bar (not seen under a tool that hides the menu, KeyTools.Tool.hidesMenu: the
     * subtitle mask editor), and neither the focus loss nor the timer closes it. false: the tool went, the panel is
     * seen again and has the keys where it was, with a fresh timer.
     */
    @Override
    public void aside(boolean on) {
        if (!onMain("aside")) { main.post(() -> aside(on)); return; }
        Panel p = on ? current : aside;
        if (p == null || !p.showing || (on && aside != null)) return;
        aside = on ? p : null;
        // stock shows that screen without its menu, unseen by alpha, not INVISIBLE: the row keeps its focus for the way back
        boolean hide = on && KeyTools.frontHidesMenu();
        if (p.root != null) p.root.setAlpha(hide ? 0f : 1f);
        Log.i(TAG, p.getClass().getSimpleName() + (on ? " waits under a tool screen" + (hide ? ", not seen" : "")
                : " back after the tool screen"));
        if (on) {
            main.removeCallbacks(autoHide);
        } else {
            resetAutoHide(p);
            if (p.root instanceof HostRoot) ((HostRoot) p.root).graceAfterAside();
        }
    }

    @Override
    public boolean panelOnScreen() {
        Panel p = current;
        return p != null && p.showing && p != aside;
    }

    @Override
    public boolean menuKey(boolean held) {
        Panel p = current;
        return p != null && p != aside && p.menuKey(held);
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

    /**
     * PanelActivity.onStop: the menu leaves with its window (HOME, screen off, another app in front), except when the
     * same app stays in front with the screen on (the TV input restarting its screen on a new HDMI signal): then the
     * panel waits and comes back where it was (Rehost).
     */
    void detach(PanelActivity a) {
        if (activity != a) return;
        Panel p = current;
        if (p != null && p.showing && p != aside && keepOnScreen()) {
            current = null;
            main.removeCallbacks(autoHide);
            unmount(p);
            pending = p;
            activity = null;
            frame = null;
            if (!a.isFinishing()) a.finish();                    // the new window comes on top of the input screen
            main.removeCallbacks(rehost);
            main.postDelayed(rehost, Rehost.WAIT_MS);
            return;
        }
        if (current != null) dismiss(current, false);
        activity = null;
        frame = null;
    }

    /** Takes a kept panel's view out of the stopped window (the new window builds it again). */
    private static void unmount(Panel p) {
        try {
            View root = p.root;
            if (root != null && root.getParent() instanceof ViewGroup) ((ViewGroup) root.getParent()).removeView(root);
        } catch (Throwable t) {
            Log.w(TAG, "unmount: " + t);
        }
    }

    /** The device's front package (Gate.front), null when unknown or failing. */
    private String front() {
        if (gate == null) return null;
        try {
            return gate.front();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Rehost.keep on what is read now: the screen on and the app that was in front at the show still there. */
    private boolean keepOnScreen() {
        boolean on;
        try {
            PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
            on = pm != null && pm.isInteractive();
        } catch (Throwable t) {
            on = false;
        }
        return Rehost.keep(on, frontAtShow, front(), app.getPackageName());
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
        if (aside == p) aside = null;
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
        p.exitThen(root, animate, main, remove);
    }

    /** No panel left (none shown, none waiting): the activity goes, the app below gets its window back. */
    private void finishIfIdle() {
        if (current != null || pending != null) return;
        PanelActivity a = activity;
        if (a != null && !a.isFinishing()) a.finish();
    }

    void resetAutoHide(Panel p) {
        if (p != current || p == aside) return;
        main.removeCallbacks(autoHide);
        long ms = p.autoHideMs();
        if (ms > 0) main.postDelayed(autoHide, ms);
    }

    private static boolean onMain(String what) {
        if (Looper.myLooper() == Looper.getMainLooper()) return true;
        Log.w(TAG, what + " called off the main thread");
        return false;
    }


    // ================================================================== root view of a panel
    /** Wraps every panel in the activity: key routing, timer reset, BACK, media keys, focus loss (as PanelRoot). */
    private final class HostRoot extends FrameLayout {
        private final Panel panel;
        private final PanelKeys keys;
        private boolean hadFocus;
        /** uptime when the window focus went, for Rehost.waitFocus. */
        private long lostAt;
        private final Runnable focusLostCheck = new Runnable() {
            @Override public void run() {
                if (panel.showing && panel != aside && !hasWindowFocus()) {
                    if (Rehost.waitFocus(keepOnScreen(), lostAt, SystemClock.uptimeMillis())) {
                        postDelayed(this, FOCUS_LOST_MS);       // the input app's own window for a moment
                        return;
                    }
                    Log.i(TAG, "focus lost: closing " + panel.getClass().getSimpleName());
                    dismiss(panel, true);
                }
            }
        };

        HostRoot(Activity a, Panel panel) {
            super(a);
            this.panel = panel;
            keys = new PanelKeys(TAG, app, panel, new PanelKeys.Root() {
                @Override public void resetAutoHide(Panel p) { ActivityPanelHost.this.resetAutoHide(p); }

                @Override public void dismiss(Panel p) { ActivityPanelHost.this.dismiss(p, true); }

                @Override public boolean viewsDispatch(KeyEvent ev) { return HostRoot.super.dispatchKeyEvent(ev); }

                @Override public View focused() { return HostRoot.this.findFocus(); }
            });
            setClipChildren(false);
            setClipToPadding(false);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent ev) {
            return keys.dispatch(ev);
        }

        @Override
        public void onWindowFocusChanged(boolean hasFocus) {
            super.onWindowFocusChanged(hasFocus);
            if (!hasFocus) keys.focusLost();
            if (hasFocus) {
                hadFocus = true;
                removeCallbacks(focusLostCheck);
            } else if (hadFocus) {
                removeCallbacks(focusLostCheck);
                lostAt = SystemClock.uptimeMillis();
                postDelayed(focusLostCheck, FOCUS_LOST_MS);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            // added to a window that already has the focus (a panel replacing another): no focus change comes
            if (hasWindowFocus()) hadFocus = true;
        }

        /** Back from aside: the window focus comes back by itself; when another window keeps it, the panel closes. */
        void graceAfterAside() {
            removeCallbacks(focusLostCheck);
            lostAt = SystemClock.uptimeMillis();
            postDelayed(focusLostCheck, BACK_FOCUS_MS);
        }

        @Override
        protected void onDetachedFromWindow() {
            removeCallbacks(focusLostCheck);
            super.onDetachedFromWindow();
        }
    }
}
