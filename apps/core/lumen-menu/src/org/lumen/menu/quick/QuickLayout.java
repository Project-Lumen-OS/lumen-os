// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu.quick;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * A quick menu as the user arranged it (Settings.Secure lumen_quick_menu, one JSON per Android user): the look of the
 * card, the items in menu order with their looks, and every catalogue id the user has seen in the editor (ids not seen
 * get the "Новое" mark there, nothing is added by itself). Immutable.
 */
public final class QuickLayout {
    public static final int VERSION = 1;

    public final int version;
    public final Density density;
    public final List<Placement> items;
    public final List<String> seen;

    public QuickLayout(int version, Density density, List<Placement> items, Collection<String> seen) {
        this.version = version;
        this.density = density == null ? Density.TILES : density;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.seen = Collections.unmodifiableList(new ArrayList<>(new LinkedHashSet<>(seen)));
    }

    public QuickLayout with(List<Placement> items) { return new QuickLayout(version, density, items, seen); }

    public QuickLayout with(Density d) { return new QuickLayout(version, d, items, seen); }

    public QuickLayout seen(Collection<String> ids) { return new QuickLayout(version, density, items, ids); }

    public int indexOf(String id) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).id.equals(id)) return i;
        return -1;
    }

    public boolean has(String id) { return indexOf(id) >= 0; }

    /** The placement of {@code id}, or null. */
    public Placement get(String id) {
        int i = indexOf(id);
        return i < 0 ? null : items.get(i);
    }

    /** Same items, looks and density (what the user sees; the seen list does not count). */
    public boolean sameMenu(QuickLayout o) { return o != null && density == o.density && items.equals(o.items); }

    @Override public String toString() { return density.token + " " + items; }
}
