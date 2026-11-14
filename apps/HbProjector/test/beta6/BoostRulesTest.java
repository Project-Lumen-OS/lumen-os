package org.lumen.projector.panel;

/** Host test of BoostRules (HB beta6, live beta5 bug "Boost cannot be turned off" in Performance). */
public final class BoostRulesTest {
    static int n, bad;
    static void eq(String what, Object got, Object want) {
        n++;
        if (want == null ? got != null : !want.equals(got)) { bad++; System.out.println("FAIL " + what + ": got " + got + ", want " + want); }
    }

    public static void main(String[] a) {
        // xgimipq.db settings_default 17 / settings_sto_mapping 2: lens_mode fixed for picture modes 5 and 25 only
        int[] all = {1, 4, 5, 9, 10, 11, 12, 13, 14, 15, 16, 17, 25, 26, 29};
        for (int pm : all) eq("locked " + pm, BoostRules.lockedByPictureMode(pm), pm == 5 || pm == 25);
        eq("locked null", BoostRules.lockedByPictureMode(null), false);
        eq("PM_PERFORMANCE", BoostRules.PM_PERFORMANCE, 5);
        eq("PM_OFFICE", BoostRules.PM_OFFICE, 25);

        // LEFT at Boost: explain in Performance / Office, arm otherwise; never explain without Boost
        eq("leave perf", BoostRules.onLeavePress(true, 5), BoostLeave.EXPLAIN_PICTURE_MODE);
        eq("leave office", BoostRules.onLeavePress(true, 25), BoostLeave.EXPLAIN_PICTURE_MODE);
        eq("leave standard", BoostRules.onLeavePress(true, 1), BoostLeave.ARM);
        eq("leave ai", BoostRules.onLeavePress(true, 12), BoostLeave.ARM);
        eq("leave unknown pm", BoostRules.onLeavePress(true, null), BoostLeave.ARM);
        eq("leave no boost", BoostRules.onLeavePress(false, 5), BoostLeave.ARM);
        eq("leave boost unknown", BoostRules.onLeavePress(null, 5), BoostLeave.ARM);

        // 52 judged by the 152 read-back (stock ignores the boolean)
        eq("off ok", BoostRules.judge(false, true, 0, 1), BoostOutcome.DONE);
        eq("off false but done", BoostRules.judge(false, false, 0, 1), BoostOutcome.DONE);
        eq("off refused perf (live 22:17:25)", BoostRules.judge(false, false, 1, 5), BoostOutcome.LOCKED_BY_PICTURE_MODE);
        eq("off refused office", BoostRules.judge(false, false, 1, 25), BoostOutcome.LOCKED_BY_PICTURE_MODE);
        eq("off refused standard", BoostRules.judge(false, false, 1, 1), BoostOutcome.FAILED);
        eq("off true but still on", BoostRules.judge(false, true, 1, 1), BoostOutcome.FAILED);
        eq("off unreadable true", BoostRules.judge(false, true, -1, 1), BoostOutcome.DONE);
        eq("off unreadable false", BoostRules.judge(false, false, -1, 5), BoostOutcome.FAILED);
        eq("on ok", BoostRules.judge(true, true, 1, 1), BoostOutcome.DONE);
        eq("on false but done", BoostRules.judge(true, false, 1, 1), BoostOutcome.DONE);
        eq("on refused", BoostRules.judge(true, false, 0, 1), BoostOutcome.FAILED);
        eq("on refused perf pm", BoostRules.judge(true, false, 0, 5), BoostOutcome.FAILED);

        // Colour accuracy: the user's Boost blocks it, a Boost held by Performance / Office does not
        eq("ca user boost", BoostRules.blocksColorAccuracy(true, 1), true);
        eq("ca perf boost", BoostRules.blocksColorAccuracy(true, 5), false);
        eq("ca office boost", BoostRules.blocksColorAccuracy(true, 25), false);
        eq("ca no boost", BoostRules.blocksColorAccuracy(false, 1), false);
        eq("ca unknown", BoostRules.blocksColorAccuracy(null, 1), false);
        eq("ca boost pm unknown", BoostRules.blocksColorAccuracy(true, null), true);

        System.out.println("BoostRulesTest: " + (n - bad) + "/" + n + " passed");
        if (bad != 0) System.exit(1);
    }
}
