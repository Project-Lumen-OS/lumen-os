// SPDX-License-Identifier: Apache-2.0
package org.lumen.sensors;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which HAL items to read now. Every vendor call of every app goes through one vendor thread, so the card spends at
 * most {@link #MAX_PER_SECOND} calls a second and none while hidden; the small card reads only its own items, the full
 * sheet adds the rest. The longest overdue goes first. Pure, host tested.
 */
public final class SensorBudget {
    public static final int MAX_PER_SECOND = 2;
    public static final long WINDOW_MS = 1_000L;

    private final Set<SensorItem> items;
    private final Map<SensorItem, Long> last = new EnumMap<>(SensorItem.class);
    private final List<Long> spent = new ArrayList<>();

    public SensorBudget(Set<SensorItem> items) { this.items = items; }

    /** The card opened again: read everything soon, but keep the slow distance read's last time. */
    public void reset() {
        Long tof = last.get(SensorItem.TOF);
        last.clear();
        if (tof != null) last.put(SensorItem.TOF, tof);
    }

    public List<SensorItem> due(long now, boolean visible, boolean detail) {
        List<SensorItem> out = new ArrayList<>();
        if (!visible) return out;
        while (!spent.isEmpty() && now - spent.get(0) >= WINDOW_MS) spent.remove(0);
        List<SensorItem> ready = new ArrayList<>();
        for (SensorItem i : SensorItem.values()) {
            if (!i.hal || !items.contains(i) || (!i.card && !detail)) continue;
            if (overdue(i, now) >= 0) ready.add(i);
        }
        ready.sort((x, y) -> Long.compare(overdue(y, now), overdue(x, now)));
        for (SensorItem i : ready) {
            if (spent.size() >= MAX_PER_SECOND) break;
            last.put(i, now);
            spent.add(now);
            out.add(i);
        }
        return out;
    }

    private long overdue(SensorItem i, long now) {
        Long t = last.get(i);
        return t == null ? Long.MAX_VALUE / 2 : now - t - i.periodMs;
    }
}
