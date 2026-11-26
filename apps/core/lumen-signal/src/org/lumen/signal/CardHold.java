// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.signal;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import org.lumen.remote.keys.KeyHook;

/**
 * Holding OK on a developer card (the signal card, the sensor card) opens its full panel, holding it again folds it
 * back, holding BACK hides the small card (OkHold, README.md). The card registers here whenever it shows or hides; only while one is on screen the key
 * filter runs (lumen-remote KeyHook), so with no card nothing at all sits between the remote and the app. Main thread.
 */
public final class CardHold {
    /** The card on screen. */
    public interface Card {
        PanelMode mode();

        void expand();

        void collapse();

        /** A short OK while the full panel is open (the panel's own OK action). */
        void sheetOk();

        /** The full panel's own window has the input focus (another window opened over it keeps the keys). */
        boolean hasKeys();

        /** BACK held on the small card: it takes the keys (CardKeys) and goes. */
        void dismiss();
    }

    private static final long RELEASE_MS = 400L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final OkHold HOLD = new OkHold();
    private static Context sApp;
    private static Card sCard;
    private static boolean sFollowing;
    /** how often the release waited for a held OK's UP (at most 10 times, 4 s) */
    private static int sWaits;

    private CardHold() {}

    /**
     * App start: no card is on screen yet, so a key filter left on by a process that died with a card showing (the
     * setting outlives the process) goes off again, unless the keyboard binds want it.
     */
    public static void start(Context c) {
        sApp = c.getApplicationContext();
        sCard = null;
        KeyHook.clear(sApp);
        followActivities(sApp);
    }

    /** The card's mode now: registers it while visible, lets the filter go when nothing is on screen. */
    public static void shown(Context c, Card card, PanelMode mode) {
        sApp = c.getApplicationContext();
        if (mode.visible()) {
            sCard = card;
            MAIN.removeCallbacks(RELEASE);
            KeyHook.set(sApp, HOOK);
        } else if (sCard == card) {
            sCard = null;
            MAIN.removeCallbacks(TIMER);
            MAIN.removeCallbacks(RELEASE);
            sWaits = 0;
            MAIN.postDelayed(RELEASE, RELEASE_MS);
        }
    }

    /**
     * The filter goes a moment after the last card: one card replacing the other (signal and sensors never show
     * together) does not switch it off and on, and an OK still held gets its UP through first. Meanwhile no card is on
     * screen, so the hook takes nothing.
     */
    private static final Runnable RELEASE = new Runnable() {
        @Override public void run() {
            if (sCard != null || sApp == null) return;
            if (HOLD.busy() && sWaits++ < 10) {
                MAIN.postDelayed(this, RELEASE_MS);
                return;
            }
            KeyHook.set(sApp, null);
        }
    };

    /** What the filter may act on now: the card's mode, and who has the keys (OkHold.shown). */
    private static OkHold.Shown shownNow() {
        Card card = sCard;
        if (card == null) return OkHold.Shown.NONE;
        return OkHold.shown(card.mode(), card.hasKeys(), KeyHook.appHasKeys());
    }

    /** Every activity of this app tells KeyHook when it is in front and when it has the input focus (once per process). */
    private static void followActivities(Context c) {
        Context app = c.getApplicationContext();
        if (sFollowing || !(app instanceof Application)) return;
        sFollowing = true;
        ((Application) app).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle b) {
                a.getWindow().getDecorView().getViewTreeObserver().addOnWindowFocusChangeListener(
                        has -> KeyHook.windowFocus(a, has));
            }

            @Override public void onActivityStarted(Activity a) {}

            @Override public void onActivityResumed(Activity a) { KeyHook.front(a, true); }

            @Override public void onActivityPaused(Activity a) { KeyHook.front(a, false); }

            @Override public void onActivityStopped(Activity a) { KeyHook.windowFocus(a, false); }

            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}

            @Override public void onActivityDestroyed(Activity a) {
                KeyHook.front(a, false);
                KeyHook.windowFocus(a, false);
            }
        });
    }

    /** The key as the card follows it: OK (remote or keyboard), BACK, any other. */
    static OkHold.Key key(int code) {
        if (ok(code)) return OkHold.Key.OK;
        return code == KeyEvent.KEYCODE_BACK ? OkHold.Key.BACK : OkHold.Key.OTHER;
    }

    /** OK on a remote or a keyboard. */
    public static boolean ok(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private static final Runnable TIMER = CardHold::tick;

    /** The filter's question for every key: the answer comes at once, the card's work runs after it on the main loop. */
    private static final KeyHook.Hook HOOK = e -> {
        OkHold.Step s = HOLD.key(key(e.getKeyCode()), e.getAction() == KeyEvent.ACTION_DOWN, e.getRepeatCount(),
                shownNow(), e.getDownTime());
        if (s.due >= 0) {
            MAIN.removeCallbacks(TIMER);
            MAIN.postAtTime(TIMER, OkHold.timerAt(s.due, SystemClock.uptimeMillis()));
        } else if (HOLD.due() < 0) {
            MAIN.removeCallbacks(TIMER);
        }
        if (s.act != OkHold.Act.NONE) MAIN.post(() -> run(s.act));
        return s.consume;
    };

    private static void tick() {
        long now = SystemClock.uptimeMillis();
        OkHold.Act a = HOLD.timer(shownNow(), now);
        if (a != OkHold.Act.NONE) run(a);
        else if (HOLD.due() > now) {
            MAIN.removeCallbacks(TIMER);
            MAIN.postAtTime(TIMER, HOLD.due());
        }
    }

    private static void run(OkHold.Act a) {
        Card card = sCard;
        if (card == null) return;
        Log.i(SignalPanel.TAG, "held: " + a);
        switch (a) {
            case EXPAND: card.expand(); break;
            case HIDE: card.dismiss(); break;
            case COLLAPSE: card.collapse(); break;
            case SHEET_OK: card.sheetOk(); break;
            default: break;
        }
    }
}
