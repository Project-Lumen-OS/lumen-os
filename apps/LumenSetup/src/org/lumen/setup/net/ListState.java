// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.setup.net;

/**
 * What the Wi-Fi list page shows, plain Java for host tests: Wi-Fi still switching on, the first scan
 * running, "Сети не найдены" (a scan came back empty: the empty block with "Искать снова" focused, the list keeps
 * scanning by itself every 10 s), or the list under "Сети Wi-Fi рядом".
 */
public final class ListState {
    public static final int WIFI_OFF = 0, SCANNING = 1, NONE = 2, LIST = 3;

    private ListState() {}

    public static int of(boolean wifiEnabled, int networks, boolean scanSeen) {
        if (networks > 0) return LIST;
        if (!wifiEnabled) return WIFI_OFF;
        return scanSeen ? NONE : SCANNING;
    }

    /** Rows the page focuses by itself, never the scrolling container. */
    public static final int FOCUS_FIRST_NETWORK = 0, FOCUS_SEARCH_AGAIN = 1, FOCUS_ADD_MANUALLY = 2;

    /**
     * The first actionable row of a state: the first network of the list, "Search again" when nothing was found,
     * "Add network manually" while Wi-Fi switches on or the first scan runs.
     */
    public static int focus(int state) {
        return state == LIST ? FOCUS_FIRST_NETWORK : state == NONE ? FOCUS_SEARCH_AGAIN : FOCUS_ADD_MANUALLY;
    }
}
