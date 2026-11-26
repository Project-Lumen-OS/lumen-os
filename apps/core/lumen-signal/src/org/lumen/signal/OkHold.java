// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.signal;

/**
 * Holding OK while a developer card is on screen (2026-11-19): the small card opens into its full panel, holding
 * OK again folds it back. Holding BACK hides the small card from anywhere (2026-11-21). Pure state machine over
 * the key filter's events (CardHold feeds it, host tested).
 *
 * The film or the console must never lose an OK press. A short press on the small card passes at once and unchanged:
 * its DOWN goes on when it comes (nothing waits for the release) and its UP too, so the app in front sees exactly the
 * press the remote made. Only a press held for {@link #LONG_MS} opens the panel; that press's DOWN has already reached
 * the app, which then gets the UP as cancelled when the panel takes the keys. While the full panel is open the keys
 * are the panel's anyway: there OK is taken whole (short = the panel's own OK, held = fold back). BACK is never
 * taken: a short one reaches the app unchanged, a held one makes the card take the keys at {@link #LONG_MS} (the app
 * gets that press cancelled, as with the held OK) and the card goes. With no card on screen nothing is taken.
 */
public final class OkHold {
    /** How long OK or BACK must be held, a little over the system long press so a quick press never acts. */
    public static final long LONG_MS = 600L;

    /** The keys the card follows. */
    public enum Key { OK, BACK, OTHER }

    /** What is on screen when a key comes. */
    public enum Shown { NONE, CARD, SHEET }

    /** What the card does. */
    public enum Act { NONE, EXPAND, COLLAPSE, SHEET_OK, HIDE }

    /** What happens to one key event. */
    public static final class Step {
        static final Step PASS = new Step(false, Act.NONE, -1), TAKE = new Step(true, Act.NONE, -1);
        /** true = the key filter takes the event, the app never sees it */
        public final boolean consume;
        public final Act act;
        /** uptime ms at which {@link #timer} must run, -1 = no timer (an armed one is cancelled) */
        public final long due;

        Step(boolean consume, Act act, long due) {
            this.consume = consume;
            this.act = act;
            this.due = due;
        }
    }

    /**
     * What the key filter may treat as on screen for a key (2026-11-21: with the full panel open, OK on the
     * gear of the quick menu opened over it switched the panel's option instead of opening the menu; 2026-11-23 the
     * same over the sensor card). One rule for every overlay: nothing is the overlay's while a Lumen panel is in front
     * ({@code appHasKeys}: the quick menu shown, a keystone page or the Remote buttons screen resumed, a window of this
     * app with the focus). Otherwise the full panel takes OK only while its own window has the input focus
     * ({@code panelHasKeys}) and the small card's hold acts on the keys that go to another app.
     */
    public static Shown shown(PanelMode mode, boolean panelHasKeys, boolean appHasKeys) {
        if (appHasKeys || !mode.visible()) return Shown.NONE;
        if (mode == PanelMode.DETAIL) return panelHasKeys ? Shown.SHEET : Shown.NONE;
        return mode.card() ? Shown.CARD : Shown.NONE;
    }

    /**
     * When the long press timer may run: never before the filter saw the press ({@code now}, uptime ms). The filter
     * runs on the app's main loop: when that loop was busy, the press's UP already waits behind the DOWN, and a timer
     * due in the past would run first and count a short press as held.
     */
    public static long timerAt(long due, long now) { return Math.max(due, now); }

    /** a press is followed (its DOWN seen, no UP yet); its DOWN was taken; the long action may still fire */
    private boolean tracking, owned, armed;
    private Key pressed = Key.OTHER;
    private long downAt;

    /** One key event from the filter, OK or not ({@link #key(Key, boolean, int, Shown, long)}). */
    public synchronized Step key(boolean ok, boolean down, int repeat, Shown shown, long downTime) {
        return key(ok ? Key.OK : Key.OTHER, down, repeat, shown, downTime);
    }

    /**
     * One key event from the filter. {@code repeat}: the event's repeat count; {@code downTime}: the press's down time
     * (uptime ms). Only OK in the full panel with the keys is ever taken; BACK and every other key go on unchanged.
     */
    public synchronized Step key(Key k, boolean down, int repeat, Shown shown, long downTime) {
        if (k == Key.OTHER) {
            if (down && tracking) armed = false;
            return Step.PASS;
        }
        if (down) {
            if (repeat > 0) return tracking && owned && pressed == k ? Step.TAKE : Step.PASS;
            tracking = true;
            pressed = k;
            owned = k == Key.OK && shown == Shown.SHEET;
            armed = k == Key.OK ? shown != Shown.NONE : shown == Shown.CARD;
            downAt = downTime;
            return new Step(owned, Act.NONE, armed ? downAt + LONG_MS : -1);
        }
        if (!tracking || pressed != k) return Step.PASS;
        boolean took = owned, quick = armed;
        tracking = owned = armed = false;
        if (!took) return Step.PASS;
        return new Step(true, quick && shown == Shown.SHEET ? Act.SHEET_OK : Act.NONE, -1);
    }

    /** The long press timer at {@code now} (uptime ms): opens, folds or hides once per press. */
    public synchronized Act timer(Shown shown, long now) {
        if (!tracking || !armed || now - downAt < LONG_MS) return Act.NONE;
        armed = false;
        if (pressed == Key.BACK) return shown == Shown.CARD ? Act.HIDE : Act.NONE;
        if (owned) return shown == Shown.SHEET ? Act.COLLAPSE : Act.NONE;
        return shown == Shown.CARD ? Act.EXPAND : Act.NONE;
    }

    /** When the armed timer is due, -1 = none. */
    public synchronized long due() { return tracking && armed ? downAt + LONG_MS : -1; }

    /** A press is being followed (the filter must stay on until its UP). */
    public synchronized boolean busy() { return tracking; }

    /**
     * OK in the full panel's own window, when the filter did not take it (it was not running yet): act on the UP of a
     * press whose DOWN this window saw, never on the UP of the press that opened the panel or a cancelled one.
     */
    public static Act windowUp(boolean sawDown, boolean cancelled, long heldMs) {
        if (!sawDown || cancelled) return Act.NONE;
        return heldMs >= LONG_MS ? Act.COLLAPSE : Act.SHEET_OK;
    }
}
