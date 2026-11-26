// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
/*
 * Copyright (C) 2026 Lumen OS.
 */
package org.lumen.tvinput;

/**
 * The first moments after HdmiWatcher's process (= the boot) started: the settle window of the automatic switches and
 * the moment the source chosen for power-on (BootSource) opens. Stock XRMService BootControlService opens that source at
 * USER_UNLOCKED with no wait of its own; this persistent app starts at the same moment, so the port opens as soon as
 * TvInputManager lists its input (the HDMI HAL reported the port), whatever is plugged in (a soundbar on the eARC port,
 * nothing: the viewer shows its own card). Plain Java: test/run.sh replays recorded boots.
 */
final class BootWindow {
    /** Plug and CEC switches are dropped this long after the start: HPD and CEC states settle. */
    static final long SETTLE_MS = 30_000;
    /** A port input listed later than this opens nothing: the user is using the projector by then. */
    static final long OPEN_BY_MS = SETTLE_MS + 2_000;

    private final long startedAt;
    private boolean decided;

    BootWindow(long startedAt) {
        this.startedAt = startedAt;
    }

    /** True while an automatic switch must not happen ({@code powerOnSource}: the switch is the power-on source). */
    boolean blocks(long now, boolean powerOnSource) {
        return !powerOnSource && now - startedAt < SETTLE_MS;
    }

    /**
     * HdmiWatcher asks at its start and at every port input TvInputManager adds; {@code portListed}: TvInputManager
     * lists the input of the chosen port. True once: the power-on source opens now.
     */
    boolean openNow(long now, boolean portListed) {
        if (decided || !portListed) return false;
        decided = true;
        return now - startedAt <= OPEN_BY_MS;
    }
}
