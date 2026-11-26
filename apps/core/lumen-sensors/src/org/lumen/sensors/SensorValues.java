// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.sensors;

/** What the last reads gave. A read that failed or answered out of range leaves its field empty: no row. Pure. */
public final class SensorValues {
    public Integer tempSystem, tempLight, tempSoc, cpuPercent, ramPercent;
    /** distance to the wall in mm */
    public Integer tofMm;
    /** accelerometer x, y, z in 1/1000 g and gyroscope x, y, z (raw counts), as the vendor reports them */
    public int[] accel, gyro;

    /** Takes the fields of {@code items} from {@code fresh} (an item asked and not answered is cleared). */
    public void merge(SensorValues fresh, Iterable<SensorItem> items) {
        for (SensorItem i : items) {
            switch (i) {
                case TEMP_SYSTEM: tempSystem = fresh.tempSystem; break;
                case TEMP_LIGHT: tempLight = fresh.tempLight; break;
                case TOF: tofMm = fresh.tofMm; break;
                case MOTION: accel = fresh.accel; gyro = fresh.gyro; break;
                case TEMP_SOC: tempSoc = fresh.tempSoc; break;
                case CPU: cpuPercent = fresh.cpuPercent; break;
                case RAM: ramPercent = fresh.ramPercent; break;
                default: break;
            }
        }
    }

    /** A plausible temperature in degrees C or null (0 is the vendor's "no sensor"). */
    public static Integer temp(double c) {
        return c > 0 && c < 150 ? (int) Math.round(c) : null;
    }

    /** A plausible distance in mm or null (the sensor reads 0 or a huge number without a wall in range). */
    public static Integer tof(int mm) {
        return mm > 0 && mm < 20_000 ? mm : null;
    }
}
