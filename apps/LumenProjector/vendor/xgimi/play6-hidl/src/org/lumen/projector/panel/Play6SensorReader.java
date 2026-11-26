// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.util.Log;

import org.lumen.projector.Hal;
import org.lumen.projector.hal.Play6Gmpf;
import org.lumen.sensors.SensorItem;
import org.lumen.sensors.SensorReader;
import org.lumen.sensors.SensorValues;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** The sensor card's reader of the Play 6 over its HIDL facade: IGmpf 438, the temperature inside (stock Device info). */
public final class Play6SensorReader implements SensorReader {
    private static final String TAG = "LumenSensors";
    private static final Set<SensorItem> ITEMS = Collections.unmodifiableSet(EnumSet.of(SensorItem.TEMP_SYSTEM));

    @Override public Set<SensorItem> items() { return ITEMS; }

    @Override public void read(List<SensorItem> items, Consumer<SensorValues> cb) {
        Hal.query((g, g2) -> readOnHal(g, items), cb::accept);
    }

    static SensorValues readOnHal(Play6Gmpf g, List<SensorItem> items) {
        SensorValues v = new SensorValues();
        if (items.contains(SensorItem.TEMP_SYSTEM)) {
            try {
                v.tempSystem = SensorValues.temp(g.getSystemTemperature());
            } catch (Throwable t) {
                Log.d(TAG, "438: " + t);
            }
        }
        return v;
    }
}
