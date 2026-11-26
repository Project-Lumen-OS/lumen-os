// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Search and kind views: a bounded walk through a store's folders (worker thread, stops when asked). */
public final class Walk {
    public static final int MAX_DEPTH = 12, MAX_FOLDERS = 20_000;

    /** What a walk found; {@code more}: it stopped at the limit. */
    public static final class Found {
        public final List<Entry> entries = new ArrayList<>();
        public boolean more;
    }

    private Walk() {}

    public static Found run(Fs fs, String root, String label, Predicate<Entry> want, int limit, BooleanSupplier stop) {
        Found f = new Found();
        into(fs, root, label, want, limit, stop, f);
        return f;
    }

    /** Adds to {@code f} (several stores share one result and its limit). */
    public static void into(Fs fs, String root, String label, Predicate<Entry> want, int limit, BooleanSupplier stop, Found f) {
        ArrayDeque<Object[]> todo = new ArrayDeque<>();
        todo.push(new Object[]{root, 0, label});
        int folders = 0;
        while (!todo.isEmpty() && !f.more) {
            if (stop.getAsBoolean() || ++folders > MAX_FOLDERS) return;
            Object[] t = todo.pop();
            String dir = (String) t[0], where = (String) t[2];
            int depth = (Integer) t[1];
            List<Entry> l;
            try {
                l = fs.list(dir);
            } catch (IOException e) {
                continue;
            }
            for (Entry e : l) {
                if (e.dir() && depth < MAX_DEPTH && !(depth == 0 && "Android".equals(e.name))) {
                    todo.push(new Object[]{e.path, depth + 1, where + " / " + e.name});
                }
                if (!want.test(e)) continue;
                if (f.entries.size() >= limit) {
                    f.more = true;
                    return;
                }
                e.where = where;
                f.entries.add(e);
            }
        }
    }
}
