// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.control.vcontrol;

/**
 * Stock FeedbackInfo as Gson writes it: its own fields by name (action, deviceInfo, endScreenCapTime, extraInfo,
 * msgid, startScreenCapTime, status, type), then the base class field agreement; null fields left out.
 */
final class Feedback {
    private final int action;
    private final String msgid;
    private final Json.Obj agreement;
    private Json.Obj deviceInfo;
    private Json.Obj extraInfo;
    private Json.Obj status;

    Feedback(int action, String msgid, Json.Obj agreement) {
        this.action = action;
        this.msgid = msgid;
        this.agreement = agreement;
    }

    Feedback deviceInfo(Json.Obj d) {
        deviceInfo = d;
        return this;
    }

    Feedback extraMessage(String message) {
        extraInfo = new Json.Obj().put("message", message);
        return this;
    }

    Feedback state(boolean ok) {
        status = new Json.Obj().put("state", ok ? 1 : 0);
        return this;
    }

    String json() {
        return new Json.Obj()
                .put("action", action)
                .put("deviceInfo", deviceInfo)
                .put("endScreenCapTime", 0)
                .put("extraInfo", extraInfo)
                .put("msgid", msgid)
                .put("startScreenCapTime", 0)
                .put("status", status)
                .put("type", 0)
                .put("agreement", agreement)
                .toString();
    }
}
