// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

/**
 * The texts of a "Continue watching" item (owner complaint: "Продолжить" four times, a cut
 * series synopsis): the title once, a season and episode line only for series, one time-left line, the episode's own
 * description. Pure: the words come through {@link Words}, so host tests run without resources.
 */
public final class ContinueText {
    /** TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_*. */
    public static final int WN_CONTINUE = 0, WN_NEXT = 1, WN_NEW = 2, WN_WATCHLIST = 3;
    public static final String DOT = " · ";
    public static final char ELLIPSIS = '…';

    public interface Words {
        /** "Сезон 1, серия 3". */
        String seasonEpisode(String season, String episode);

        /** "Серия 3". */
        String episode(String episode);

        /** "Осталось 14 мин". */
        String leftMin(int min);

        /** "Осталось 1 ч". */
        String leftHour(int hours);

        /** "Осталось 1 ч 12 мин". */
        String leftHourMin(int hours, int min);

        String nextEpisode();

        String newEpisode();
    }

    private ContinueText() {}

    /**
     * Second line, series only: "Сезон N, серия M · episode title", without a title "Сезон N, серия M", only an
     * episode number "Серия M · title", no numbers at all: "" (no line). An episode title equal to the show's title
     * is not repeated.
     */
    public static String seasonLine(String season, String episode, String episodeTitle, String title, Words w) {
        String s = trim(season), e = trim(episode), et = trim(episodeTitle);
        if (et.equalsIgnoreCase(trim(title))) et = "";
        String nums;
        if (!s.isEmpty() && !e.isEmpty()) nums = w.seasonEpisode(s, e);
        else if (!e.isEmpty()) nums = w.episode(e);
        else return "";
        return et.isEmpty() ? nums : nums + DOT + et;
    }

    /**
     * The one status line: NEXT "Следующая серия", NEW "Новая серия", a started item "Осталось K мин" (rounded up to
     * the minute; an hour or more "Осталось 1 ч 12 мин"), else "".
     */
    public static String leftLine(int wnType, long durMs, long posMs, Words w) {
        if (wnType == WN_NEXT) return w.nextEpisode();
        if (wnType == WN_NEW) return w.newEpisode();
        if (!started(durMs, posMs)) return "";
        long min = (durMs - posMs + 59_999L) / 60_000L;
        if (min < 60) return w.leftMin((int) Math.max(1, min));
        int h = (int) (min / 60), m = (int) (min % 60);
        return m == 0 ? w.leftHour(h) : w.leftHourMin(h, m);
    }

    /** The card shows the progress bar only for a started item that is not NEXT or NEW (they show a label). */
    public static boolean showsProgress(int wnType, long durMs, long posMs) {
        return wnType != WN_NEXT && wnType != WN_NEW && started(durMs, posMs);
    }

    /** Watched part 1..1000, -1 when not started. */
    public static int permille(long durMs, long posMs) {
        if (!started(durMs, posMs)) return -1;
        return (int) Math.max(1, Math.min(1000, posMs * 1000 / durMs));
    }

    /** The card's label instead of a bar: "Следующая серия" or "Новая серия", else "". */
    public static String tag(int wnType, Words w) {
        return wnType == WN_NEXT ? w.nextEpisode() : wnType == WN_NEW ? w.newEpisode() : "";
    }

    /**
     * The description under the lines: the program's own text. A series item shows nothing rather than the show's
     * synopsis: when its text equals {@code seriesOverview} (what a title lookup returned), and when the episode has
     * no title of its own (an app that knows only the show, such as Lumen Play, writes the show's overview there).
     */
    public static String description(String desc, boolean episodic, String episodeTitle, String seriesOverview) {
        String d = trim(desc);
        if (d.isEmpty()) return "";
        if (episodic && trim(episodeTitle).isEmpty()) return "";
        if (episodic && !trim(seriesOverview).isEmpty() && norm(d).equals(norm(seriesOverview))) return "";
        return d;
    }

    /** {@code s} cut before {@code end} at the last whole word, with an ellipsis; unchanged when it fits. */
    public static String cutAtWord(String s, int end) {
        if (s == null) return "";
        if (end >= s.length()) return s;
        if (end <= 0) return "";
        int cut = end;
        while (cut > 0 && !Character.isWhitespace(s.charAt(cut))) cut--;
        if (cut == 0) cut = end; // one long word: cut inside it
        String head = s.substring(0, cut).replaceAll("[\\s,.;:!?\\-]+$", "");
        return head + ELLIPSIS;
    }

    static boolean started(long dur, long pos) {
        return dur > 0 && pos > 0 && pos < dur;
    }

    static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    static String norm(String s) {
        return trim(s).toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }
}
