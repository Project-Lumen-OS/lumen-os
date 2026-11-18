// SPDX-License-Identifier: Apache-2.0
package org.lumen.control.vcontrol;

/** Who sent a message, written the way stock names it in "phoneIp". */
public final class Peer {
    /** UDP: the host address ("<lan-ip>"); TCP: the socket address ("/<lan-ip>:51234"). */
    public final String address;
    public final boolean tcp;

    private Peer(String address, boolean tcp) {
        this.address = address;
        this.tcp = tcp;
    }

    public static Peer udp(String hostAddress) {
        return new Peer(hostAddress, false);
    }

    public static Peer tcp(String socketAddress) {
        return new Peer(socketAddress, true);
    }
}
