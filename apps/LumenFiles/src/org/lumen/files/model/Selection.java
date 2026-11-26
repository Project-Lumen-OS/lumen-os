// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/** The rows picked for one action, in the order they were picked, keyed by path. */
public final class Selection {
    private final LinkedHashMap<String, Entry> on = new LinkedHashMap<>();

    /** Returns true when the row is picked after the toggle. */
    public boolean toggle(Entry e) {
        if (on.remove(e.path) != null) return false;
        on.put(e.path, e);
        return true;
    }

    public boolean has(Entry e) {
        return on.containsKey(e.path);
    }

    public int count() {
        return on.size();
    }

    public boolean empty() {
        return on.isEmpty();
    }

    public List<Entry> items() {
        return new ArrayList<>(on.values());
    }

    public void clear() {
        on.clear();
    }

    public void all(Collection<Entry> shown) {
        for (Entry e : shown) on.put(e.path, e);
    }

    /** After a reload: rows that are gone are no longer picked. */
    public void keepOnly(Collection<Entry> shown) {
        Set<String> keep = new HashSet<>();
        for (Entry e : shown) keep.add(e.path);
        on.keySet().retainAll(keep);
    }

    /** Bytes of the picked files (folders count once their size is known: 0 here). */
    public long bytes() {
        long n = 0;
        for (Entry e : on.values()) n += e.dir() ? 0 : e.size;
        return n;
    }
}
