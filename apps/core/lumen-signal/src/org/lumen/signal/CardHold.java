// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.signal;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import org.lumen.remote.keys.KeyHook;

/**
 * Holding OK on a developer card (the signal card, the sensor card) opens its full panel, holding it again folds it
 * back (OkHold, README.md). The card registers here whenever it shows or hides; only while one is on screen the key
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
    }

    private static final String PREFS = "lumen_signal", SEEN = "hold_hint_seen", LEARNED = "hold_learned";
    private static final long RELEASE_MS = 400L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final OkHold HOLD = new OkHold();
    private static Context sApp;
    private static Card sCard;
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

    /** True when the small card should show the hint now (the first few cards, until the user held OK once). */
    public static boolean hint(Context c) { return hint(c, false); }

    /** {@code byKey}: opened by the remote key the user gave it (OkHold.hint). */
    public static boolean hint(Context c, boolean byKey) {
        SharedPreferences p = prefs(c);
        return OkHold.hint(p.getInt(SEEN, 0), p.getBoolean(LEARNED, false), byKey);
    }

    /** A small card came on screen with the hint: one of the few times is used. */
    public static void hintShown(Context c) {
        SharedPreferences p = prefs(c);
        p.edit().putInt(SEEN, p.getInt(SEEN, 0) + 1).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static OkHold.Shown shownNow() {
        Card card = sCard;
        PanelMode m = card == null ? PanelMode.HIDDEN : card.mode();
        return m == PanelMode.DETAIL ? OkHold.Shown.SHEET : m.card() ? OkHold.Shown.CARD : OkHold.Shown.NONE;
    }

    /** OK on a remote or a keyboard. */
    public static boolean ok(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private static final Runnable TIMER = CardHold::tick;

    /** The filter's question for every key: the answer comes at once, the card's work runs after it on the main loop. */
    private static final KeyHook.Hook HOOK = e -> {
        OkHold.Step s = HOLD.key(ok(e.getKeyCode()), e.getAction() == KeyEvent.ACTION_DOWN, e.getRepeatCount(),
                shownNow(), e.getDownTime());
        if (s.due >= 0) {
            MAIN.removeCallbacks(TIMER);
            MAIN.postAtTime(TIMER, s.due);
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
        Log.i(SignalPanel.TAG, "OK held: " + a);
        switch (a) {
            case EXPAND:
                if (sApp != null) prefs(sApp).edit().putBoolean(LEARNED, true).apply();
                card.expand();
                break;
            case COLLAPSE: card.collapse(); break;
            case SHEET_OK: card.sheetOk(); break;
            default: break;
        }
    }
}
