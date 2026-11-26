// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu.quick;

/** How one item of the quick menu is drawn (2026-11-18, the pencil editor): its token is what the stored layout keeps. */
public enum Presentation {
    /** inline slider, the whole card width */
    SLIDER("slider"),
    /** icon, name and value */
    WIDE("wide"),
    /** a square tile, the icon only (the name on focus) */
    SMALL("small"),
    /** a circle of the top row */
    ROUND("round"),
    /** a button under the tiles (All settings and the pencil only) */
    FOOTER("footer");

    public final String token;

    Presentation(String token) { this.token = token; }

    /** The presentation of a stored token, or null for an unknown one. */
    public static Presentation of(String token) {
        for (Presentation p : values()) if (p.token.equals(token)) return p;
        return null;
    }
}
