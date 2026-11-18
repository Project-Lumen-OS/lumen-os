// SPDX-License-Identifier: Apache-2.0
package org.lumen.control.ucontrol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import org.lumen.control.KeyAction;

/**
 * Keys through the vendor daemon /vendor/bin/ucontrolMDNS (UDP 16735, the phone app's own key channel), which
 * presses them on its uinput keypad "ucontrol_key" (Vendor_3697_Product_0002, keys 0..255). The key then has the
 * meaning the model's Lumen key layout gives the remote's button, focus steps pass the projector app's XGIMI device
 * check, and no injection permission is needed. Text as the XGIMI app sends it: a click "KEYPRESSES:<scancode>",
 * a held key "KEYSSTATUS:<scancode>+1" and "KEYSSTATUS:<scancode>+0".
 */
public final class UcontrolKeys {
    /** The daemon's port (init.xgimi.common.rc service ucontrolMDNS, started unless ro.vendor.ucontrolMDNS.disable). */
    public static final int PORT = 16735;
    /** The keypad's highest key: the daemon registers keys 0..255 only (UI_SET_KEYBIT loop in ucontrolMDNS). */
    public static final int MAX_SCANCODE = 255;

    private final InetAddress host;
    private final int port;

    public UcontrolKeys(InetAddress host, int port) {
        this.host = host;
        this.port = port;
    }

    public static UcontrolKeys local() {
        return new UcontrolKeys(InetAddress.getLoopbackAddress(), PORT);
    }

    /** The daemon's text for a key, null for a scancode its keypad does not have. */
    public static String message(int scancode, KeyAction action) {
        if (scancode < 0 || scancode > MAX_SCANCODE) return null;
        switch (action) {
            case DOWN: return "KEYSSTATUS:" + scancode + "+1";
            case UP: return "KEYSSTATUS:" + scancode + "+0";
            default: return "KEYPRESSES:" + scancode;
        }
    }

    /** Sends the key; false when the scancode is not on the keypad or the packet did not leave. */
    public boolean press(int scancode, KeyAction action) {
        String m = message(scancode, action);
        if (m == null) return false;
        byte[] b = m.getBytes(StandardCharsets.US_ASCII);
        try (DatagramSocket s = new DatagramSocket()) {
            s.send(new DatagramPacket(b, b.length, host, port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
