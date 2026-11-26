// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

import java.util.Comparator;
import java.util.List;

/** Row order: folders first, then the chosen order, ties by name. */
public final class Sorter {
    private Sorter() {}

    public static void sort(List<Entry> l, Sort by, Comparator<String> names) {
        l.sort((a, b) -> {
            if (a.dir() != b.dir()) return a.dir() ? -1 : 1;
            int c = 0;
            if (by == Sort.DATE) c = Long.compare(b.modified, a.modified);
            else if (by == Sort.SIZE && !a.dir()) c = Long.compare(b.size, a.size);
            return c != 0 ? c : names.compare(a.name, b.name);
        });
    }

    /** Keeps the first {@code max} rows (each one is a view); returns how many were cut. */
    public static int cap(List<Entry> l, int max) {
        int cut = Math.max(0, l.size() - max);
        if (cut > 0) l.subList(max, l.size()).clear();
        return cut;
    }

    public static Sort next(Sort s) {
        Sort[] all = Sort.values();
        return all[(s.ordinal() + 1) % all.length];
    }
}
