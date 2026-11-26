// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.tvinput;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * BootWindow over recorded boots: the source chosen for power-on opens as soon as its port input exists (stock
 * XRMService BootControlService opens it at USER_UNLOCKED, no wait of its own), the plug and CEC switches still wait
 * for the settle window. The replay calls BootWindow where HdmiWatcher does (its start, each port input added) and
 * also every 100 ms, as a timer would.
 *   java org.lumen.tvinput.BootWindowTest TEST_DIR
 */
public final class BootWindowTest {
    /** Stock opens at USER_UNLOCKED; Lumen's HDMI app starts then and needs the HAL's port input: 3 s at most. */
    static final long AFTER_BOOT_SCREEN_MS = 3_000;

    private static int fails;

    static final class Row {
        final long ms; final String event; final int port;
        Row(long ms, String event, int port) { this.ms = ms; this.event = event; this.port = port; }
    }

    /** One replayed boot: when the power-on source opened and which plug switches passed. */
    static final class Run {
        long openAt = -1;
        int opens;
        boolean blockedAtOpen;
        final List<Long> plugsPassed = new ArrayList<>();
    }

    static List<Row> load(Path f) throws IOException {
        List<Row> rows = new ArrayList<>();
        for (String l : Files.readAllLines(f, StandardCharsets.UTF_8)) {
            if (l.isEmpty() || l.startsWith("#")) continue;
            String[] c = l.split("\t");
            rows.add(new Row(Long.parseLong(c[0]), c[1], Integer.parseInt(c[2])));
        }
        rows.sort((a, b) -> Long.compare(a.ms, b.ms));
        return rows;
    }

    static long at(List<Row> rows, String event, int port) {
        for (Row r : rows) if (r.event.equals(event) && r.port == port) return r.ms;
        throw new IllegalStateException("no " + event + " " + port);
    }

    /** bootPort 0 = the home screen (HdmiWatcher does not ask BootWindow then). */
    static Run replay(List<Row> rows, int bootPort) {
        Run run = new Run();
        long start = at(rows, "start", 0);
        BootWindow w = new BootWindow(start);
        Set<Integer> listed = new HashSet<>();
        for (Row r : rows) if (r.event.equals("input_added") && r.ms < start) listed.add(r.port);
        List<long[]> calls = new ArrayList<>();      // {ms, kind 0 ask / 1 plug, port}
        calls.add(new long[]{start, 0, 0});
        for (Row r : rows) {
            if (r.ms < start) continue;
            if (r.event.equals("input_added")) calls.add(new long[]{r.ms, 0, r.port});
            if (r.event.equals("plug")) calls.add(new long[]{r.ms, 1, r.port});
        }
        for (long t = start; t <= start + 60_000; t += 100) calls.add(new long[]{t, 0, 0});
        calls.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(b[2], a[2]));
        for (long[] c : calls) {
            long t = c[0];
            if (c[1] == 1) {
                if (!w.blocks(t, false)) run.plugsPassed.add(t);
                continue;
            }
            if (c[2] > 0) listed.add((int) c[2]);
            if (bootPort <= 0) continue;
            if (w.openNow(t, listed.contains(bootPort))) {
                run.opens++;
                if (run.openAt < 0) {
                    run.openAt = t;
                    run.blockedAtOpen = w.blocks(t, true);
                }
            }
        }
        return run;
    }

    static void check(boolean ok, String what) {
        if (!ok) fails++;
        System.out.println((ok ? "ok   " : "FAIL ") + what);
    }

    static List<Row> synthetic(long start, long... inputAt) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(start, "start", 0));
        for (long ms : inputAt) rows.add(new Row(ms, "input_added", 1));
        return rows;
    }

    public static void main(String[] a) throws IOException {
        Path dir = Path.of(a.length > 0 ? a[0] : ".");
        List<Row> x50 = load(dir.resolve("boot_x50_g0111.tsv"));
        long screen = at(x50, "boot_screen_end", 0);
        long unlock = at(x50, "user_unlock", 0);
        long start = at(x50, "start", 0);

        // the reported case: HDMI 1 = the eARC port with the soundbar, auto switch on, return home off
        Run r1 = replay(x50, 1);
        System.out.println("X50 boot, power-on source HDMI 1: opens at " + r1.openAt + " ms, "
                + (r1.openAt - screen) + " ms after the boot screen (stock at USER_UNLOCKED: "
                + (unlock - screen) + " ms; the old single look at start + 32 s: " + (start + 32_000 - screen) + " ms)");
        check(r1.opens == 1, "HDMI 1 opens once (" + r1.opens + ")");
        check(r1.openAt == at(x50, "input_added", 1), "HDMI 1 opens when its port input is listed (27374), at " + r1.openAt);
        check(r1.openAt >= 0 && r1.openAt - screen <= AFTER_BOOT_SCREEN_MS,
                "HDMI 1 opens within " + AFTER_BOOT_SCREEN_MS + " ms after the boot screen");
        check(!r1.blockedAtOpen, "the settle window of the plug and CEC switches does not hold the power-on source");
        check(r1.plugsPassed.isEmpty(), "the soundbar's eARC bring-up on HDMI 1 inside the settle window switches nothing "
                + r1.plugsPassed);

        Run r2 = replay(x50, 2);
        check(r2.opens == 1 && r2.openAt == at(x50, "input_added", 2),
                "HDMI 2 (the Box R) opens at its port input 27431, not at its CEC child: " + r2.openAt);
        Run r3 = replay(x50, 3);
        check(r3.opens == 1 && r3.openAt == at(x50, "input_added", 3),
                "HDMI 3 (nothing plugged) opens at its port input 27465 like stock (no signal card): " + r3.openAt);
        check(replay(x50, 0).opens == 0, "home screen: nothing opens");

        Run early = replay(synthetic(10_000, 9_000), 1);
        check(early.opens == 1 && early.openAt == 10_000, "the port input listed before the start: opens at the start, "
                + early.openAt);
        Run edge = replay(synthetic(10_000, 10_000 + BootWindow.OPEN_BY_MS), 1);
        check(edge.opens == 1 && edge.openAt == 10_000 + BootWindow.OPEN_BY_MS,
                "the port input at the last moment the old rule opened (start + 32 s): opens, " + edge.openAt);
        Run late = replay(synthetic(10_000, 10_000 + BootWindow.OPEN_BY_MS + 1), 1);
        check(late.opens == 0, "a port input listed later than that: nothing opens out of the blue (" + late.openAt + ")");

        BootWindow w = new BootWindow(10_000);
        check(w.blocks(10_000 + BootWindow.SETTLE_MS - 1, false), "a plug 1 ms before the end of the settle window waits");
        check(!w.blocks(10_000 + BootWindow.SETTLE_MS, false), "a plug at the end of the settle window passes");
        check(BootWindow.SETTLE_MS == 30_000, "the settle window stays 30 s");

        System.out.println(fails == 0 ? "BootWindowTest: all passed" : "BootWindowTest: " + fails + " failed");
        if (fails != 0) System.exit(1);
    }
}
