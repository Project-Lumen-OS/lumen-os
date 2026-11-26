// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hdmi;

/**
 * Is the picture of the HDMI input on screen up, and may Lumen act on it. Plain Java, host tested
 * (apps/core/lumen-hdmi/test). The rules of every model: Z9X and HB (IGmpf 592 / TV 106), X50 family (TV 106).
 *
 * Vendor facts (Z9X stock dump 6.15.62: libporting PlatformInputSource, libxgimi Msrv_Tv_Control, the stock HDMI
 * player xhplayer InputActivity, tvconfig Customer_Module.ini):
 * <ul>
 *   <li>getSignalStatus answers {@link #STABLE} (3) for a stable picture, the value the stock player waits for
 *       ("SignalStable"), 1 or 2 while there is none;</li>
 *   <li>getHdmiInfo keeps answering after a failed read (vendor log "getHdmiInfo:437 error, check return fail[3]"):
 *       the 1280x720p60 DVI it gave on 2026-11-14 was the vendor's default, not a picture of the source;</li>
 *   <li>the vendor pulls HPD low only when it writes another EDID (setEdidVersion to a different version, 500 ms by
 *       F_HDMI_SET_EDID_HPD_DELAY_COUNT = 5) and in its HDCP auto EDID for PS4 and Xbox One. No stock call pulses HPD
 *       by itself (PlatformInputSource::setHotPlug has no binder path, IHdmiConnection.setHpdSignal does nothing) and
 *       the source switch the stock player sends on every entry was already sent on each of the owner's five
 *       re-entries without a picture.</li>
 * </ul>
 * So a source that stayed powered while the projector rebooted can keep a dead link until the cable is replugged.
 * The relink is that replug: one EDID flip to another stock version and back (two HPD pulses, the user's version
 * restored and read back), only for a source CEC has reported on for {@link #NO_LOCK_MS}, after as long without a
 * picture.
 */
public final class HdmiLink {
    /** getSignalStatus: a stable picture. */
    public static final int STABLE = 3;
    /** getSignalStatus was not read (call failed, or the projector has no such call). */
    public static final int UNKNOWN = -1;
    /** No stable picture, without a break, this long before a relink. */
    public static final long NO_LOCK_MS = 10_000;
    /** At most this many relinks per boot (every HDMI session gets at most one). */
    public static final int MAX_PER_BOOT = 3;
    /** The flipped EDID stays this long before the user's version goes back (500 ms HPD low plus the EDID read). */
    public static final long FLIP_HOLD_MS = 2_000;
    /** GmTvManager event type of the vendor's signal reports ("status:N"). */
    public static final int EVENT_SIGNAL = 0;

    private static final int NO_PICTURE = 1, UNSTABLE = 2;

    private HdmiLink() {}

    public static boolean stable(int status) {
        return status == STABLE;
    }

    /** The vendor reports no stable picture (an unread status is not "no picture"). */
    public static boolean noPicture(int status) {
        return status == NO_PICTURE || status == UNSTABLE;
    }

    /** Uptime since which the vendor reports no picture without a break; 0 = it does not (now). */
    public static long noPictureSince(long since, int status, long now) {
        return since(since, noPicture(status), now);
    }

    /**
     * A getSignalStatus read is due: only while the picture is not known to be stable. The stock HDMI player
     * (xhplayer InputActivity, Z9X 6.15.62) asks once a second only while it has no signal and hears the loss from the
     * vendor's signal event. A read during a stable picture is not free: with the owner's PS5 on the Z9X every other
     * 2 s read held the vendor 0.3 to 0.6 s (getSignalStatus CALLJUDGE times, log 2026-11-16).
     */
    public static boolean readDue(int known) {
        return !stable(known);
    }

    /**
     * The status the projector knows after a vendor GmTvManager event: N of the signal report (type
     * {@link #EVENT_SIGNAL}, message "status:N", as stock XRMService and xhplayer parse it), else {@code known}.
     */
    public static int afterEvent(int known, int type, String msg) {
        if (type != EVENT_SIGNAL || msg == null) return known;
        String[] p = msg.trim().split(":");
        if (p.length < 2 || !"status".equals(p[0])) return known;
        try {
            return Integer.parseInt(p[1].trim());
        } catch (NumberFormatException e) {
            return known;
        }
    }

    /** Uptime since which {@code holds} has been true without a break (the first true reading); 0 = false now. */
    public static long since(long since, boolean holds, long now) {
        if (!holds) return 0;
        return since > 0 ? since : now;
    }

    /**
     * Game profile: only on a stable HDMI picture. A plausible DVI reading is no console picture (a console in DVI
     * read no EDID, and a failed vendor read gives 1280x720p60 DVI). An unread status alone does not block (the
     * projector may not have the call). {@code signalKnown}: the vendor's timing was read and is plausible;
     * {@code hdmiMode}: that timing is HDMI, not DVI.
     */
    public static boolean gameReady(int status, boolean signalKnown, boolean hdmiMode) {
        if (status != UNKNOWN && !stable(status)) return false;
        return !signalKnown || hdmiMode;
    }

    /**
     * Relink now: the screen is on and out of the lamp-only standby, tvinput streams the port (the cable is in), the
     * vendor has reported no picture AND a CEC source behind the port has reported power on, each for
     * {@link #NO_LOCK_MS} (a console that just woke gets its own time to train the link, -1 = not on), this HDMI
     * session has not relinked yet and the boot has relinks left.
     */
    public static boolean relinkNow(long noPictureMs, long sourceOnMs, boolean awake, boolean streaming,
                                    int sessionRelinks, int bootRelinks) {
        return awake && streaming && sourceOnMs >= NO_LOCK_MS && noPictureMs >= NO_LOCK_MS && sessionRelinks == 0
                && bootRelinks < MAX_PER_BOOT;
    }

    /**
     * The EDID version to flip to and back from {@code current}: {@code first}, else {@code second}, when it is a stock
     * value here and differs; -1 when {@code current} is not a stock value we may write back (nothing is flipped).
     */
    public static int flipTo(int current, int[] allowed, int first, int second) {
        if (!contains(allowed, current)) return -1;
        if (first != current && contains(allowed, first)) return first;
        if (second != current && contains(allowed, second)) return second;
        return -1;
    }

    /**
     * A user version saved before a flip may be written back: it is one of this model's stock values now (a value an
     * older build saved, such as the X50 beta 7 value 2, is dropped instead of being refused at every HAL connect).
     */
    public static boolean restorable(int user, int[] allowed) {
        return contains(allowed, user);
    }

    private static boolean contains(int[] a, int v) {
        if (a == null) return false;
        for (int x : a) if (x == v) return true;
        return false;
    }
}
