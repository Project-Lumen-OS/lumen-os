// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hdmi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Host test of SourceOff (apps/core/lumen-hdmi/test/run.sh): the rule cases, then the CEC traces of
 * test/source_off_traces.tsv replayed through the framework's power status cache as Android 14 keeps it
 * (HdmiCecLocalDeviceTv.handleActiveSource: updateDevicePowerStatus(source, ON); HdmiCecNetwork.handleReportPowerStatus;
 * HdmiCecLocalDevice.handleStandby: goToSleep HDMI; handleInactiveSource: no change while the source's port is the
 * previous port) with one SourceOff read every {@link SourceOff#READ_MS} ms of a shown port.
 */
public final class SourceOffTest {
    private static int checks, failures;

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL " + what);
        }
    }

    private static final int ON = SourceOff.ON, STANDBY = SourceOff.STANDBY, TO_ON = SourceOff.TO_ON,
            TO_STANDBY = SourceOff.TO_STANDBY, UNKNOWN = SourceOff.UNKNOWN;

    public static void main(String[] args) throws Exception {
        rules();
        replay(Path.of(args[0], "source_off_traces.tsv"));
        System.out.println("SourceOffTest: " + checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }

    private static void rules() {
        check(ON == 0 && STANDBY == 1 && TO_ON == 2 && TO_STANDBY == 3 && UNKNOWN == -1,
                "HdmiControlManager POWER_STATUS values");
        check(SourceOff.READ_MS == 5_000, "one read every 5 s");
        check(SourceOff.source(1) && SourceOff.source(3) && SourceOff.source(4), "recorder, tuner, playback are sources");
        check(!SourceOff.source(0) && !SourceOff.source(5) && !SourceOff.source(6) && !SourceOff.source(2),
                "TV, audio system, switch and reserved are not");

        SourceOff s = new SourceOff();
        check(!s.read(new int[] {STANDBY}, true, true), "standby before the source was seen on");
        check(!s.read(new int[] {STANDBY}, true, true), "still never seen on");
        check(!s.read(new int[] {ON}, true, true), "on");
        check(!s.read(new int[] {STANDBY}, true, true), "first standby read waits");
        check(s.read(new int[] {STANDBY}, true, true), "second standby read: sleep");
        check(!s.read(new int[] {STANDBY}, true, true), "once per viewing");
        s.reset();
        check(!s.read(new int[] {STANDBY}, true, true) && !s.read(new int[] {STANDBY}, true, true),
                "after reset the source must be seen on again");

        s = new SourceOff();
        s.read(new int[] {TO_ON}, true, true);
        check(!s.read(new int[] {TO_STANDBY}, true, true) && s.read(new int[] {TO_STANDBY}, true, true),
                "turning on, then turning to standby twice: sleep");

        s = new SourceOff();
        s.read(new int[] {ON}, true, true);
        check(!s.read(new int[] {STANDBY}, true, true), "one off read");
        check(!s.read(new int[] {ON}, true, true), "back on: the count starts over");
        check(!s.read(new int[] {STANDBY}, true, true), "one off read again");
        check(s.read(new int[] {STANDBY}, true, true), "two in a row: sleep");

        s = new SourceOff();
        s.read(new int[] {ON}, false, true);
        check(!s.read(new int[] {STANDBY}, false, true) && !s.read(new int[] {STANDBY}, false, true)
                && !s.read(new int[] {STANDBY}, false, true), "setting off: never");
        check(!s.read(new int[] {STANDBY}, true, true), "setting on later: one more read");
        check(s.read(new int[] {STANDBY}, true, true), "setting on later: then sleep");

        s = new SourceOff();
        s.read(new int[] {ON}, true, true);
        check(!s.read(new int[] {STANDBY}, true, false), "projector asleep: nothing");
        check(!s.read(new int[] {STANDBY}, true, true) && !s.read(new int[] {STANDBY}, true, true),
                "a wake starts over: the source must be seen on again");

        s = new SourceOff();
        s.read(new int[] {ON}, true, true);
        check(!s.read(new int[] {}, true, true) && !s.read(new int[] {UNKNOWN}, true, true),
                "no source or unknown power: nothing");
        check(!s.read(new int[] {STANDBY}, true, true) && s.read(new int[] {STANDBY}, true, true),
                "unknown in between keeps the source seen on");

        s = new SourceOff();
        s.read(new int[] {ON, ON}, true, true);
        check(!s.read(new int[] {ON, STANDBY}, true, true) && !s.read(new int[] {ON, STANDBY}, true, true),
                "one of two sources behind the port still on: nothing");
        check(!s.read(new int[] {STANDBY, STANDBY}, true, true) && s.read(new int[] {STANDBY, STANDBY}, true, true),
                "both off: sleep");
    }

    // ===================================================================== replay

    private static final class Dev {
        final int port, type;
        int power;

        Dev(int port, int type, int power) {
            this.port = port;
            this.type = type;
            this.power = power;
        }
    }

    private static int sec(String hms) {
        String[] p = hms.split(":");
        return Integer.parseInt(p[0]) * 3600 + Integer.parseInt(p[1]) * 60 + Integer.parseInt(p[2]);
    }

    private static String hms(long s) {
        return String.format("%02d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
    }

    private static void replay(Path tsv) throws Exception {
        Map<String, List<String[]>> traces = new LinkedHashMap<>();
        for (String line : Files.readAllLines(tsv)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t");
            traces.computeIfAbsent(f[0], k -> new ArrayList<>()).add(f);
        }
        check(traces.containsKey("ps5-rest") && traces.containsKey("ps5-off"), "both live PS5 traces are there");
        for (Map.Entry<String, List<String[]>> e : traces.entrySet()) replayOne(e.getKey(), e.getValue());
    }

    private static void replayOne(String name, List<String[]> lines) {
        Map<Integer, Dev> devs = new TreeMap<>();
        TreeMap<Integer, List<String[]>> events = new TreeMap<>();
        int from = -1, to = -1;
        boolean never = false;
        for (String[] f : lines) {
            switch (f[2]) {
                case "init": {
                    String[] a = f[3].split(":");
                    devs.put(Integer.parseInt(a[0]), new Dev(Integer.parseInt(a[1]), Integer.parseInt(a[2]),
                            Integer.parseInt(a[3])));
                    break;
                }
                case "expect": {
                    String[] a = f[3].split(" ");
                    from = sec(a[0]);
                    to = sec(a[1]);
                    break;
                }
                case "never":
                    never = true;
                    break;
                default:
                    events.computeIfAbsent(sec(f[1]), k -> new ArrayList<>()).add(f);
            }
        }
        check(never || from >= 0, name + ": has an expectation");
        int start = events.firstKey(), end = events.lastKey() + 120;
        int shown = 0;
        long nextRead = Long.MAX_VALUE;
        boolean awake = true;
        SourceOff rule = new SourceOff();
        List<Integer> fired = new ArrayList<>();
        for (int t = start; t <= end; t++) {
            List<String[]> now = events.get(t);
            if (now != null) for (String[] f : now) {
                switch (f[2]) {
                    case "shown":
                        shown = Integer.parseInt(f[3]);
                        rule.reset();
                        nextRead = t + SourceOff.READ_MS / 1000;
                        break;
                    case "hidden":
                        if (shown == Integer.parseInt(f[3])) {
                            shown = 0;
                            nextRead = Long.MAX_VALUE;
                        }
                        break;
                    case "rx": {
                        String[] b = f[3].split(":");
                        int src = Integer.parseInt(b[0].substring(0, 1), 16);
                        int op = Integer.parseInt(b[1], 16);
                        Dev d = devs.get(src);
                        if (op == 0x90 && d != null) d.power = Integer.parseInt(b[2], 16);
                        else if (op == 0x82 && d != null) d.power = ON;
                        else if (op == 0x36) awake = false;
                        break;
                    }
                    default:
                        check(false, name + ": unknown event " + f[2]);
                }
            }
            if (t == nextRead) {
                nextRead = t + SourceOff.READ_MS / 1000;
                List<Integer> powers = new ArrayList<>();
                for (Dev d : devs.values()) if (d.port == shown && SourceOff.source(d.type)) powers.add(d.power);
                int[] p = powers.stream().mapToInt(Integer::intValue).toArray();
                if (rule.read(p, true, awake)) {
                    fired.add(t);
                    awake = false;                              // the projector sleeps
                }
            }
        }
        if (never) {
            check(fired.isEmpty(), name + ": never sleeps (slept at " + (fired.isEmpty() ? "-" : hms(fired.get(0))) + ")");
        } else {
            check(fired.size() == 1 && fired.get(0) >= from && fired.get(0) <= to, name + ": sleeps once in "
                    + hms(from) + "-" + hms(to) + " (slept at " + (fired.isEmpty() ? "never" : hms(fired.get(0))) + ")");
        }
    }
}
