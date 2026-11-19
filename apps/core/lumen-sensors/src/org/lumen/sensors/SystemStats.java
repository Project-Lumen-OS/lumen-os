// SPDX-License-Identifier: Apache-2.0
package org.lumen.sensors;

import java.io.BufferedReader;
import java.io.FileReader;

/** The system items: kernel files only, no vendor call. Any thread; a file that cannot be read leaves its field empty. */
final class SystemStats {
    private static final String ZONE = "/sys/class/thermal/thermal_zone0/temp", STAT = "/proc/stat", MEM = "/proc/meminfo";
    private long lastBusy, lastTotal;

    void read(SensorValues v) {
        try {
            v.tempSoc = SensorValues.temp(Long.parseLong(first(ZONE).trim()) / 1000.0);
        } catch (Throwable t) {
            v.tempSoc = null;
        }
        try {
            v.cpuPercent = cpu(first(STAT));
        } catch (Throwable t) {
            v.cpuPercent = null;
        }
        try {
            v.ramPercent = ram();
        } catch (Throwable t) {
            v.ramPercent = null;
        }
    }

    /** "cpu  user nice system idle iowait irq softirq ...": busy share since the last read (null on the first). */
    Integer cpu(String line) {
        String[] f = line.trim().split("\\s+");
        long total = 0, idle = 0;
        for (int i = 1; i < f.length && i <= 8; i++) {
            long n = Long.parseLong(f[i]);
            total += n;
            if (i == 4 || i == 5) idle += n;
        }
        long busy = total - idle, dt = total - lastTotal, db = busy - lastBusy;
        boolean first = lastTotal == 0;
        lastTotal = total;
        lastBusy = busy;
        return first || dt <= 0 ? null : (int) Math.max(0, Math.min(100, Math.round(db * 100.0 / dt)));
    }

    private static Integer ram() throws Exception {
        long total = 0, avail = 0;
        try (BufferedReader r = new BufferedReader(new FileReader(MEM))) {
            for (String l; (l = r.readLine()) != null; ) {
                if (l.startsWith("MemTotal:")) total = kb(l);
                else if (l.startsWith("MemAvailable:")) avail = kb(l);
            }
        }
        return total <= 0 ? null : (int) Math.round((total - avail) * 100.0 / total);
    }

    private static long kb(String l) { return Long.parseLong(l.replaceAll("[^0-9]", "")); }

    private static String first(String path) throws Exception {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String l = r.readLine();
            return l == null ? "" : l;
        }
    }
}
