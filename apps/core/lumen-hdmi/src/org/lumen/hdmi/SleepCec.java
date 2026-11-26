// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hdmi;

/**
 * What the projector says on HDMI-CEC before it goes to sleep (plain Java, SleepSource runs it).
 *
 * <p>Stock: every sleep is XgimiWindowManagerDomestic.reallyGoToSleep, which goes Home first and then calls goToSleep
 * (X50 stock services.jar). The HDMI picture leaves the screen, so the TV is the active source again: Slava's bus
 * sniffer shows {@code <Active Source> 0F:82:00:00} 274 ms before the framework's standby frames (ARC termination,
 * then {@code <Standby>}, test/cec_stock_x50_sleep.txt). Lumen sleeps with the HDMI picture still up, so that frame
 * was missing (test/cec_x50_live_sleep.txt). A box with power_state_change_on_active_source_lost=standby_now goes to
 * standby on it even with the standby switch off, as on stock.
 */
public final class SleepCec {
    private SleepCec() {}

    /**
     * True: before the Android sleep the TV takes the active source back (HdmiTvClient.selectDevice(ADDR_INTERNAL);
     * the framework sends {@code <Active Source>} only when a device is the active source). {@code cecOn}:
     * hdmi_cec_enabled; {@code hdmiShown}: an HDMI port is on screen now, or was when the projector's standby began
     * (its black screen covers the picture before the sleep). Independent of the standby switch, as stock.
     */
    public static boolean tvTakesSource(boolean cecOn, boolean hdmiShown) {
        return cecOn && hdmiShown;
    }
}
