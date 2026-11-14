// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

/**
 * Plain decisions of the control API that other Lumen apps use (StateProvider, the projector's quick menu), kept
 * free of Android so the host checks can run them: which setting a call changes, how a switch value is read, and
 * the one status the settings screen and the quick menu both show.
 */
public final class Control {
    private Control() {}

    /** call("state"): the current settings and status. */
    public static final String STATE = "state";
    /** call("set_enabled", "1" | "0"): screen mirroring on or off. */
    public static final String SET_ENABLED = "set_enabled";
    /** call("set_ask", "1" | "0"): ask before every connection, or not. */
    public static final String SET_ASK = "set_ask";

    /** Settings a set call may change (the names are the Prefs keys). */
    public static final String ENABLED = "enabled";
    public static final String ASK = "ask";

    /** The setting a set call changes ({@link #ENABLED} or {@link #ASK}), or null for any other method. */
    public static String settingOf(String method) {
        if (SET_ENABLED.equals(method)) return ENABLED;
        if (SET_ASK.equals(method)) return ASK;
        return null;
    }

    /** "1" is on, "0" is off, anything else is null: the call then changes nothing. */
    public static Boolean parseSwitch(String arg) {
        if ("1".equals(arg)) return Boolean.TRUE;
        if ("0".equals(arg)) return Boolean.FALSE;
        return null;
    }

    /**
     * The status line: off while the setting is off, starting until the service runs, the peer while a session
     * runs, then the Wi-Fi Direct state ({@code p2pState} = P2pState name; unknown names count as
     * starting).
     */
    public static ReceiverStatus status(boolean enabled, boolean serviceUp, boolean mirroring, String p2pState) {
        if (!enabled) return ReceiverStatus.OFF;
        if (!serviceUp) return ReceiverStatus.STARTING;
        if (mirroring) return ReceiverStatus.IN_USE;
        if ("UNSUPPORTED".equals(p2pState)) return ReceiverStatus.UNSUPPORTED;
        if ("WIFI_OFF".equals(p2pState)) return ReceiverStatus.NO_WIFI;
        if ("VISIBLE".equals(p2pState)) return ReceiverStatus.READY;
        return ReceiverStatus.STARTING;
    }
}
