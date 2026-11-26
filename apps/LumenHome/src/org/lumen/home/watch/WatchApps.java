// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.watch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The "where to watch" table ({@code assets/watch/apps.tsv}): per app its packages, name, Russian phrase, kind, TMDB
 * provider ids and how a title is opened in it. Plain Java: the host test reads the shipped file itself.
 */
public final class WatchApps {
    public static final String ASSET = "watch/apps.tsv";

    public enum Kind { PROVIDER, CATALOG, PLATFORM }

    public enum Open { TMDB, SEARCH, URL, LAUNCH }

    /** One app of the table. */
    public static final class Row {
        public final List<String> pkgs;
        public final String name, inRu, url;
        public final Kind kind;
        public final List<Integer> tmdb;
        public final Open open;

        Row(List<String> pkgs, String name, String inRu, Kind kind, List<Integer> tmdb, Open open, String url) {
            this.pkgs = pkgs;
            this.name = name;
            this.inRu = inRu;
            this.kind = kind;
            this.tmdb = tmdb;
            this.open = open;
            this.url = url;
        }

        public boolean offers(int providerId) {
            return tmdb.contains(providerId);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final List<Row> mRows;

    private WatchApps(List<Row> rows) {
        mRows = Collections.unmodifiableList(rows);
    }

    public List<Row> rows() {
        return mRows;
    }

    /** The row of a package, or null. */
    public Row of(String pkg) {
        for (Row r : mRows) if (r.pkgs.contains(pkg)) return r;
        return null;
    }

    /** Parses the table; a malformed line throws (the host test runs it on the shipped file). */
    public static WatchApps parse(String text) {
        ArrayList<Row> rows = new ArrayList<>();
        int n = 0;
        for (String line : text.split("\n")) {
            n++;
            String l = line.replace("\r", "");
            if (l.trim().isEmpty() || l.startsWith("#")) continue;
            String[] f = l.split("\t", -1);
            if (f.length != 6) throw new IllegalArgumentException("line " + n + ": " + f.length + " fields");
            ArrayList<String> pkgs = new ArrayList<>();
            for (String p : f[0].split(",")) if (!p.trim().isEmpty()) pkgs.add(p.trim());
            ArrayList<Integer> ids = new ArrayList<>();
            if (!f[4].trim().equals("-")) for (String s : f[4].split(",")) ids.add(Integer.parseInt(s.trim()));
            String o = f[5].trim(), url = null;
            Open open;
            if (o.startsWith("url:")) {
                open = Open.URL;
                url = o.substring(4);
                if (!url.contains("{q}")) throw new IllegalArgumentException("line " + n + ": url without {q}");
            } else {
                open = Open.valueOf(o.toUpperCase(java.util.Locale.ROOT));
            }
            Kind kind = Kind.valueOf(f[3].trim().toUpperCase(java.util.Locale.ROOT));
            if (pkgs.isEmpty() || f[1].trim().isEmpty() || f[2].trim().isEmpty()) {
                throw new IllegalArgumentException("line " + n + ": empty field");
            }
            rows.add(new Row(Collections.unmodifiableList(pkgs), f[1].trim(), f[2].trim(), kind,
                    Collections.unmodifiableList(ids), open, url));
        }
        return new WatchApps(rows);
    }
}
