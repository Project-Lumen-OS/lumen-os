// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu.quick;

import java.util.List;

/** One entry of the editor's catalogue: an id of this menu, its kind and the catalogue group it is listed under. */
public final class QuickItem {
    public final String id;
    public final ItemKind kind;
    /** The section it is listed under (MenuSpec section ids, general for the rest). */
    public final String group;

    QuickItem(String id, ItemKind kind, String group) {
        this.id = id;
        this.kind = kind;
        this.group = group;
    }

    public List<Presentation> allowed() { return Presentations.of(kind); }

    public Presentation preferred() { return allowed().get(0); }

    public boolean allows(Presentation p) { return p != null && allowed().contains(p); }

    public boolean pinned() { return kind == ItemKind.PINNED; }

    @Override public String toString() { return id + " " + kind.name().toLowerCase() + " in " + group; }
}
