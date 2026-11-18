// SPDX-License-Identifier: Apache-2.0
package org.lumen.control.vcontrol;

import org.lumen.control.Command;
import org.lumen.control.ControlSink;
import org.lumen.control.KeyAction;
import org.lumen.control.RemoteKey;

/**
 * The stock phone remote protocol (com.xgimi.vcontrol MobileControl.moduleControl) as a thin adapter on the command
 * core: one phone message in, at most one answer out, commands to the host's {@link ControlSink}. Connect does not
 * ask for an on-screen code: stock answers it directly for a phone it knows, in its smart-connect mode and for any
 * phone that sends authState -1, 0 or no authCode (ConnectHelper; the XGIMI app picks -1 itself for a connect
 * without a code), no stock command depends on a connect, and the keys of the same app go to ucontrolMDNS on UDP
 * 16735 without any code anyway.
 */
public final class Vcontrol {
    private static final String KEY_VOLUME = "volume";
    private static final String KEY_TYPE = "type";
    private static final String KEY_ACTION = "action";
    private static final String KEY_CODE = "code";
    private static final int STATE_ACCEPTED = 1;

    private final VcontrolModels model;
    private final Facts facts;
    private final ControlSink sink;

    public Vcontrol(VcontrolModels model, Facts facts, ControlSink sink) {
        this.model = model;
        this.facts = facts;
        this.sink = sink;
    }

    /** The answer to one message, null when stock would not answer it; tcpPort null while TCP is down. */
    public String handle(Request r, Peer p, Integer tcpPort) {
        if (r == null) return null;
        Exchange x = new Exchange(r, p, tcpPort);
        switch (r.action) {
            case Action.FIND_DEVICE: return Replies.deviceInfo(x, facts, model);
            case Action.REQUEST_CONNECT: return Replies.connectInfo(x, facts, model, sink.volume());
            case Action.HEART_REQUEST: return Replies.heartbeat(x);
            case Action.CONTROL: return control(x);
            case Action.AUTO_FOCUS: return Replies.status(x, r.action, model.autofocus && click(RemoteKey.AUTOFOCUS));
            case Action.SHOW_AUTH_CODE_STATE: return Replies.status(x, r.action, true);
            case Action.ACCEPT_AUTH_CODE: return null;
            case Action.CALL_BACK_AUTH_CODE: return Replies.status(x, r.action, r.statusState == STATE_ACCEPTED);
            case Action.VOLUME: return volume(x);
            case Action.CONNECT_DEVICE: return null;
            case Action.DISCONNECT_DEVICE: return null;
            case Action.SOUND_OFF: return Replies.status(x, r.action, click(RemoteKey.MUTE));
            case Action.DORMANCY_MODE: return Replies.status(x, r.action, click(RemoteKey.POWER));
            case Action.SEND_KEY_EVENT: return keyEvent(x);
            default:
                return Action.stockOther(r.action) ? Replies.status(x, r.action, false) : Replies.unknown(x);
        }
    }

    private String control(Exchange x) {
        Request r = x.request;
        if (!r.hasControl) return null;
        if (r.controlMode == ControlMode.KEY) {
            RemoteKey k = StockKeys.keyMode(r.controlType);
            if (k != null) click(k);
            return null;
        }
        if (r.controlMode == ControlMode.POWER && r.controlType == ControlMode.POWER_KEY) {
            click(RemoteKey.POWER);
            return Replies.controlDone(x);
        }
        return null;
    }

    /** Stock volumeAdjustment: a string "volume", clamped to the music stream; a non-number gets no answer. */
    private String volume(Exchange x) {
        String v = x.request.commonString(KEY_VOLUME);
        if (v == null) return Replies.status(x, Action.VOLUME, false);
        int step;
        try {
            step = Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return null;
        }
        return Replies.status(x, Action.VOLUME, sink.run(Command.volume(step)));
    }

    /** Stock dealKeyEvent + KeyManager.sendKeyEvent. */
    private String keyEvent(Exchange x) {
        Request r = x.request;
        if (!r.hasCommon) return null;
        String type = r.commonString(KEY_TYPE);
        Integer action = intOrNull(r.commonString(KEY_ACTION));
        Integer code = intOrNull(r.commonString(KEY_CODE));
        boolean ok = false;
        if (StockKeys.SENDER.equals(type) && action != null && code != null) {
            KeyAction a = StockKeys.keyAction(action);
            Command c = a == null ? null : StockKeys.keyEvent(code, a);
            ok = c != null && sink.run(c);
        }
        return Replies.status(x, Action.SEND_KEY_EVENT, ok);
    }

    private boolean click(RemoteKey k) {
        return sink.run(Command.key(k, KeyAction.CLICK));
    }

    /** Kotlin String.toIntOrNull: an optional sign and digits, nothing else. */
    static Integer intOrNull(String s) {
        if (s == null || !s.matches("[+-]?[0-9]+")) return null;
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
