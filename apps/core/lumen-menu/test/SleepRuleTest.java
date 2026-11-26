// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

/** Host test of the stock sleep or power off rule (SleepRule, PowerManagerService.getShutdownMode), run by test/run.sh. */
public final class SleepRuleTest {
    private static int pass, fail;

    private static void check(boolean ok, String what) {
        if (ok) pass++;
        else { fail++; System.out.println("FAIL " + what); }
    }

    public static void main(String[] args) {
        check(SleepRule.STOCK_MAX_STR == 15 && "persist.xgimi.max_str_cnt".equals(SleepRule.MAX_STR_PROP), "stock: 15, persist.xgimi.max_str_cnt");
        check(SleepRule.powerOffReason(0, 0, true) == null, "the first sleep of a boot is STR");
        check(SleepRule.powerOffReason(13, 0, true) == null, "the 14th sleep is STR");
        check(SleepRule.powerOffReason(14, 0, true) != null, "the 15th sleep of a boot powers off (strSuspendCnt 15 >= 15)");
        check(SleepRule.powerOffReason(30, 0, true) != null, "every later sleep of that boot too (the count only resets at boot)");
        check(SleepRule.powerOffReason(14, 15, true) != null && SleepRule.powerOffReason(13, 15, true) == null, "the property 15 = the default");
        check(SleepRule.powerOffReason(2, 3, true) != null && SleepRule.powerOffReason(1, 3, true) == null, "persist.xgimi.max_str_cnt 3: the 3rd sleep");
        check(SleepRule.powerOffReason(14, -1, true) != null && SleepRule.powerOffReason(13, -1, true) == null, "a junk value is the default");
        check(SleepRule.powerOffReason(0, 0, false) != null, "setup not complete: every sleep powers off");
        String r = SleepRule.powerOffReason(14, 0, true);
        check(r != null && r.contains("15"), "the reason names the sleep: " + r);
        System.out.println("SleepRuleTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
