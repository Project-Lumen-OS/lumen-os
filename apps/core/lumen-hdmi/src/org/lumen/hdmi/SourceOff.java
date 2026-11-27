// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hdmi;

/**
 * The HDMI source being watched turned off: the projector follows it (SourceFollow runs it, plain Java).
 *
 * <p>A source that turns off with its own button rarely says {@code <Standby>} (0x36): the owner's PS5, in rest mode
 * and powered off, sends {@code <Menu Status>} deactivated and {@code <Inactive Source>} (0x9D) only (live traces in
 * test/source_off_traces.tsv). Android 14's TV takes no action on that {@code <Inactive Source>} while the source's
 * port is the one shown (HdmiCecLocalDeviceTv.handleInactiveSource returns when the previous port is that port), and
 * apps never see the message. What apps do see is the framework's power status cache: {@code <Active Source>} sets
 * it to on (handleActiveSource), {@code <Report Power Status>} to the reported value, the TV asks every source once a
 * minute (PowerStatusMonitorAction). So the rule reads that cache: a source behind the shown port that was on or
 * turning on during this viewing and then reports standby (or turning to standby) on {@value #CONFIRM_READS}
 * reads in a row means "the device you are watching turned off". Several sources behind one port (a console behind
 * an AV receiver's input) count only when all of them are off; an audio system, the TV and switches never count.
 * A source that was already asleep when the port was opened never puts the projector to sleep, nor does one that
 * only says it has no video and stays on ({@code <Inactive Source>} without standby, CEC 1.4b 13.2).
 */
public final class SourceOff {
    /** HdmiControlManager.POWER_STATUS_* (SourceOffTest checks the values). */
    public static final int ON = 0, STANDBY = 1, TO_ON = 2, TO_STANDBY = 3, UNKNOWN = -1;
    /** One read of the cache every 5 s while a port is shown (the cache itself changes at most once a minute). */
    public static final long READ_MS = 5_000;
    /**
     * The first read of a viewing waits this long (cec-fix-1127): a viewing that starts with a wake shares its first
     * seconds with the framework's CEC re-init (ARC, System Audio, discovery), and the cached power status of a source
     * is the one from before the sleep for up to a minute anyway, so a read there could only cost the CEC thread time.
     */
    public static final long FIRST_READ_MS = 30_000;
    /** The stock switch (tv_send_standby_on_sleep) is read once per viewing and again after this long, not per read. */
    public static final long SWITCH_READ_MS = 60_000;
    /** Standby on this many reads in a row: a source passing through standby for a moment does not count. */
    static final int CONFIRM_READS = 2;

    private boolean seenOn;
    private int offReads;
    private boolean done;

    /** HdmiDeviceInfo device types that are sources: recorder 1, tuner 3, playback 4. */
    public static boolean source(int type) {
        return type == 1 || type == 3 || type == 4;
    }

    /** A new viewing of the port (shown again, another port). */
    public void reset() {
        seenOn = false;
        offReads = 0;
        done = false;
    }

    /**
     * One read while the port is shown. {@code powers}: the cached power status of every source behind the port;
     * {@code follow}: the projector follows its devices (tv_send_standby_on_sleep, the stock switch); {@code awake}:
     * the screen is on and the projector is not in its own standby. True once per viewing: sleep now.
     */
    public boolean read(int[] powers, boolean follow, boolean awake) {
        if (done) return false;
        if (!awake) {                                           // asleep: a wake is a new viewing
            seenOn = false;
            offReads = 0;
            return false;
        }
        boolean on = false, off = false;
        for (int p : powers) {
            if (p == ON || p == TO_ON) on = true;
            else if (p == STANDBY || p == TO_STANDBY) off = true;
        }
        if (on) {
            seenOn = true;
            offReads = 0;
            return false;
        }
        if (!off) {                                             // no source or no known power: no step either way
            offReads = 0;
            return false;
        }
        if (!seenOn || !follow) return false;
        if (++offReads < CONFIRM_READS) return false;
        done = true;
        return true;
    }
}
