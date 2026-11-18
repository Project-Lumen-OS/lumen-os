// SPDX-License-Identifier: Apache-2.0
package org.lumen.control.vcontrol;

/** Stock action numbers (xgimi.com.mobileinteraction.ActionConstants and MobileControl.moduleControl). */
final class Action {
    static final int FIND_DEVICE = 9998;
    static final int DEVICE_INFO = 9999;
    static final int REQUEST_CONNECT = 10000;
    static final int CONNECT_INFO = 10001;
    static final int HEART_REQUEST = 10002;
    static final int HEART_RESPONSE = 10003;
    static final int CONTROL = 20000;
    static final int CONTROL_DONE = 20001;
    static final int AUTO_FOCUS = 46000;
    static final int SHOW_AUTH_CODE_STATE = 50000;
    static final int ACCEPT_AUTH_CODE = 50001;
    static final int CALL_BACK_AUTH_CODE = 50002;
    static final int VOLUME = 50009;
    static final int CONNECT_DEVICE = 50010;
    static final int DISCONNECT_DEVICE = 50011;
    static final int SOUND_OFF = 50012;
    static final int DORMANCY_MODE = 50021;
    static final int SEND_KEY_EVENT = 50036;

    /**
     * Every other action stock vcontrol 2.5.x answers (moduleControl): Lumen does not do them and reports a failure
     * with the action itself instead of the "unknown action" answer.
     */
    static final int[] STOCK_OTHERS = {
        11000, 30000, 30200, 30400, 40000, 41000, 41001, 42000, 43000, 44000, 45000, 47000, 48000, 49000,
        50003, 50004, 50005, 50006, 50008, 50013, 50014, 50015, 50016, 50017, 50018, 50019, 50020, 50022, 50023,
        50024, 50025, 50026, 50027, 50028, 50029, 50030, 50031, 50035, 50037, 50038, 50039, 50040, 50041, 50042,
        70000, 70001, 70002, 70003, 70004
    };

    private Action() {}

    static boolean stockOther(int action) {
        for (int a : STOCK_OTHERS) if (a == action) return true;
        return false;
    }
}
