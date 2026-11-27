// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.power;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * str-gmpf-1127 (GitHub issue 5, HB G0148 V6.17.12, Lumen 1.0.10-beta22): only the first sleep after a cold boot reached
 * STR, then gmpf_wake_lock stayed held by xgimi.hardware.gmpf-service and the fans kept running. Replay of the issue's
 * cycle over a fake vendor that behaves as the stock PowerManagerImpl (Ghidra on the HB gmpf-service 6.17.12:
 * goToSleep type 0 sets the standby stage, runs its shutdown step and calls release_wake_lock; type 1 is the power HAL's
 * step at the suspend entry; wakeUp always calls acquire_wake_lock first) and a kernel that suspends only while no wake
 * lock is held. Then the stock prepare order, the once-per-sleep rule and the wiring of the gmpf core (VendorSleep in
 * StandbyController / PowerPolicy, GmpfBase, the code tables of Z9X, HB and RS 20).
 *   java StrPrepareTest GSI
 */
public final class StrPrepareTest {
    private static int sFail, sOk;

    private static void ok(String what, boolean c) {
        if (c) sOk++;
        else { sFail++; System.out.println("FAIL " + what); }
    }

    /** The HB vendor and kernel as the issue's log shows them. */
    static final class Unit implements StrPrepare.Vendor {
        final List<String> log = new ArrayList<>();
        boolean gmpfLock;       // gmpf_wake_lock (native, xgimi.hardware.gmpf-service)
        boolean screen = true;  // DisplayManager 28
        int success;            // /sys/power/suspend_stats/success

        @Override public void vendorSleep() { goToSleep(StrPrepare.MODE_STR, StrPrepare.TYPE_PREPARE); }
        @Override public boolean screenOn() { log.add("DM28=" + screen); return screen; }
        @Override public void setScreen(boolean on) { log.add("DM27(" + on + ")"); screen = on; }

        void goToSleep(int mode, int type) {
            log.add("goToSleep: in_mode=" + mode + ",in_type=" + type);
            if (type == 0) {
                gmpfLock = false;
                log.add("goToSleep: in_type 0  end !!! release_wake_lock:0");
            }
        }

        void wakeUp(int reserve) {
            gmpfLock = true;
            log.add("reserve:" + reserve + ", acquire_wake_lock:0");
            log.add("wakeUp[215] Wait DisplayResumeThread Finished!");
            screen = true;
        }

        /** Android asleep, our wake locks gone: the power HAL's step, then the kernel suspends when nothing holds it. */
        boolean kernelTries() {
            if (gmpfLock) return false;
            goToSleep(2, 1);            // APM_USER_SUSPEND -> gmpf_suspend_start
            success++;
            return true;
        }
    }

    /** cycles sleeps from a cold boot: Lumen's sleep (with or without the prepare), the kernel, the remote's wake. */
    static Unit replay(int cycles, boolean prepare) throws Exception {
        Unit u = new Unit();
        StrPrepare.Once once = new StrPrepare.Once();
        for (int i = 0; i < cycles; i++) {
            // Z9xStandby "sleep: STR allowed" (onSleepGate), our lamp-off already confirmed
            if (prepare && once.take(true, true, false)) StrPrepare.prepare(u);
            if (u.kernelTries()) u.wakeUp(0);   // the power HAL resumes the vendor on the wake
            once.woke();
        }
        return u;
    }

    static String read(String gsi, String rel) throws Exception {
        return new String(Files.readAllBytes(Paths.get(gsi, rel)), StandardCharsets.UTF_8);
    }

    static String body(String src, String sig) {
        int i = src.indexOf(sig);
        if (i < 0) return "";
        int depth = 0, j = src.indexOf('{', i);
        for (int k = j; k < src.length(); k++) {
            char c = src.charAt(k);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return src.substring(j, k + 1);
        }
        return "";
    }

    static boolean before(String s, String a, String b) {
        int i = s.indexOf(a), j = s.indexOf(b);
        return i >= 0 && j >= 0 && i < j;
    }

    public static void main(String[] a) throws Exception {
        String gsi = a[0];

        // 1. the issue: without the prepare only the first sleep after a cold boot reaches STR
        Unit old = replay(3, false);
        ok("issue 5 replay without the prepare: success 0 -> 1 and stays 1 (got " + old.success + ")", old.success == 1);
        ok("issue 5 replay without the prepare: gmpf_wake_lock held after the first wake", old.gmpfLock);
        // 2. with the stock prepare every sleep reaches STR (3 consecutive cycles)
        Unit u = replay(3, true);
        ok("3 cycles with the stock prepare: success 3 (got " + u.success + ")", u.success == 3);
        int sleeps = 0, releases = 0;
        for (String l : u.log) {
            if (l.equals("goToSleep: in_mode=0,in_type=0")) sleeps++;
            if (l.startsWith("goToSleep: in_type 0  end !!! release_wake_lock")) releases++;
            ok("only stock vendor values sent: " + l, !l.startsWith("goToSleep") || l.contains("in_mode=0,in_type=0")
                    || l.contains("in_mode=2,in_type=1") || l.contains("in_type 0  end"));
        }
        ok("one goToSleep(0, 0) per sleep (" + sleeps + ")", sleeps == 3);
        ok("the vendor released its lock at every sleep (" + releases + ")", releases == 3);

        // 3. stock xgimiPrepareSleep order: goToSleep(0, 0), then 28, then 27(false) only when on
        Unit p = new Unit();
        StrPrepare.prepare(p);
        ok("prepare order " + p.log, p.log.size() == 4 && p.log.get(0).equals("goToSleep: in_mode=0,in_type=0")
                && p.log.get(2).equals("DM28=true") && p.log.get(3).equals("DM27(false)"));
        Unit off = new Unit();
        off.screen = false;
        StrPrepare.prepare(off);
        ok("light already off: no 27 " + off.log, !off.log.contains("DM27(false)"));

        // 4. once per sleep, never awake, never with a blocker or while our lamp-off runs, again after a failure
        StrPrepare.Once o = new StrPrepare.Once();
        ok("awake: nothing", !o.take(false, true, false));
        ok("STR blocked: nothing", !o.take(true, false, false));
        ok("our lamp-off still running: nothing", !o.take(true, true, true));
        ok("asleep, STR allowed: sent", o.take(true, true, false));
        ok("second chance of the same sleep: nothing", !o.take(true, true, false));
        o.failed();
        ok("after a failed send: sent again", o.take(true, true, false));
        o.woke();
        ok("next sleep: sent", o.take(true, true, false));
        // 4b. cec-fix-1127 (AUDIT R6): before a Lumen sleep, as stock xgimiPrepareSleep inside goToSleep
        StrPrepare.Once b = new StrPrepare.Once();
        ok("before the sleep, STR blocked: nothing", !b.takeBefore(false, false));
        ok("before the sleep, our lamp-off running: nothing", !b.takeBefore(true, true));
        ok("before the sleep, STR allowed: sent", b.takeBefore(true, false));
        ok("SCREEN_OFF of that sleep: not again", !b.take(true, true, false));
        b.failed();
        ok("before-sleep send failed (HAL not connected): SCREEN_OFF sends it", b.take(true, true, false));
        b.woke();
        ok("next sleep: before the sleep again", b.takeBefore(true, false));

        // 5. wiring of the gmpf core (the families Z9X, Z9X HB, RS 20)
        String P = "apps/LumenProjector/core/src/org/lumen/projector/power/";
        String gate = body(read(gsi, P + "StandbyController.java"), "static void onSleepGate(String why)");
        ok("onSleepGate: the prepare before the CPU is let go", before(gate, "VendorSleep.maybeSend(sApp, why, true)", "release(sHoldLock)")
                && before(gate, "consumeRebootWhenOff", "VendorSleep.maybeSend") && before(gate, "if (b != null)", "VendorSleep.maybeSend"));
        String pp = read(gsi, P + "PowerPolicy.java");
        String lampOff = body(pp, "private static void onSleepLampOff(boolean ok, String detail)");
        ok("onSleepLampOff: the prepare after our lamp-off, STR allowed only, before the hold ends",
                before(lampOff, "VendorSleep.maybeSend(sApp,", "StandbyController.releaseSleepPrep()")
                        && lampOff.contains("VendorSleep.maybeSend(sApp, \"lamp off \" + (ok ? \"done\" : \"not confirmed\"), StrGate.blocker(sApp) == null)"));
        ok("SCREEN_ON: the next sleep sends its own", body(pp, "public static void onScreenOn(Context ctx)").contains("VendorSleep.onWake()"));
        String ss = read(gsi, "apps/core/lumen-hdmi/src/org/lumen/hdmi/SleepSource.java");
        String gts = body(ss, "public static void goToSleep(Context ctx, int reason, int flags)");
        ok("SleepSource.goToSleep: <Active Source>, the vendor step, then goToSleep (stock order)",
                before(gts, "beforeSleep(ctx);", ".goToSleep(SystemClock.uptimeMillis()")
                        && before(body(ss, "public static void beforeSleep(Context ctx)"), "tvTakesSource(ctx);", "p.beforeSleep(ctx);"));
        ok("PowerPolicy.install: the vendor step of a Lumen sleep is VendorSleep.beforeSleep with the STR gate",
                body(pp, "public static void install(Context ctx)").contains(
                        "org.lumen.hdmi.SleepSource.setVendorPrepare(c -> VendorSleep.beforeSleep(c, StandbyController.strWouldBeAllowed()));"));
        String would = body(read(gsi, P + "StandbyController.java"), "static boolean strWouldBeAllowed()");
        ok("strWouldBeAllowed: no update reboot, no blocker, no reboot when off (onSleepGate's own conditions)",
                would.contains("sUpdateReboot") && would.contains("StrGate.blocker(sApp) == null")
                        && would.contains("OtaStateReceiver.rebootWhenOffPending(sApp)") && !would.contains("consumeRebootWhenOff"));
        String vs = read(gsi, P + "VendorSleep.java");
        String vb = body(vs, "static void beforeSleep(Context app, boolean strAllowed)");
        ok("VendorSleep.beforeSleep: once, holds the suspend, waits for the vendor at most BEFORE_WAIT_MS",
                vb.contains("ONCE.takeBefore(strAllowed, LampControl.isOffPending())") && vb.contains("hold(pm);")
                        && vb.contains("done.await(BEFORE_WAIT_MS") && vb.contains("ONCE.failed();"));
        ok("VendorSleep: holds the suspend, asks LampControl, sends through StrPrepare", vs.contains("PARTIAL_WAKE_LOCK")
                && vs.contains("LampControl.isOffPending()") && vs.contains("StrPrepare.prepare(v)") && vs.contains("Family.strVendor(g)"));
        for (String fam : new String[] {"aidl-gmpf", "hidl-gmpf"}) {
            ok(fam + ": Family.strVendor is the shared gmpf one", body(read(gsi, "apps/LumenProjector/vendor/xgimi/" + fam
                    + "/src/org/lumen/projector/Family.java"), "strVendor(").contains("GmpfStrVendor.of(g)"));
        }
        String sv = body(read(gsi, "apps/LumenProjector/vendor/xgimi/gmpf/src/org/lumen/projector/power/GmpfStrVendor.java"), "StrPrepare.Vendor of(");
        ok("GmpfStrVendor: strPrepareSleep, 28, 27 when the HAL has goToSleep", sv.contains("g.hasStrPrepareSleep()")
                && sv.contains("g.strPrepareSleep()") && sv.contains("g.getScreenOn()") && sv.contains("g.setScreenOn(on)"));
        String base = read(gsi, "apps/core/lumen-gmpf/src/org/lumen/projector/hal/GmpfBase.java");
        ok("GmpfBase: goToSleep with the fixed stock values", body(base, "public void strPrepareSleep()")
                .contains("wire.i32I32(key(GO_TO_SLEEP), SLEEP_MODE_STR, SLEEP_TYPE_PREPARE)")
                && base.contains("private static final int SLEEP_MODE_STR = 0;") && base.contains("private static final int SLEEP_TYPE_PREPARE = 0;"));
        ok("Z9X table: IGmpf 241 goToSleep", read(gsi, "devices/horse_popular/hal/codes.tsv").contains("IGmpf\tGMPF_GO_TO_SLEEP\t241\tgoToSleep\tW=writeInt32,writeInt32 R=\t"));
        for (String f : new String[] {"rs20-hal312", "rs20-hal330"}) {
            ok(f + " table: IGmpf 241 goToSleep", read(gsi, "devices/_family/" + f + "/hal/codes.tsv").contains("IGmpf\tGMPF_GO_TO_SLEEP\t241\tgoToSleep\t"));
        }
        ok("HB table: PowerManager 6 goToSleep", read(gsi, "devices/goat_elegant/hal/codes.tsv").contains("PowerManager\tPM_GO_TO_SLEEP\t6\tgoToSleep\tvoid(int, int)\t"));
        String V = "apps/LumenProjector/vendor/xgimi/";
        ok("HB call carries the range 0,0", read(gsi, V + "aidl-gmpf/src/org/lumen/projector/hal/GmpfCall.java")
                .contains("GO_TO_SLEEP(GmpfIface.POWER, HalCodes.PM_GO_TO_SLEEP, \"goToSleep\", \"0,0\")"));
        ok("HB and Z9X maps name the call", read(gsi, V + "aidl-gmpf/src/org/lumen/projector/hal/HbCodes.java").contains(".put(GO_TO_SLEEP, GmpfCall.GO_TO_SLEEP)")
                && read(gsi, V + "hidl-gmpf/src/org/lumen/projector/hal/Z9xCodes.java").contains(".put(GO_TO_SLEEP, HalCodes.GMPF_GO_TO_SLEEP)"));
        String unban = read(gsi, "devices/goat_elegant/research/unbanned_hbprojector.tsv");
        ok("HB exception: PowerManager 6 with 0,0 only", unban.contains("HbProjector\tPowerManager\t6\tgoToSleep\targs:0,0\t"));
        String callers = read(gsi, "apps/check_hal_callers.py");
        ok("Z9X guard: 241 only with (0, 0)", callers.contains("241: {(0, 0)}") && !callers.contains("241: \"power\""));
        ok("X50 family: the same rule", read(gsi, V + "mt9681-aidl/src/org/lumen/x50/projector/SleepSequences.java").contains("StrPrepare.prepare(h)"));

        System.out.println("StrPrepareTest: " + sOk + " ok, " + sFail + " failed");
        if (sFail > 0) System.exit(1);
    }
}
