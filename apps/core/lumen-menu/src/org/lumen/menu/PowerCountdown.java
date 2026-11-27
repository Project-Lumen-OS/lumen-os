// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

/**
 * The 15 s power off of the power menu, as stock (com.xgimi.shutdown PowerDialog, one code on every family: Z9X, HB,
 * X50, RS30, MIRA, Titan, Play 6). Stock: when the menu's enter animation ends with the focus on Power off and no sleep
 * timer running (onAnimationEnd: setTimeDown(true), MSG_SHUT_DOWN_15_SECOND 1 s later), the Power off tile reads
 * "关机 15s" and counts down once a second (handleShutDown15Second); one second after it read 0 the projector powers
 * off, if Power off still has the focus. The focus leaving Power off stops it for the rest of that show (onFocusChange:
 * removeMessages, setTimeDown(false); coming back does not start it again); the next show starts at 15 again (onCancel:
 * setTimeDown(15)). Keys that keep the focus on Power off (UP, DOWN in the one row of tiles) do not stop it. Plain Java,
 * main thread.
 */
public final class PowerCountdown {
    /** The stock count (PowerData.timeDown = 15). */
    public static final int SECONDS = 15;
    /** One step a second (sendTimeMessage(index, 1000)). */
    public static final long TICK_MS = 1000;
    private static final int OFF = -1;

    private int left = OFF;

    /** The menu settled: the count starts at {@link #SECONDS} when the focus is on Power off and no sleep timer runs. */
    public boolean start(boolean focusOnPowerOff, boolean timerRunning) {
        left = focusOnPowerOff && !timerRunning ? SECONDS : OFF;
        return running();
    }

    public boolean running() {
        return left != OFF;
    }

    /** The seconds Power off shows, -1 when the count does not run. */
    public int left() {
        return left;
    }

    /** One {@link #TICK_MS} passed: true when the time is up (power off now, the count ends), else it reads one less. */
    public boolean tick() {
        if (left == OFF) return false;
        if (left == 0) {
            left = OFF;
            return true;
        }
        left--;
        return false;
    }

    /**
     * The focus moved (stock onFocusChange): leaving Power off stops the count for this show (coming back does not start
     * it again), a move onto Power off changes nothing. True when this move stopped a running count.
     */
    public boolean focusChanged(boolean onPowerOff) {
        if (onPowerOff || left == OFF) return false;
        left = OFF;
        return true;
    }

    /** The menu closing or the power key again: no power off in this show. */
    public void stop() {
        left = OFF;
    }
}
