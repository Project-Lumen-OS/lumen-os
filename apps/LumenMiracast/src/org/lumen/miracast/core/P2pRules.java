// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

/**
 * Wi-Fi Direct decisions of the receiver that do not need Android (host tested). The numbers mirror
 * the framework constants (android.net.wifi.WpsInfo, WifiP2pManager.ExternalApproverRequestListener)
 * so the core stays free of android.* imports.
 */
public final class P2pRules {
    private P2pRules() {}

    // WpsInfo.setup
    public static final int WPS_PBC = 0, WPS_DISPLAY = 1, WPS_KEYPAD = 2, WPS_LABEL = 3, WPS_INVALID = 4;

    // ExternalApproverRequestListener.APPROVER_DETACH_REASON_*
    public static final int DETACH_REMOVE = 0, DETACH_FAILURE = 1, DETACH_REPLACE = 2, DETACH_CLOSE = 3;

    /**
     * Attach the broadcast-address approver again after it was detached?
     *
     * Android 14 WifiP2pServiceImpl.handleSetConnectionResult() ends EVERY answer (accept, reject,
     * defer) with detachExternalApproverFromPeer(), which removes the wildcard (broadcast address)
     * approver when no per-peer one exists and reports REMOVE. Without attaching it again, the second
     * connection would get the framework's own dialog and the receiver's "ask" setting would no
     * longer apply. So: REMOVE that we did not ask for, and FAILURE (a transient add failure), attach
     * again; CLOSE (our channel is gone, the channel-lost path reopens it) and REPLACE (another app
     * took the wildcard on purpose) do not.
     */
    public static boolean reattach(int reason, boolean running, boolean removedByUs) {
        if (!running) return false;
        switch (reason) {
            case DETACH_REMOVE:
                return !removedByUs;
            case DETACH_FAILURE:
                return true;
            default:
                return false;
        }
    }

    /**
     * Who handles a connection request: our own Allow / Decline card for push button (what phones
     * and Windows use by default), the framework's dialog for the PIN methods (it has the PIN entry
     * and PIN display and drives the supplicant). An unknown or missing method counts as push button,
     * as the framework's default WifiP2pConfig does.
     */
    public static boolean ownPrompt(int wpsSetup) {
        return wpsSetup != WPS_KEYPAD && wpsSetup != WPS_DISPLAY;
    }

    public static String wpsName(int wpsSetup) {
        switch (wpsSetup) {
            case WPS_PBC: return "pbc";
            case WPS_DISPLAY: return "pin-display";
            case WPS_KEYPAD: return "pin-keypad";
            case WPS_LABEL: return "label";
            default: return "wps" + wpsSetup;
        }
    }

    public static String detachName(int reason) {
        switch (reason) {
            case DETACH_REMOVE: return "remove";
            case DETACH_FAILURE: return "failure";
            case DETACH_REPLACE: return "replace";
            case DETACH_CLOSE: return "close";
            default: return "reason" + reason;
        }
    }

    /**
     * The sender's RTSP control port: the one from its WFD information element (seen in discovery
     * or in the connection request) when valid, else the WFD default 7236.
     */
    public static int controlPort(int fromGroup, int fromRequest) {
        if (fromGroup > 0 && fromGroup < 65536) return fromGroup;
        if (fromRequest > 0 && fromRequest < 65536) return fromRequest;
        return 7236;
    }
}
