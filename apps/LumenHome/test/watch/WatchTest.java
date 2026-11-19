package org.lumen.home.watch;

import org.lumen.home.data.Card;
import org.lumen.home.model.HeroBind;
import org.lumen.home.search.Relevance;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The hero recommends only titles a user can watch now and opens them in the right app: the shipped "where to watch"
 * table, the release filter and order, the app choice (provider with the title, then Lampa, then other catalogue apps,
 * else Lumen search), the "Искать в" order, the region, the search results' relevance, the hero rebinding on a new
 * button text. Plain JDK: sh apps/LumenHome/test/watch/run.sh
 */
public final class WatchTest {
    static int passed, failed;
    static final String TODAY = "2026-11-19";

    static void check(boolean ok, String what) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL " + what);
        }
    }

    static void eq(Object a, Object b, String what) {
        check(a == null ? b == null : a.equals(b), what + ": got [" + a + "] want [" + b + "]");
    }

    public static void main(String[] a) throws Exception {
        WatchApps t;
        try {
            t = WatchApps.parse(new String(Files.readAllBytes(Paths.get(System.getProperty("home.dir"), "assets",
                    WatchApps.ASSET)), StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            check(false, "the table ships in assets/" + WatchApps.ASSET);
            t = WatchApps.parse("");
        }
        table(t);
        release();
        order();
        pick(t);
        searchOrder(t);
        region();
        relevance();
        heroBind();
        try {
            wiring();
        } catch (java.io.IOException e) {
            check(false, "wiring: " + e);
        }
        System.out.println("Watch: " + passed + " checks, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ the table

    static void table(WatchApps t) {
        check(t.rows().size() >= 21, "table: every app the owner named");
        Set<String> seen = new HashSet<>();
        boolean unique = true;
        for (WatchApps.Row r : t.rows()) for (String p : r.pkgs) unique &= seen.add(p);
        check(unique, "table: a package belongs to one row");
        String[] named = { "top.rootu.lampa", "mobi.zona", "com.seena.app", "dkc.video.hdbox", "net.filmix.filmix",
                "com.falcofemoralis.hdrezkaapp", "com.yoku.marumovie", "com.vk.tv", "org.smarttube.stable",
                "com.google.android.youtube.tv", "ru.kinopoisk.tv", "ru.more.play", "ru.ivi.client", "ru.rt.video.app.tv",
                "ru.start.androidmobile", "gpm.tnt_premier", "ru.mts.mtstv", "com.megogo.application", "com.amdteka",
                "com.ctcmediagroup.videomore", "com.netflix.ninja" };
        for (String p : named) check(t.of(p) != null, "table has " + p);
        WatchApps.Row lampa = t.of("top.rootu.lampa");
        if (lampa == null) return;
        check(lampa.kind == WatchApps.Kind.CATALOG && lampa.open == WatchApps.Open.TMDB, "Lampa: catalogue, opens by TMDB id");
        WatchApps.Row firstCatalog = null;
        for (WatchApps.Row r : t.rows()) if (firstCatalog == null && r.kind == WatchApps.Kind.CATALOG) firstCatalog = r;
        check(firstCatalog == lampa, "Lampa leads the catalogue apps");
        eq(t.of("ru.kinopoisk.tv").tmdb, Collections.singletonList(117), "Kinopoisk TMDB id");
        eq(t.of("ru.more.play").tmdb, Collections.singletonList(115), "Okko TMDB id");
        eq(t.of("ru.ivi.client").tmdb, Collections.singletonList(113), "ivi TMDB id");
        check(t.of("com.netflix.ninja").offers(8), "Netflix TMDB id");
        eq(t.of("ru.kinopoisk.tv").inRu, "в Кинопоиске", "Russian phrase");
        for (WatchApps.Row r : t.rows()) {
            if (r.kind == WatchApps.Kind.PLATFORM) check(r.tmdb.isEmpty(), "platform without provider ids: " + r);
            if (r.open == WatchApps.Open.URL) check(r.url.contains("{q}") && r.url.startsWith("https://"), "url row: " + r);
        }
        check(t.of("org.smarttube.beta") == t.of("com.liskovsoft.smarttubetv.beta"), "SmartTube forks in one row");
        boolean threw = false;
        try {
            WatchApps.parse("x.y\tX\tв X\tcatalog\t-\n");
        } catch (RuntimeException e) {
            threw = true;
        }
        check(threw, "a short line is refused");
        threw = false;
        try {
            WatchApps.parse("x.y\tX\tв X\tcatalog\t-\turl:https://x/\n");
        } catch (RuntimeException e) {
            threw = true;
        }
        check(threw, "a url without {q} is refused");
    }

    // ------------------------------------------------------------------ release filter and order

    static Showcase.Item film(String date, String home, Integer... prov) {
        return new Showcase.Item(false, date, home, new ArrayList<>(Arrays.asList(prov)), prov.length > 0);
    }

    static Showcase.Item series(String date) {
        return new Showcase.Item(true, date, null, null, false);
    }

    static int tier(Showcase.Item it) {
        return Showcase.tier(it, TODAY, p -> p.contains(117));
    }

    static void release() {
        check(!Showcase.released("2026-12-16", TODAY), "Мстители: Доктор Дум (Dec 16) is not released");
        check(Showcase.released(TODAY, TODAY) && Showcase.released("2022-09-01", TODAY), "today and before are released");
        check(!Showcase.released("", TODAY) && !Showcase.released(null, TODAY) && !Showcase.released("2026", TODAY), "no date: never");
        eq(tier(film("2026-12-16", null)), Showcase.DROP, "announced film dropped");
        eq(tier(film("2026-12-16", "", 117)), Showcase.DROP, "announced film dropped even with a provider");
        eq(tier(film("2026-11-18", "")), Showcase.DROP, "only in cinemas since yesterday: dropped");
        eq(tier(film("2026-11-03", "")), Showcase.DROP, "only in cinemas: dropped");
        eq(tier(film("2026-07-15", "2026-11-17")), Showcase.WATCHABLE, "digital release out: watchable");
        eq(tier(film("2026-09-16", "2026-11-26")), Showcase.DROP, "digital release next week: dropped");
        eq(tier(film("2026-09-16", null)), Showcase.UNKNOWN, "details failed: last");
        eq(tier(film("2026-01-10", "", 115)), Showcase.WATCHABLE, "a provider somewhere: watchable");
        eq(tier(film("2026-01-10", "", 117)), Showcase.IN_APP, "in an installed app: first");
        eq(tier(series("2022-09-01")), Showcase.WATCHABLE, "aired series: watchable");
        eq(tier(series("2027-01-01")), Showcase.DROP, "announced series: dropped");
    }

    static void order() {
        List<String> names = Arrays.asList("doom", "rings", "kp", "unknown", "odyssey", "cinema");
        List<Showcase.Item> items = Arrays.asList(film("2026-12-16", null), series("2022-09-01"),
                film("2025-03-01", "", 117), film("2026-09-16", null), film("2026-07-15", "2026-11-17"),
                film("2026-11-18", ""));
        List<String> got = Showcase.order(names, n -> items.get(names.indexOf(n)), TODAY, p -> p.contains(117));
        eq(got, Arrays.asList("kp", "rings", "odyssey", "unknown"), "order: installed app, watchable by popularity, unknown; never announced or cinema only");
    }

    // ------------------------------------------------------------------ which app

    static final class Dev implements WatchPick.Device {
        final Set<String> installed = new HashSet<>(), broken = new HashSet<>();

        Dev(String... pkgs) {
            installed.addAll(Arrays.asList(pkgs));
        }

        @Override
        public String installed(WatchApps.Row r) {
            for (String p : r.pkgs) if (installed.contains(p)) return p;
            return null;
        }

        @Override
        public boolean opens(WatchApps.Row r, String pkg) {
            return !broken.contains(pkg);
        }
    }

    static String pick(WatchApps t, Dev d, Integer... prov) {
        WatchPick.Pick p = WatchPick.forTitle(t, Arrays.asList(prov), d);
        return p == null ? "search" : p.pkg;
    }

    static void pick(WatchApps t) {
        Dev d = new Dev("top.rootu.lampa", "ru.kinopoisk.tv", "mobi.zona", "com.google.android.youtube.tv");
        eq(pick(t, d, 117), "ru.kinopoisk.tv", "the provider that has the title wins");
        eq(pick(t, d, 115), "top.rootu.lampa", "provider without the title: Lampa");
        eq(pick(t, d), "top.rootu.lampa", "no provider data (RU today): Lampa for every released title");
        d.broken.add("ru.kinopoisk.tv");
        eq(pick(t, d, 117), "top.rootu.lampa", "provider without a working intent: Lampa, never a dead end");
        Dev z = new Dev("mobi.zona", "com.google.android.youtube.tv");
        eq(pick(t, z), "mobi.zona", "no Lampa: the next catalogue app");
        z.broken.add("mobi.zona");
        eq(pick(t, z), "search", "catalogue app without an intent: Найти (YouTube is never the hero's app)");
        eq(pick(t, new Dev()), "search", "nothing installed: Найти");
        Dev beta = new Dev("dkc.video.beta_vbox");
        eq(pick(t, beta), "dkc.video.beta_vbox", "a second package of a row counts");
        WatchApps l = WatchApps.parse("a.b\tA\tв A\tcatalog\t-\tlaunch\n");
        eq(pick(l, new Dev("a.b")), "search", "an app marked launch is never the hero's app");
        check(WatchPick.inInstalledProvider(t, Arrays.asList(117), d), "Kinopoisk installed carries 117");
        check(!WatchPick.inInstalledProvider(t, Arrays.asList(115), d), "Okko is not installed");
        eq(WatchPick.installedProviderIds(t, d), Collections.singletonList(117), "discover by the installed official apps");
        eq(WatchPick.installedProviderIds(t, z), Collections.emptyList(), "no official app: no discover");
    }

    static void searchOrder(WatchApps t) {
        Dev d = new Dev("ru.kinopoisk.tv", "top.rootu.lampa", "com.google.android.youtube.tv", "org.smarttube.beta", "mobi.zona");
        List<String> got = WatchPick.searchOrder(t, d, Arrays.asList("com.android.vending", "ru.kinopoisk.tv", "com.spotify.tv.android"));
        eq(got, Arrays.asList("top.rootu.lampa", "mobi.zona", "ru.kinopoisk.tv", "com.google.android.youtube.tv",
                "org.smarttube.beta", "com.android.vending", "com.spotify.tv.android"), "Искать в: catalogue, services, platforms, others");
    }

    static void region() {
        eq(Region.of("Europe/Moscow", "US"), "RU", "Moscow time on an English UI: RU");
        eq(Region.of("Asia/Vladivostok", ""), "RU", "Vladivostok: RU");
        eq(Region.of("Asia/Almaty", "RU"), "KZ", "Almaty: KZ");
        eq(Region.of("Europe/Berlin", "de"), "DE", "other zones: the locale's country");
        eq(Region.of("UTC", ""), "RU", "nothing known: RU");
        eq(Region.of(null, null), "RU", "null: RU");
    }

    // ------------------------------------------------------------------ search results

    static void relevance() {
        String q = "Мстители: Доктор Дум";
        eq(Relevance.score(q, "Мстители: Доктор Дум"), Relevance.EXACT, "exact title");
        eq(Relevance.score(q, "Мстители: Доктор Дум (2026)"), Relevance.PREFIX, "title with year");
        eq(Relevance.score(q, "Доктор Хаус"), 0, "one word of three: dropped");
        eq(Relevance.score(q, "Доктор"), 0, "the medical series: dropped");
        eq(Relevance.score(q, "Доктор Рихтер"), 0, "Доктор Рихтер: dropped");
        eq(Relevance.score(q, "Мстители: Финал"), 0, "another Avengers: dropped");
        check(Relevance.score(q, "Доктор Дум") > 0, "two of three words as a phrase: kept");
        eq(Relevance.score("Кухня", "Кухня"), Relevance.EXACT, "single word exact");
        eq(Relevance.score("Кухня", "Кухня в Париже"), Relevance.PREFIX, "single word prefix");
        eq(Relevance.score("Кухня", "Шеф. Кухня"), Relevance.PHRASE, "single word inside");
        eq(Relevance.score("Ёлки", "Елки"), Relevance.EXACT, "ё = е");
        check(Relevance.score("Хаус доктор", "Доктор Хаус") > 0, "all words, other order: kept");
        check(Relevance.score("Доктор Хаус", "Доктор Живаго") == 0, "half the words: dropped");
        List<String> r = Relevance.rank(Arrays.asList("Доктор Хаус", "Мстители: Доктор Дум (2026)", "Доктор",
                "Мстители: Доктор Дум", "Доктор Рихтер"), s -> s, q);
        eq(r, Arrays.asList("Мстители: Доктор Дум", "Мстители: Доктор Дум (2026)"), "rank: exact first, weak matches gone");
    }

    // ------------------------------------------------------------------ wiring (source greps, RED on the old code)

    static String src(String rel) throws Exception {
        return new String(Files.readAllBytes(Paths.get(System.getProperty("home.dir"), "src/org/lumen/home", rel)), StandardCharsets.UTF_8);
    }

    static void wiring() throws Exception {
        String sa = src("search/SearchActivity.java"), tile = src("ui/TileView.java"), home = src("HomeActivity.java");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("static final int FIELD_Z = (\\d+);").matcher(sa);
        int z = m.find() ? Integer.parseInt(m.group(1)) : 0;
        check(sa.contains("mField.setElevation(Theme.pxf(FIELD_Z))") && sa.contains("mMic.setElevation(Theme.pxf(FIELD_Z))")
                && z > 10 && sa.contains("Theme.pxf(10) : Theme.pxf(2)"), "search: the query text sits above the field box (it hid the query)");
        check(sa.contains("content = Relevance.rank(content, k -> k.title, q);"), "search: results ranked by relevance");
        check(sa.contains("(caption ? CAPTION_H : 0)") && sa.contains("top += pitch(r);"), "search: room under the results for the focused caption (it ran into Искать в)");
        check(sa.contains("v.setAppIcon(o.icon)") && !sa.contains("v.setIcon(((Drawable) o[2]).mutate())"), "search: Искать в shows the real icon");
        check(tile.contains("if (mTintIcon) mIcon.setTint(Theme.TEXT2);"), "tile: an app icon is never tinted");
        check(sa.contains("mRing.draw(") && !sa.contains("g.setStroke(Theme.px(Tokens.RING_W), Theme.RING)"), "search: the field wears the Lumen ring");
        check(home.contains("WatchLaunch.open(this, k)") && !home.contains("new Intent(this, SearchActivity.class).putExtra(SearchManager.QUERY, k.title)"),
                "hero: Смотреть goes through the watch pick");
        check(src("ui/ShelfView.java").contains("mBtn[0].setText(watch)"), "hero: the button says where it opens");
    }

    // ------------------------------------------------------------------ hero rebinding

    static void heroBind() {
        int[] binds = { 0 };
        HeroBind<String> h = new HeroBind<>(new HeroBind.Target<String>() {
            @Override
            public void item(Card k, boolean buttons, int page, int pages) {
            }

            @Override
            public void feature(Card k, int page, int pages) {
                binds[0]++;
            }

            @Override
            public void badge(String b, boolean slot) {
            }

            @Override
            public void loadBadge(String pkg) {
            }
        });
        Card k = new Card(Card.FEATURE, "tmdb:tv:84773", "Властелин колец: Кольца Власти");
        k.watchLabel = "Найти";
        check(h.feature(k, 0, 3) && !h.feature(k, 0, 3), "same showcase card: bound once");
        k.watchLabel = "Смотреть в Lampa";
        check(h.feature(k, 0, 3) && binds[0] == 2, "a new watch button (Lampa installed) rebinds the hero");
    }
}
