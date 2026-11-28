// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

/**
 * One OK press on a geometry screen (zoom, shift, rotation, keystone): a short press at its release, or a hold, once,
 * while the key is still down (HOLD_MS after its DOWN, read from the key repeats). A release after a hold, a cancelled
 * release and a lone release whose DOWN went to another surface are nothing. Feed it every OK event of the screen.
 */
public final class OkPress {
    public static final int NONE = 0, SHORT = 1, HOLD = 2;
    public static final long HOLD_MS = 800;
    /** android.view.KeyEvent ACTION_DOWN / ACTION_UP and the OK key codes (DPAD_CENTER, ENTER, NUMPAD_ENTER). */
    static final int DOWN = 0, UP = 1;
    private static final int[] OK_KEYS = {23, 66, 160};

    private boolean down, held;

    public static boolean isOk(int keyCode) {
        for (int k : OK_KEYS) if (k == keyCode) return true;
        return false;
    }

    /** SHORT, HOLD or NONE for one OK event (KeyEvent action, repeat count, down and event times, canceled flag). */
    public int feed(int action, int repeat, long downTime, long eventTime, boolean canceled) {
        if (action == DOWN) {
            if (repeat == 0) {
                down = true;
                held = false;
                return NONE;
            }
            if (down && !held && eventTime - downTime >= HOLD_MS) {
                held = true;
                return HOLD;
            }
            return NONE;
        }
        if (action != UP) return NONE;
        boolean isShort = down && !held && !canceled;
        down = false;
        return isShort ? SHORT : NONE;
    }
}
