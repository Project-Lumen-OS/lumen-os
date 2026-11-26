// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu.quick;

/** One line of the packed card ({@link QuickPacker}). */
public enum LineKind {
    /** a row of tiles: one wide tile and up to two small ones, or up to four small ones */
    TILES,
    /** a slider across the card */
    SLIDER,
    /** one item as a list row */
    LIST,
    /** a slider as a list row (its name above the track) */
    LIST_SLIDER
}
