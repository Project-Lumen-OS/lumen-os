// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.remote;

import java.util.Locale;

/**
 * One LE advertisement as {@link RemoteMatch} judges it (pure, host-tested): the name (advertised, else the cached
 * one, null when none), connectable, the flags AD (-1 when absent), the HID service 0x1812, the manufacturer data of
 * {@link RemoteMatch#ADV_COMPANY} (null when absent) and the RSSI for the log.
 */
public final class RemoteAdvert {
    public final String name;
    public final boolean connectable, hid;
    public final int flags, rssi;
    public final byte[] xgimiData;

    public RemoteAdvert(String name, boolean connectable, int flags, boolean hid, byte[] xgimiData, int rssi) {
        this.name = name;
        this.connectable = connectable;
        this.flags = flags;
        this.hid = hid;
        this.xgimiData = xgimiData;
        this.rssi = rssi;
    }

    /** LE Limited or General Discoverable in the flags AD. */
    public boolean discoverable() {
        return flags >= 0 && (flags & (RemoteMatch.FLAG_LE_LIMITED | RemoteMatch.FLAG_LE_GENERAL)) != 0;
    }

    /** LE Limited Discoverable (the Z9X remote's pairing mode). */
    public boolean limited() {
        return flags > 0 && (flags & RemoteMatch.FLAG_LE_LIMITED) != 0;
    }

    /** The stock RcPair pairing advertisement: the HID service and the remote pairing mark in the manufacturer data. */
    public boolean stockPairingMark() {
        return hid && startsWith(xgimiData, RemoteMatch.REMOTE_PAIRING_MARK);
    }

    static boolean startsWith(byte[] data, byte[] mark) {
        if (data == null || data.length < mark.length) return false;
        for (int i = 0; i < mark.length; i++) if (data[i] != mark[i]) return false;
        return true;
    }

    /** The log form: every field the match reads, the manufacturer bytes in hex. */
    @Override public String toString() {
        StringBuilder hex = new StringBuilder();
        if (xgimiData != null) for (byte b : xgimiData) hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return "name=" + name + " rssi=" + rssi + " flags=" + flags + " hid=" + hid + " connectable=" + connectable
                + " xgimi=" + (xgimiData == null ? "-" : hex);
    }
}
