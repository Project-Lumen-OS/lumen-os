// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

import java.util.Locale;

/** Sizes in decimal units like Android's own Files (1.2 GB, 734 MB, 88 KB); the unit names come localised. */
public final class Sizes {
    private Sizes() {}

    /** {@code units}: KB, MB, GB, TB in the UI language. */
    public static String format(long b, String[] units, Locale loc) {
        if (b >= 1_000_000_000_000L) return String.format(loc, "%.1f %s", b / 1e12, units[3]);
        if (b >= 1_000_000_000L) return String.format(loc, "%.1f %s", b / 1e9, units[2]);
        if (b >= 1_000_000L) return String.format(loc, "%.0f %s", b / 1e6, units[1]);
        return String.format(loc, "%d %s", b <= 0 ? 0 : Math.max(1, (b + 999) / 1000), units[0]);
    }

    public static int percent(long done, long total) {
        if (total <= 0) return 0;
        return (int) Math.max(0, Math.min(100, done * 100 / total));
    }
}
