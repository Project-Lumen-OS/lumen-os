// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.system;

/**
 * Bluetooth absolute volume, the stock switch of every model (Bluetooth and accessories > more_bluetooth_switch,
 * newsettings MoreRepository.setBlueToothVolume): on, the projector and the Bluetooth speaker keep one volume (AVRCP
 * absolute volume); off (stock default, vendor build.prop persist.bluetooth.disableabsvol=true), the projector drives
 * the level itself. It is persist.bluetooth.disableabsvol, which the Bluetooth stack reads when a device connects, so
 * it applies from the next connection, as on stock.
 *
 * <p>The images that force the stock default at early-boot (hb_bt.rc, z9x_base.rc, play6_base.rc: units of Lumen 1.0.6
 * kept a stored false) keep the user's choice through {@link #CHOICE}: they set the default unless it is 1.
 */
public final class BtAbsVolume {
    static final String PROP = "persist.bluetooth.disableabsvol", CHOICE = "persist.lumen.bt_absvol";

    private BtAbsVolume() { }

    public static boolean on() { return on(Props.get(PROP)); }

    /** Stock reads the property as a boolean with true as the default: on only for an explicit false. */
    static boolean on(String disableAbsVol) { return "false".equals(disableAbsVol); }

    /** Stores the choice and the property, then reads it back. */
    public static boolean set(boolean on) {
        Props.set(CHOICE, on ? "1" : "0");
        Props.set(PROP, on ? "false" : "true");
        return on() == on;
    }
}
