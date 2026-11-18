package org.lumen.home.model;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The order of the apps row and the Apps page (the design notes: "порядок пользователя"). Pure. Kept from 1.x: the user's
 * order (Prefs favorites, component names) and hidden apps (component or package); apps hidden by default (Lumen's own
 * system helpers) stay hidden unless the user showed them.
 */
public final class AppOrder {
    /** One launchable app: flattened component, package, label. */
    public static final class App {
        public final String component, pkg, label;

        public App(String component, String pkg, String label) {
            this.component = component;
            this.pkg = pkg;
            this.label = label == null ? "" : label;
        }
    }

    private AppOrder() {}

    public static boolean hidden(App a, Set<String> hidden, Set<String> unhidden, Set<String> defaultHidden) {
        if (hidden.contains(a.component) || hidden.contains(a.pkg)) return true;
        boolean byDefault = defaultHidden.contains(a.pkg) || defaultHidden.contains(a.component);
        return byDefault && !unhidden.contains(a.pkg) && !unhidden.contains(a.component);
    }

    /**
     * Visible apps: first the user's order (only apps still installed), then the rest: {@code firstPkgs} in their
     * order (YouTube, Kinopoisk, Play), then by label in the system language.
     */
    public static List<App> order(Collection<App> apps, List<String> userOrder, Set<String> hidden,
                                  Set<String> unhidden, Set<String> defaultHidden, List<String> firstPkgs, Locale loc) {
        ArrayList<App> visible = new ArrayList<>();
        for (App a : apps) if (!hidden(a, hidden, unhidden, defaultHidden)) visible.add(a);
        ArrayList<App> out = new ArrayList<>();
        HashSet<String> taken = new HashSet<>();
        for (String c : userOrder) {
            for (App a : visible) {
                if (a.component.equals(c) && taken.add(a.component)) out.add(a);
            }
        }
        ArrayList<App> rest = new ArrayList<>();
        for (App a : visible) if (!taken.contains(a.component)) rest.add(a);
        Collator col = Collator.getInstance(loc);
        col.setStrength(Collator.PRIMARY);
        rest.sort((x, y) -> {
            int ix = rank(firstPkgs, x.pkg), iy = rank(firstPkgs, y.pkg);
            if (ix != iy) return Integer.compare(ix, iy);
            int c = col.compare(x.label, y.label);
            return c != 0 ? c : x.component.compareTo(y.component);
        });
        out.addAll(rest);
        return out;
    }

    /** Apps the user hid (to show them again), by label. */
    public static List<App> hiddenOnes(Collection<App> apps, Set<String> hidden, Set<String> unhidden,
                                       Set<String> defaultHidden, Locale loc) {
        ArrayList<App> out = new ArrayList<>();
        for (App a : apps) if (hidden(a, hidden, unhidden, defaultHidden)) out.add(a);
        Collator col = Collator.getInstance(loc);
        out.sort((x, y) -> col.compare(x.label, y.label));
        return out;
    }

    /** The order after moving the item at {@code from} to {@code to} (both clamped), as component names. */
    public static List<String> move(List<String> order, int from, int to) {
        ArrayList<String> l = new ArrayList<>(order);
        if (l.isEmpty()) return l;
        int f = Math.max(0, Math.min(l.size() - 1, from)), t = Math.max(0, Math.min(l.size() - 1, to));
        String x = l.remove(f);
        l.add(t, x);
        return l;
    }

    static int rank(List<String> first, String pkg) {
        int i = first.indexOf(pkg);
        return i < 0 ? Integer.MAX_VALUE : i;
    }
}
