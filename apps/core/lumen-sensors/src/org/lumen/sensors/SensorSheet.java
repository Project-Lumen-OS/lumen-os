// SPDX-License-Identifier: Apache-2.0
package org.lumen.sensors;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** What the sensor card draws: sections and label / value rows, only rows with a value. Pure, host tested. */
public final class SensorSheet {
    /** Texts of the current language: lumen_sensors_&lt;key&gt; (Words.get formats the arguments). */
    public interface Words {
        String get(String key, Object... args);

        char decimal();
    }

    public static final class Row {
        public final boolean section;
        public final String key, label, value;

        Row(boolean section, String key, String label, String value) {
            this.section = section;
            this.key = key;
            this.label = label;
            this.value = value;
        }

        @Override public String toString() { return section ? "# " + label : label + " = " + value; }
    }

    public static final String SEC_TEMP = "sec_temp", SEC_POSITION = "sec_position", SEC_SYSTEM = "sec_system";

    public final List<Row> rows = new ArrayList<>();
    public final List<Row> cardRows = new ArrayList<>();

    private SensorSheet() {}

    public static SensorSheet build(SensorValues v, Set<SensorItem> items, Words w) {
        SensorSheet s = new SensorSheet();
        List<Row> temp = new ArrayList<>(), pos = new ArrayList<>(), sys = new ArrayList<>();
        add(temp, items, SensorItem.TEMP_SYSTEM, w, "temp_system", deg(v.tempSystem, w));
        add(temp, items, SensorItem.TEMP_LIGHT, w, "temp_light", deg(v.tempLight, w));
        add(temp, items, SensorItem.TEMP_SOC, w, "temp_soc", deg(v.tempSoc, w));
        add(pos, items, SensorItem.TOF, w, "tof", v.tofMm == null ? null : w.get("metres", dec(v.tofMm / 1000.0, 2, w)));
        add(pos, items, SensorItem.MOTION, w, "accel", axes(v.accel, 1000.0, 2, w));
        add(pos, items, SensorItem.MOTION, w, "gyro", axes(v.gyro, 1.0, 0, w));
        add(sys, items, SensorItem.CPU, w, "cpu", pct(v.cpuPercent, w));
        add(sys, items, SensorItem.RAM, w, "ram", pct(v.ramPercent, w));
        section(s, SEC_TEMP, temp, w);
        section(s, SEC_POSITION, pos, w);
        section(s, SEC_SYSTEM, sys, w);
        for (List<Row> part : java.util.Arrays.asList(temp, sys)) {
            for (Row r : part) if (cardKey(r.key)) s.cardRows.add(r);
        }
        return s;
    }

    private static boolean cardKey(String key) {
        for (SensorItem i : SensorItem.values()) if (i.card && i.token.equals(key)) return true;
        return false;
    }

    private static void add(List<Row> to, Set<SensorItem> items, SensorItem i, Words w, String key, String value) {
        if (value != null && items.contains(i)) to.add(new Row(false, i.card ? i.token : key, w.get(key), value));
    }

    private static void section(SensorSheet s, String key, List<Row> rows, Words w) {
        if (rows.isEmpty()) return;
        s.rows.add(new Row(true, key, w.get(key), null));
        s.rows.addAll(rows);
    }

    static String deg(Integer c, Words w) { return c == null ? null : w.get("degrees", c); }

    static String pct(Integer p, Words w) { return p == null ? null : w.get("percent", p); }

    /** "x 0,01  y -0,02  z 1,00": three axes, the units as the vendor reports them divided by {@code div}. */
    static String axes(int[] a, double div, int digits, Words w) {
        if (a == null || a.length < 3) return null;
        return "x " + dec(a[0] / div, digits, w) + "  y " + dec(a[1] / div, digits, w) + "  z " + dec(a[2] / div, digits, w);
    }

    static String dec(double v, int digits, Words w) {
        String s = String.format(java.util.Locale.ROOT, "%." + digits + "f", v);
        return s.replace('.', w.decimal()).replace('-', '\u2212');
    }
}
