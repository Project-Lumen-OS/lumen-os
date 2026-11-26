// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.data;

import org.lumen.hdmiports.HdmiPort;

/**
 * Home's chips with fake ports, as the HDMI app reports them (X50 Ultra, Slava 2026-11-24: an eARC soundbar on
 * HDMI 1, a CEC box on HDMI 2, a laptop without CEC on HDMI 3): only an empty port says "Not connected".
 */
final class PortChipTest {
    private PortChipTest() {}

    static void run() {
        chip(HdmiPort.ON, false, "CEC sink (eARC soundbar), +5V");
        chip(HdmiPort.ON, false, "CEC source with its name, +5V");
        chip(HdmiPort.ON, false, "source without CEC (laptop), +5V");
        chip(HdmiPort.NO_SIGNAL, false, "+5V without a picture");
        chip(HdmiPort.ASLEEP, false, "CEC source asleep");
        chip(HdmiPort.DISCONNECTED, true, "unplugged");
        chip(HdmiPort.UNKNOWN, true, "a port the HDMI app does not know yet");
        T.ok(PortChip.off(PortChip.TIF_DISCONNECTED), "another TV input's disconnected input");
        T.ok(!PortChip.off(0), "another TV input's connected input");
        T.ok(HdmiPort.state("on") == HdmiPort.ON && HdmiPort.state("disconnected") == HdmiPort.DISCONNECTED
                && HdmiPort.state("no_signal") == HdmiPort.NO_SIGNAL && HdmiPort.state("asleep") == HdmiPort.ASLEEP
                && HdmiPort.state("x") == HdmiPort.UNKNOWN, "the provider's state names");
        HdmiPort laptop = new HdmiPort(3, "org.lumen.tvinput/.HdmiInputService/HW3", null, "HDMI 3", null, "other",
                HdmiPort.ON, 0, 0);
        T.ok(laptop.plugged() && !laptop.disconnected(), "a laptop with +5V is plugged");
        HdmiPort pulled = new HdmiPort(1, "x/HW1", null, "HDMI 1 (ARC)", null, "other", HdmiPort.DISCONNECTED, 1, 0);
        T.ok(!pulled.plugged(), "X50: cable out is TIF CONNECTED_STANDBY, not plugged (stock: state 1 = signal out)");
        HdmiPort box = new HdmiPort(2, "x/HW2", "x/HDMI200004", "HDMI 2", "Box R 4K Plus", "player", HdmiPort.ON, 0, 0);
        T.ok("x/HDMI200004".equals(box.target()), "a CEC device opens through its own input");
    }

    private static void chip(int portState, boolean off, String what) {
        T.ok(PortChip.off(PortChip.state(portState)) == off, "chip " + (off ? "off" : "on") + ": " + what);
    }
}
