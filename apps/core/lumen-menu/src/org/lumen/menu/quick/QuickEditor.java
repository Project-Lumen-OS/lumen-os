// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu.quick;

import java.util.ArrayList;
import java.util.List;

/**
 * The pencil editor's changes as plain steps (DATA_MODEL section 5): add, remove, look, move, density, reset. Every
 * step keeps the card on the screen: a change that would make it taller than the screen is refused ({@link
 * EditResult#FULL}, decision 3: refuse and suggest a smaller look or the list, no scrolling). All settings and
 * the pencil are never removed. Nothing is stored here; {@link #toStore} says what the store gets when done.
 */
public final class QuickEditor {
    /** Directions of {@link #move}, the QpdLayout values. */
    public static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;

    private final QuickCatalog cat;
    private final QuickLayout defaults;
    private QuickLayout work;
    private boolean changed, reset;

    public QuickEditor(QuickCatalog cat, QuickLayout defaults, QuickLayout shown) {
        this.cat = cat;
        this.defaults = defaults;
        this.work = shown == null ? defaults : shown;
    }

    public QuickLayout layout() { return work; }

    public QuickCatalog catalog() { return cat; }

    /** True once a step changed the menu. */
    public boolean changed() { return changed; }

    public boolean inMenu(String id) { return work.has(id); }

    /** The item's look now, or null when it is not in the menu. */
    public Presentation look(String id) {
        Placement p = work.get(id);
        return p == null ? null : p.as;
    }

    /** "Новое" in the catalogue: the user has a menu of his own and never saw this item in the editor. */
    public static boolean isNew(QuickLayout stored, String id) { return stored != null && !stored.seen.contains(id); }

    private EditResult apply(QuickLayout next) {
        if (next.sameMenu(work)) return EditResult.SAME;
        if (!QuickPacker.pack(next).fits()) return EditResult.FULL;
        work = next;
        changed = true;
        reset = false;
        return EditResult.DONE;
    }

    /** Adds {@code id} at the end in its preferred look (the editor then moves it). */
    public EditResult add(String id) {
        QuickItem it = cat.get(id);
        if (it == null) return EditResult.UNKNOWN;
        if (work.has(id)) return EditResult.SAME;
        List<Placement> l = new ArrayList<>(work.items);
        l.add(new Placement(id, it.preferred()));
        return apply(work.with(l));
    }

    public EditResult remove(String id) {
        QuickItem it = cat.get(id);
        if (it == null || !work.has(id)) return EditResult.UNKNOWN;
        if (it.pinned()) return EditResult.PINNED;
        List<Placement> l = new ArrayList<>(work.items);
        l.remove(work.indexOf(id));
        return apply(work.with(l));
    }

    public EditResult setLook(String id, Presentation as) {
        QuickItem it = cat.get(id);
        if (it == null || !work.has(id) || !it.allows(as)) return EditResult.UNKNOWN;
        List<Placement> l = new ArrayList<>(work.items);
        l.set(work.indexOf(id), new Placement(id, as));
        return apply(work.with(l));
    }

    public EditResult setDensity(Density d) {
        return d == null ? EditResult.UNKNOWN : apply(work.with(d));
    }

    /**
     * Moves {@code id} one place: LEFT / RIGHT before the previous or after the next item of the same zone (the circles,
     * the footer, the card body), UP / DOWN into the line above or below at the same column (in a list: one row).
     */
    public EditResult move(String id, int dir) {
        int i = work.indexOf(id);
        if (i < 0) return EditResult.UNKNOWN;
        Presentation zone = zone(work.items.get(i).as);
        List<Placement> l = new ArrayList<>(work.items);
        Placement me = l.remove(i);
        int to;
        boolean body = zone == null, list = work.density == Density.LIST;
        if (!body && (dir == UP || dir == DOWN)) return EditResult.SAME;
        if (dir == LEFT || dir == RIGHT || list) {
            boolean back = dir == LEFT || dir == UP;
            int j = neighbour(work.items, i, zone, back);
            if (j < 0) return EditResult.SAME;
            to = j;                        // back: before it; on: after it (it moved to j - 1 when this one left)
        } else {
            QuickPacker.Card card = QuickPacker.pack(work);
            int line = card.lineOf(id);
            int target = dir == UP ? line - 1 : line + 1;
            if (line < 0 || target < 0 || target >= card.lines.size()) return EditResult.SAME;
            List<Placement> cells = card.lines.get(line).cells, there = card.lines.get(target).cells;
            int col = indexOf(cells, id);
            Placement t = there.get(Math.min(col, there.size() - 1));
            // up and down swap places with the tile there: a wide tile under a wide one leaves no hole in the grid
            List<Placement> w = new ArrayList<>(work.items);
            int j = indexOf(w, t.id);
            w.set(j, work.items.get(i));
            w.set(i, t);
            return apply(work.with(w));
        }
        l.add(Math.max(0, Math.min(l.size(), to)), me);
        return apply(work.with(l));
    }

    /** The zone of a look for moves: ROUND, FOOTER or null for the card body. */
    private static Presentation zone(Presentation p) {
        return p == Presentation.ROUND || p == Presentation.FOOTER ? p : null;
    }

    /** Index in {@code items} of the next item of the same zone before or after {@code i}, or -1. */
    private static int neighbour(List<Placement> items, int i, Presentation zone, boolean back) {
        for (int j = back ? i - 1 : i + 1; j >= 0 && j < items.size(); j += back ? -1 : 1) {
            if (zone(items.get(j).as) == zone) return j;
        }
        return -1;
    }

    private static int indexOf(List<Placement> l, String id) {
        for (int i = 0; i < l.size(); i++) if (l.get(i).id.equals(id)) return i;
        return -1;
    }

    /** The menu as it was before a move (BACK while moving): no check, it was shown before. */
    public void restore(QuickLayout before) {
        if (before == null) return;
        work = before;
    }

    /** Back to today's menu: when done without a later change the stored value is deleted. */
    public void reset() {
        if (!work.sameMenu(defaults) || !reset) changed = true;
        work = defaults;
        reset = true;
    }

    /** True when the last step was a reset (done then deletes the stored value). */
    public boolean wasReset() { return reset; }

    /** The value to store when done: null = delete it (a reset), else the menu with the hidden items kept. */
    public QuickLayout toStore(QuickLayout stored) {
        return reset ? null : QuickLayoutJson.toStore(stored, work, cat);
    }
}
