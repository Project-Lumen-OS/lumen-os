package org.lumen.system;

import android.content.Context;
import android.provider.Settings;

import java.util.Arrays;

/** ScreensaverDelay against a map-backed Settings.System: the stock list, Never, a time outside the list, refusals. */
public final class ScreensaverDelayTest {
    private static int failed;

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "ok   " : "FAIL ") + what);
        if (!ok) failed++;
    }

    private static Long v() { return Settings.System.VALUES.get(Settings.System.SCREEN_OFF_TIMEOUT); }

    public static void main(String[] a) {
        Context c = new Context();
        check(Arrays.equals(ScreensaverDelay.TOKENS, new String[]{"never", "m1", "m5", "m10", "m30", "m60"}),
                "the stock list: Never, 1, 5, 10, 30 min, 1 h");
        check("never".equals(ScreensaverDelay.get(c)), "nothing stored: never");
        long[] ms = {Integer.MAX_VALUE, 60_000L, 300_000L, 600_000L, 1_800_000L, 3_600_000L};
        for (int i = 0; i < ms.length; i++) {
            String t = ScreensaverDelay.TOKENS[i];
            check(ScreensaverDelay.set(c, t), t + ": set and read back");
            check(Long.valueOf(ms[i]).equals(v()), t + ": screen_off_timeout " + ms[i] + ", got " + v());
        }
        Settings.System.VALUES.put(Settings.System.SCREEN_OFF_TIMEOUT, 900_000L);
        check("m15".equals(ScreensaverDelay.get(c)), "15 min stored (the Android default): read as m15");
        check(ScreensaverDelay.options("m15").equals(Arrays.asList("never", "m1", "m5", "m10", "m15", "m30", "m60")),
                "a time outside the list is offered at its place: " + ScreensaverDelay.options("m15"));
        check(ScreensaverDelay.options("m5").equals(Arrays.asList(ScreensaverDelay.TOKENS)), "a stock time adds nothing");
        check(ScreensaverDelay.options("never").equals(Arrays.asList(ScreensaverDelay.TOKENS)), "never adds nothing");
        check(ScreensaverDelay.options("m2000").equals(Arrays.asList(ScreensaverDelay.TOKENS)), "a time over a day adds nothing");
        check("never".equals(ScreensaverDelay.token(0)) && "never".equals(ScreensaverDelay.token(-1)), "0 and -1: never");
        check("m1".equals(ScreensaverDelay.token(10_000L)), "under a minute: m1");
        for (String bad : new String[]{null, "", "m0", "m", "5", "m01", "m99999", "always"}) {
            Settings.System.VALUES.put(Settings.System.SCREEN_OFF_TIMEOUT, 300_000L);
            check(!ScreensaverDelay.set(c, bad) && Long.valueOf(300_000L).equals(v()), "refused, nothing written: " + bad);
        }
        Settings.System.FAIL = true;
        check(!ScreensaverDelay.set(c, "m10"), "provider refuses: false");
        check(ScreensaverDelay.get(c) == null, "provider refuses: no value");
        Settings.System.FAIL = false;
        System.out.println(failed == 0 ? "ScreensaverDelayTest: all ok" : "ScreensaverDelayTest: " + failed + " FAILED");
        if (failed != 0) System.exit(1);
    }
}
