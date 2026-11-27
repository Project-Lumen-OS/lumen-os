// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.audio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One sound output as the shared output menu names it (plain data, host tested): the kind (the AudioOutputKind names
 * every Lumen projector stores, SoundbarMode uses the same), its address, its real name (CEC OSD name of the soundbar,
 * the Bluetooth alias, the USB product name; null = the menu's own label), eARC, and whether it is connected now (a
 * paired Bluetooth device that is not connected is listed with connected false). {@link Scan} is what OutputScan read.
 */
public final class OutputDevice {
    public static final String SPEAKER = "SPEAKER", ARC = "ARC", BT = "BT", WIRED = "WIRED", USB = "USB", OPTICAL = "OPTICAL";

    public final String kind;
    public final String addr;
    public final String name;
    public final boolean earc;
    public final boolean connected;

    public OutputDevice(String kind, String addr, String name, boolean earc, boolean connected) {
        this.kind = kind;
        this.addr = addr == null ? "" : addr;
        this.name = name == null || name.trim().isEmpty() ? null : name.trim();
        this.earc = earc;
        this.connected = connected;
    }

    @Override public String toString() {
        return kind + (addr.isEmpty() ? "" : " " + addr) + (name == null ? "" : " '" + name + "'") + (earc ? " eARC" : "")
                + (connected ? "" : " offline");
    }

    /** One read of the outputs: connected ones, paired Bluetooth ones that are not connected, where media plays. */
    public static final class Scan {
        public final List<OutputDevice> connected;
        public final List<OutputDevice> offline;
        /** The kind and address media is routed to now (null kind: unknown). */
        public final String playingKind, playingAddr;
        /** The OSD name of the CEC audio system (the soundbar), or null. */
        public final String soundbarName;

        public Scan(List<OutputDevice> connected, List<OutputDevice> offline, String playingKind, String playingAddr,
                    String soundbarName) {
            this.connected = connected == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(connected));
            this.offline = offline == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(offline));
            this.playingKind = playingKind;
            this.playingAddr = playingAddr == null ? "" : playingAddr;
            this.soundbarName = soundbarName == null || soundbarName.trim().isEmpty() ? null : soundbarName.trim();
        }

        public static Scan empty() { return new Scan(null, null, null, null, null); }

        /** The first connected output of {@code kind}: the one at {@code addr} first. */
        public OutputDevice find(String kind, String addr) {
            OutputDevice first = null;
            for (OutputDevice d : connected) {
                if (!d.kind.equals(kind)) continue;
                if (addr != null && !addr.isEmpty() && addr.equalsIgnoreCase(d.addr)) return d;
                if (first == null) first = d;
            }
            return first;
        }

        @Override public String toString() {
            return "connected " + connected + ", offline " + offline + ", playing " + playingKind
                    + (playingAddr.isEmpty() ? "" : " " + playingAddr) + (soundbarName == null ? "" : ", soundbar '" + soundbarName + "'");
        }
    }
}
