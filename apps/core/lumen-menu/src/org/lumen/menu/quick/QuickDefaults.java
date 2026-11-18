// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu.quick;

import org.lumen.menu.MenuSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Today's layout D as a quick menu (what a user who never edits sees, nothing stored): the round buttons with the
 * pencil before the gear, per row one wide tile and the next two small ones (the order QpdCard always drew), the slider
 * tiles at the bottom. Packed by {@link QuickPacker} it draws exactly the card MenuSpec gives.
 */
public final class QuickDefaults {
    private QuickDefaults() {}

    public static QuickLayout of(MenuSpec spec, Predicate<String> usable) {
        List<Placement> out = new ArrayList<>();
        for (MenuSpec.Tile t : spec.roundRow(usable)) {
            if (MenuSpec.GENERAL.equals(t.id)) out.add(new Placement(QuickCatalog.EDIT, Presentation.ROUND));
            out.add(new Placement(t.id, Presentation.ROUND));
        }
        List<String> wides = new ArrayList<>(), smalls = new ArrayList<>();
        for (List<MenuSpec.Tile> row : spec.cardRows(usable)) {
            for (MenuSpec.Tile t : row) (MenuSpec.WIDE.equals(t.size) ? wides : smalls).add(t.id);
        }
        int s = 0;
        for (String w : wides) {
            out.add(new Placement(w, Presentation.WIDE));
            for (int k = 0; k < 2 && s < smalls.size(); k++) out.add(new Placement(smalls.get(s++), Presentation.SMALL));
        }
        while (s < smalls.size()) out.add(new Placement(smalls.get(s++), Presentation.SMALL));
        for (MenuSpec.Tile t : spec.sliders) if (usable.test(t.id)) out.add(new Placement(t.id, Presentation.SLIDER));
        return new QuickLayout(QuickLayout.VERSION, Density.TILES, out, Collections.emptyList());
    }
}
