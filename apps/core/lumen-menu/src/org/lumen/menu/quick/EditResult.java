// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu.quick;

/** The answer of one editor step ({@link QuickEditor}). */
public enum EditResult {
    /** done, the menu changed */
    DONE,
    /** nothing to do (already so) */
    SAME,
    /** refused: the card would be taller than the screen (2026-11-18: refuse, suggest a smaller look or the list) */
    FULL,
    /** refused: All settings and the pencil stay in the menu */
    PINNED,
    /** not an item of this menu, or a look the item does not have */
    UNKNOWN
}
