// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

/**
 * The network mark of Home's corner (4PDA 2026-11-22: Wi-Fi with its strength, the cable, nothing, and a key beside it
 * while a VPN is on). Pure: the default network's transports and the Wi-Fi level give the icon. Ethernet wins over
 * Wi-Fi (the cable carries the traffic when both are up), no network shows the crossed Wi-Fi mark.
 */
public final class NetIcon {
    public static final int OFF = 0, WIFI_1 = 1, WIFI_2 = 2, WIFI_3 = 3, WIFI_4 = 4, ETHERNET = 5;

    private NetIcon() {}

    /**
     * @param validated the default network has internet (validated); a network without it shows the weakest bars
     * @param level     Wi-Fi strength 0..4 (WifiManager.calculateSignalLevel), below 0 when unknown
     */
    public static int of(boolean connected, boolean wifi, boolean ethernet, boolean validated, int level) {
        if (!connected) return OFF;
        if (ethernet) return ETHERNET;
        if (!wifi) return OFF;
        if (!validated) return WIFI_1;
        if (level < 0) return WIFI_4;
        return Math.max(WIFI_1, Math.min(WIFI_4, level));
    }

    /** The key mark: a VPN carries the default network and the network itself is up. */
    public static boolean vpn(boolean connected, boolean vpn) {
        return connected && vpn;
    }
}
