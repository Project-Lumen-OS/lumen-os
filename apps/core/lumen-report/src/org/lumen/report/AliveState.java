// SPDX-License-Identifier: Apache-2.0
package org.lumen.report;

/**
 * The sign of life of this start event by event (AliveWatch feeds it): UP while the picture is on, CLEAN after every
 * normal way out. Screen off (sleep, the deadline of a standby), lamp-only standby (the STANDBY_CHANGED broadcast of
 * Z9X, HB and Play 6, or the standby screen of Z9X and HB in front, which an update restart shows from the boot on),
 * shutdown and reboot. After a shutdown or reboot nothing is written any more: that start is over. Plain Java.
 */
final class AliveState {
    static final String START = "start", BEAT = "beat", SCREEN_ON = "screen_on", SCREEN_OFF = "screen_off",
            STANDBY_ON = "standby", STANDBY_OFF = "standby_off", DREAM_ON = "dream", DREAM_OFF = "dream_off",
            SHUTDOWN = "shutdown", REBOOT = "reboot";
    /** A beat refreshes an UP record this often; its time is the last sign of life of a stop (10 minutes). */
    static final long BEAT_MS = 10 * 60_000L;
    /** CLEAN because the standby screen is in front (its broadcast may have gone out before this process listened). */
    static final String STANDBY_SCREEN = "standby_screen";

    private boolean standby, dreaming, down;
    private Alive last;

    /** {@code prev}: the record on disk before this process looked; a record of this start is continued. */
    AliveState(Alive prev, String boot, long up) {
        if (prev == null || !Alive.sameBoot(prev, boot, up) || "start".equals(prev.why)) return;
        last = prev;
        standby = Alive.CLEAN.equals(prev.state) && STANDBY_ON.equals(prev.why);
        dreaming = prev.screensaver;
    }

    /** The record to write after {@code event}, or null when the one on disk still holds. */
    Alive on(String event, boolean interactive, String front, String boot, long time, long up) {
        if (down) return null;
        String clean = null;
        switch (event) {
            case SHUTDOWN:
            case REBOOT:
                down = true;
                clean = event;
                break;
            case SCREEN_OFF:
                standby = false;                    // a real screen off ends a lamp-only standby on every model
                dreaming = false;
                clean = event;
                break;
            case STANDBY_ON:
                standby = true;
                break;
            case STANDBY_OFF:
                standby = false;
                break;
            case DREAM_ON:
                dreaming = true;
                break;
            case DREAM_OFF:
                dreaming = false;
                break;
            default:
                break;
        }
        if (clean == null) {
            clean = !interactive ? SCREEN_OFF : standby ? STANDBY_ON : Alive.STANDBY_ACTIVITY.equals(front) ? STANDBY_SCREEN : null;
        }
        Alive next = new Alive(clean == null ? Alive.UP : Alive.CLEAN, clean == null ? event : clean, time, up, boot, front,
                dreaming);
        if (BEAT.equals(event) && Alive.CLEAN.equals(next.state) && last != null && Alive.CLEAN.equals(last.state)) return null;
        last = next;
        return next;
    }

    /** Beats run while the screen is on: they refresh an UP record and notice a standby screen that went away. */
    static boolean beats(boolean interactive) {
        return interactive;
    }
}
