package org.lumen.home.watch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Which installed app a title opens in, and the order of the "Искать в" tiles, from the table ({@link WatchApps}) and
 * what the device answers ({@link Device}). Hero priority: an official provider that has the title in the user's region
 * (TMDB watch providers), then the catalogue apps in table order (Lampa first), else null = Lumen search ("Найти").
 * An app whose intent does not resolve is never picked, so the button never leads to a dead end.
 */
public final class WatchPick {
    private WatchPick() {}

    /** What the device says about a table row. */
    public interface Device {
        /** The installed package of the row (the first of its packages that is installed), or null. */
        String installed(WatchApps.Row r);

        /** True when the row's way of opening a title ({@link WatchApps.Open}) resolves in that package. */
        boolean opens(WatchApps.Row r, String pkg);
    }

    /** The chosen app: its row and its installed package. */
    public static final class Pick {
        public final WatchApps.Row row;
        public final String pkg;

        Pick(WatchApps.Row row, String pkg) {
            this.row = row;
            this.pkg = pkg;
        }

        @Override
        public String toString() {
            return row.name + "@" + pkg;
        }
    }

    /** The app the watch button opens the title in, or null (Lumen search with the title). */
    public static Pick forTitle(WatchApps t, Collection<Integer> providers, Device d) {
        for (WatchApps.Row r : t.rows()) {
            if (r.kind != WatchApps.Kind.PROVIDER || !has(r, providers)) continue;
            Pick p = usable(r, d);
            if (p != null) return p;
        }
        for (WatchApps.Row r : t.rows()) {
            if (r.kind != WatchApps.Kind.CATALOG) continue;
            Pick p = usable(r, d);
            if (p != null) return p;
        }
        return null;
    }

    /** True when an installed official provider app has the title (it goes first on the hero). */
    public static boolean inInstalledProvider(WatchApps t, Collection<Integer> providers, Device d) {
        for (WatchApps.Row r : t.rows()) {
            if (r.kind == WatchApps.Kind.PROVIDER && has(r, providers) && d.installed(r) != null) return true;
        }
        return false;
    }

    /** TMDB provider ids of the installed provider apps (the discover query for titles they carry). */
    public static List<Integer> installedProviderIds(WatchApps t, Device d) {
        ArrayList<Integer> out = new ArrayList<>();
        for (WatchApps.Row r : t.rows()) {
            if (r.kind != WatchApps.Kind.PROVIDER || d.installed(r) == null) continue;
            for (int id : r.tmdb) if (!out.contains(id)) out.add(id);
        }
        return out;
    }

    /**
     * "Искать в": the installed table apps, catalogue first (Lampa leads), then providers, then video platforms, each
     * in table order; then the other installed apps that declare search ({@code others}, kept in their order).
     */
    public static List<String> searchOrder(WatchApps t, Device d, List<String> others) {
        ArrayList<String> out = new ArrayList<>();
        for (WatchApps.Kind k : new WatchApps.Kind[] { WatchApps.Kind.CATALOG, WatchApps.Kind.PROVIDER, WatchApps.Kind.PLATFORM }) {
            for (WatchApps.Row r : t.rows()) {
                if (r.kind != k) continue;
                String p = d.installed(r);
                if (p != null && !out.contains(p)) out.add(p);
            }
        }
        for (String p : others) if (!out.contains(p) && t.of(p) == null) out.add(p);
        return out;
    }

    private static boolean has(WatchApps.Row r, Collection<Integer> providers) {
        if (providers == null) return false;
        for (int id : providers) if (r.offers(id)) return true;
        return false;
    }

    private static Pick usable(WatchApps.Row r, Device d) {
        if (r.open == WatchApps.Open.LAUNCH) return null;
        String p = d.installed(r);
        return p != null && d.opens(r, p) ? new Pick(r, p) : null;
    }
}
