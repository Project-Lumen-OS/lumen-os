// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu.quick;

/** The card's look: several items in a row, or one per row. */
public enum Density {
    TILES("tiles"), LIST("list");

    public final String token;

    Density(String token) { this.token = token; }

    public static Density of(String token) {
        for (Density d : values()) if (d.token.equals(token)) return d;
        return null;
    }
}
