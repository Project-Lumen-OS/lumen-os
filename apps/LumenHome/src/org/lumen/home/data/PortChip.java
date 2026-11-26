// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.data;

import org.lumen.hdmiports.HdmiPort;

/**
 * The state of an Inputs chip from the HDMI app's port state (lumen-hdmiports HdmiPort) and the one rule for when a
 * chip says "Not connected": only a port with nothing on it (no +5V and no CEC source), or another TV input's input
 * the framework calls disconnected. Plain Java, host-tested (PortChipTest).
 */
public final class PortChip {
    /** TvInputManager.INPUT_STATE_DISCONNECTED: another TV input's input without a device. */
    static final int TIF_DISCONNECTED = 2;

    private PortChip() {}

    /** The card state (Card.DEV_*) of a port state. */
    public static int state(int portState) {
        switch (portState) {
            case HdmiPort.ON: return Card.DEV_ON;
            case HdmiPort.ASLEEP: return Card.DEV_ASLEEP;
            case HdmiPort.NO_SIGNAL: return Card.DEV_NO_SIGNAL;
            default: return Card.DEV_DISCONNECTED;
        }
    }

    /** The chip is greyed and says "Not connected". */
    public static boolean off(int cardState) {
        return cardState == Card.DEV_DISCONNECTED || cardState == TIF_DISCONNECTED;
    }
}
