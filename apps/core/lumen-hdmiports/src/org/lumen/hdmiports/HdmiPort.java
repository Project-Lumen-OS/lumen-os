// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hdmiports;

/**
 * One HDMI port as org.lumen.tvinput's PortState describes it (its hdmi_state provider, bundle "port_n"). The state
 * rule lives there and only there: {@link #ON} a picture or +5V, {@link #ASLEEP} a CEC source in standby,
 * {@link #NO_SIGNAL} +5V without video, {@link #DISCONNECTED} no +5V and no CEC source. +5V is the framework's
 * input state of the port (TIF CONNECTED), which the stock launchers read the same way (XGIMI Home
 * HDMIChangeReceiver: state 0 signal in, 1 signal out). The CEC name is only a label ({@link #title}).
 */
public final class HdmiPort {
    public static final int UNKNOWN = 0, ON = 1, ASLEEP = 2, NO_SIGNAL = 3, DISCONNECTED = 4;
    private static final String[] NAMES = {"unknown", "on", "asleep", "no_signal", "disconnected"};
    /** The framework's input state of a port with +5V (TvInputManager.INPUT_STATE_CONNECTED). */
    static final int TIF_CONNECTED = 0;

    public final int port;
    /** The port input, and the CEC device's own input when there is one (open it to route to the device). */
    public final String inputId, childInputId;
    /** The port's label ("HDMI 1 (ARC)") and what to call the device on it (null: no name known). */
    public final String label, title;
    /** "console", "player" or "other": the glyph of the device's kind. */
    public final String kind;
    public final int state, inputState, accent;

    public HdmiPort(int port, String inputId, String childInputId, String label, String title, String kind, int state,
            int inputState, int accent) {
        this.port = port;
        this.inputId = inputId;
        this.childInputId = childInputId;
        this.label = label;
        this.title = title;
        this.kind = kind;
        this.state = state;
        this.inputState = inputState;
        this.accent = accent;
    }

    /** The state of a provider name ("on" ...), {@link #UNKNOWN} for anything else. */
    public static int state(String name) {
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) return i;
        return UNKNOWN;
    }

    public static String name(int state) {
        return state >= 0 && state < NAMES.length ? NAMES[state] : NAMES[UNKNOWN];
    }

    /** A cable with a powered device is in: the stock "signal in" (+5V), what the Source key steps to first. */
    public boolean plugged() {
        return inputState == TIF_CONNECTED;
    }

    /** Nothing on the port: the only state a chip calls "Not connected". */
    public boolean disconnected() {
        return state == DISCONNECTED;
    }

    /** The input that shows the device: its CEC input when there is one, else the port's. */
    public String target() {
        return childInputId != null && !childInputId.isEmpty() ? childInputId : inputId;
    }
}
