// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu.quick;

import org.lumen.menu.MenuJson;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The stored quick menu as JSON (DATA_MODEL sections 2 and 3) and its repair on every read. Fail closed: a missing,
 * unreadable or newer value gives the default menu and is never written over (a newer build may own it). Nothing here
 * writes back by itself: the shown menu is the stored one repaired for this projector now.
 */
public final class QuickLayoutJson {
    /** The Settings.Secure key (per Android user). */
    public static final String KEY = "lumen_quick_menu";

    private QuickLayoutJson() {}

    /** The stored value, or null when there is none, it cannot be read or it is of a newer version. */
    public static QuickLayout parse(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        Object o;
        try {
            o = MenuJson.parse(json);
        } catch (RuntimeException e) {
            return null;
        }
        if (!(o instanceof Map)) return null;
        Map<?, ?> m = (Map<?, ?>) o;
        Object v = m.get("v");
        if (!(v instanceof Long) || (Long) v < 1 || (Long) v > QuickLayout.VERSION) return null;
        Density d = m.get("density") instanceof String ? Density.of((String) m.get("density")) : null;
        if (d == null || !(m.get("items") instanceof List)) return null;
        List<Placement> items = new ArrayList<>();
        for (Object e : (List<?>) m.get("items")) {
            if (!(e instanceof Map)) return null;
            Object id = ((Map<?, ?>) e).get("id"), as = ((Map<?, ?>) e).get("as");
            if (!(id instanceof String) || ((String) id).isEmpty()) return null;
            items.add(new Placement((String) id, as instanceof String ? Presentation.of((String) as) : null));
        }
        List<String> seen = new ArrayList<>();
        if (m.get("seen") instanceof List) for (Object s : (List<?>) m.get("seen")) if (s instanceof String) seen.add((String) s);
        return upgrade(new QuickLayout(((Long) v).intValue(), d, items, seen));
    }

    /** An older stored version brought up to {@link QuickLayout#VERSION}, one step at a time (version 1 is the first). */
    static QuickLayout upgrade(QuickLayout l) {
        return l.version == QuickLayout.VERSION ? l : null;
    }

    public static String write(QuickLayout l) {
        StringBuilder b = new StringBuilder("{\"v\": ").append(QuickLayout.VERSION).append(", \"density\": ");
        str(b, l.density.token).append(", \"items\": [");
        for (int i = 0; i < l.items.size(); i++) {
            Placement p = l.items.get(i);
            b.append(i == 0 ? "" : ", ").append("{\"id\": ");
            str(b, p.id).append(", \"as\": ");
            str(b, p.as == null ? "" : p.as.token).append('}');
        }
        b.append("], \"seen\": [");
        for (int i = 0; i < l.seen.size(); i++) str(b.append(i == 0 ? "" : ", "), l.seen.get(i));
        return b.append("]}").toString();
    }

    private static StringBuilder str(StringBuilder b, String s) {
        b.append('"');
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"');
    }

    /**
     * The menu to show for {@code stored} on this projector now (null: the default). Ids this catalogue lacks are left
     * out (the stored value keeps them, the feature may come back), duplicates after the first go, a look the item
     * does not have becomes its preferred one, All settings and the pencil come back where they are missing, and a
     * menu too tall for the screen loses items from its end until it fits (the card never scrolls).
     */
    public static QuickLayout reconcile(QuickLayout stored, QuickCatalog cat, QuickLayout defaults) {
        if (stored == null) return defaults;
        List<Placement> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Placement p : stored.items) {
            QuickItem it = cat.get(p.id);
            if (it == null || !ids.add(p.id)) continue;
            out.add(new Placement(p.id, it.allows(p.as) ? p.as : it.preferred()));
        }
        for (String pin : new String[]{QuickCatalog.EDIT, org.lumen.menu.MenuSpec.GENERAL}) {
            QuickItem it = cat.get(pin);
            if (it == null || ids.contains(pin)) continue;
            Placement d = defaults.get(pin);
            out.add(new Placement(pin, d != null ? d.as : it.preferred()));
            ids.add(pin);
        }
        QuickLayout l = new QuickLayout(QuickLayout.VERSION, stored.density, out, stored.seen);
        while (!QuickPacker.pack(l).fits()) {
            int drop = -1;
            for (int i = l.items.size() - 1; i >= 0 && drop < 0; i--) {
                QuickItem it = cat.get(l.items.get(i).id);
                if (it != null && !it.pinned()) drop = i;
            }
            if (drop < 0) break;
            List<Placement> fewer = new ArrayList<>(l.items);
            fewer.remove(drop);
            l = l.with(fewer);
        }
        return l;
    }

    /**
     * What to store after the user changed {@code edited}: those items, the ones {@code stored} keeps for features
     * missing now after the item they followed, and every catalogue id as seen.
     */
    public static QuickLayout toStore(QuickLayout stored, QuickLayout edited, QuickCatalog cat) {
        List<Placement> out = new ArrayList<>(edited.items);
        if (stored != null) {
            Placement before = null;
            for (Placement p : stored.items) {
                if (!cat.has(p.id) && !edited.has(p.id) && indexOf(out, p.id) < 0) {
                    int at = before == null ? 0 : indexOf(out, before.id) + 1;
                    out.add(Math.max(0, at), p);
                }
                before = p;
            }
        }
        Set<String> seen = new LinkedHashSet<>();
        if (stored != null) seen.addAll(stored.seen);
        seen.addAll(cat.ids());
        return new QuickLayout(QuickLayout.VERSION, edited.density, out, seen);
    }

    private static int indexOf(List<Placement> l, String id) {
        for (int i = 0; i < l.size(); i++) if (l.get(i).id.equals(id)) return i;
        return -1;
    }
}
