package org.lumen.projector;

/**
 * Host check of the Play 6 focus rules ({@link P6AfRules}): the AF busy window follows the vendor's 330
 * AutoFocusing start / end (the hall AF never sends 4 / 8 / 333-335), the stock fine-tune notice is due on
 * 18 "AF_Success_Into_Adjust" only, closing the manual focus window sends manualFocus(2) only when
 * something may be moving, and a manual move sends the motor command before the flag's disk write. The 330
 * samples are real lines of logs/<device>_<serial> of a Play 6 (t14 10-13 13:11:36
 * and 13:11:37) and an AutoKeystone report. sh test/afrules/run.sh
 */
public final class P6AfRulesTest {
    private static int failed, passed;

    private static void eq(String what, Object want, Object got) {
        if (want == got || (want != null && want.equals(got))) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL " + what + ": want " + want + ", got " + got);
        }
    }

    /** Runs P6AfRules.startMove with steps that log their order; the result goes to R[0]. */
    private static String startMove(boolean motorOk, boolean flagOk, int[] r) {
        StringBuilder log = new StringBuilder();
        r[0] = P6AfRules.startMove(
                () -> { log.append(" motor"); return motorOk; },
                () -> { log.append(" flag"); return flagOk; },
                () -> log.append(" stop"));
        return log.toString().trim();
    }

    static final String AF_START = "{\"ev_describe\":\"AutoFocusing\",\"classify_code\":5,\"sub_classify_code\":42,"
            + "\"version_code\":2,\"attribute\":[{\"key\":\"action\",\"value\":\"start\"},{\"key\":\"trigger_type\","
            + "\"value\":\"remote_control\"},{\"key\":\"focus_type\",\"value\":\"hall\"},{\"key\":\"tof_distance\","
            + "\"value\":null},{\"key\":\"theoretical_motor_steps\",\"value\":null},{\"key\":\"actual_motor_steps\","
            + "\"value\":null},{\"key\":\"final_state_after_af\",\"value\":null},{\"key\":\"cur_pos\",\"value\":212},"
            + "{\"key\":\"cur_focus_temper\",\"value\":null},{\"key\":\"cur_env_temper\",\"value\":null}]}";
    static final String AF_END = "{\"ev_describe\":\"AutoFocusing\",\"classify_code\":5,\"sub_classify_code\":42,"
            + "\"version_code\":2,\"attribute\":[{\"key\":\"action\",\"value\":\"end\"},{\"key\":\"trigger_type\","
            + "\"value\":\"remote_control\"},{\"key\":\"focus_type\",\"value\":\"hall\"},{\"key\":\"tof_distance\","
            + "\"value\":510},{\"key\":\"theoretical_motor_steps\",\"value\":null},{\"key\":\"actual_motor_steps\","
            + "\"value\":422},{\"key\":\"final_state_after_af\",\"value\":\"EVENT_DONE_OK\"},{\"key\":\"cur_pos\","
            + "\"value\":212},{\"key\":\"cur_focus_temper\",\"value\":32},{\"key\":\"cur_env_temper\",\"value\":32}]}";
    static final String KST = "{\"ev_describe\":\"AutoKeystone\", \"classify_code\":5,\"sub_classify_code\":41,"
            + "\"version_code\":5,\"attribute\":[ { \"key\":\"trigger_type\", \"value\":\"lckst_curtain\" }, "
            + "{ \"key\":\"action\", \"value\":\"start\" }, { \"key\":\"distance\", \"value\":2404.652588}]}";

    public static void main(String[] a) {
        // ---- 330: AutoFocusing start / end arm and clear the busy window
        eq("330 AF start", P6AfRules.REPORT_AF_START, P6AfRules.report330(AF_START));
        eq("330 AF end", P6AfRules.REPORT_AF_END, P6AfRules.report330(AF_END));
        eq("330 after_ak start", P6AfRules.REPORT_AF_START,
                P6AfRules.report330(AF_START.replace("remote_control", "after_ak")));
        eq("330 spaced JSON end", P6AfRules.REPORT_AF_END, P6AfRules.report330(
                "{ \"ev_describe\" : \"AutoFocusing\", \"attribute\" : [ { \"key\" : \"action\" , \"value\" : \"end\" } ] }"));
        // other 330 reports (keystone, eye protection) and bad input do nothing
        eq("330 keystone report with an action key", P6AfRules.REPORT_NONE, P6AfRules.report330(KST));
        eq("330 eye protection", P6AfRules.REPORT_NONE,
                P6AfRules.report330("{\"ev_describe\":\"ChildEyeProtectionFromHardware\",\"attribute\":[]}"));
        eq("330 AF without action", P6AfRules.REPORT_NONE,
                P6AfRules.report330("{\"ev_describe\":\"AutoFocusing\",\"attribute\":[]}"));
        eq("330 null", P6AfRules.REPORT_NONE, P6AfRules.report330(null));
        eq("330 empty", P6AfRules.REPORT_NONE, P6AfRules.report330(""));
        eq("330 garbage", P6AfRules.REPORT_NONE, P6AfRules.report330("{\"ev_describe\":\"Auto"));
        // a trigger_type value "start" is not the action
        eq("330 start only as another value", P6AfRules.REPORT_NONE, P6AfRules.report330(
                "{\"ev_describe\":\"AutoFocusing\",\"attribute\":[{\"key\":\"trigger_type\",\"value\":\"start\"}]}"));

        // ---- 18: the stock notice only with the stock value
        eq("18 hall done", true, P6AfRules.fineTuneHint(18, "AF_Success_Into_Adjust"));
        eq("18 other value", false, P6AfRules.fineTuneHint(18, "AF_Fail"));
        eq("18 null value", false, P6AfRules.fineTuneHint(18, null));
        eq("18 empty value", false, P6AfRules.fineTuneHint(18, ""));
        eq("18 case differs", false, P6AfRules.fineTuneHint(18, "af_success_into_adjust"));
        for (int t : new int[] {0, 2, 5, 12, 302, 330}) {
            eq("no notice on " + t, false, P6AfRules.fineTuneHint(t, "AF_Success_Into_Adjust"));
        }

        // ---- AF end events clear the busy window; the others do not
        for (int t : new int[] {5, 2, 18}) eq("clears busy " + t, true, P6AfRules.clearsBusy(t));
        for (int t : new int[] {0, 1, 4, 8, 12, 302, 330, 333}) eq("keeps busy " + t, false, P6AfRules.clearsBusy(t));

        // ---- manual window close: 2 only when something may be moving (stock sends none for an idle close)
        eq("close idle after 17", false, P6AfRules.stopOnClose(false, false));
        eq("close while moving", true, P6AfRules.stopOnClose(true, false));
        eq("close with the persisted flag", true, P6AfRules.stopOnClose(false, true));
        eq("close moving and flagged", true, P6AfRules.stopOnClose(true, true));

        // ---- manual move start: the motor command first, the flag's disk write after it (no write before the
        // first motor step), a flag that cannot be saved stops the move, a failed motor command saves the flag
        // before its fail-safe stop (the reconnect resends a stop the HAL cannot take now)
        int[] res = new int[1];
        eq("move: motor, then flag", "motor flag", startMove(true, true, res));
        eq("move: started", P6AfRules.MOVE_STARTED, res[0]);
        eq("move: flag not saved stops the move", "motor flag stop", startMove(true, false, res));
        eq("move: flag failure reported", P6AfRules.MOVE_FLAG_FAILED, res[0]);
        eq("move: motor failed, flag then stop", "motor flag stop", startMove(false, true, res));
        eq("move: motor failure reported", P6AfRules.MOVE_MOTOR_FAILED, res[0]);
        eq("move: motor and flag failed, still stopped", "motor flag stop", startMove(false, false, res));
        eq("move: motor failure first", P6AfRules.MOVE_MOTOR_FAILED, res[0]);

        // ---- a whole remote AF as logged (t14 13:11:36-37): 330 start, 12, 5 "0:44", 18, 330 end
        boolean busy = false;
        boolean notice = false;
        Object[][] seq = {{330, AF_START}, {12, ""}, {5, "0:44"}, {18, "AF_Success_Into_Adjust"}, {330, AF_END}};
        boolean busyAfterStart = false;
        for (Object[] e : seq) {
            int t = (Integer) e[0];
            String v = (String) e[1];
            if (t == 330) {
                int r = P6AfRules.report330(v);
                if (r == P6AfRules.REPORT_AF_START) busy = true;
                if (r == P6AfRules.REPORT_AF_END) busy = false;
            } else if (P6AfRules.clearsBusy(t)) {
                busy = false;
            }
            if (P6AfRules.fineTuneHint(t, v)) notice = true;
            if (t == 330 && v == AF_START) busyAfterStart = busy;
        }
        eq("sequence busy after start", true, busyAfterStart);
        eq("sequence busy at the end", false, busy);
        eq("sequence notice once due", true, notice);

        System.out.println((failed == 0 ? "OK " : "FAILED ") + passed + " passed, " + failed + " failed");
        if (failed != 0) System.exit(1);
    }
}
