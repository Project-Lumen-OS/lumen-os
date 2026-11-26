// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.signal;

/**
 * The one button of the panel (2026-11-18, variant A): first press the small card, second press the full panel,
 * third press hides. BACK closes the full panel at once. With the user's option on, a new signal shows the small card
 * by itself for {@link #POPUP_MS} and it hides again. With the option "keep the card on screen" on (2026-11-18,
 * for people who film the screen) the small card stays over any HDMI input, through signal changes and input switches;
 * it goes only when the option is turned off or the user presses the key (then it waits for the next time an HDMI input
 * comes on screen). Holding OK on the small card opens the full panel (OkHold, 2026-11-19); holding it again or
 * BACK folds it back into the card it came from. Pure state machine, host tested.
 */
public final class PanelToggle {
    public static final long POPUP_MS = 5_000L;
    /** How long no HDMI input may be on screen before a pinned card goes (an input switch passes through none). */
    public static final long PIN_LEAVE_MS = 3_000L;

    private PanelMode mode = PanelMode.HIDDEN;
    /** the option is on; the user closed the pinned card with the key; the card on screen came from the pin */
    private boolean pinned, dismissed, byPin;
    /** since when no HDMI input is on screen, -1 = one is */
    private long goneAt = -1;
    /** the full panel was opened by holding OK, from this card (POPUP or COMPACT) */
    private boolean held;
    private PanelMode from = PanelMode.COMPACT;

    public PanelMode mode() { return mode; }

    /** The panel's key, the round menu button or the remote action. */
    public PanelMode press() {
        switch (mode) {
            case HIDDEN: mode = PanelMode.COMPACT; break;
            case POPUP:
            case COMPACT: mode = PanelMode.DETAIL; break;
            default: mode = PanelMode.HIDDEN; break;
        }
        byPin = false;
        held = false;
        if (mode == PanelMode.HIDDEN && pinned) dismissed = true;
        return mode;
    }

    /** BACK reaches the panel only while the full panel has the keys: it folds a held-open panel back, else closes. */
    public PanelMode back() {
        if (mode == PanelMode.DETAIL && held) return collapse();
        if (mode == PanelMode.DETAIL) {
            mode = PanelMode.HIDDEN;
            if (pinned) dismissed = true;
        }
        return mode;
    }

    /** OK held on the small card: the full panel. Nothing happens without a card on screen. */
    public PanelMode expand() {
        if (!mode.card()) return mode;
        from = mode;
        mode = PanelMode.DETAIL;
        held = true;
        return mode;
    }

    /** OK held again (or BACK) in a panel opened by holding OK: back to the card it came from. */
    public PanelMode collapse() {
        if (mode != PanelMode.DETAIL || !held) return mode;
        mode = from;
        held = false;
        return mode;
    }

    /** The full panel on screen was opened by holding OK (BACK folds it back instead of closing). */
    public boolean held() { return held && mode == PanelMode.DETAIL; }

    /** The option "keep the card on screen" changed: on shows the card over an HDMI input now, off takes it away. */
    public PanelMode pin(boolean on, boolean hdmiOnScreen) {
        pinned = on;
        dismissed = false;
        goneAt = -1;
        if (on && hdmiOnScreen && mode == PanelMode.HIDDEN) {
            mode = PanelMode.COMPACT;
            byPin = true;
        } else if (!on && mode.card()) {
            mode = PanelMode.HIDDEN;
        }
        return mode;
    }

    public boolean pinned() { return pinned; }

    /**
     * The pin watch, about once a second while the option is on (no vendor call): an HDMI input on screen brings the
     * card back unless the user closed it; no HDMI input for {@link #PIN_LEAVE_MS} takes away a card the pin showed
     * and forgets the user's close, so the next HDMI input shows it again.
     */
    public PanelMode pinTick(boolean hdmiOnScreen, long now) {
        if (!pinned) return mode;
        if (hdmiOnScreen) {
            goneAt = -1;
            if (mode == PanelMode.HIDDEN && !dismissed) {
                mode = PanelMode.COMPACT;
                byPin = true;
            }
        } else if (goneAt < 0) {
            goneAt = now;
        } else if (now - goneAt >= PIN_LEAVE_MS) {
            dismissed = false;
            if (byPin && mode.card()) mode = PanelMode.HIDDEN;
        }
        return mode;
    }

    /**
     * A new signal on the input: the small card by itself, only when the user turned the option on (off by default)
     * and an HDMI input is on screen (never over the apps or Home).
     */
    public PanelMode signalChanged(boolean popupOn, boolean hdmiOnScreen) {
        if (popupOn && hdmiOnScreen && mode == PanelMode.HIDDEN) mode = PanelMode.POPUP;
        return mode;
    }

    /** The popup's time is over: hide it, unless the user pressed the key meanwhile. */
    public PanelMode popupOver() {
        if (mode == PanelMode.POPUP && !pinned) mode = PanelMode.HIDDEN;
        else if (mode == PanelMode.POPUP) mode = PanelMode.COMPACT;
        return mode;
    }

    /** Standby, light off, setup, another full screen panel: everything goes. */
    public PanelMode hide() {
        mode = PanelMode.HIDDEN;
        byPin = false;
        held = false;
        return mode;
    }
}
