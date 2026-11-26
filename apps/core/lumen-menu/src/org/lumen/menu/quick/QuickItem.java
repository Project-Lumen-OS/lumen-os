// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu.quick;

import org.lumen.menu.MenuSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One entry of the editor's catalogue: an id of this menu, its kind, the group the editor lists it under and the All
 * settings category whose page has it as a row (2026-11-24: any setting of the full menu can be on the card).
 */
public final class QuickItem {
    public final String id;
    public final ItemKind kind;
    /** The group of the editor's list: the row's All settings category, else the item's section (general for the rest). */
    public final String group;
    /** The All settings category whose page lists it (a row of a page counts for the page's category), or null. */
    public final String section;

    QuickItem(String id, ItemKind kind, String group, String section) {
        this.id = id;
        this.kind = kind;
        this.group = group;
        this.section = section;
    }

    /** The looks of its kind; a developer tool never a circle of the open top row (2026-11-19). */
    public List<Presentation> allowed() {
        List<Presentation> all = Presentations.of(kind);
        if (!MenuSpec.DEVELOPER.equals(section) || !all.contains(Presentation.ROUND)) return all;
        List<Presentation> out = new ArrayList<>(all);
        out.remove(Presentation.ROUND);
        return Collections.unmodifiableList(out);
    }

    public Presentation preferred() { return allowed().get(0); }

    public boolean allows(Presentation p) { return p != null && allowed().contains(p); }

    public boolean pinned() { return kind == ItemKind.PINNED; }

    @Override public String toString() { return id + " " + kind.name().toLowerCase() + " in " + group; }
}
