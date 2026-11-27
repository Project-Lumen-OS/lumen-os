// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

/** Halt: the power menu's Power off is Android's shutdown (as Settings > Power off), Restart Android's reboot, never a sleep. */
public final class HaltTest {
    private static int bad;

    private static void ok(String what, boolean c) {
        System.out.println((c ? "ok   " : "FAIL ") + what);
        if (!c) bad++;
    }

    public static void main(String[] a) {
        Context c = new Context();
        Halt.powerOff(c, "power menu");
        ok("power off = PowerManager.shutdown(no confirm, userrequested, no wait), the ShutdownThread of Settings > Power off",
                PowerManager.CALLS.equals(java.util.List.of("shutdown false userrequested false")));
        ok("power off logs its caller under LumenHalt (the emulator walk reads it)",
                Log.LINES.equals(java.util.List.of("LumenHalt: power off: Android shutdown, userrequested (power menu)")));
        PowerManager.CALLS.clear();
        Halt.restart(c, "power menu");
        ok("restart = PowerManager.reboot(userrequested)", PowerManager.CALLS.equals(java.util.List.of("reboot userrequested")));
        ok("never a sleep", !PowerManager.CALLS.contains("goToSleep"));
        System.out.println(bad == 0 ? "HaltTest: all green" : "HaltTest: " + bad + " FAIL");
        if (bad != 0) System.exit(1);
    }
}
