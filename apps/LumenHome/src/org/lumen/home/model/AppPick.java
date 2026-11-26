// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One app = one entry, whatever number of launcher activities its package declares. INCY VPN ships
 * {@code .MainActivity} and the icon alias {@code .MainActivityDefault}, both MAIN + LEANBACK_LAUNCHER, and Home showed
 * it twice. The activity is the one the system launches: the package's leanback launch intent, else its launch
 * intent, else the first resolved activity. Pure.
 *
 * <p>Home before this kept the user's order and hidden apps by flattened component ("pkg/cls"); {@link #order},
 * {@link #hidden} and {@link #shown} move them to package keys once.
 */
public final class AppPick {
    private AppPick() {}

    /** A launcher activity: package, flattened component, found by the leanback query. */
    public static final class Activity {
        public final String pkg, component;
        public final boolean leanback;

        public Activity(String pkg, String component, boolean leanback) {
            this.pkg = pkg;
            this.component = component;
            this.leanback = leanback;
        }
    }

    /** The flattened component of the package's launch intents as PackageManager gives them, or null. */
    public interface Launch {
        String leanback(String pkg);

        String main(String pkg);
    }

    /** The activity to show per package, packages in the order they were first resolved. */
    public static List<Activity> onePerPackage(List<Activity> resolved, Launch system) {
        Map<String, List<Activity>> byPkg = new LinkedHashMap<>();
        for (Activity a : resolved) byPkg.computeIfAbsent(a.pkg, k -> new ArrayList<>()).add(a);
        ArrayList<Activity> out = new ArrayList<>();
        for (Map.Entry<String, List<Activity>> e : byPkg.entrySet()) {
            String pkg = e.getKey();
            List<Activity> mine = e.getValue();
            boolean leanback = false;
            for (Activity a : mine) leanback |= a.leanback;
            String cn = system.leanback(pkg);
            if (cn == null) cn = system.main(pkg);
            out.add(new Activity(pkg, cn != null ? cn : mine.get(0).component, leanback));
        }
        return out;
    }

    /** "pkg/cls" -> "pkg", a package stays as it is. */
    public static String pkgOf(String key) {
        int i = key.indexOf('/');
        return i < 0 ? key : key.substring(0, i);
    }

    /** True when a stored list still holds component keys. */
    public static boolean oldKeys(Collection<String> keys) {
        for (String k : keys) if (k.indexOf('/') >= 0) return true;
        return false;
    }

    /** The user's order by package: each app keeps the place of its first entry. */
    public static List<String> order(List<String> stored) {
        return new ArrayList<>(new LinkedHashSet<>(pkgsOf(stored)));
    }

    /**
     * Hidden apps by package. A hidden component hides its app only when every launcher activity of that package was
     * hidden (or the package resolves none now): a user who hid one of two duplicate tiles keeps the app, once.
     */
    public static Set<String> hidden(Set<String> stored, List<Activity> resolved) {
        Map<String, Set<String>> comps = new HashMap<>();
        for (Activity a : resolved) comps.computeIfAbsent(a.pkg, k -> new HashSet<>()).add(a.component);
        Set<String> out = new HashSet<>();
        for (String k : stored) {
            String pkg = pkgOf(k);
            Set<String> all = comps.get(pkg);
            if (k.equals(pkg) || all == null || stored.containsAll(all)) out.add(pkg);
        }
        return out;
    }

    /** Apps the user showed again, by package. */
    public static Set<String> shown(Set<String> stored) {
        return new HashSet<>(pkgsOf(stored));
    }

    private static List<String> pkgsOf(Collection<String> keys) {
        ArrayList<String> out = new ArrayList<>();
        for (String k : keys) out.add(pkgOf(k));
        return out;
    }
}
