// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.power;

import android.content.BroadcastReceiver;
import android.content.Context;

/**
 * The family side of the power key sleep timer (SleepAlarm, core feature sleep-alarm), given by Family.sleepHost: the
 * family's stock offsets of the warnings, how they show, and the power key at the end. Times are elapsedRealtime.
 */
public interface SleepHost {
    /** Whether this unit runs the timer at all (an alarm of another image is ignored). */
    boolean active();

    /** When the notice before the end shows, -1 when none (past, or no timer at {@code at}). */
    long warnAt(long at, long now);

    /** When the seconds countdown before the end starts, -1 when none. */
    long countdownAt(long at, long now);

    /** Whether the timer ending at {@code at} may still act: only a pending timer while the projector is on. */
    boolean mayPress(long at, boolean interactive);

    /** Shows the notice ({@code minutes}) or the seconds countdown to {@code at}. */
    void notice(Context c, boolean minutes, long at);

    /** The timer was stopped from a notice: say so. */
    void stopped(Context c);

    /** The end: the stock power key press, finishing {@code r} when it is done. */
    void press(String why, BroadcastReceiver.PendingResult r);
}
