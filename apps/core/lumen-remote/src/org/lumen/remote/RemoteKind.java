package org.lumen.remote;

/** What a row of the remote table is (devices/_remotes/remotes.tsv, column kind). */
public enum RemoteKind {
    /** an XGIMI Bluetooth LE remote: paired by lumen-remote */
    BLE_REMOTE,
    /** the XGIMI music controller: a Bluetooth accessory, never paired automatically */
    BLE_MUSIC,
    /** a 2.4 GHz remote with its own USB receiver: nothing to pair */
    USB_DONGLE,
    /** the projector's IR receiver */
    IR,
    /** the projector's own keys */
    KEYPAD,
    /** a vendor reference layout of another device */
    GENERIC
}
