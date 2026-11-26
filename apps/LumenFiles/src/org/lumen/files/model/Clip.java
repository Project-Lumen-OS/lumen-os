// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

import java.util.ArrayList;
import java.util.List;

/** Rows waiting for "paste here": what, from which store, copy or move. */
public final class Clip {
    public final TransferOp op;
    public final Fs from;
    public final List<Entry> items;

    public Clip(TransferOp op, Fs from, List<Entry> items) {
        this.op = op;
        this.from = from;
        this.items = new ArrayList<>(items);
    }

    /** True when the rows may land in {@code dir} of {@code to}. */
    public boolean fits(Fs to, String dir) {
        if (to == null || dir == null || !to.writable() || items.isEmpty()) return false;
        if (op == TransferOp.MOVE && !from.writable()) return false;
        boolean all = true;
        for (Entry e : items) {
            if (to.same(from) && e.dir() && Transfer.inside(e.path, dir)) return false;
            all &= to.same(from) && dir.equals(from.parent(e.path));
        }
        return !(op == TransferOp.MOVE && all);
    }
}
