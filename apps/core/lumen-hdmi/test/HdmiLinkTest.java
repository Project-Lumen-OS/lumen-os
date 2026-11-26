// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hdmi;

/**
 * Host test of HdmiLink (apps/core/lumen-hdmi/test/run.sh): the signal status values, the no-picture clock, the
 * game profile gate, the relink decision and the EDID flip, with the 2026-11-14 PS5 incident replayed.
 */
public final class HdmiLinkTest {
    /** GmpfBase.EDID_* (the wire values of setEdidVersion; run.sh checks GmpfBase still says so). */
    private static final int EDID_14 = 0, EDID_20 = 1, EDID_AUTO = 2, EDID_21 = 3;
    private static int checks, failures;
    /** HdmiRelink.TICK_MS (run.sh checks the relink still ticks every 2 s). */
    private static final long TICK_MS = 2_000;

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL " + what);
        }
    }


    public static void main(String[] args) {
        // stock values: 3 = stable (xhplayer GmTvListener.TV_SIGNAL_STABLE, libporting getSignalStatus), 1 / 2 none
        check(HdmiLink.STABLE == 3, "STABLE is 3");
        check(HdmiLink.stable(3), "3 stable");
        for (int s : new int[] {1, 2, 0, -1, 4}) check(!HdmiLink.stable(s), s + " not stable");
        check(HdmiLink.noPicture(1) && HdmiLink.noPicture(2), "1 and 2 no picture");
        for (int s : new int[] {3, 0, HdmiLink.UNKNOWN, 4}) check(!HdmiLink.noPicture(s), s + " is not no picture");

        // the clock: starts at the first no-picture read, keeps its start, restarts on anything else
        check(HdmiLink.noPictureSince(0, 1, 100) == 100, "clock starts");
        check(HdmiLink.noPictureSince(50, 2, 100) == 50, "clock keeps its start");
        check(HdmiLink.noPictureSince(50, 3, 100) == 0, "stable stops the clock");
        check(HdmiLink.noPictureSince(50, HdmiLink.UNKNOWN, 100) == 0, "an unread status stops the clock");

        // signal reads: only while the picture is not known to be stable (stock xhplayer asks only without a signal)
        check(!HdmiLink.readDue(3), "a stable picture: no read");
        for (int st : new int[] {HdmiLink.UNKNOWN, 0, 1, 2}) check(HdmiLink.readDue(st), st + ": read");
        check(HdmiLink.EVENT_SIGNAL == 0, "the signal report is GmTvManager event 0");
        check(HdmiLink.afterEvent(HdmiLink.UNKNOWN, 0, "status:3") == 3, "event status:3");
        check(HdmiLink.afterEvent(3, 0, "status:1") == 1, "event status:1");
        check(HdmiLink.afterEvent(3, 0, " status: 2 ") == 2, "event with blanks");
        check(HdmiLink.afterEvent(3, 5, "status:1") == 3, "another event type keeps the status");
        check(HdmiLink.afterEvent(3, 0, "x:0,state:0") == 3, "another message keeps the status");
        check(HdmiLink.afterEvent(3, 0, "hdr:1") == 3, "a report of another name keeps the status");
        check(HdmiLink.afterEvent(3, 0, "status:") == 3, "an empty status keeps the status");
        check(HdmiLink.afterEvent(3, 0, "status:on") == 3, "a status that does not parse keeps the status");
        check(HdmiLink.afterEvent(3, 0, null) == 3, "no message keeps the status");

        // replay, the owner's PS5 on the Z9X (log 2026-11-16 12:47:18 to 12:50:01): the relink tick every 2 s, the
        // vendor answers 3 every time; that build sent 82 reads and every other one held the vendor 0.3 to 0.6 s
        int known = HdmiLink.UNKNOWN, reads = 0;
        for (long t = 0; t <= 162_000; t += HdmiLinkTest.TICK_MS) {
            if (HdmiLink.readDue(known)) {
                reads++;
                known = 3;
            }
            if (t == 60_000) known = HdmiLink.afterEvent(known, 5, "pic:10");       // a picture mode event
        }
        check(reads == 1, "PS5 replay, stable picture: 1 read, not " + reads);
        // the console then loses the picture (rest mode, cable): the vendor event makes the ticks read again until 3
        known = HdmiLink.afterEvent(known, 0, "status:1");
        int[] vendor = {1, 1, 2, 3, 3, 3};
        reads = 0;
        for (int i = 0; i < vendor.length; i++) {
            if (HdmiLink.readDue(known)) known = vendor[reads++];
        }
        check(reads == 4, "lost picture: reads until the vendor says 3 again, " + reads + " reads");
        check(HdmiLink.noPictureSince(0, vendor[0], 100) == 100, "the reads after the event still start the clock");

        // game profile gate
        // (signalKnown, hdmiMode): a plausible vendor timing and its HDMI flag; 1280x720p60 DVI is the vendor default
        // of a failed 662 read, an implausible (0x0) reading counts as not known
        check(HdmiLink.gameReady(3, false, false), "stable, signal not read yet");
        check(HdmiLink.gameReady(3, true, true), "stable HDMI 4K60");
        check(!HdmiLink.gameReady(3, true, false), "stable status but DVI: no game profile");
        check(HdmiLink.gameReady(3, false, true), "an implausible reading is ignored");
        check(!HdmiLink.gameReady(1, true, true), "no picture: no game profile");
        check(!HdmiLink.gameReady(2, false, false), "unstable: no game profile");
        check(HdmiLink.gameReady(HdmiLink.UNKNOWN, false, false), "unread status alone does not block");
        check(HdmiLink.gameReady(HdmiLink.UNKNOWN, true, true), "unread status, HDMI signal");
        check(!HdmiLink.gameReady(HdmiLink.UNKNOWN, true, false), "unread status, DVI: blocked");

        // relink decision
        long n = HdmiLink.NO_LOCK_MS;
        check(n == 10_000, "10 s without a picture");
        check(HdmiLink.MAX_PER_BOOT == 3, "3 per boot");
        check(HdmiLink.FLIP_HOLD_MS >= 1_000, "the flip holds past the 500 ms HPD low");
        check(HdmiLink.relinkNow(n, n, true, true, 0, 0), "all met");
        check(!HdmiLink.relinkNow(n - 1, n, true, true, 0, 0), "not yet 10 s without a picture");
        check(!HdmiLink.relinkNow(n, n - 1, true, true, 0, 0), "the source not yet on for 10 s");
        check(!HdmiLink.relinkNow(n, -1, true, true, 0, 0), "no powered CEC source");
        check(!HdmiLink.relinkNow(n, n, false, true, 0, 0), "asleep");
        check(!HdmiLink.relinkNow(n, n, true, false, 0, 0), "tvinput does not show the port");
        check(!HdmiLink.relinkNow(n, n, true, true, 1, 0), "this session relinked already");
        check(HdmiLink.relinkNow(n, n, true, true, 0, 2), "third of the boot");
        check(!HdmiLink.relinkNow(n, n, true, true, 0, 3), "boot budget spent");
        check(HdmiLink.since(0, true, 7) == 7 && HdmiLink.since(5, true, 7) == 5 && HdmiLink.since(5, false, 7) == 0,
                "since: first true reading, kept, 0 when false");

        // EDID flip: to the other stock version and back to the user's
        int[] z9x = {EDID_14, EDID_20, EDID_21};
        int[] hb = {EDID_14, EDID_20};
        int a = EDID_14, b = EDID_20;
        check(HdmiLink.flipTo(EDID_14, z9x, a, b) == EDID_20, "Z9X 1.4 -> 2.0");
        check(HdmiLink.flipTo(EDID_20, z9x, a, b) == EDID_14, "Z9X 2.0 -> 1.4");
        check(HdmiLink.flipTo(EDID_21, z9x, a, b) == EDID_14, "Z9X 2.1 -> 1.4");
        check(HdmiLink.flipTo(EDID_AUTO, z9x, a, b) == -1, "AUTO is not written back on the Z9X");
        check(HdmiLink.flipTo(EDID_21, hb, a, b) == -1, "2.1 is not an HB value");
        check(HdmiLink.flipTo(EDID_14, hb, a, b) == EDID_20, "HB 1.4 -> 2.0");
        check(HdmiLink.flipTo(EDID_20, hb, a, b) == EDID_14, "HB 2.0 -> 1.4");
        check(HdmiLink.flipTo(EDID_14, new int[] {EDID_14}, a, b) == -1, "nothing to flip to");
        check(HdmiLink.flipTo(EDID_14, null, a, b) == -1, "no table");
        check(HdmiLink.flipTo(-1, z9x, a, b) == -1, "unreadable version");
        // X50 family: TV 40 takes the two stock HDMI mode values 0 and 1 (EdidModes, X50RelinkHost.EDID_VERSIONS), the
        // value 2 that Lumen beta 7 wrote for Enhanced is not written back
        EdidModes m = EdidModes.of(EdidModes.FEATURE_HDMI_20);
        int[] x50 = m.wires();
        int c = m.wire(EdidModes.COMPATIBLE), e = m.wire(EdidModes.ENHANCED);
        check(HdmiLink.flipTo(0, x50, c, e) == 1, "X50 1.4 -> 2.0");
        check(HdmiLink.flipTo(1, x50, c, e) == 0, "X50 2.0 -> 1.4");
        check(HdmiLink.flipTo(2, x50, c, e) == -1, "X50: the beta 7 value 2 is not written back");
        check(HdmiLink.flipTo(-1, x50, c, e) == -1, "X50: an unread version is not written back");
        check(HdmiLink.restorable(0, x50) && HdmiLink.restorable(1, x50), "X50: a saved 0 or 1 goes back");
        check(!HdmiLink.restorable(2, x50) && !HdmiLink.restorable(-1, x50), "X50: a saved beta 7 value 2 is dropped");
        check(HdmiLink.restorable(EDID_21, z9x) && !HdmiLink.restorable(EDID_AUTO, z9x), "Z9X: 2.1 goes back, AUTO not");
        check(!HdmiLink.restorable(0, null), "no table: nothing goes back");

        // 2026-11-14: the PS5 woke, the vendor reported no picture (fail[3] from 23:01:31) for minutes; one relink
        // 10 s after the first no-picture read, none on the next ticks of the same session
        long since = 0, on = 0;
        int relinks = 0, boot = 0;
        long firstAt = -1;
        for (long t = 0; t <= 60_000; t += 2_000) {
            since = HdmiLink.noPictureSince(since, 1, 1_000 + t);
            on = HdmiLink.since(on, true, 1_000 + t);
            if (HdmiLink.relinkNow(1_000 + t - since, 1_000 + t - on, true, true, relinks, boot)) {
                relinks++;
                boot++;
                if (firstAt < 0) firstAt = t;
            }
        }
        check(relinks == 1 && firstAt == 10_000, "incident: one relink at 10 s (" + relinks + " at " + firstAt + ")");
        // a stable read in between restarts the clock
        since = 0;
        firstAt = -1;
        int[] seq = {1, 1, 1, 3, 1, 1, 1, 1, 1, 1, 1};
        for (int i = 0; i < seq.length; i++) {
            long t = 1_000 + i * 2_000L;
            since = HdmiLink.noPictureSince(since, seq[i], t);
            if (firstAt < 0 && HdmiLink.relinkNow(since == 0 ? 0 : t - since, t, true, true, 0, 0)) firstAt = i;
        }
        check(firstAt == 9, "a stable read restarts the clock (relink at tick " + firstAt + ")");
        // the PS5 in rest mode on the shown port (no picture, CEC standby), woken at 30 s: CEC says on from the next
        // power poll, the link gets 10 s of its own before any relink
        since = 0;
        on = 0;
        firstAt = -1;
        for (long t = 0; t <= 60_000; t += 2_000) {
            since = HdmiLink.noPictureSince(since, 1, 1_000 + t);
            on = HdmiLink.since(on, t >= 32_000, 1_000 + t);
            long onMs = on == 0 ? -1 : 1_000 + t - on;
            if (firstAt < 0 && HdmiLink.relinkNow(1_000 + t - since, onMs, true, true, 0, 0)) firstAt = t;
        }
        check(firstAt == 42_000, "a woken console gets 10 s first (relink at " + firstAt + ")");

        System.out.println("HdmiLinkTest: " + checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
