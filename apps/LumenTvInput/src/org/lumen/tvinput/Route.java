// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.tvinput;

/**
 * Which framework routing call an HDMI session that becomes main makes (plain Java, HdmiSession asks it through
 * CecHelper.route).
 *
 * <p>AOSP 14 DeviceSelectActionFromTv (deviceSelect) sends {@code <Set Stream Path>} in start() and, when the TV or
 * the device is below CEC 2.0, once more in selectDevice() after the device reported power on: one call, two frames
 * (evleo790 LR-5MVYWT: one "deviceSelect(11) -> 0", vendor sends 18:23:35.548 and 18:23:36.836 to the Yandex Module
 * on 2.0.0.0, CEC 1.4). RoutingControlAction (portSelect) sends {@code <Routing Change>} and after its 1 s wait ONE
 * {@code <Set Stream Path>}, and nothing at all when the port is the active one already; stock XRMService
 * SignalService routes with portSelect only. So a device that is on and sits directly on the port gets portSelect;
 * deviceSelect stays for a device in standby or of unknown power (the action asks and wakes it) and for a device
 * behind a switch or AVR (its own path).
 */
final class Route {
    static final int PORT = 1, DEVICE = 2;

    private Route() {}

    /**
     * deviceId: the framework id of the CEC device of a child input, -1 for a port input. on: the framework has its
     * power status as ON. direct: its physical address is the port's own.
     */
    static int forMain(int deviceId, boolean on, boolean direct) {
        if (deviceId < 0 || on && direct) return PORT;
        return DEVICE;
    }
}
