// SPDX-License-Identifier: Apache-2.0
package org.lumen.control.vcontrol;

/**
 * The answers of stock vcontrol (xgimi.com.mobileinteraction.callback.CallBackCenter). DeviceInfo fields go in Gson
 * order; apiVersion (the feature-state API Lumen does not serve) and leboNfcUid (no Lebo NFC) stay out, as stock
 * leaves them out where it lacks them.
 */
final class Replies {
    /** Stock XgimiDevice.getDeviceType() for every Lumen model (no 628/918/928/Z4/801 in Build.DEVICE), sic. */
    static final String DEVICE_TYPE = "unkonwn";
    /** Stock R.string.unknown_error (values/strings.xml). */
    static final String UNKNOWN_ERROR = "Unknown exception";

    private Replies() {}

    /** findDevice (9998) -> 9999. */
    static String deviceInfo(Exchange x, Facts f, VcontrolModels m) {
        Json.Obj d = new Json.Obj()
                .put("bluetoothMacAddress", f.bluetoothMac())
                .put("currentVolume", 0)
                .put("device", m.device)
                .put("deviceip", f.ip())
                .put("devicename", f.name())
                .put("devicetype", DEVICE_TYPE)
                .put("maxVolume", 0)
                .put("pid", f.pid())
                .put("productName", f.productName())
                .put("versioncode", 0);
        return new Feedback(Action.DEVICE_INFO, x.msgid(), x.agreement()).deviceInfo(d).json();
    }

    /** requestConnect (10000) -> 10001; ipaddress is the phone's own address (stock ConnectInfo(remoteIp)). */
    static String connectInfo(Exchange x, Facts f, VcontrolModels m, int[] volume) {
        Json.Obj d = new Json.Obj()
                .put("bluetoothMacAddress", f.bluetoothMac())
                .put("currentVolume", volume[1])
                .put("device", m.device)
                .put("devicetype", DEVICE_TYPE)
                .put("ipaddress", x.peer.address)
                .put("mac", f.mac())
                .put("maxVolume", volume[0])
                .put("pid", f.pid())
                .put("productName", f.productName())
                .put("versioncode", m.versionCode);
        return new Feedback(Action.CONNECT_INFO, x.msgid(), x.agreement()).deviceInfo(d).json();
    }

    /** heartbeat (10002) -> 10003. */
    static String heartbeat(Exchange x) {
        return new Feedback(Action.HEART_RESPONSE, x.msgid(), x.agreement()).json();
    }

    /** getDealCmdStatus: the action itself with status.state 1 or 0. */
    static String status(Exchange x, int action, boolean ok) {
        return new Feedback(action, x.msgid(), x.agreement()).state(ok).json();
    }

    /** controlCmd done: 20001 with the msgid exactly as the phone wrote it (stock does not default it here). */
    static String controlDone(Exchange x) {
        return new Feedback(Action.CONTROL_DONE, x.request.msgid, x.agreement()).json();
    }

    /** An action stock does not know: getErrorMessage(action + 1). */
    static String unknown(Exchange x) {
        return new Feedback(x.request.action + 1, x.msgid(), x.agreement()).extraMessage(UNKNOWN_ERROR).json();
    }
}
