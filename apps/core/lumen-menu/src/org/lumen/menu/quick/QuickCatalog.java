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
 * What the pencil editor offers on this projector, from one place (2026-11-24: every setting of the model's full
 * menu can be put into the quick menu): the card MenuSpec draws (layout D: its circles with the pencil, its tiles, its
 * sliders), the volume every model has, then every row of the model's full menu that has a quick look
 * ({@link #fullMenu}). No list per model and no second list of tiles: a model differs only by its profile and by the
 * runtime checks {@code usable} (the mirroring receiver installed, a feature usable now) and {@code rowUsable} (the
 * full menu built that row).
 */
public final class QuickCatalog {
    /** The pencil: opens the editor, pinned like All settings. */
    public static final String EDIT = "edit";
    /** Groups of card items that are no All settings category (their rows sit on another page), after the categories. */
    public static final List<String> GROUPS = Collections.unmodifiableList(Arrays.asList(MenuSpec.PICTURE, MenuSpec.SOUND,
            MenuSpec.KEYSTONE, MenuSpec.FOCUS, MenuSpec.PROJECTION, MenuSpec.GAME, MenuSpec.GENERAL));

    private final Map<String, QuickItem> items;
    /** The editor's groups: the All settings categories in the rail's order (MenuSpec.allSettings), then {@link #GROUPS}. */
    private final List<String> order;

    private QuickCatalog(Map<String, QuickItem> items, List<String> order) {
        this.items = items;
        this.order = order;
    }

    /** The catalogue of {@code spec}; {@code usable} answers for a feature or section id whether it can show now. */
    public static QuickCatalog of(MenuSpec spec, Predicate<String> usable) { return of(spec, usable, usable); }

    /** The same, {@code rowUsable} answering for the rows of the full menu (the full menu has the row now). */
    public static QuickCatalog of(MenuSpec spec, Predicate<String> usable, Predicate<String> rowUsable) {
        LinkedHashMap<String, ItemKind> kinds = new LinkedHashMap<>();
        for (String id : cardIds(spec)) {
            ItemKind k = kindOf(spec, id);
            if (k == ItemKind.PINNED || k == ItemKind.THEME || usable.test(id)) kinds.putIfAbsent(id, k);
        }
        for (FeatureId f : MenuSpec.EVERY_MODEL) if (usable.test(f.id)) kinds.putIfAbsent(f.id, kindOf(spec, f.id));
        for (FeatureId f : MenuSpec.QUICK_ONLY) {
            if (spec.profile.has(f) && usable.test(f.id)) kinds.putIfAbsent(f.id, kindOf(spec, f.id));
        }
        Map<FeatureId, String> full = fullMenu(spec);
        for (FeatureId f : full.keySet()) if (!kinds.containsKey(f.id) && rowUsable.test(f.id)) kinds.put(f.id, kindOf(spec, f.id));
        LinkedHashMap<String, QuickItem> out = new LinkedHashMap<>();
        for (Map.Entry<String, ItemKind> e : kinds.entrySet()) {
            FeatureId f = FeatureId.of(e.getKey());
            String sec = f == null ? null : full.get(f);
            out.put(e.getKey(), new QuickItem(e.getKey(), e.getValue(), sec != null ? sec : groupOf(spec, e.getKey()), sec));
        }
        List<String> order = new ArrayList<>(spec.allSettings);
        for (String g : GROUPS) if (!order.contains(g)) order.add(g);
        return new QuickCatalog(Collections.unmodifiableMap(out), Collections.unmodifiableList(order));
    }

    /** The card MenuSpec draws: its circles and then the pencil, its tiles (with a tile's stand-in), its sliders. */
    private static List<String> cardIds(MenuSpec spec) {
        List<String> out = new ArrayList<>();
        for (MenuSpec.Tile t : spec.round) out.add(t.id);
        out.add(EDIT);
        for (List<MenuSpec.Tile> row : spec.rows) {
            for (MenuSpec.Tile t : row) {
                out.add(t.id);
                if (t.orElse != null) out.add(t.orElse);
            }
        }
        for (MenuSpec.Tile t : spec.sliders) out.add(t.id);
        return out;
    }

    /**
     * Every row of the full menu of {@code spec} that has a quick look ({@link #quick}), with the All settings category
     * whose page lists it, in the pages' order: each row of each category page, the rows of a page after the page row
     * (a page row of a page again), the picture sliders one by one. The catalogue's only source of full menu rows.
     */
    public static Map<FeatureId, String> fullMenu(MenuSpec spec) {
        LinkedHashMap<FeatureId, String> out = new LinkedHashMap<>();
        for (String sec : spec.allSettings) for (FeatureId f : spec.rowsOf(sec)) walk(spec, sec, f, out);
        return out;
    }

    private static void walk(MenuSpec spec, String sec, FeatureId f, Map<FeatureId, String> out) {
        if (f == FeatureId.PICTURE_PQ) {
            for (String item : spec.profile.items(f)) walk(spec, sec, FeatureId.pq(item), out);
            return;
        }
        if (quick(f)) out.putIfAbsent(f, sec);
        if (spec.pages.containsKey(f)) for (FeatureId r : spec.pageRowsOf(f)) walk(spec, sec, r, out);
    }

    /**
     * The one rule of which full menu row can be on the card: a switch or a choice as a tile, a number as a slider, a
     * page, screen or action as a tile that opens or runs it. A readout only shows a value, and the light level is the
     * card's own slider (its All settings row holds only the key hints).
     */
    public static boolean quick(FeatureId f) { return f.kind != FeatureKind.INFO && f != FeatureId.LAMP; }

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

    /**
     * The group of an item that is no full menu row: the volume under Sound, the light level under Picture, a section
     * whose page is a category under that category, else its section's group, everything else under General.
     */
    static String groupOf(MenuSpec spec, String id) {
        FeatureId f = FeatureId.of(id);
        if (f == FeatureId.SOUND_VOLUME) return MenuSpec.SOUND;
        if (f == FeatureId.LAMP) return MenuSpec.PICTURE;
        if (spec.allSettings.contains(id) || f == null && GROUPS.contains(id)) return id;
        String s = f == null ? null : MenuSpec.sectionOf(f.parent != null ? f.parent : f);
        return s != null && (GROUPS.contains(s) || spec.allSettings.contains(s)) ? s : MenuSpec.GENERAL;
    }

    public List<QuickItem> items() { return new ArrayList<>(items.values()); }

    public QuickItem get(String id) { return items.get(id); }

    public boolean has(String id) { return items.containsKey(id); }

    /** The items of {@code group}, in catalogue order (the card's own items first, then the page's rows). */
    public List<QuickItem> group(String group) {
        List<QuickItem> out = new ArrayList<>();
        for (QuickItem i : items.values()) if (i.group.equals(group)) out.add(i);
        return out;
    }

    /** The groups that have items, in the editor's order: the All settings categories as the rail, then the rest. */
    public List<String> groups() {
        List<String> out = new ArrayList<>();
        for (String g : order) if (!group(g).isEmpty()) out.add(g);
        for (QuickItem i : items.values()) if (!out.contains(i.group)) out.add(i.group);
        return out;
    }

    /**
     * The items whose name {@code title} gives, or whose own word of the menu, matches {@code query} ({@link #matches}),
     * by group: "memc" finds Motion smoothing in every language.
     */
    public List<QuickItem> find(String query, java.util.function.Function<String, CharSequence> title) {
        List<QuickItem> out = new ArrayList<>();
        for (String g : groups()) {
            for (QuickItem i : group(g)) {
                String word = i.id.substring(i.id.lastIndexOf('.') + 1).replace('_', ' ');
                if (matches(title.apply(i.id) + " " + word, query)) out.add(i);
            }
        }
        return out;
    }

    /**
     * The editor's search: every word of {@code query} is in {@code text}, case and ё/е ignored ("memc", "чёрн" finds
     * Динамический черный). An empty query matches nothing (the whole list shows instead).
     */
    public static boolean matches(CharSequence text, String query) {
        String q = fold(query), t = fold(text == null ? null : text.toString());
        if (q.isEmpty() || t.isEmpty()) return false;
        for (String w : q.split(" +")) if (!t.contains(w)) return false;
        return true;
    }

    private static String fold(String s) {
        return s == null ? "" : s.toLowerCase(java.util.Locale.ROOT).replace('ё', 'е').replaceAll("\\s+", " ").trim();
    }

    public List<String> ids() { return new ArrayList<>(items.keySet()); }
}
