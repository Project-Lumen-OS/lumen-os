// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.signal;

/** What the signal panel shows now. */
public enum PanelMode {
    /** nothing on screen, no HAL call at all */
    HIDDEN,
    /** the small card shown by itself for a few seconds after the signal changed (the user's option, off by default) */
    POPUP,
    /** the small card in the top right corner (first press), keys stay with the film or the console */
    COMPACT,
    /** the full panel on the right side (second press), keys are the panel's until it closes */
    DETAIL;

    public boolean visible() { return this != HIDDEN; }

    /** The look: POPUP and COMPACT draw the small card. */
    public boolean card() { return this == POPUP || this == COMPACT; }
}
