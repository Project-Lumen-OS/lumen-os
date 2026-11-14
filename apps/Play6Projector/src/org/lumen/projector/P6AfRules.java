package org.lumen.projector;

import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * Plain-Java focus decisions of the Play 6 (no Android, host-tested: sh test/afrules/run.sh).
 *
 * The Play 6 autofocus is the vendor ToF + Hall AF (G0058 Autofocus.ini focusTypeTable 8). It reports only
 * 330 (report JSON, "action" start / end), 12, 5, 18 and 302, never the Z9X camera-AF set 4 / 8 / 333-335
 * (logs of a Play 6: 330 x88, 12 x44, 5 x44, 18 x14, 302 x6, 0 x2). So the AF busy window is armed
 * by a 330 AutoFocusing start and cleared by its end (and by 5, 2 or 18).
 *
 * Stock sysui FocusUIV2.onProjectorFocusEventAfCall: type 18 with value "AF_Success_Into_Adjust" shows a
 * short "focus done, fine-tune by hand" notice, unless the starry sky lens is in (isConvexLensIn). It comes
 * only after a remote-key AF (t17 22:19:54), not after the AF that follows an auto keystone.
 *
 * Stock FocusAdjust closes the manual focus window without manualFocus(2) (hideFocusAdjust, handler msg 2
 * only removes the view), and the Play 6 vendor has no 17 mode to leave ("invalid m_MF_StateMachine:17"),
 * so the close sends 2 only when something may be moving.
 *
 * A manual focus move sends the motor command first and writes the persisted manual_moving flag after it
 * ({@link #startMove}): the flag is a SharedPreferences commit, a synchronous disk write that used to hold back the
 * first motor step of every key press.
 */
final class P6AfRules {
    private P6AfRules() {}

    static final int MOVE_STARTED = 0;
    static final int MOVE_MOTOR_FAILED = 1;
    static final int MOVE_FLAG_FAILED = 2;

    /** The 18 value stock compares with (ConstantFocus.AF_HALL_FOCUS_SUCCESS_END_MSG). */
    static final String HALL_DONE = "AF_Success_Into_Adjust";

    static final int REPORT_NONE = 0;
    static final int REPORT_AF_START = 1;
    static final int REPORT_AF_END = 2;

    private static final Pattern AF_REPORT = Pattern.compile("\"ev_describe\"\\s*:\\s*\"AutoFocusing\"");
    private static final Pattern ACTION_START =
            Pattern.compile("\\{\\s*\"key\"\\s*:\\s*\"action\"\\s*,\\s*\"value\"\\s*:\\s*\"start\"\\s*\\}");
    private static final Pattern ACTION_END =
            Pattern.compile("\\{\\s*\"key\"\\s*:\\s*\"action\"\\s*,\\s*\"value\"\\s*:\\s*\"end\"\\s*\\}");

    /** Event 330: an AutoFocusing start or end, or REPORT_NONE (keystone and other reports, bad JSON). */
    static int report330(String json) {
        if (json == null || !AF_REPORT.matcher(json).find()) return REPORT_NONE;
        if (ACTION_START.matcher(json).find()) return REPORT_AF_START;
        if (ACTION_END.matcher(json).find()) return REPORT_AF_END;
        return REPORT_NONE;
    }

    /** Type 18 with the stock value: the fine-tune notice is due (still subject to the lens check). */
    static boolean fineTuneHint(int type, String value) {
        return type == 18 && HALL_DONE.equals(value);
    }

    /** An AF end event that clears the busy window (330 end is handled by report330). */
    static boolean clearsBusy(int type) {
        return type == 5 || type == 2 || type == 18;
    }

    /** Manual focus window closed: send manualFocus(2) only if something may be moving (stock sends none). */
    static boolean stopOnClose(boolean moving, boolean flagged) {
        return moving || flagged;
    }

    /**
     * Starts a manual focus move: MOTOR (manualFocus 1 / 0, false when the call failed), then SAVE_FLAG (commit
     * manual_moving=true, false when it did not reach the disk). A move whose flag could not be saved is stopped at
     * once, because a restarted process could not stop it. A failed motor command may have started the motor all the
     * same, so the flag is saved before its fail-safe STOP: a stop the HAL cannot take now is sent on reconnect.
     */
    static int startMove(BooleanSupplier motor, BooleanSupplier saveFlag, Runnable stop) {
        if (!motor.getAsBoolean()) {
            saveFlag.getAsBoolean();
            stop.run();
            return MOVE_MOTOR_FAILED;
        }
        if (saveFlag.getAsBoolean()) return MOVE_STARTED;
        stop.run();
        return MOVE_FLAG_FAILED;
    }
}
