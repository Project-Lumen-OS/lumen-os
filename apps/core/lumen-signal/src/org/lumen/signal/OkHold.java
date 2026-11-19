// SPDX-License-Identifier: Apache-2.0
package org.lumen.signal;

/**
 * Holding OK while a developer card is on screen (2026-11-19): the small card opens into its full panel, holding
 * OK again folds it back. Pure state machine over the key filter's events (CardHold feeds it, host tested).
 *
 * The film or the console must never lose an OK press. A short press on the small card passes at once and unchanged:
 * its DOWN goes on when it comes (nothing waits for the release) and its UP too, so the app in front sees exactly the
 * press the remote made. Only a press held for {@link #LONG_MS} opens the panel; that press's DOWN has already reached
 * the app, which then gets the UP as cancelled when the panel takes the keys. While the full panel is open the keys
 * are the panel's anyway: there OK is taken whole (short = the panel's own OK, held = fold back). With no card on
 * screen nothing is taken.
 */
public final class OkHold {
    /** How long OK must be held, a little over the system long press so a quick press never opens the panel. */
    public static final long LONG_MS = 600L;
    /** How many times the small card shows the hint before it goes (and never again once the user held OK). */
    public static final int HINT_TIMES = 3;

    /** What is on screen when a key comes. */
    public enum Shown { NONE, CARD, SHEET }

    /** What the card does. */
    public enum Act { NONE, EXPAND, COLLAPSE, SHEET_OK }

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

    /** an OK press is followed (its DOWN seen, no UP yet); its DOWN was taken; the long action may still fire */
    private boolean tracking, owned, armed;
    private long downAt;

    /**
     * One key event from the filter. {@code ok}: DPAD_CENTER or ENTER; {@code repeat}: the event's repeat count;
     * {@code downTime}: the press's down time (uptime ms).
     */
    public synchronized Step key(boolean ok, boolean down, int repeat, Shown shown, long downTime) {
        if (!ok) {
            if (down && tracking) armed = false;
            return Step.PASS;
        }
        if (down) {
            if (repeat > 0) return tracking && owned ? Step.TAKE : Step.PASS;
            tracking = true;
            owned = shown == Shown.SHEET;
            armed = shown != Shown.NONE;
            downAt = downTime;
            return new Step(owned, Act.NONE, armed ? downAt + LONG_MS : -1);
        }
        if (!tracking) return Step.PASS;
        boolean took = owned, quick = armed;
        tracking = owned = armed = false;
        if (!took) return Step.PASS;
        return new Step(true, quick && shown == Shown.SHEET ? Act.SHEET_OK : Act.NONE, -1);
    }

    /** The long press timer at {@code now} (uptime ms): opens or folds the panel once per press. */
    public synchronized Act timer(Shown shown, long now) {
        if (!tracking || !armed || now - downAt < LONG_MS) return Act.NONE;
        armed = false;
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

    /** The hint on the small card: the first few times, never again once the user held OK. */
    public static boolean hint(int seen, boolean learned) {
        return !learned && seen < HINT_TIMES;
    }
}
