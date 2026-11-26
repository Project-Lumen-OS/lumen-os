// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.sensors;

/**
 * One value of the sensor card. A HAL item costs one vendor call (SensorBudget counts it, never more than two a second
 * and none while the card is hidden), a system item reads a kernel file. Which HAL items a model has is data:
 * devices/&lt;c&gt;/sensors.tsv (assets/lumen_sensors/items.tsv).
 */
public enum SensorItem {
    /** temperature inside the projector, degrees C (Z9X / Play 6 IGmpf 438, HB / X50 TEMPERATURE 6) */
    TEMP_SYSTEM("temp_system", true, true, 4_000L),
    /** light source temperature, degrees C (Z9X LED sensor 5 of IGmpf 439, X50 family red laser sensor 5 of TEMPERATURE 7) */
    TEMP_LIGHT("temp_light", true, true, 4_000L),
    /** distance to the wall in mm (Z9X IGmpf 279): one active measurement of about 0.6 s, so at most once a minute */
    TOF("tof", true, false, 60_000L),
    /** accelerometer and gyroscope (Z9X IGmpf 584): the projector's position */
    MOTION("motion", true, false, 10_000L),
    /** processor temperature from the kernel's first thermal zone: no vendor call */
    TEMP_SOC("temp_soc", false, true, 2_000L),
    /** processor load from /proc/stat: no vendor call */
    CPU("cpu", false, true, 2_000L),
    /** memory in use from /proc/meminfo: no vendor call */
    RAM("ram", false, false, 2_000L);

    public final String token;
    /** true = one vendor HAL call through the app's own client */
    public final boolean hal;
    /** true = on the small card too, false = only in the full sheet */
    public final boolean card;
    /** how often it is read while shown */
    public final long periodMs;

    SensorItem(String token, boolean hal, boolean card, long periodMs) {
        this.token = token;
        this.hal = hal;
        this.card = card;
        this.periodMs = periodMs;
    }

    public static SensorItem of(String token) {
        for (SensorItem i : values()) if (i.token.equals(token)) return i;
        return null;
    }
}
