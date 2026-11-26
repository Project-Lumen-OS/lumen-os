// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.sensors;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Host test of the sensor card's pure parts: items, the HAL budget, the rows and the system reads' parser. */
public final class SensorTest {
    private static int fails, runs;

    private static void ok(String what, boolean cond) {
        runs++;
        if (!cond) { fails++; System.out.println("FAIL " + what); }
    }

    private static final SensorSheet.Words W = new SensorSheet.Words() {
        @Override public String get(String key, Object... args) {
            switch (key) {
                case "degrees": return args[0] + " °C";
                case "percent": return args[0] + " %";
                case "metres": return args[0] + " m";
                default: return key;
            }
        }

        @Override public char decimal() { return ','; }
    };

    public static void main(String[] a) {
        String tsv = "# c\nitem\tcall\tnote\ntemp_system\tIGmpf 438\tx\ntof\tIGmpf 279\tx\ncpu\t-\tnot a HAL item\nbogus\t-\t-\n";
        Set<SensorItem> model = SensorItems.parse(tsv);
        ok("parse: HAL items only", model.equals(EnumSet.of(SensorItem.TEMP_SYSTEM, SensorItem.TOF)));
        Set<SensorItem> shown = SensorItems.shown(model, EnumSet.of(SensorItem.TEMP_SYSTEM, SensorItem.MOTION));
        ok("shown: model and reader, plus system items", shown.equals(EnumSet.of(SensorItem.TEMP_SYSTEM,
                SensorItem.TEMP_SOC, SensorItem.CPU, SensorItem.RAM)));

        SensorBudget b = new SensorBudget(EnumSet.of(SensorItem.TEMP_SYSTEM, SensorItem.TEMP_LIGHT, SensorItem.TOF,
                SensorItem.MOTION));
        ok("hidden: no call", b.due(0, false, true).isEmpty());
        int calls = 0, tof = 0;
        for (long t = 0; t < 120_000; t += 500) {
            List<SensorItem> d = b.due(t, true, true);
            ok("at most 2 calls per tick", d.size() <= SensorBudget.MAX_PER_SECOND);
            calls += d.size();
            for (SensorItem i : d) if (i == SensorItem.TOF) tof++;
        }
        ok("distance at most once a minute over 2 min: " + tof, tof >= 1 && tof <= 2);
        ok("at most 2 calls a second over 2 min: " + calls, calls <= 2 * 120);
        SensorBudget c = new SensorBudget(EnumSet.of(SensorItem.TEMP_SYSTEM, SensorItem.TOF, SensorItem.MOTION));
        List<SensorItem> card = c.due(0, true, false);
        ok("small card: only card items", card.equals(Arrays.asList(SensorItem.TEMP_SYSTEM)));
        SensorBudget r = new SensorBudget(EnumSet.of(SensorItem.TOF));
        ok("first open reads the distance", r.due(0, true, true).contains(SensorItem.TOF));
        r.reset();
        ok("reopened within a minute: no second distance read", r.due(5_000, true, true).isEmpty());
        ok("a minute later it is due", r.due(61_000, true, true).contains(SensorItem.TOF));

        SensorValues v = new SensorValues();
        v.tempSystem = 41;
        v.tofMm = 2350;
        v.accel = new int[] {10, -20, 1000};
        v.cpuPercent = 23;
        SensorSheet s = SensorSheet.build(v, EnumSet.allOf(SensorItem.class), W);
        ok("rows: " + s.rows, s.rows.toString().equals("[# sec_temp, temp_system = 41 °C, # sec_position, "
                + "tof = 2,35 m, accel = x 0,01  y \u22120,02  z 1,00, # sec_system, cpu = 23 %]"));
        ok("card rows: " + s.cardRows, s.cardRows.toString().equals("[temp_system = 41 °C, cpu = 23 %]"));
        SensorSheet only = SensorSheet.build(v, EnumSet.of(SensorItem.TEMP_SYSTEM), W);
        ok("a model without the item has no row", only.rows.size() == 2);
        ok("temp range", SensorValues.temp(0) == null && SensorValues.temp(151) == null && SensorValues.temp(47.4) == 47);
        ok("tof range", SensorValues.tof(0) == null && SensorValues.tof(25_000) == null && SensorValues.tof(1200) == 1200);
        SensorValues m = new SensorValues();
        m.tempSystem = 50;
        SensorValues fresh = new SensorValues();
        m.merge(fresh, EnumSet.of(SensorItem.TEMP_SYSTEM));
        ok("a failed read clears the old value", m.tempSystem == null);

        SystemStats st = new SystemStats();
        ok("first cpu line has no share", st.cpu("cpu  100 0 100 800 0 0 0 0 0 0") == null);
        Integer p = st.cpu("cpu  150 0 150 900 0 0 0 0 0 0");
        ok("cpu share 50 %: " + p, p != null && p == 50);
        System.out.println((fails == 0 ? "ok   " : "FAIL ") + "SensorTest " + (runs - fails) + "/" + runs);
        System.exit(fails == 0 ? 0 : 1);
    }
}
