// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.tvinput;

/**
 * When the TV takes the active source back after an HDMI session stops being shown (plain Java, HdmiSession runs it).
 *
 * <p>Stock: every sleep goes Home first (XgimiWindowManagerDomestic.reallyGoToSleep: launchHome, then goToSleep), so
 * the HDMI picture leaves and the stock bus shows {@code <Active Source> 0F:82:00:00} 274 ms before the framework's
 * ARC termination and {@code <Standby>} (Slava's sniffer, lumen-hdmi test/cec_stock_x50_sleep.txt). A Lumen session
 * ends at the sleep without a Home step (the display goes off under it): when Android is not interactive any more
 * the internal source is selected at once, before the framework's own standby frames (live X50: session released
 * 214 ms before its ARC termination). An exit while awake keeps the user's choice (off by default, 1 s later).
 */
final class ExitSelect {
    static final long ON_EXIT_DELAY_MS = 1_000;

    private ExitSelect() {}

    /** Milliseconds until selectInternal, 0 = now, -1 = never. */
    static long delayMs(boolean interactive, boolean cecControl, boolean onExitPref) {
        if (!cecControl) return -1;
        if (!interactive) return 0;
        return onExitPref ? ON_EXIT_DELAY_MS : -1;
    }
}
