// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the list is and how BACK walks out: a stack of places, each with the name that had the focus when a
 * deeper one was opened (BACK puts the focus back on the folder the user came from).
 */
public final class Nav {
    /** One step: a folder of a store, the network page, a kind view or search results. */
    public static final class Loc {
        public final LocKind kind;
        public final Fs fs;
        public final String path, label;
        public Category category;
        public String query = "";
        public String focus = "";

        public Loc(LocKind kind, Fs fs, String path, String label) {
            this.kind = kind;
            this.fs = fs;
            this.path = path;
            this.label = label;
        }
    }

    private final ArrayList<Loc> stack = new ArrayList<>();

    /** A new start (a card of the strip): the old steps are dropped. */
    public void root(Loc l) {
        stack.clear();
        stack.add(l);
    }

    /** One level deeper; {@code focused}: the row to focus when the user comes back. */
    public void open(Loc l, String focused) {
        Loc t = top();
        if (t != null) t.focus = focused == null ? "" : focused;
        stack.add(l);
    }

    public Loc top() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    public boolean canUp() {
        return stack.size() > 1;
    }

    /** BACK: the level above (its {@code focus} is the row to focus), null at the top. */
    public Loc up() {
        if (!canUp()) return null;
        stack.remove(stack.size() - 1);
        return top();
    }

    public List<String> crumbs() {
        List<String> out = new ArrayList<>();
        for (Loc l : stack) out.add(l.label);
        return out;
    }

    /** True when any step lies on {@code fs} (a drive that was just removed). */
    public boolean uses(Fs fs) {
        for (Loc l : stack) if (l.fs != null && l.fs.same(fs)) return true;
        return false;
    }
}
