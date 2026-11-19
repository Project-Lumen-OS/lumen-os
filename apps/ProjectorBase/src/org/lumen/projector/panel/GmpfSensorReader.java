// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import android.util.Log;

import org.lumen.projector.Hal;
import org.lumen.projector.hal.Gmpf2Client;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.sensors.SensorItem;
import org.lumen.sensors.SensorReader;
import org.lumen.sensors.SensorValues;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The sensor card's reader of the Z9X (and, through the recipe, the Z9X HB) over the app's own gmpf client: IGmpf 438
 * inside, 439 LED sensor 5, 279 distance to the wall, 584 motion sensor (devices/&lt;c&gt;/sensors.tsv says which a model
 * has; the HB's motion reads stay behind their own gate). Never 194, 275 or IGmpf2 252.
 */
public final class GmpfSensorReader implements SensorReader {
    private static final String TAG = "LumenSensors";
    private static final Set<SensorItem> ITEMS = Collections.unmodifiableSet(EnumSet.of(SensorItem.TEMP_SYSTEM,
            SensorItem.TEMP_LIGHT, SensorItem.TOF, SensorItem.MOTION));
    /** The LED board sensor of 439, as the signal panel (GmpfSignalReader.LED_SENSOR). */
    private static final int LED_SENSOR = 5;
    /** getMotionDetection: {acc x, y, z, gyro x, y, z, type} */
    private static final int ACC = 0, GYRO = 3, MOTION_FIELDS = 6;

    @Override public Set<SensorItem> items() { return ITEMS; }

    @Override public void read(List<SensorItem> items, Consumer<SensorValues> cb) {
        Hal.query((g, g2) -> readOnHal(g, g2, items), cb::accept);
    }

    static SensorValues readOnHal(GmpfClient g, Gmpf2Client g2, List<SensorItem> items) {
        SensorValues v = new SensorValues();
        for (SensorItem i : items) {
            try {
                switch (i) {
                    case TEMP_SYSTEM: v.tempSystem = SensorValues.temp(g.getSystemTemperature()); break;
                    case TEMP_LIGHT: v.tempLight = SensorValues.temp(g.getSensorTemperature(LED_SENSOR)); break;
                    case TOF: v.tofMm = SensorValues.tof(g.getTofDistance()); break;
                    case MOTION: {
                        int[] m = g.getMotionDetection();
                        if (m != null && m.length >= MOTION_FIELDS) {
                            v.accel = new int[] {m[ACC], m[ACC + 1], m[ACC + 2]};
                            v.gyro = new int[] {m[GYRO], m[GYRO + 1], m[GYRO + 2]};
                        }
                        break;
                    }
                    default: break;
                }
            } catch (Throwable t) {
                Log.d(TAG, i + ": " + t);
            }
        }
        return v;
    }
}
