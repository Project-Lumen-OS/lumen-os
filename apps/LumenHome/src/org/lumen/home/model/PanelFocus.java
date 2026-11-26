// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

import java.util.List;

/**
 * Where the focus of a side panel page goes when the page is filled again with new values (a switch, a look, the
 * theme): onto the item with the same tag, so a row that appears or goes above it (Custom look) never moves the
 * focus to another row; without a tag the same place.
 */
public final class PanelFocus {
    private PanelFocus() {}

    /** @param focus the focused index before, -1 = the page's text field (stays there) */
    public static int keep(List<?> before, List<?> after, int focus) {
        if (focus < 0) return focus;
        Object tag = focus < before.size() ? before.get(focus) : null;
        if (tag != null) {
            int i = after.indexOf(tag);
            if (i >= 0) return i;
        }
        return Math.min(focus, Math.max(after.size() - 1, 0));
    }

    /** Same rows in the same places (tags and kinds): the shown rows can take the new values in place. */
    public static boolean sameRows(List<?> tagsA, List<Integer> kindsA, List<?> tagsB, List<Integer> kindsB) {
        return tagsA.equals(tagsB) && kindsA.equals(kindsB);
    }
}
