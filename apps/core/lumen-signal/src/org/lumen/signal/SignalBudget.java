// SPDX-License-Identifier: Apache-2.0
package org.lumen.signal;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which HAL items to read now. Every vendor call of every app goes through one vendor thread, so the panel spends at
 * most {@link #MAX_PER_SECOND} calls per second and none while it is hidden. The small card reads the card items
 * every {@link #FAST_MS}, the full panel adds the detail items every {@link #SLOW_MS} and the ONCE items once per
 * opening or input change. HDMI items wait while the apps are on screen. Pure, host tested.
 */
public final class SignalBudget {
    public static final int MAX_PER_SECOND = 2;
    public static final long FAST_MS = 2_000L, SLOW_MS = 10_000L, WINDOW_MS = 1_000L;

    private final Set<SignalItem> items;
    private final Map<SignalItem, Long> last = new EnumMap<>(SignalItem.class);
    /** times of the calls of the last WINDOW_MS */
    private final List<Long> spent = new ArrayList<>();

    /** {@code items}: the HAL items of this model (the reader's intersected with items.tsv). */
    public SignalBudget(Set<SignalItem> items) { this.items = items; }

    /** Forget every read time (the panel opened again or the input changed: read everything soon). */
    public void reset() {
        last.clear();
    }

    /**
     * The items to read now, at most what the last second left, the longest overdue first (a slow item waiting since
     * its period ended is never starved by the fast ones); they count as spent. {@code hdmi}: an HDMI input is on
     * screen (else the HDMI items wait).
     */
    public List<SignalItem> due(long now, PanelMode mode, boolean hdmi) {
        List<SignalItem> out = new ArrayList<>();
        if (!mode.visible()) return out;
        while (!spent.isEmpty() && now - spent.get(0) >= WINDOW_MS) spent.remove(0);
        List<SignalItem> ready = new ArrayList<>();
        for (SignalItem i : SignalItem.values()) {
            if (!i.hal || !items.contains(i) || (i.hdmiOnly && !hdmi)) continue;
            if (!i.card() && mode != PanelMode.DETAIL) continue;
            if (overdue(i, now) >= 0) ready.add(i);
        }
        ready.sort((x, y) -> Long.compare(overdue(y, now), overdue(x, now)));
        for (SignalItem i : ready) {
            if (spent.size() >= MAX_PER_SECOND) break;
            last.put(i, now);
            spent.add(now);
            out.add(i);
        }
        return out;
    }

    /** How long past its period the item is (never read = the most), negative = not due yet (a ONCE item read). */
    private long overdue(SignalItem i, long now) {
        Long t = last.get(i);
        if (t == null) return Long.MAX_VALUE / 2;
        if (i.speed == ReadSpeed.ONCE) return -1;
        return now - t - (i.speed == ReadSpeed.CARD ? FAST_MS : SLOW_MS);
    }
}
