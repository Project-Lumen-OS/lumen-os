// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu.quick;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The one presentation table of the quick menu (DATA_MODEL section 4): which looks an item kind may take, the first is
 * the look an added item gets. No per-model data: a model only decides which items exist, through its profile.
 */
public final class Presentations {
    private static final Map<ItemKind, List<Presentation>> TABLE = new EnumMap<>(ItemKind.class);

    static {
        put(ItemKind.SLIDER, Presentation.SLIDER, Presentation.WIDE, Presentation.SMALL, Presentation.ROUND);
        put(ItemKind.SECTION, Presentation.WIDE, Presentation.SMALL, Presentation.ROUND);
        put(ItemKind.CHOICE, Presentation.WIDE, Presentation.SMALL, Presentation.ROUND);
        put(ItemKind.TOGGLE, Presentation.SMALL, Presentation.WIDE, Presentation.ROUND);   // a switch is a tile (2026-11-24)
        put(ItemKind.ACTION, Presentation.WIDE, Presentation.SMALL, Presentation.ROUND);
        put(ItemKind.THEME, Presentation.ROUND, Presentation.SMALL, Presentation.WIDE);
        put(ItemKind.PINNED, Presentation.ROUND, Presentation.FOOTER);
    }

    private Presentations() {}

    private static void put(ItemKind k, Presentation... p) {
        TABLE.put(k, Collections.unmodifiableList(Arrays.asList(p)));
    }

    /** The looks of {@code kind}, the preferred one first. */
    public static List<Presentation> of(ItemKind kind) { return TABLE.get(kind); }
}
