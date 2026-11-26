// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The order of the apps row and the Apps page (the design notes: "порядок пользователя"). Pure. One app per package
 * ({@link AppPick}): the user's order (Prefs favorites) and hidden apps are package names; apps hidden by default
 * (Lumen's own system helpers) stay hidden unless the user showed them.
 */
public final class AppOrder {
    /** One launchable app: package, label. */
    public static final class App {
        public final String pkg, label;

        public App(String pkg, String label) {
            this.pkg = pkg;
            this.label = label == null ? "" : label;
        }
    }

    private AppOrder() {}

    /**
     * Hidden unless the user shows them again (Customize, Hidden apps): Lumen's own system helpers and the service apps
     * with a launcher entry that are no app to open, such as Google's Android TV Remote Service of the Google editions
     * (2026-11-19: the tile is gone from Home by default, the service keeps working). Only the default: the
     * user's own hidden and shown sets win, so an app shown again stays shown.
     */
    public static final Set<String> DEFAULT_HIDDEN = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "org.lumen.home", "com.android.tv.settings", "org.lumen.tvinput", "org.lumen.setup", "org.lumen.updater",
            "com.android.tv.frameworkpackagestubs", "org.lineageos.setupwizard",
            // Google editions: setup, voice, remote and helper services, never a user app
            "com.google.android.tungsten.setupwraith", "com.google.android.katniss",
            "com.google.android.tv.remote.service", "com.google.android.gms", "com.google.android.gsf",
            "com.google.android.backdrop", "com.google.android.tvrecommendations", "com.google.android.apps.mediashell",
            "com.google.android.tts", "com.google.android.marvin.talkback", "com.google.android.ext.shared",
            "com.mtg.atvoverlay")));

    public static boolean hidden(App a, Set<String> hidden, Set<String> unhidden, Set<String> defaultHidden) {
        if (hidden.contains(a.pkg)) return true;
        return defaultHidden.contains(a.pkg) && !unhidden.contains(a.pkg);
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
        for (String k : userOrder) {
            String pkg = AppPick.pkgOf(k);
            for (App a : visible) {
                if (a.pkg.equals(pkg) && taken.add(a.pkg)) out.add(a);
            }
        }
        ArrayList<App> rest = new ArrayList<>();
        for (App a : visible) if (!taken.contains(a.pkg)) rest.add(a);
        Collator col = Collator.getInstance(loc);
        col.setStrength(Collator.PRIMARY);
        rest.sort((x, y) -> {
            int ix = rank(firstPkgs, x.pkg), iy = rank(firstPkgs, y.pkg);
            if (ix != iy) return Integer.compare(ix, iy);
            int c = col.compare(x.label, y.label);
            return c != 0 ? c : x.pkg.compareTo(y.pkg);
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

    /** The order after moving the item at {@code from} to {@code to} (both clamped), as package names. */
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
