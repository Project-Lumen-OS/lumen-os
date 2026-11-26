// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.tmdb;


import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which TMDB search result is the program on a Continue watching card (Lumen OS 1.0.5; pure Java,
 * test/TmdbTest). The query is the card's title without what file names add (" S01 E01", the year,
 * "1080p", "WEB-DL", ...). Only a confident match counts, the art fills the whole screen: a result needs key
 * art (backdrop_path) and its title or original title must BE the query (normalised as ContinueRules.norm: case,
 * "ё", punctuation and spaces do not count), a title that only contains it is no match ("Кухня в Париже" is not
 * "Кухня"), and an item with a season or episode takes only a series (tv). Then the year when the title carried
 * one, and TMDB's popularity last. No match keeps the card's own art (the frame).
 */
public final class TmdbMatch {
    private TmdbMatch() {}

    /** One result of /3/search/multi. */
    public static final class Result {
        public String type = "";      // "tv", "movie" ("person" and others are skipped)
        public String title = "";     // title (movie) or name (tv), in the asked language
        public String original = "";  // original_title / original_name
        public String backdrop = "";  // backdrop_path ("/abc.jpg"), "" = none
        public double popularity;
        public int year;              // of release_date / first_air_date, 0 = unknown

        public Result() {}

        public Result(String type, String title, String original, String backdrop, double popularity, int year) {
            this.type = type;
            this.title = title == null ? "" : title;
            this.original = original == null ? "" : original;
            this.backdrop = backdrop == null ? "" : backdrop;
            this.popularity = popularity;
            this.year = year;
        }
    }

    private static final Pattern EPISODE = Pattern.compile("(?i)\\bS\\d{1,2}\\s*E\\d{1,3}\\b|\\b\\d{1,2}x\\d{2}\\b|\\bS\\d{1,2}\\b");
    private static final Pattern YEAR = Pattern.compile("(?<![\\d])(19\\d{2}|20\\d{2})(?![\\d])");
    private static final Pattern RIP = Pattern.compile("(?i)\\b(\\d{3,4}p|[48]k|uhd|hdr\\d*|sdr|bdrip|bdremux|bluray|blu-ray"
            + "|web-?dl|web-?rip|webrip|hdtv|dvdrip|remux|x26[45]|h\\.?26[45]|hevc|avc|aac\\d?|ac3|dts|ddp?\\d?"
            + "|repack|okko|amzn)\\b");

    /** The title to search: from the start up to the first episode mark, year or rip word; trimmed. */
    public static String query(String raw) {
        if (raw == null) return "";
        String s = raw.replace('_', ' ').replace('.', ' ').trim();
        int cut = s.length();
        for (Pattern p : new Pattern[]{EPISODE, YEAR, RIP}) {
            Matcher m = p.matcher(s);
            if (m.find() && m.start() > 0) cut = Math.min(cut, m.start());
        }
        s = s.substring(0, cut).trim();
        // trailing separators left by the cut ("Title -", "Title (")
        while (!s.isEmpty() && "-–([,:".indexOf(s.charAt(s.length() - 1)) >= 0) s = s.substring(0, s.length() - 1).trim();
        return s;
    }

    /** The year a file-name title carries ("Gran Turismo 2023 1080p" -> 2023), else 0. */
    public static int year(String raw) {
        if (raw == null) return 0;
        Matcher m = YEAR.matcher(raw);
        while (m.find()) if (m.start() > 0) return Integer.parseInt(m.group(1));
        return 0;
    }

    /** True when the title itself names an episode or season ("S01 E01", "1x02"). */
    public static boolean episodic(String raw) {
        return raw != null && EPISODE.matcher(raw).find();
    }

    /** True when the result's title or original title is the query (normalised). */
    static boolean exact(String query, Result r) {
        String q = TmdbMatch.normTitle(query);
        if (q.isEmpty()) return false;
        for (String t : new String[]{r.title, r.original}) if (q.equals(TmdbMatch.normTitle(t))) return true;
        return false;
    }

    /**
     * The result that is the program, or null.
     *
     * @param title    the card's title (raw)
     * @param episodic the card is an episode (season / episode columns) or its title says so
     */
    public static Result pick(List<Result> results, String title, boolean episodic) {
        if (results == null) return null;
        String q = query(title);
        int year = year(title);
        boolean ep = episodic || episodic(title);
        Result best = null;
        double bestScore = 0;
        for (Result r : results) {
            if (r == null || r.backdrop.isEmpty() || !("tv".equals(r.type) || "movie".equals(r.type))) continue;
            if (ep && !"tv".equals(r.type) || !exact(q, r)) continue;
            double s = 100;
            if (year > 0 && r.year == year) s += 30;
            s += Math.min(30, Math.log1p(Math.max(0, r.popularity)) * 5);
            if (best == null || s > bestScore) {
                best = r;
                bestScore = s;
            }
        }
        return best;
    }

    /**
     * The languages to search in, each once: the system's, then the one the title is written in when its
     * script says so (Cyrillic: Russian, Ukrainian with і ї є ґ), then English. TMDB matches titles in
     * the asked language and the original one, so a Russian title on an English UI ("Великолепный век":
     * "Magnificent Century" / "Muhteşem Yüzyıl") is only found in Russian (seen on the Z9X).
     */
    public static java.util.List<String> languages(String title, String system) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>(3);
        if (system != null && !system.isEmpty()) out.add(system);
        String script = scriptLanguage(title);
        if (script != null && !sameLanguage(out, script)) out.add(script);
        if (!sameLanguage(out, "en-US")) out.add("en-US");
        return out;
    }

    private static boolean sameLanguage(java.util.List<String> l, String tag) {
        String lang = tag.split("-")[0];
        for (String t : l) if (t.split("-")[0].equalsIgnoreCase(lang)) return true;
        return false;
    }

    /** "ru-RU" / "uk-UA" for a Cyrillic title, else null. */
    static String scriptLanguage(String title) {
        if (title == null) return null;
        boolean cyr = false, uk = false;
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC) {
                cyr = true;
                if ("іїєґІЇЄҐ".indexOf(c) >= 0) uk = true;
            }
        }
        return !cyr ? null : uk ? "uk-UA" : "ru-RU";
    }

    /** Lower case, "ё" as "е", letters and digits only (one rule with Continue's one item per series). */
    public static String normTitle(String s) {
        return org.lumen.home.model.ContinueRules.norm(s);
    }
}
