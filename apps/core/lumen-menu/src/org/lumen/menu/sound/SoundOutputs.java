// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu.sound;

import org.lumen.audio.OutputDevice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The sound output page of every projector (owner variant C, 2026-11-18, Apple TV / Google TV style): a card with the
 * output the sound plays on now, then Auto and the outputs by their real names, the paired Bluetooth devices that are
 * not connected greyed with a Connect action, and the audio format at the bottom. Plain Java, host tested
 * (test/SoundOutputsTest.java); the view is OutputSheet, the reads are lumen-audio OutputScan.
 *
 * <p>Model data: {@code model} is the stock output list of the projector (menu.json sound.output options, stock
 * voice_output), so a model never shows an output its stock lacks. {@code offered} is what the device's own output
 * choice lists now (the soundbar while it is known, the stored choice even while its device is missing), null = the
 * outputs connected now. {@code jackSensed} false (the HB kernel senses no 3.5 mm plug, menu.json "jack": "manual",
 * MenuProfile.jackSensed):
 * the headphones row is always listed, as stock lists it, and says it is chosen by hand.
 *
 * <p>Rows: Auto first, then the model's order. Exactly one choice row is selected (the stored choice, Auto when it
 * is not listed). The playing mark goes on the one row media is routed to now, never on Auto or an offline row. The
 * card names that row and says whether the system picked it (Auto, or the choice is not reachable) or the user did.
 */
public final class SoundOutputs {
    /** Choice tokens (menu.json sound.output options). */
    public static final String AUTO = "auto", SPEAKER = "speaker", ARC = "arc", BT = "bt", WIRED = "wired", USB = "usb";
    /** How a row's output is reached (the line under its name). */
    public static final String VIA_ARC = "arc", VIA_EARC = "earc", VIA_BT = "bt", VIA_JACK = "jack", VIA_USB = "usb";
    /** The state on that line: connected now, a paired device that is not connected, headphones chosen by hand. */
    public static final String CONNECTED = "connected", OFFLINE = "offline", BY_HAND = "by_hand";

    private static final List<String> KNOWN = Collections.unmodifiableList(java.util.Arrays.asList(AUTO, SPEAKER, ARC, BT, WIRED, USB));

    /** One row of the page. */
    public static final class Row {
        /** The choice token, null for an offline device (its only action is Connect). */
        public final String token;
        public final String kind;
        /** The device's own name, null = the module's label of the kind. */
        public final String name;
        public final String addr;
        /** VIA_* or null (no line), CONNECTED / OFFLINE / BY_HAND or null. */
        public final String via, state;
        public final boolean selected, playing;

        Row(String token, String kind, String name, String addr, String via, String state, boolean selected, boolean playing) {
            this.token = token;
            this.kind = kind;
            this.name = name;
            this.addr = addr == null ? "" : addr;
            this.via = via;
            this.state = state;
            this.selected = selected;
            this.playing = playing;
        }

        public boolean offline() { return token == null; }

        Row with(boolean sel, boolean play) { return new Row(token, kind, name, addr, via, state, sel, play); }

        @Override public String toString() {
            return (token == null ? "(" + kind + ")" : token) + (name == null ? "" : " '" + name + "'")
                    + (via == null ? "" : " " + via) + (state == null ? "" : " " + state)
                    + (selected ? " [x]" : "") + (playing ? " playing" : "");
        }
    }

    public final List<Row> rows;
    /** Where the sound goes now (the playing row), null when unknown. */
    public final Row now;
    /** True: the system picked {@link #now} (Auto, or the chosen output is not reachable now). */
    public final boolean nowAuto;
    /** The audio format token on the card (only while the sound goes to HDMI ARC / eARC), else null. */
    public final String nowFormat;

    private SoundOutputs(List<Row> rows, Row now, boolean nowAuto, String nowFormat) {
        this.rows = Collections.unmodifiableList(rows);
        this.now = now;
        this.nowAuto = nowAuto;
        this.nowFormat = nowFormat;
    }

    /** The selected choice row (never null for a page built by {@link #of}). */
    public Row selected() {
        for (Row r : rows) if (r.selected) return r;
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** The kind of a choice token: a device's runtime token may carry a number (bt2). */
    public static String kindOf(String token) {
        if (token == null) return null;
        String k = token.toLowerCase(Locale.ROOT).replaceAll("[0-9]+$", "");
        return KNOWN.contains(k) ? k : null;
    }

    /** The token of an OutputDevice kind (OutputDevice.ARC -&gt; arc). */
    static String tokenOf(String deviceKind) { return deviceKind == null ? null : kindOf(deviceKind); }

    /**
     * The page. model: the stock output tokens of the projector; offered: the choice tokens the device lists now (null
     * = the outputs connected now); chosen: the stored choice token (null or empty = Auto); format: the audio format
     * token read back (null = none).
     */
    public static SoundOutputs of(List<String> model, List<String> offered, String chosen, OutputDevice.Scan scan,
                                  boolean jackSensed, String format) {
        if (scan == null) scan = OutputDevice.Scan.empty();
        List<String> kinds = new ArrayList<>();
        if (model != null) for (String t : model) { String k = kindOf(t); if (k != null && !kinds.contains(k)) kinds.add(k); }
        if (!kinds.contains(AUTO)) kinds.add(0, AUTO);
        String chosenKind = kindOf(chosen);
        if (chosenKind == null || !kinds.contains(chosenKind)) chosenKind = AUTO;
        Set<String> listed = listed(kinds, offered, scan, jackSensed);
        listed.add(chosenKind);
        java.util.Map<String, String> tokens = new java.util.HashMap<>();     // the device's own token of each kind
        if (offered != null) for (String t : offered) { String k = kindOf(t); if (k != null && !tokens.containsKey(k)) tokens.put(k, t); }
        String playing = tokenOf(scan.playingKind);

        List<Row> rows = new ArrayList<>();
        for (String k : kinds) {
            if (!AUTO.equals(k) && !SPEAKER.equals(k) && !listed.contains(k)) {
                if (BT.equals(k)) addOffline(rows, scan);
                continue;
            }
            String tok = tokens.containsKey(k) ? tokens.get(k) : k;
            switch (k) {
                case AUTO:
                case SPEAKER:
                    rows.add(new Row(tok, k, null, "", null, null, false, false));
                    break;
                case ARC: {
                    OutputDevice d = scan.find(OutputDevice.ARC, null);
                    rows.add(new Row(tok, k, scan.soundbarName, d == null ? "" : d.addr, d != null && d.earc ? VIA_EARC : VIA_ARC,
                            d != null ? CONNECTED : null, false, false));
                    break;
                }
                case BT: {
                    OutputDevice d = BT.equals(playing) ? scan.find(OutputDevice.BT, scan.playingAddr) : scan.find(OutputDevice.BT, null);
                    rows.add(new Row(tok, k, d == null ? null : d.name, d == null ? "" : d.addr, VIA_BT, d != null ? CONNECTED : null, false, false));
                    addOffline(rows, scan);
                    break;
                }
                case WIRED: {
                    OutputDevice d = scan.find(OutputDevice.WIRED, null);
                    rows.add(new Row(tok, k, null, d == null ? "" : d.addr, VIA_JACK, jackSensed ? null : BY_HAND, false, false));
                    break;
                }
                default: {                                                  // USB
                    OutputDevice d = scan.find(OutputDevice.USB, null);
                    rows.add(new Row(tok, k, d == null ? null : d.name, d == null ? "" : d.addr, VIA_USB, d != null ? CONNECTED : null, false, false));
                }
            }
        }
        Row now = null;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            boolean sel = !r.offline() && chosenKind.equals(r.kind);
            boolean play = now == null && !r.offline() && !AUTO.equals(r.kind) && r.kind.equals(playing);
            if (sel || play) rows.set(i, r = r.with(sel, play));
            if (play) now = r;
        }
        boolean auto = AUTO.equals(chosenKind) || now == null || !now.selected;
        String fmt = now != null && ARC.equals(now.kind) ? format : null;
        return new SoundOutputs(rows, now, auto, fmt);
    }

    /** The kinds with a row besides Auto and Speaker: the device's list, else what is connected now. */
    private static Set<String> listed(List<String> kinds, List<String> offered, OutputDevice.Scan scan, boolean jackSensed) {
        Set<String> out = new LinkedHashSet<>();
        if (offered != null) {
            for (String t : offered) { String k = kindOf(t); if (k != null) out.add(k); }
        } else {
            for (OutputDevice d : scan.connected) out.add(tokenOf(d.kind));
            if (scan.soundbarName != null) out.add(ARC);
        }
        if (!jackSensed && kinds.contains(WIRED)) out.add(WIRED);      // stock lists the jack it cannot sense
        out.retainAll(kinds);
        return out;
    }

    private static void addOffline(List<Row> rows, OutputDevice.Scan scan) {
        for (OutputDevice d : scan.offline) {
            if (OutputDevice.BT.equals(d.kind)) rows.add(new Row(null, BT, d.name, d.addr, VIA_BT, OFFLINE, false, false));
        }
    }

    @Override public String toString() {
        return rows + " now " + now + (nowAuto ? " auto" : " by hand") + (nowFormat == null ? "" : " " + nowFormat);
    }
}
