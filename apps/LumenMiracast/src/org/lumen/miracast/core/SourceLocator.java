// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Finding the source's IP address when the projector is the Wi-Fi Direct group owner (it then knows
 * only the peer's MAC address; the peer got its IPv4 from the group's DHCP server).
 *
 * {@link #arpEntries} reads /proc/net/arp text; {@link #sweepOrder} lists the group subnet so the
 * caller can try the WFD control port host by host when no table answers.
 */
public final class SourceLocator {
    private SourceLocator() {}

    public static final class Entry {
        public final String ip, mac, iface;

        Entry(String ip, String mac, String iface) {
            this.ip = ip;
            this.mac = mac;
            this.iface = iface;
        }
    }

    /**
     * Complete entries (flags 0x2) of /proc/net/arp whose device name starts with {@code ifacePrefix}
     * (for example "p2p"), skipping incomplete 00:00:00:00:00:00 rows.
     */
    public static List<Entry> arpEntries(String procNetArp, String ifacePrefix) {
        List<Entry> out = new ArrayList<>();
        if (procNetArp == null) return out;
        for (String line : procNetArp.split("\n")) {
            String[] t = line.trim().split("\\s+");
            // IP address  HW type  Flags  HW address  Mask  Device
            if (t.length < 6 || !t[0].matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) continue;
            int flags;
            try {
                flags = Integer.decode(t[2]);
            } catch (NumberFormatException e) {
                continue;
            }
            if ((flags & 0x2) == 0 || t[3].equals("00:00:00:00:00:00")) continue;
            if (ifacePrefix != null && !t[5].startsWith(ifacePrefix)) continue;
            out.add(new Entry(t[0], t[3].toLowerCase(Locale.ROOT), t[5]));
        }
        return out;
    }

    /**
     * Host addresses of the /24 around {@code ownIp} without ownIp: .1 first (a group owner), then .2
     * to .254 in order. The caller probes the WFD control port on each.
     */
    public static List<String> sweepOrder(String ownIp) {
        List<String> out = new ArrayList<>();
        String[] o = ownIp.split("\\.");
        if (o.length != 4) return out;
        String prefix = o[0] + "." + o[1] + "." + o[2] + ".";
        int own = Integer.parseInt(o[3]);
        for (int i = 2; i <= 254; i++) {
            if (i != own) out.add(prefix + i);
        }
        if (own != 1) out.add(0, prefix + 1);
        return out;
    }

    /** Lower-case MAC with the locally administered bit cleared: P2P interface vs device address often differ only there. */
    public static String macKey(String mac) {
        if (mac == null) return "";
        String m = mac.toLowerCase(Locale.ROOT);
        if (m.length() != 17) return m;
        int first = Integer.parseInt(m.substring(0, 2), 16) & ~0x02;
        return String.format(Locale.ROOT, "%02x", first) + m.substring(2);
    }
}
