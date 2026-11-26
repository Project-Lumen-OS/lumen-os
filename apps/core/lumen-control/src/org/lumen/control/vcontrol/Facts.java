// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.control.vcontrol;

/** What the projector says about itself to the phone; the host reads it from the running system. */
public interface Facts {
    /** The projector's LAN IPv4 address. */
    String ip();

    /** The device name the user sees (Android device name). */
    String name();

    /** Stock product name (vendor prop ro.xgimi.product.name, e.g. Product_dianapro). */
    String productName();

    /** A stable id of this projector (Lumen: a keyed hash of the serial, never the serial itself). */
    String pid();

    /** Bluetooth address, null when unknown. */
    String bluetoothMac();

    /** Network MAC address, null when unknown. */
    String mac();
}
