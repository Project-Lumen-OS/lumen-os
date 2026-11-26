// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hdmi;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * InactiveSleep (the rule behind Family.INACTIVE_SOURCE_SLEEP, off on every family) on the owner's two live PS5
 * histories (test/cec_ps5_rest.txt, cec_ps5_off.txt: the PS5, logical 8 behind HDMI 1, sends {@code <Inactive
 * Source>} and never comes back) in virtual time: the history as dumpsys shows it at each read, one read every
 * InactiveSleep.READ_MS. Negative cases add lines to the history (a key, the PS5 back, another device).
 */
public final class InactiveSleepTest {
    private static int checks, fails;

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            fails++;
            System.out.println("FAIL " + what);
        }
    }

    static long t(String hms) {
        String[] p = hms.split(":");
        return ((Long.parseLong(p[0]) * 60 + Long.parseLong(p[1])) * 60 + Long.parseLong(p[2])) * 1000;
    }

    /** The history lines of a dump with their second (virtual time ms). */
    static List<long[]> times(List<String> lines) {
        List<long[]> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            int k = l.indexOf("time=");
            if (k < 0 || CecLog.parse(l) == null) continue;
            out.add(new long[] {t(l.substring(k + 16, k + 24)), i});
        }
        return out;
    }

    static String line(long ms, String text) {
        long s = ms / 1000;
        return String.format("    %s time=2026-11-26 %02d:%02d:%02d %s", text.substring(0, 3), s / 3600, s / 60 % 60, s % 60,
                text.substring(4));
    }

    /**
     * Replays {@code lines} (+ {@code extra}, each with its time) from {@code from} to {@code to}; returns the virtual
     * time of the sleep or -1. {@code keyAt}: a projector key at that time (-1 none); {@code on}, {@code awake} as the
     * rule takes them.
     */
    static long replay(List<String> lines, List<String> extra, long from, long to, int[] behind, long keyAt, boolean on,
            boolean awake) {
        List<String> all = new ArrayList<>(lines);
        all.addAll(extra);
        List<long[]> ts = times(all);
        ts.sort((a, b) -> Long.compare(a[0], b[0]));
        InactiveSleep rule = new InactiveSleep();
        boolean keyed = false;
        long sleptAt = -1;
        for (long now = from; now <= to; now += InactiveSleep.READ_MS) {
            if (keyAt >= 0 && !keyed && now >= keyAt) {
                rule.key();
                keyed = true;
            }
            StringBuilder dump = new StringBuilder("HDMI CEC history:\n");
            for (long[] e : ts) if (e[0] <= now) dump.append(all.get((int) e[1])).append('\n');
            if (rule.read(now, dump.toString(), behind, on, awake)) {
                if (sleptAt >= 0) return -2;                    // twice: a failure of its own
                sleptAt = now;
            }
        }
        return sleptAt;
    }

    public static void main(String[] args) throws Exception {
        Path dir = Paths.get(args[0]);
        int[] ps5 = {8};
        for (String[] tr : new String[][] {{"cec_ps5_rest.txt", "11:32:34"}, {"cec_ps5_off.txt", "11:49:54"}}) {
            List<String> l = Files.readAllLines(dir.resolve(tr[0]), StandardCharsets.UTF_8);
            long in = t(tr[1]), from = in - 60_000, to = in + 120_000;
            String n = tr[0] + ": ";
            long at = replay(l, List.of(), from, to, ps5, -1, true, true);
            check(at >= in + InactiveSleep.WAIT_MS && at <= in + InactiveSleep.WAIT_MS + InactiveSleep.READ_MS + 1_000,
                    n + "sleeps 5 s after <Inactive Source> (read period + 1 s history clock): at " + (at - in) + " ms");
            check(replay(l, List.of(), from, to, ps5, -1, false, true) == -1, n + "flag or switch off: no sleep");
            check(replay(l, List.of(), from, to, ps5, -1, true, false) == -1, n + "asleep: nothing");
            check(replay(l, List.of(), from, to, new int[] {4}, -1, true, true) == -1,
                    n + "<Inactive Source> of a device behind another port: nothing");
            check(replay(l, List.of(), from, to, ps5, in + 2_000, true, true) == -1, n + "a projector key 2 s after: no sleep");
            check(replay(l, List.of(line(in + 2_000, "[S] message=<User Control Pressed> 08:44:00 (ACK)")), from, to, ps5,
                    -1, true, true) == -1, n + "a key sent to the PS5 2 s after: no sleep");
            check(replay(l, List.of(line(in + 3_000, "[R] message=<Active Source> 8F:82:10:00")), from, to, ps5, -1, true,
                    true) == -1, n + "the PS5 back as the active source 3 s after: no sleep");
            check(replay(l, List.of(line(in + 3_000, "[R] message=<Text View On> 80:0D")), from, to, ps5, -1, true, true)
                    == -1, n + "the PS5's <Text View On> 3 s after: no sleep");
            long late = replay(l, List.of(line(in + 7_000, "[R] message=<Active Source> 8F:82:10:00")), from, to, ps5, -1,
                    true, true);
            check(late >= in + InactiveSleep.WAIT_MS && late < in + 7_000 + InactiveSleep.READ_MS,
                    n + "back only after the 5 s: the sleep already happened (" + (late - in) + " ms)");
            check(replay(l, List.of(), in + 30_000, to, ps5, -1, true, true) == -1,
                    n + "started after the <Inactive Source> (first read is the baseline): nothing");
        }
        System.out.println("InactiveSleepTest: " + checks + " checks, " + fails + " failures");
        if (fails > 0) System.exit(1);
    }
}
