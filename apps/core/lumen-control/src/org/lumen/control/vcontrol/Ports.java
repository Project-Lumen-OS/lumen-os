// SPDX-License-Identifier: Apache-2.0
package org.lumen.control.vcontrol;

/** The ports of the stock phone remote (com.xgimi.vcontrol and the vendor daemon ucontrolMDNS). */
public final class Ports {
    /** UDP, phone broadcast "findDevice" (stock xgimi.com.udpserver.FindDevice). */
    public static final int DISCOVERY = 16752;
    /** UDP, JSON commands (stock UdpThread). */
    public static final int COMMANDS = 16750;
    /** UDP, where every answer to a UDP message goes, on the phone (stock UdpSender). */
    public static final int PHONE = 16751;
    /** TCP, framed JSON commands (stock com.xgimi.tcp.ServerThread DEFAULT_PORT). */
    public static final int TCP = 13145;
    /** UDP, "KEYPRESSES:"/"KEYSSTATUS:" keys of the vendor daemon /vendor/bin/ucontrolMDNS (not ours, runs in Lumen). */
    public static final int UCONTROL_KEYS = 16735;

    private Ports() {}
}
