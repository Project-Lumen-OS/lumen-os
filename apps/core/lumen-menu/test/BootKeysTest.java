// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Host test of BootKeys, run by test/run.sh: a power press during the start does nothing (stock drops every key while
 * the boot animation runs; Lumen waits for the start to complete), any press after it acts. Then the replay of
 * LR-P2ZK15 (test/boot_press_lr_p2zk15.txt): the press that opened the power menu at that start is refused, a press one
 * second after BOOT_COMPLETED is not. Arg: the test directory.
 */
public final class BootKeysTest {
    private static int pass, fail;

    private static void check(boolean ok, String what) {
        if (ok) pass++;
        else { fail++; System.out.println("FAIL " + what); }
    }

    /** Milliseconds of the day of a log line ("MM-DD HH:MM:SS.mmm ..."). */
    private static long ms(String line) {
        String[] t = line.substring(6, 18).split("[:.]");
        return ((Long.parseLong(t[0]) * 60 + Long.parseLong(t[1])) * 60 + Long.parseLong(t[2])) * 1000 + Long.parseLong(t[3]);
    }

    private static long at(List<String> log, String marker) {
        for (String l : log) if (!l.startsWith("#") && l.contains(marker)) return ms(l);
        throw new IllegalStateException("no " + marker);
    }

    /** The two properties at {@code t} of that start, from its log: animation and start complete as they happened. */
    private static String refuseAt(long t, long animEnd, long booted) {
        return BootKeys.refuse(t >= booted ? "1" : "", t >= animEnd ? "stopped" : "running");
    }

    public static void main(String[] a) throws Exception {
        check("boot animation running".equals(BootKeys.refuse("", "running")), "animation running, start not complete");
        check("boot animation running".equals(BootKeys.refuse("1", "running")), "animation running after the start (stock)");
        check("start not complete".equals(BootKeys.refuse("", "stopped")), "animation over, start not complete");
        check("start not complete".equals(BootKeys.refuse(null, null)), "properties unreadable: still the start");
        check("start not complete".equals(BootKeys.refuse("0", "")), "start complete 0");
        check(BootKeys.refuse("1", "stopped") == null, "started, animation over: the press acts");
        check(BootKeys.refuse("1", "") == null, "started, no animation service: the press acts");
        check("sys.boot_completed".equals(BootKeys.BOOT_COMPLETED) && "init.svc.bootanim".equals(BootKeys.BOOTANIM),
                "the two Android properties");

        List<String> log = Files.readAllLines(Paths.get(a[0], "boot_press_lr_p2zk15.txt"), StandardCharsets.UTF_8);
        long animEnd = at(log, "sf_stop_bootanim"), menu = at(log, "Z9xPowerMenu"), booted = at(log, "Posting BOOT_COMPLETED");
        check(animEnd < menu && menu < booted, "LR-P2ZK15: the menu opened after the animation and before the start completed");
        check(refuseAt(menu, animEnd, booted) != null, "LR-P2ZK15: the press that opened the power menu does nothing now");
        check(refuseAt(menu - 400, animEnd, booted) != null, "LR-P2ZK15: that press going down up to 0.4 s earlier too");
        check(BootKeys.refuse("", "stopped") != null && "stopped".equals(menu >= animEnd ? "stopped" : "running"),
                "LR-P2ZK15: the stock animation rule alone would have let it through");
        check(refuseAt(booted + 1000, animEnd, booted) == null, "LR-P2ZK15: a press 1 s after BOOT_COMPLETED acts");

        System.out.println("BootKeysTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
