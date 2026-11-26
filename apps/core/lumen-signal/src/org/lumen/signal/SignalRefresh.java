// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.signal;

/**
 * How often the visible panel reads the HDMI receiver (FormatWatch): the user's choice in All settings, General, For
 * developers. Normal (the default): on a signal change and every 5 s. Fast: every second, for people who film the
 * screen to show modes switching (each read may cost a frame now and then).
 */
public enum SignalRefresh {
    NORMAL("normal", 5_000L), FAST("fast", 1_000L);

    public final String token;
    public final long periodMs;

    SignalRefresh(String token, long periodMs) {
        this.token = token;
        this.periodMs = periodMs;
    }

    /** The token's choice, Normal for anything else. */
    public static SignalRefresh of(String token) { return FAST.token.equals(token) ? FAST : NORMAL; }
}
