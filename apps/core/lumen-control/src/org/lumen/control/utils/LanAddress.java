// SPDX-License-Identifier: Apache-2.0
package org.lumen.control.utils;

import java.net.Inet6Address;
import java.net.InetAddress;

/** Control inputs answer the local network only: loopback, link-local, private IPv4 and IPv6 unique local. */
public final class LanAddress {
    private LanAddress() {}

    public static boolean isLan(InetAddress a) {
        if (a == null) return false;
        if (a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()) return true;
        return a instanceof Inet6Address && (a.getAddress()[0] & 0xFE) == 0xFC;
    }
}
