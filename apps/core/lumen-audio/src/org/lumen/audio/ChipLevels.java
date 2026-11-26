// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.audio;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The chip levels of the stock volume split (stock XGIMI AudioService VolumeStreamState.applyDeviceVolume_syncVSS and
 * setDspVolumesToHal): each output's own music index goes to the HAL with its key, 0 while muted, the full level for an
 * A2DP device that applies AVRCP absolute volume itself. Remembers what each HAL slot got, so an unchanged level is not
 * sent again unless forced. Plain Java, host tested; StockVolume is the Android side.
 */
public final class ChipLevels {
    /** The HAL level that leaves the sound as it is (0 dB on the vendor curve, the top of the stock 0..100 scale). */
    public static final int FULL = 100;

    private final Map<String, Integer> sent = new HashMap<>();

    /** The HAL level of a music index: the index on the stock 0..100 scale, 0 while muted. */
    public static int level(int index, int max, boolean muted) {
        if (muted) return 0;
        int v = Math.round(index * 100f / Math.max(1, max));
        return Math.max(0, Math.min(FULL, v));
    }

    /** The level of an output: A2DP with AVRCP absolute volume stays full (0 at index 0), every other the index level. */
    public static int levelOf(ChipKey row, int index, int max, boolean muted, boolean avrcpAbsolute) {
        int v = level(index, max, muted);
        if (row.type != ChipTable.TYPE_A2DP || !avrcpAbsolute) return v;
        return v == 0 ? 0 : FULL;
    }

    /** The row of a slot whose device level counts: the first one connected now, else the first one. */
    public static ChipKey pick(List<ChipKey> slot, List<Integer> connectedTypes) {
        if (connectedTypes != null) {
            for (ChipKey r : slot) if (connectedTypes.contains(r.type)) return r;
        }
        return slot.get(0);
    }

    /** The parameter to send for a slot now, or null: an out of range level, or the level the slot has (unless force). */
    public synchronized String due(ChipKey row, int level, boolean force) {
        if (level < 0 || level > FULL) return null;
        Integer had = sent.get(row.slot());
        if (!force && had != null && had == level) return null;
        return row.param(level);
    }

    /** The HAL took {@code level} for the row's slot. */
    public synchronized void sent(ChipKey row, int level) {
        sent.put(row.slot(), level);
    }

    /** The level a slot got last, -1 none. */
    public synchronized int last(ChipKey row) {
        Integer had = sent.get(row.slot());
        return had == null ? -1 : had;
    }

    /** The audio server went down: the HAL lost every level. */
    public synchronized void forget() {
        sent.clear();
    }
}
