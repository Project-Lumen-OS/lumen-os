// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu.quick;

/** One item of a stored or shown quick menu: its id and how it is drawn. */
public final class Placement {
    public final String id;
    /** The look; null only in a value just read, before {@link QuickLayoutJson#reconcile} repairs it. */
    public final Presentation as;

    public Placement(String id, Presentation as) {
        this.id = id;
        this.as = as;
    }

    @Override public boolean equals(Object o) {
        return o instanceof Placement && ((Placement) o).id.equals(id) && ((Placement) o).as == as;
    }

    @Override public int hashCode() { return id.hashCode() * 31 + (as == null ? 0 : as.hashCode()); }

    @Override public String toString() { return id + " " + (as == null ? "?" : as.token); }
}
