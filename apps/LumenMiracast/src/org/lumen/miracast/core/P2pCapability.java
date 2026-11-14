// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

/**
 * Can this unit do Wi-Fi Direct? Decided from what the framework did, never from the model or the Wi-Fi chip: one
 * receiver serves every projector (the Play 6 alone ships two chips on one vendor). Host tested.
 *
 * - YES once a Wi-Fi Direct call succeeded ({@link #proven}): the framework answers success only in its enabled state,
 *   with the P2P interface set up (any other state answers BUSY).
 * - NO for good ({@link #definitive}) when the framework has no Wi-Fi Direct at all: no feature, no manager, no
 *   channel, or a call answered P2P_UNSUPPORTED.
 * - NO for now when Wi-Fi stayed on for {@link #PROOF_WINDOW_MS} with the channel open and no call ever succeeded:
 *   Android 14 sets the P2P interface up for the first channel while Wi-Fi is on, so a chip or driver without it
 *   leaves every call failing. A later success still turns it into YES.
 * - While Wi-Fi is off nothing is decided: Wi-Fi Direct needs Wi-Fi on everywhere.
 */
public final class P2pCapability {
    /** Wi-Fi on with the channel open this long and still no Wi-Fi Direct: the unit has none. */
    public static final long PROOF_WINDOW_MS = 120_000;
    /** WifiP2pManager.P2P_UNSUPPORTED, the framework's answer on a device without Wi-Fi Direct. */
    public static final int REASON_UNSUPPORTED = 1;

    private P2pVerdict verdict = P2pVerdict.UNKNOWN;
    private boolean definitive;
    private long wifiOnSince = -1;
    private String why = "not decided yet";

    public P2pVerdict verdict() {
        return verdict;
    }

    /** NO that no later event can change: the receiver has nothing to wait for. */
    public boolean definitive() {
        return definitive;
    }

    public String why() {
        return why;
    }

    /** The framework has no Wi-Fi Direct (no feature, no manager, no channel). */
    public P2pVerdict noFramework(String what) {
        verdict = P2pVerdict.NO;
        definitive = true;
        why = what;
        return verdict;
    }

    /** A Wi-Fi Direct call succeeded. */
    public P2pVerdict proven(String how) {
        if (definitive) return verdict;
        verdict = P2pVerdict.YES;
        why = how;
        return verdict;
    }

    /** A Wi-Fi Direct call failed with this WifiP2pManager reason. */
    public P2pVerdict callFailed(int reason, String what) {
        if (reason == REASON_UNSUPPORTED && verdict != P2pVerdict.YES) return noFramework(what + ": unsupported");
        return verdict;
    }

    /** Wi-Fi switched on or off at {@code now} (elapsed ms). */
    public P2pVerdict wifi(boolean on, long now) {
        if (!on) wifiOnSince = -1;
        else if (wifiOnSince < 0) wifiOnSince = now;
        return verdict;
    }

    /** The proof window ran out: NO when Wi-Fi was on all along and nothing succeeded. */
    public P2pVerdict check(long now) {
        if (verdict == P2pVerdict.UNKNOWN && wifiOnSince >= 0 && now - wifiOnSince >= PROOF_WINDOW_MS) {
            verdict = P2pVerdict.NO;
            why = "Wi-Fi on for " + (now - wifiOnSince) / 1000 + " s and no Wi-Fi Direct call succeeded";
        }
        return verdict;
    }

    /** Milliseconds until {@link #check} can decide, or -1 when there is nothing to wait for. */
    public long nextCheckIn(long now) {
        if (verdict != P2pVerdict.UNKNOWN || wifiOnSince < 0) return -1;
        return Math.max(0, wifiOnSince + PROOF_WINDOW_MS - now);
    }

    /**
     * The control provider other Lumen apps find the receiver by (the quick menu tile, Lumen Home's tile): on for YES,
     * off for NO, null = leave it as it is (its state survives a reboot, so a unit decided once starts right).
     */
    public static Boolean providerOn(P2pVerdict v) {
        if (v == P2pVerdict.YES) return Boolean.TRUE;
        if (v == P2pVerdict.NO) return Boolean.FALSE;
        return null;
    }
}
