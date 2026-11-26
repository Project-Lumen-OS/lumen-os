// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu.quick;

import org.lumen.menu.FeatureId;
import org.lumen.menu.FeatureKind;
import org.lumen.menu.MenuSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * What the pencil editor offers on this projector (DATA_MODEL section 1): every tile of the card the profile gives
 * (MenuSpec), the slider tiles, the shared rows below and the pencil. Model aware only through the profile and the
 * runtime check {@code usable} (the mirroring receiver installed, a feature usable now): no model checks.
 */
public final class QuickCatalog {
    /** The pencil: opens the editor, pinned like All settings. */
    public static final String EDIT = "edit";
    /** Catalogue groups, in the editor's order. */
    public static final List<String> GROUPS = Collections.unmodifiableList(Arrays.asList(MenuSpec.PICTURE, MenuSpec.SOUND,
            MenuSpec.KEYSTONE, MenuSpec.FOCUS, MenuSpec.PROJECTION, MenuSpec.GAME, MenuSpec.GENERAL));
    /**
     * Rows the editor offers beyond the card where the profile has them, never in the default card (decision 2,
     * 2026-11-18: the volume is an optional item). The picture sliders of the profile follow as picture.pq.&lt;item&gt;.
     */
    private static final FeatureId[] EXTRAS = {FeatureId.SOUND_VOLUME, FeatureId.PICTURE_MODE, FeatureId.SOUND_MODE,
            FeatureId.SOUND_OUTPUT, FeatureId.PICTURE_ASPECT, FeatureId.FOCUS_AF_NOW};

    private final Map<String, QuickItem> items;

    private QuickCatalog(Map<String, QuickItem> items) { this.items = items; }

    /** The catalogue of {@code spec}; {@code usable} answers for a feature or section id whether it can show now. */
    public static QuickCatalog of(MenuSpec spec, Predicate<String> usable) {
        LinkedHashMap<String, QuickItem> out = new LinkedHashMap<>();
        for (MenuSpec.Tile t : spec.round) add(out, spec, t.id, usable);
        add(out, spec, EDIT, usable);
        for (List<MenuSpec.Tile> row : spec.rows) {
            for (MenuSpec.Tile t : row) {
                add(out, spec, t.id, usable);
                if (t.orElse != null) add(out, spec, t.orElse, usable);
            }
        }
        for (MenuSpec.Tile t : spec.sliders) add(out, spec, t.id, usable);
        for (FeatureId f : EXTRAS) {
            if (MenuSpec.EVERY_MODEL.contains(f) || spec.profile.has(f)) add(out, spec, f.id, usable);
        }
        if (spec.profile.has(FeatureId.PICTURE_PQ)) {
            for (String item : spec.profile.items(FeatureId.PICTURE_PQ)) add(out, spec, FeatureId.pq(item).id, usable);
        }
        return new QuickCatalog(Collections.unmodifiableMap(out));
    }

    private static void add(Map<String, QuickItem> out, MenuSpec spec, String id, Predicate<String> usable) {
        if (id == null || out.containsKey(id)) return;
        ItemKind k = kindOf(spec, id);
        if (k != ItemKind.PINNED && k != ItemKind.THEME && !usable.test(id)) return;
        out.put(id, new QuickItem(id, k, groupOf(id)));
    }

    /** The kind of an id of this menu (the key of the presentation table). */
    static ItemKind kindOf(MenuSpec spec, String id) {
        if (EDIT.equals(id) || MenuSpec.GENERAL.equals(id)) return ItemKind.PINNED;
        if (MenuSpec.THEME.equals(id)) return ItemKind.THEME;
        FeatureId f = FeatureId.of(id);
        if (f == null) return ItemKind.SECTION;
        if (f.kind == FeatureKind.SLIDER || MenuSpec.isSliderTile(id) && spec.sliderRange(f) != null) return ItemKind.SLIDER;
        if (f.kind == FeatureKind.CHOICE) return ItemKind.CHOICE;
        if (f.kind == FeatureKind.TOGGLE) return ItemKind.TOGGLE;
        return ItemKind.ACTION;
    }

    /** The catalogue group: the item's section, the light level under Picture, everything else under General. */
    static String groupOf(String id) {
        if (MenuSpec.isSection(id) && FeatureId.of(id) == null) return GROUPS.contains(id) ? id : MenuSpec.GENERAL;
        FeatureId f = FeatureId.of(id);
        if (f == null) return MenuSpec.GENERAL;
        if (f == FeatureId.SOUND_VOLUME) return MenuSpec.SOUND;
        if (f == FeatureId.LAMP) return MenuSpec.PICTURE;
        String s = MenuSpec.sectionOf(f.parent != null ? f.parent : f);
        return s != null && GROUPS.contains(s) ? s : MenuSpec.GENERAL;
    }

    public List<QuickItem> items() { return new ArrayList<>(items.values()); }

    public QuickItem get(String id) { return items.get(id); }

    public boolean has(String id) { return items.containsKey(id); }

    /** The items of {@code group}, in catalogue order. */
    public List<QuickItem> group(String group) {
        List<QuickItem> out = new ArrayList<>();
        for (QuickItem i : items.values()) if (i.group.equals(group)) out.add(i);
        return out;
    }

    public List<String> ids() { return new ArrayList<>(items.keySet()); }
}
