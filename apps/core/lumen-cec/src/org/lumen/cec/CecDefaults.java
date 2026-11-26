// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.cec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The HDMI-CEC defaults of every Lumen image (devices/_image/cec_defaults.tsv, shipped as the app asset
 * {@value #ASSET}): the stock XGIMI value of each framework setting, per device where its stock differs, and the
 * switches a device puts back to stock once after the update to this table. The image's framework RRO carries the
 * same values (tools/devices/cec_defaults.py), so a unit that never stored a value has them already; the one-time
 * step is only for a value the old Lumen image may have stored by itself. Plain Java, host tested. Lines ('#' starts
 * a comment):
 * <pre>
 *   default  SETTING  VALUE          the stock value of an HdmiCecConfig setting
 *   device   DEVICE  SETTING  VALUE  the stock value on one device where it differs
 *   owner    SETTING  VALUE          the owner's value on every image where it differs from stock
 *   reset    DEVICE  SETTING         put the stock value back once on that device (the all-on step runs after it)
 * </pre>
 * The decision 2026-11-26: every HDMI-CEC switch ON on every family; units of an older table get them ON once
 * ({@link #allOnOnce}), after that a user's choice is never touched again.
 */
public final class CecDefaults {
    /** The one-time step of this version ran on a unit (a new reset line needs a new version). */
    public static final int VERSION = 1;
    public static final String ASSET = "lumen_cec/defaults.tsv";

    private final Map<CecSetting, String> defaults;
    private final Map<String, Map<CecSetting, String>> devices;
    private final Map<String, List<CecSetting>> resets;
    private final Map<CecSetting, String> owner;

    private CecDefaults(Map<CecSetting, String> defaults, Map<String, Map<CecSetting, String>> devices,
                        Map<String, List<CecSetting>> resets, Map<CecSetting, String> owner) {
        this.defaults = defaults;
        this.devices = devices;
        this.resets = resets;
        this.owner = owner;
    }

    /** Parses the table text. Throws IllegalArgumentException naming the bad line. */
    public static CecDefaults parse(String text) {
        Map<CecSetting, String> defaults = new EnumMap<>(CecSetting.class);
        Map<String, Map<CecSetting, String>> devices = new HashMap<>();
        Map<String, List<CecSetting>> resets = new HashMap<>();
        Map<CecSetting, String> owner = new EnumMap<>(CecSetting.class);
        String[] lines = text == null ? new String[0] : text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash);
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] f = line.split("\\s+");
            try {
                switch (f[0]) {
                    case "default":
                        need(f, 3);
                        put(defaults, setting(f[1]), f[2]);
                        break;
                    case "device":
                        need(f, 4);
                        put(devices.computeIfAbsent(f[1], k -> new EnumMap<>(CecSetting.class)), setting(f[2]), f[3]);
                        break;
                    case "owner":
                        need(f, 3);
                        put(owner, setting(f[1]), f[2]);
                        break;
                    case "reset":
                        need(f, 3);
                        CecSetting s = setting(f[2]);
                        if (!s.writable()) throw new IllegalArgumentException(s.key + " is no switch of the app");
                        List<CecSetting> l = resets.computeIfAbsent(f[1], k -> new ArrayList<>());
                        if (l.contains(s)) throw new IllegalArgumentException("reset " + f[1] + " " + s.key + " twice");
                        l.add(s);
                        break;
                    default:
                        throw new IllegalArgumentException("unknown directive " + f[0]);
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("defaults line " + (i + 1) + ": " + e.getMessage() + ": " + lines[i]);
            }
        }
        for (CecSetting s : CecSetting.values()) {
            if (!defaults.containsKey(s)) throw new IllegalArgumentException("no default for " + s.key);
        }
        return new CecDefaults(defaults, devices, resets, owner);
    }

    private static void need(String[] f, int n) {
        if (f.length != n) throw new IllegalArgumentException(n + " fields expected");
    }

    private static CecSetting setting(String key) {
        CecSetting s = CecSetting.of(key);
        if (s == null) throw new IllegalArgumentException("unknown setting " + key);
        return s;
    }

    private static void put(Map<CecSetting, String> m, CecSetting s, String v) {
        if (s.writable() && !"0".equals(v) && !"1".equals(v)) throw new IllegalArgumentException(s.key + " is 0 or 1");
        if (m.put(s, v) != null) throw new IllegalArgumentException(s.key + " given twice");
    }

    /** The stock value of a setting on a device (an HdmiCecConfig value: "0" / "1" for a switch). */
    public String stock(String device, CecSetting s) {
        Map<CecSetting, String> d = devices.get(device);
        String v = d == null ? null : d.get(s);
        return v != null ? v : defaults.get(s);
    }

    /** The value the image of a device ships: the owner's line, else the stock one. */
    public String value(String device, CecSetting s) {
        String v = owner.get(s);
        return v != null ? v : stock(device, s);
    }

    /** The switches the device puts back to stock once, in table order. */
    public List<CecSetting> resets(String device) {
        List<CecSetting> l = resets.get(device);
        return l == null ? Collections.emptyList() : Collections.unmodifiableList(l);
    }

    /** What the one-time step reads and writes on the unit (lumen-cec CecStockDefaults, a fake in the tests). */
    public interface Unit {
        /** The switch now; null = unknown (no CEC service yet). */
        Boolean on(CecSetting s);

        /** False when the framework refused it. */
        boolean set(CecSetting s, boolean on);

        /** The power policy switched it off for a while and will turn it on again (CecPolicy's guarded sleeps). */
        boolean heldOff(CecSetting s);

        /** That later switch-on is not wanted any more. */
        void dropHold(CecSetting s);

        /** The user switched this row of the Lumen HDMI-CEC page. */
        boolean userChose(CecRow row);
    }

    /**
     * The one-time step on a device: every reset switch whose value (counting a held switch-off as on) is not the
     * stock one gets the stock value, unless the user switched its row in the Lumen menu. True when it is complete;
     * false = a switch could not be read or written, run it again at the next start. Every decision goes to log.
     */
    public boolean apply(String device, Unit u, List<String> log) {
        boolean done = true;
        for (CecSetting s : resets(device)) {
            boolean stock = "1".equals(stock(device, s));
            if (s.row != null && u.userChose(s.row)) {
                log.add(s.key + ": kept, the user chose it in the menu");
                continue;
            }
            Boolean now = u.on(s);
            if (now == null) {
                log.add(s.key + ": unknown now, again at the next start");
                done = false;
                continue;
            }
            boolean held = u.heldOff(s);
            if ((now || held) == stock) {
                log.add(s.key + ": already stock (" + (stock ? 1 : 0) + ")");
                continue;
            }
            if (now != stock && !u.set(s, stock)) {
                log.add(s.key + ": refused, again at the next start");
                done = false;
                continue;
            }
            if (held) u.dropHold(s);
            log.add(s.key + ": " + (now || held ? 1 : 0) + " -> stock " + (stock ? 1 : 0));
        }
        return done;
    }

    /**
     * The switches of the HDMI-CEC page the user wants ON on every family (2026-11-26): master, turn on with an HDMI
     * device, turn off devices with the projector (and the projector follows the device), switch to a device when it
     * turns on, remote controls the device, soundbar sound (System Audio / ARC) and soundbar on with the projector.
     */
    public static final List<CecRow> ALL_ON = Collections.unmodifiableList(Arrays.asList(CecRow.MASTER, CecRow.WAKE,
            CecRow.SEND_STANDBY, CecRow.SWITCH, CecRow.REMOTE, CecRow.SOUNDBAR, CecRow.SOUNDBAR_ON));

    /** The switches of the page (lumen-cec CecStockDefaults over CecSettings, a fake in the tests). */
    public interface Rows {
        /** The switch now; null = unknown now (service not up), {@link #absent} = this projector has no such row. */
        Boolean on(CecRow r);

        boolean absent(CecRow r);

        /** False when it was refused. */
        boolean set(CecRow r, boolean on);

        /** The one-time step ran on this unit (a persisted flag, kept across reboots and updates). */
        boolean done();

        void markDone();
    }

    /**
     * Once per unit, on the first start of the build that brought the decision: every switch of
     * {@link #ALL_ON} goes ON, also one the user had switched off before; the flag is set only when every present
     * switch is ON, so an unknown or refused one is tried again at the next start. After that it never runs again: a
     * switch the user turns off stays off. True when it changed something.
     */
    public static boolean allOnOnce(Rows u, List<String> log) {
        if (u.done()) return false;
        boolean complete = true, changed = false;
        for (CecRow r : ALL_ON) {
            if (u.absent(r)) {
                log.add(r + ": not on this projector");
                continue;
            }
            Boolean now = u.on(r);
            if (now == null) {
                log.add(r + ": unknown now, again at the next start");
                complete = false;
            } else if (now) {
                log.add(r + ": already on");
            } else if (u.set(r, true)) {
                log.add(r + ": 0 -> 1 (owner default, once)");
                changed = true;
            } else {
                log.add(r + ": refused, again at the next start");
                complete = false;
            }
        }
        if (complete) u.markDone();
        return changed;
    }
}
