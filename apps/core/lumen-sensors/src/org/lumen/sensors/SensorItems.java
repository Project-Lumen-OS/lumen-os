// SPDX-License-Identifier: Apache-2.0
package org.lumen.sensors;

import java.util.EnumSet;
import java.util.Set;

/**
 * The HAL items of one model: devices/&lt;c&gt;/sensors.tsv, in the app as assets/lumen_sensors/items.tsv. One row per
 * item (column 1 = SensorItem token, then the vendor call and a note), '#' starts a comment. Pure.
 */
public final class SensorItems {
    public static final String ASSET = "lumen_sensors/items.tsv";

    private SensorItems() {}

    public static Set<SensorItem> parse(String tsv) {
        Set<SensorItem> out = EnumSet.noneOf(SensorItem.class);
        if (tsv == null) return out;
        for (String line : tsv.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("item\t")) continue;
            SensorItem i = SensorItem.of(t.split("\t", 2)[0].trim());
            if (i != null && i.hal) out.add(i);
        }
        return out;
    }

    /** What the card shows here: the model's HAL items the reader has, plus every system item. */
    public static Set<SensorItem> shown(Set<SensorItem> model, Set<SensorItem> reader) {
        Set<SensorItem> out = EnumSet.noneOf(SensorItem.class);
        for (SensorItem i : SensorItem.values()) if (!i.hal || (model.contains(i) && reader.contains(i))) out.add(i);
        return out;
    }
}
