// SPDX-License-Identifier: Apache-2.0
package org.lumen.control.vcontrol;

import org.lumen.control.Command;
import org.lumen.control.KeyAction;
import org.lumen.control.RemoteKey;

/** The key tables of stock vcontrol, as core commands. */
final class StockKeys {
    /** Stock KeyBean.type of the one key sender stock has (KeyManager keySenderMap: "inst" = Instrumentation). */
    static final String SENDER = "inst";

    private StockKeys() {}

    /** controlCmd mode 0: XgimiKeyEvent.keyMode(type), a click; null for a type stock ignores. */
    static RemoteKey keyMode(int type) {
        switch (type) {
            case 1: return RemoteKey.LEFT;
            case 2: return RemoteKey.UP;
            case 3: return RemoteKey.RIGHT;
            case 4: return RemoteKey.DOWN;
            case 5: return RemoteKey.OK;
            case 6: return RemoteKey.BACK;
            case 7: return RemoteKey.MENU;
            case 8: return RemoteKey.HOME;
            case 9: return RemoteKey.VOLUME_UP;
            case 10: return RemoteKey.VOLUME_DOWN;
            default: return null;
        }
    }

    /** sendKeyEvent (50036) action: 1 long press down, 2 up, 3 click; null for any other (stock: false). */
    static KeyAction keyAction(int action) {
        switch (action) {
            case 1: return KeyAction.DOWN;
            case 2: return KeyAction.UP;
            case 3: return KeyAction.CLICK;
            default: return null;
        }
    }

    /**
     * sendKeyEvent (50036) code: KeyManager.getRealKeyCode. 1..15 are the remote's keys (11 KPPOWER and 13 POWER are
     * both the power key, 12 is the gear MOVE_HOME), any other code from 0 up is an Android key code passed through,
     * a negative code is null (stock: false).
     */
    static Command keyEvent(int code, KeyAction action) {
        RemoteKey k = gmKey(code);
        if (k != null) return Command.key(k, action);
        return code < 0 ? null : Command.androidKey(code, action);
    }

    private static RemoteKey gmKey(int code) {
        switch (code) {
            case 1: return RemoteKey.LEFT;
            case 2: return RemoteKey.RIGHT;
            case 3: return RemoteKey.UP;
            case 4: return RemoteKey.DOWN;
            case 5: return RemoteKey.OK;
            case 6: return RemoteKey.BACK;
            case 7: return RemoteKey.MENU;
            case 8: return RemoteKey.HOME;
            case 9: return RemoteKey.VOLUME_UP;
            case 10: return RemoteKey.VOLUME_DOWN;
            case 11: return RemoteKey.POWER;
            case 12: return RemoteKey.GEAR;
            case 13: return RemoteKey.POWER;
            case 14: return RemoteKey.FOCUS_LEFT;
            case 15: return RemoteKey.FOCUS_RIGHT;
            default: return null;
        }
    }
}
