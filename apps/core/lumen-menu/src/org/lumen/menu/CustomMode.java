// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

/** One user picture mode: the name the user gave it and the vendor picture mode id it got at creation. */
public final class CustomMode {
    public final String name;
    public final int value;

    public CustomMode(String name, int value) {
        this.name = name;
        this.value = value;
    }

    @Override public boolean equals(Object o) {
        return o instanceof CustomMode && ((CustomMode) o).value == value && ((CustomMode) o).name.equals(name);
    }

    @Override public int hashCode() { return name.hashCode() * 31 + value; }

    @Override public String toString() { return name + "=" + value; }
}
