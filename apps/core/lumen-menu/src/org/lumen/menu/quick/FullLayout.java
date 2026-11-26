// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu.quick;

import org.lumen.menu.FeatureId;
import org.lumen.menu.MenuJson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All settings as the user arranged it (Slava 2026-11-22: hide and reorder the rows of the full menu as the quick menu
 * editor allows): per section page the rows in his order and the rows he hid. One JSON in Settings.Secure {@link #KEY}
 * of the current user, nothing stored until the first change, read again whenever All settings opens (the settings
 * backup of Lumen Home may have restored it). Plain Java, no Android, no model checks: a row this projector does not
 * have is simply never shown, a row that came with an update stands at the end of its group. Rows move within their
 * group (the block under one header), so headers keep their rows. Immutable: every change gives a new layout.
 */
public final class FullLayout {
    public static final String KEY = "lumen_full_menu";
    public static final int VERSION = 1;
    private static final int MAX_ROWS = 400;
    public static final FullLayout EMPTY = new FullLayout(new HashMap<>(), new LinkedHashSet<>());

    /** section -> row ids in the user's order */
    private final Map<String, List<String>> order;
    /** "section/row id" of every hidden row */
    private final Set<String> hidden;

    private FullLayout(Map<String, List<String>> order, Set<String> hidden) {
        this.order = order;
        this.hidden = hidden;
    }

    public boolean isEmpty() {
        return hidden.isEmpty() && order.isEmpty();
    }

    private static String key(String section, FeatureId f) {
        return section + "/" + f.id;
    }

    public boolean shown(String section, FeatureId f) {
        return !hidden.contains(key(section, f));
    }

    /** The rows of one group of {@code section} as the page shows them: the user's order, hidden ones left out. */
    public List<FeatureId> arrange(String section, List<FeatureId> group) {
        List<FeatureId> out = new ArrayList<>();
        for (FeatureId f : ordered(section, group)) if (shown(section, f)) out.add(f);
        return out;
    }

    /** Every row of the group in the user's order (hidden ones too): the editor's list. */
    public List<FeatureId> ordered(String section, List<FeatureId> group) {
        List<String> o = order.get(section);
        if (o == null || o.isEmpty()) return new ArrayList<>(group);
        List<FeatureId> known = new ArrayList<>(), later = new ArrayList<>();
        for (FeatureId f : group) (o.contains(f.id) ? known : later).add(f);
        known.sort((a, b) -> Integer.compare(o.indexOf(a.id), o.indexOf(b.id)));
        known.addAll(later);
        return known;
    }

    /** The row shown again or hidden. */
    public FullLayout toggle(String section, FeatureId f) {
        Set<String> h = new LinkedHashSet<>(hidden);
        if (!h.remove(key(section, f))) h.add(key(section, f));
        return new FullLayout(copy(order), h);
    }

    /**
     * The row moved {@code delta} places (-1 up, 1 down) inside its group ({@code groups}: the section's groups in the
     * stock order); null when it is already at the group's edge or not in it.
     */
    public FullLayout move(String section, List<List<FeatureId>> groups, FeatureId f, int delta) {
        List<FeatureId> mine = null;
        for (List<FeatureId> g : groups) if (g.contains(f)) mine = g;
        if (mine == null || delta == 0) return null;
        List<FeatureId> now = ordered(section, mine);
        int at = now.indexOf(f), to = at + Integer.signum(delta);
        if (to < 0 || to >= now.size()) return null;
        Collections.swap(now, at, to);
        List<String> ids = new ArrayList<>();
        for (List<FeatureId> g : groups) for (FeatureId x : (g == mine ? now : ordered(section, g))) ids.add(x.id);
        Map<String, List<String>> o = copy(order);
        o.put(section, ids);
        return new FullLayout(o, new LinkedHashSet<>(hidden));
    }

    /** One page back to how it came. */
    public FullLayout reset(String section) {
        Map<String, List<String>> o = copy(order);
        o.remove(section);
        Set<String> h = new LinkedHashSet<>();
        for (String k : hidden) if (!k.startsWith(section + "/")) h.add(k);
        return new FullLayout(o, h);
    }

    private static Map<String, List<String>> copy(Map<String, List<String>> m) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : m.entrySet()) out.put(e.getKey(), new ArrayList<>(e.getValue()));
        return out;
    }

    // ------------------------------------------------------------------ JSON

    /** The stored value, or null when there is none, it cannot be read or it is of a newer version (never written over). */
    public static FullLayout parse(String json) {
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
        if (!(v instanceof Long) || (Long) v < 1 || (Long) v > VERSION) return null;
        Map<String, List<String>> order = new LinkedHashMap<>();
        if (m.get("order") instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) m.get("order")).entrySet()) {
                if (!(e.getValue() instanceof List)) continue;
                List<String> ids = new ArrayList<>();
                for (Object x : (List<?>) e.getValue()) {
                    if (x instanceof String && FeatureId.known((String) x) != null && !ids.contains(x) && ids.size() < MAX_ROWS) ids.add((String) x);
                }
                if (!ids.isEmpty()) order.put(String.valueOf(e.getKey()), ids);
            }
        }
        Set<String> hidden = new LinkedHashSet<>();
        if (m.get("hidden") instanceof List) {
            for (Object x : (List<?>) m.get("hidden")) {
                if (!(x instanceof String)) continue;
                String s = (String) x;
                int slash = s.indexOf('/');
                if (slash > 0 && FeatureId.known(s.substring(slash + 1)) != null && hidden.size() < MAX_ROWS) hidden.add(s);
            }
        }
        return new FullLayout(order, hidden);
    }

    public String write() {
        StringBuilder b = new StringBuilder("{\"v\": ").append(VERSION).append(", \"order\": {");
        boolean first = true;
        for (Map.Entry<String, List<String>> e : order.entrySet()) {
            if (!first) b.append(", ");
            first = false;
            str(b, e.getKey()).append(": ");
            list(b, e.getValue());
        }
        b.append("}, \"hidden\": ");
        list(b, new ArrayList<>(hidden));
        return b.append('}').toString();
    }

    private static void list(StringBuilder b, List<String> l) {
        b.append('[');
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) b.append(", ");
            str(b, l.get(i));
        }
        b.append(']');
    }

    private static StringBuilder str(StringBuilder b, String s) {
        b.append('"');
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\');
            if (c >= 0x20) b.append(c);
        }
        return b.append('"');
    }
}
