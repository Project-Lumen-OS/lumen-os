// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The user's Home layout (4PDA 2026-11-22: hide the big clock and the Continue row, more rows of apps, folders): the
 * look and its values, the order of the apps and folders, hidden apps, the folders. One JSON in Settings.Secure
 * {@link #KEY} of the current user (data/LayoutStore is the only class that reads or writes it), so the settings
 * backup takes it with the menus and a restore after a reinstall brings it back. Nothing is stored until the first
 * change: until then the layout comes from the old Home settings ({@link #fromOld}). Pure, mutable value: change a
 * {@link #copy} and store it.
 */
public final class HomeLayout {
    public static final String KEY = "lumen_home_layout";
    public static final int VERSION = 1;
    public static final int MIN_ROWS = 1, MAX_ROWS = 3;
    /** A folder in the order: this prefix and its id. */
    public static final String FOLDER = "folder:";
    public static final int MAX_FOLDERS = 24, MAX_NAME = 40, MAX_LIST = 600;

    public String look = Looks.CLASSIC;
    public int hero = Looks.FULL;
    public boolean clock = true, cont = true, showcase = true, weather = true;
    public int rows = 1;
    /** The user moved something: {@link #order} is his (else the default order: YouTube, Kinopoisk, Play, by name). */
    public boolean orderSet;
    /** Packages and folder tokens in the order of the dock and the Apps page. */
    public final List<String> order = new ArrayList<>();
    public final Set<String> hidden = new TreeSet<>(), shown = new TreeSet<>();
    public final List<Folder> folders = new ArrayList<>();

    /** A folder of the dock: its id, its name and its apps in their order. */
    public static final class Folder {
        public final String id;
        public String name;
        public final List<String> apps = new ArrayList<>();

        public Folder(String id, String name) {
            this.id = id;
            this.name = name(name);
        }

        public String token() {
            return FOLDER + id;
        }
    }

    public static int rows(int n) {
        return Math.max(MIN_ROWS, Math.min(MAX_ROWS, n));
    }

    static String name(String s) {
        String t = s == null ? "" : s.replace('\n', ' ').replace('\t', ' ').trim();
        return t.length() > MAX_NAME ? t.substring(0, MAX_NAME).trim() : t;
    }

    private static boolean pkg(String p) {
        return p != null && !p.isEmpty() && p.length() <= 255 && p.indexOf('\n') < 0 && !p.startsWith(FOLDER);
    }

    /** The layout of a Home that never stored one: the old settings (order, hidden apps, Continue, weather). */
    public static HomeLayout fromOld(List<String> favorites, boolean favoritesSet, Collection<String> hidden,
                                     Collection<String> shown, boolean cont, boolean weather) {
        HomeLayout l = new HomeLayout();
        for (String p : favorites) if (pkg(p) && !l.order.contains(p)) l.order.add(p);
        l.orderSet = favoritesSet;
        for (String p : hidden) if (pkg(p)) l.hidden.add(p);
        for (String p : shown) if (pkg(p)) l.shown.add(p);
        l.cont = cont;
        l.weather = weather;
        return l;
    }

    /** A look's values (its hero, clock, Continue, recommendations, rows); folders, order and hidden apps stay. */
    public void apply(Looks.Look k) {
        look = k.id;
        hero = k.hero;
        clock = k.clock;
        cont = k.cont;
        showcase = k.showcase;
        rows = k.rows;
    }

    public HomeLayout copy() {
        HomeLayout l = parse(write());
        return l != null ? l : new HomeLayout();
    }

    // ------------------------------------------------------------------ folders

    public Folder folder(String id) {
        for (Folder f : folders) if (f.id.equals(id)) return f;
        return null;
    }

    /** The folder an app is in, or null. */
    public Folder folderOf(String pkg) {
        for (Folder f : folders) if (f.apps.contains(pkg)) return f;
        return null;
    }

    /** The id of the folder a token names ({@code folder:<id>}), or null for a package. */
    public static String folderId(String token) {
        return token != null && token.startsWith(FOLDER) ? token.substring(FOLDER.length()) : null;
    }

    private String nextId() {
        int n = 1;
        while (folder("f" + n) != null) n++;
        return "f" + n;
    }

    /**
     * A new folder named {@code name} holding {@code pkg}, standing where the app stood in {@code dock} (the order the
     * user sees now); null when there are {@link #MAX_FOLDERS} already.
     */
    public Folder newFolder(String name, String pkg, List<String> dock) {
        if (folders.size() >= MAX_FOLDERS) return null;
        Folder f = new Folder(nextId(), name);
        folders.add(f);
        keepOrder(dock);
        int at = order.indexOf(pkg);
        if (at < 0) order.add(f.token());
        else order.set(at, f.token());
        if (pkg(pkg)) moveToFolder(pkg, f.id);
        return f;
    }

    /** {@code pkg} into folder {@code id} (out of any other folder), last in it. */
    public boolean moveToFolder(String pkg, String id) {
        Folder to = folder(id);
        if (to == null || !pkg(pkg)) return false;
        for (Folder f : folders) f.apps.remove(pkg);
        to.apps.add(pkg);
        order.remove(pkg);
        return true;
    }

    /** {@code pkg} out of its folder: right after the folder in the order. A folder left empty goes. */
    public boolean removeFromFolder(String pkg, List<String> dock) {
        Folder f = folderOf(pkg);
        if (f == null) return false;
        keepOrder(dock);
        f.apps.remove(pkg);
        int at = order.indexOf(f.token());
        order.remove(pkg);
        if (at < 0) order.add(pkg);
        else order.add(at + 1, pkg);
        if (f.apps.isEmpty()) deleteFolder(f.id, dock);
        return true;
    }

    /** The folder goes, its apps come back where it stood, in its order. */
    public boolean deleteFolder(String id, List<String> dock) {
        Folder f = folder(id);
        if (f == null) return false;
        keepOrder(dock);
        int at = order.indexOf(f.token());
        order.remove(f.token());
        if (at < 0) at = order.size();
        for (String p : f.apps) {
            order.remove(p);
            order.add(Math.min(at++, order.size()), p);
        }
        folders.remove(f);
        return true;
    }

    public boolean rename(String id, String name) {
        Folder f = folder(id);
        String n = name(name);
        if (f == null || n.isEmpty()) return false;
        f.name = n;
        return true;
    }

    /** The order the user sees now becomes his order (a move, a new folder): from then on it is kept as it is. */
    public void keepOrder(List<String> dock) {
        if (dock == null) return;
        ArrayList<String> next = new ArrayList<>();
        for (String t : dock) if (!next.contains(t)) next.add(t);
        for (String t : order) if (!next.contains(t) && folderId(t) == null && folderOf(t) == null) next.add(t);
        order.clear();
        order.addAll(next);
        orderSet = true;
    }

    /** One more row of apps, after the last one the first again (the settings row cycles). */
    public void cycleRows() {
        rows = rows >= MAX_ROWS ? MIN_ROWS : rows + 1;
    }

    // ------------------------------------------------------------------ JSON

    /**
     * The stored value, repaired (rows in range, an unknown hero is full, a package in one folder only, folders
     * without a name or an id go), or null when there is none, it cannot be read or it is of a newer version (never
     * written over then: a newer Home may own it).
     */
    public static HomeLayout parse(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        Object o;
        try {
            o = Json.parse(json);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!(o instanceof Map)) return null;
        Map<?, ?> m = (Map<?, ?>) o;
        long v = Json.num(m, "v", -1);
        if (v < 1 || v > VERSION) return null;
        HomeLayout l = new HomeLayout();
        String look = Json.str(m, "look", Looks.CLASSIC);
        l.look = look.matches("[a-z][a-z0-9_]{0,23}") ? look : Looks.CLASSIC;
        int h = Looks.hero(Json.str(m, "hero", "full"));
        l.hero = h < 0 ? Looks.FULL : h;
        l.clock = Json.bool(m, "clock", true);
        l.cont = Json.bool(m, "cont", true);
        l.showcase = Json.bool(m, "showcase", true);
        l.weather = Json.bool(m, "weather", true);
        l.rows = rows((int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Json.num(m, "rows", 1))));
        l.orderSet = Json.bool(m, "orderSet", false);
        for (String p : Json.strings(m, "hidden")) if (pkg(p) && l.hidden.size() < MAX_LIST) l.hidden.add(p);
        for (String p : Json.strings(m, "shown")) if (pkg(p) && l.shown.size() < MAX_LIST) l.shown.add(p);
        Object fs = m.get("folders");
        if (fs instanceof List) {
            for (Object e : (List<?>) fs) {
                if (!(e instanceof Map) || l.folders.size() >= MAX_FOLDERS) continue;
                Map<?, ?> fm = (Map<?, ?>) e;
                String id = Json.str(fm, "id", "");
                String name = name(Json.str(fm, "name", ""));
                if (!id.matches("[a-z0-9]{1,8}") || name.isEmpty() || l.folder(id) != null) continue;
                Folder f = new Folder(id, name);
                for (String p : Json.strings(fm, "apps")) {
                    if (pkg(p) && l.folderOf(p) == null && !f.apps.contains(p) && f.apps.size() < MAX_LIST) f.apps.add(p);
                }
                l.folders.add(f);
            }
        }
        for (String t : Json.strings(m, "order")) {
            if (l.order.size() >= MAX_LIST || l.order.contains(t)) continue;
            String fid = folderId(t);
            if (fid != null ? l.folder(fid) != null : pkg(t) && l.folderOf(t) == null) l.order.add(t);
        }
        for (Folder f : l.folders) if (!l.order.contains(f.token())) l.order.add(f.token());
        return l;
    }

    public String write() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("v", (long) VERSION);
        m.put("look", look);
        m.put("hero", Looks.heroName(hero));
        m.put("clock", clock);
        m.put("cont", cont);
        m.put("showcase", showcase);
        m.put("weather", weather);
        m.put("rows", (long) rows);
        m.put("orderSet", orderSet);
        m.put("order", new ArrayList<>(order));
        m.put("hidden", new ArrayList<>(hidden));
        m.put("shown", new ArrayList<>(shown));
        List<Object> fl = new ArrayList<>();
        for (Folder f : folders) {
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("id", f.id);
            fm.put("name", f.name);
            fm.put("apps", new ArrayList<>(f.apps));
            fl.add(fm);
        }
        m.put("folders", fl);
        return Json.write(m);
    }
}
