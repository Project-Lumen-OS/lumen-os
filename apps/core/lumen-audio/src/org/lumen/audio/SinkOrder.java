// SPDX-License-Identifier: Apache-2.0
package org.lumen.audio;

/**
 * The order of the vendor audio HAL sink selections ("xgimi_audiopath_switch_end=P": the device the HAL writes the mix
 * to, 0 speaker, 2 ARC, 3 Bluetooth A2DP, 4 the 3.5 mm jack). The HAL keeps the last switch_end it got and writes to
 * A2DP only while that sink is A2DP, so a switch_end decided for an older media route must never land after a newer one.
 *
 * Why (HB rig 2026-11-14 17:49): the speaker end of an ARC -> speaker step was posted to the main thread, the route
 * moved on to Bluetooth and switch_end=3 went out from the HAL thread, then the late speaker end arrived 114 ms after it:
 * the HAL sink stayed "speaker", A2DP never played (mIsPlaying false) for the HDMI input and the projector's own sound.
 *
 * Rule: every switch_end gets a ticket when it is decided, together with the route it was decided for. It reaches the
 * HAL only when no newer ticket reached it already and the route is still that one (an unknown route, before the first
 * report of the process, does not block). Plain Java, host tested (test/run.sh). Used through HalSink.
 */
public final class SinkOrder {
    /** The route is not known yet (no path reported in this process). */
    public static final int ROUTE_UNKNOWN = -1;

    private int issued;
    private int written;

    /** A switch_end is decided now: its ticket. Any thread. */
    public synchronized int ticket() {
        return ++issued;
    }

    /**
     * The switch_end of {@code ticket}, decided for route {@code forRoute}, may go to the HAL now that the route is
     * {@code routeNow}; true also records it as the newest one sent.
     */
    public synchronized boolean claim(int ticket, int forRoute, int routeNow) {
        if (ticket <= written) return false;
        if (routeNow != ROUTE_UNKNOWN && forRoute != routeNow) return false;
        written = ticket;
        return true;
    }

    /** The newest ticket that went to the HAL, 0 none. */
    public synchronized int written() {
        return written;
    }
}
