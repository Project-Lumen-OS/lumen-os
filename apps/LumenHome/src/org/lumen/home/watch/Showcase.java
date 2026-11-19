package org.lumen.home.watch;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Which TMDB titles the hero may show and in which order: only titles a user can watch now. Never a title whose
 * release or first air date is after today (or unknown). A film counts as watchable once it has a digital, disc or TV
 * release on or before today or any watch provider; a film that is only in cinemas is left out. Order: a title an
 * installed official app carries first, then the other watchable titles, then released titles whose details failed
 * to load, each group in TMDB popularity order.
 */
public final class Showcase {
    public static final int DROP = -1, IN_APP = 0, WATCHABLE = 1, UNKNOWN = 2;

    private Showcase() {}

    /** What the showcase needs to know about one title (from the TMDB cache). */
    public static final class Item {
        public final boolean tv;
        /** Release (film) or first air (series) date, yyyy-MM-dd, "" unknown. */
        public final String date;
        /** Film: the earliest digital, disc or TV release (yyyy-MM-dd), "" none known, null = details not loaded. */
        public final String homeDate;
        /** Watch providers in the user's region. */
        public final List<Integer> providers;
        /** Watch providers anywhere. */
        public final boolean anyProviders;

        public Item(boolean tv, String date, String homeDate, List<Integer> providers, boolean anyProviders) {
            this.tv = tv;
            this.date = date == null ? "" : date;
            this.homeDate = homeDate;
            this.providers = providers == null ? new ArrayList<>() : providers;
            this.anyProviders = anyProviders;
        }
    }

    /** True when the date is known and not after today (ISO dates compare as text). */
    public static boolean released(String date, String today) {
        return date != null && date.length() >= 10 && date.substring(0, 10).compareTo(today) <= 0;
    }

    /** The group of a title, {@link #DROP} for one the hero never shows. */
    public static int tier(Item it, String today, Predicate<List<Integer>> inInstalledApp) {
        if (!released(it.date, today)) return DROP;
        if (inInstalledApp.test(it.providers)) return IN_APP;
        if (it.tv || !it.providers.isEmpty() || it.anyProviders) return WATCHABLE;
        if (it.homeDate == null) return UNKNOWN;
        return released(it.homeDate, today) ? WATCHABLE : DROP;
    }

    /** The titles the hero shows, in its order (stable inside a group). */
    public static <T> List<T> order(List<T> titles, Function<T, Item> item, String today, Predicate<List<Integer>> inInstalledApp) {
        ArrayList<T> out = new ArrayList<>();
        for (int g = IN_APP; g <= UNKNOWN; g++) {
            for (T t : titles) if (tier(item.apply(t), today, inInstalledApp) == g) out.add(t);
        }
        return out;
    }
}
