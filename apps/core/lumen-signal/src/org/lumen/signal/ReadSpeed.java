// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.signal;

/** How often the panel reads one item (SignalBudget). */
public enum ReadSpeed {
    /** for the small card too, every SignalBudget.FAST_MS */
    CARD,
    /** for the small card too, every SignalBudget.SLOW_MS (settings that move only by hand: range, aspect) */
    CARD_SLOW,
    /** only while the full panel is open, every SignalBudget.SLOW_MS */
    DETAIL,
    /** only while the full panel is open, once per opening or input change (the port and its EDID do not move) */
    ONCE
}
