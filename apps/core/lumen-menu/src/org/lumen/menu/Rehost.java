// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

/**
 * Whether the menu stays when its window loses the screen for a moment (TITAN testers 2026-11-19: an HDMI source that
 * renegotiates closed the menu). The TV input app restarts its own screen on a new signal, so the menu's window stops
 * or loses the focus while the same app stays in front: then the menu comes back where it was. HOME, another app, a
 * screen that goes off (power off) close it as before, and the menu never comes back while the screen is off, so a power
 * off is never interrupted. Plain Java, the host hands in what it read.
 */
public final class Rehost {
    private Rehost() {}

    /** The wait before the menu window comes back over the restarted input screen. */
    public static final long WAIT_MS = 600;
    /** How long a lost focus may last while the same app stays in front before the menu closes after all. */
    public static final long FOCUS_GRACE_MS = 6000;

    /**
     * {@code interactive}: the screen is on. {@code frontAtShow}: the package in front when the menu opened,
     * {@code frontNow}: the package in front now, {@code own}: the menu's own package (null when unknown). True when the
     * menu should come back (or wait for its focus).
     */
    public static boolean keep(boolean interactive, String frontAtShow, String frontNow, String own) {
        return interactive && frontAtShow != null && !frontAtShow.isEmpty() && frontAtShow.equals(frontNow)
                && !frontAtShow.equals(own);
    }

    /** True while a lost focus may still come back: kept and inside {@link #FOCUS_GRACE_MS} since it went. */
    public static boolean waitFocus(boolean keep, long lostAtMs, long nowMs) {
        return keep && nowMs - lostAtMs < FOCUS_GRACE_MS;
    }
}
