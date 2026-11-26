// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

import org.lumen.home.data.Card;
import org.lumen.home.data.HomeData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.lumen.ui.FocusRing;
import org.lumen.ui.Grid;

/**
 * Host tests of Home's pure rules: rows (Витрина: 1, 2, 3 and many Continue items), Continue texts, one item per
 * series, app order, the D-pad map, which element shows the focus and the accent ring (exactly one, through leaving
 * and returning),
 * greeting, the Витрина's layout, the art rule, the hero binding.
 */
public final class HomeModelTest {
    static int checks, fails;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            fails++;
            System.out.println("FAIL " + what);
        }
    }

    static void eq(Object a, Object b, String what) {
        check(a == null ? b == null : a.equals(b), what + ": got [" + a + "] want [" + b + "]");
    }

    static final ContinueText.Words RU = new ContinueText.Words() {
        public String seasonEpisode(String s, String e) { return "Сезон " + s + ", серия " + e; }
        public String episode(String e) { return "Серия " + e; }
        public String leftMin(int m) { return "Осталось " + m + " мин"; }
        public String leftHour(int h) { return "Осталось " + h + " ч"; }
        public String leftHourMin(int h, int m) { return "Осталось " + h + " ч " + m + " мин"; }
        public String nextEpisode() { return "Следующая серия"; }
        public String newEpisode() { return "Новая серия"; }
    };

    public static void main(String[] a) {
        continueText();
        continueRules();
        rowPlan();
        appOrder();
        appPick();
        appPickWiring();
        focusMap();
        focusPaint();
        focusWiring();
        grid();
        greeting();
        vitrina();
        artRule();
        heroBind();
        lanes();
        snapshot();
        startupWiring();
        System.out.println((fails == 0 ? "ok   " : "FAIL ") + "Home model: " + checks + " checks, " + fails + " failed");
        System.exit(fails == 0 ? 0 : 1);
    }

    static void continueText() {
        eq(ContinueText.seasonLine("1", "3", "Ламиго", "Каминандес", RU), "Сезон 1, серия 3 · Ламиго", "full line");
        eq(ContinueText.seasonLine("1", "3", "", "Каминандес", RU), "Сезон 1, серия 3", "no episode title");
        eq(ContinueText.seasonLine("1", "3", "Каминандес", "Каминандес", RU), "Сезон 1, серия 3", "title not repeated");
        eq(ContinueText.seasonLine(null, "7", "Пилот", "X", RU), "Серия 7 · Пилот", "episode only");
        eq(ContinueText.seasonLine(null, null, "Пилот", "X", RU), "", "no numbers: no line");
        eq(ContinueText.seasonLine("2", "", "", "X", RU), "", "season without episode: no line");
        long min = 60_000L;
        eq(ContinueText.leftLine(0, 100 * min, 86 * min, RU), "Осталось 14 мин", "14 min left");
        eq(ContinueText.leftLine(0, 100 * min, 86 * min + 1, RU), "Осталось 14 мин", "rounded up");
        eq(ContinueText.leftLine(0, 100 * min, 86 * min - 30_000, RU), "Осталось 15 мин", "half a minute more rounds up");
        eq(ContinueText.leftLine(0, 120 * min, 48 * min, RU), "Осталось 1 ч 12 мин", "over an hour");
        eq(ContinueText.leftLine(0, 120 * min, 60 * min, RU), "Осталось 1 ч", "whole hour");
        eq(ContinueText.leftLine(0, 100 * min, 100 * min - 1000, RU), "Осталось 1 мин", "seconds left show 1 min");
        eq(ContinueText.leftLine(ContinueText.WN_NEXT, 100 * min, 10 * min, RU), "Следующая серия", "next");
        eq(ContinueText.leftLine(ContinueText.WN_NEW, 0, 0, RU), "Новая серия", "new");
        eq(ContinueText.leftLine(0, 0, 0, RU), "", "not started");
        eq(ContinueText.leftLine(0, 100 * min, 0, RU), "", "position 0");
        check(!ContinueText.showsProgress(ContinueText.WN_NEXT, 100, 50), "next has no bar");
        check(ContinueText.showsProgress(0, 100, 50), "started has a bar");
        check(!ContinueText.showsProgress(0, 100, 100), "finished has no bar");
        eq(ContinueText.permille(1000, 250), 250, "permille");
        eq(ContinueText.permille(1000, 0), -1, "permille none");
        eq(ContinueText.tag(ContinueText.WN_NEW, RU), "Новая серия", "tag new");
        eq(ContinueText.tag(0, RU), "", "no tag");
        eq(ContinueText.description("Эпизод о ламах.", true, "Ламиго", "Сериал о ламах."), "Эпизод о ламах.", "episode text kept");
        eq(ContinueText.description("Сериал о ламах.", true, "Ламиго", "сериал  о ламах."), "", "series synopsis on an episode hidden");
        eq(ContinueText.description("Фильм.", false, "", "Фильм."), "Фильм.", "film keeps its text");
        eq(ContinueText.description("  ", true, "Ламиго", ""), "", "blank");
        // Lumen Play, a test unit: S2E22 with no episode title and the show's overview as its text
        eq(ContinueText.description("Сюжет сериала сосредоточен вокруг четырех молодых американок", true, "", ""), "",
                "episode without its own title: the show's synopsis is not shown");
        eq(ContinueText.description("Сюжет фильма.", false, "", ""), "Сюжет фильма.", "a film without episode title keeps its text");
        eq(ContinueText.cutAtWord("Голодный лама Коро и пингвин", 16), "Голодный лама…", "cut at word");
        eq(ContinueText.cutAtWord("short", 50), "short", "fits");
        eq(ContinueText.cutAtWord("one, two three", 5), "one…", "trailing comma dropped");
        eq(ContinueText.cutAtWord("one, two three", 9), "one, two…", "cut before the word in the way");
    }

    static Card wn(String id, String pkg, String title, boolean episodic, long engaged) {
        Card k = new Card(Card.PROGRAM, id, title);
        k.pkg = pkg;
        k.episodic = episodic;
        k.engaged = engaged;
        return k;
    }

    static void continueRules() {
        List<Card> in = Arrays.asList(
                wn("wn:1", "kp", "Сериал", true, 10), wn("wn:2", "kp", "Сериал", true, 30), wn("wn:3", "kp", "СЕРИАЛ!", true, 20),
                wn("wn:4", "kp", "Фильм", false, 25), wn("wn:5", "kp", "Фильм", false, 5), wn("wn:6", "yt", "Сериал", true, 1));
        List<Card> out = ContinueRules.pick(in, 12);
        ArrayList<String> ids = new ArrayList<>();
        for (Card k : out) ids.add(k.id);
        eq(ids, Arrays.asList("wn:2", "wn:4", "wn:5", "wn:6"), "one per series, newest first, films apart");
        ArrayList<Card> many = new ArrayList<>();
        for (int i = 0; i < 30; i++) many.add(wn("wn:m" + i, "p", "Film " + i, false, i));
        eq(ContinueRules.pick(many, ContinueRules.MAX).size(), 12, "at most 12");
        eq(ContinueRules.pick(Collections.emptyList(), 12).size(), 0, "empty");
        eq(ContinueRules.norm("Ёлки, 2!"), "елки2", "norm");
    }

    static void rowPlan() {
        // a test unit: one Lumen Play item, no TMDB, six apps, HDMI 1 and 2
        RowPlan p = RowPlan.of(0, 1, 6);
        eq(p.calm, false, "owner: not calm");
        eq(p.heroContinue, 1, "owner: the item is the hero");
        eq(p.rows, Arrays.asList(RowKind.APPS, RowKind.INPUTS), "owner: dock, then the bottom line");
        check(p.heroButtons() && !p.continueRow(), "owner: Продолжить / Подробнее on the shelf");
        p = RowPlan.of(0, 2, 6);
        eq(p.heroContinue, 2, "two items: two hero pages");
        eq(p.rows, Arrays.asList(RowKind.APPS, RowKind.INPUTS), "two items: no lone card row");
        p = RowPlan.of(5, 3, 8);
        eq(p.rows, Arrays.asList(RowKind.CONTINUE, RowKind.APPS, RowKind.INPUTS), "3 items: the Continue row first");
        eq(p.heroContinue, 0, "3 items: none in the hero");
        check(p.continueRow() && !p.heroButtons(), "3 items: a row, no hero buttons");
        p = RowPlan.of(0, 12, 40);
        eq(p.rows, Arrays.asList(RowKind.CONTINUE, RowKind.APPS, RowKind.INPUTS), "many items, many apps: still 3 rows");
        p = RowPlan.of(5, 0, 8);
        eq(p.calm, false, "TMDB showcase alone is not calm");
        eq(p.rows, Arrays.asList(RowKind.APPS, RowKind.INPUTS), "no continue row without items");
        p = RowPlan.of(0, 0, 8);
        eq(p.calm, true, "nothing to watch, no showcase: calm");
        check(!p.heroButtons(), "calm: no hero buttons");
        eq(p.rows, Arrays.asList(RowKind.APPS, RowKind.INPUTS), "calm rows");
        eq(RowPlan.of(3, 0, 0).rows, Arrays.asList(RowKind.INPUTS), "no empty dock, the bottom line stays");
    }

    static AppOrder.App app(String pkg, String label) {
        return new AppOrder.App(pkg, label);
    }

    static void appOrder() {
        List<AppOrder.App> apps = Arrays.asList(app("b", "Бета"), app("a", "Альфа"), app("yt", "YouTube"), app("kp", "Кинопоиск"),
                app("sys", "Служебное"), app("h", "Скрытое"));
        Set<String> hidden = new HashSet<>(Arrays.asList("h"));
        Set<String> defHidden = new HashSet<>(Arrays.asList("sys"));
        List<AppOrder.App> o = AppOrder.order(apps, Arrays.asList("b/.Main", "gone/.Main"), hidden, new HashSet<>(), defHidden,
                Arrays.asList("yt", "kp"), new Locale("ru"));
        ArrayList<String> pk = new ArrayList<>();
        for (AppOrder.App x : o) pk.add(x.pkg);
        eq(pk, Arrays.asList("b", "yt", "kp", "a"), "user order, then first apps, then by label, hidden out");
        Set<String> unhidden = new HashSet<>(Arrays.asList("sys"));
        o = AppOrder.order(apps, Collections.emptyList(), hidden, unhidden, defHidden, Collections.emptyList(), new Locale("ru"));
        check(o.size() == 5, "shown again by the user");
        eq(AppOrder.hiddenOnes(apps, hidden, new HashSet<>(), defHidden, new Locale("ru")).size(), 2, "hidden list");
        eq(AppOrder.move(Arrays.asList("a", "b", "c", "d"), 0, 2), Arrays.asList("b", "c", "a", "d"), "move right");
        eq(AppOrder.move(Arrays.asList("a", "b", "c", "d"), 3, 0), Arrays.asList("d", "a", "b", "c"), "move to front");
        eq(AppOrder.move(Arrays.asList("a", "b"), 1, 9), Arrays.asList("a", "b"), "clamped");
        defaultHidden();
    }

    static final String INCY = "llc.itdev.incy", MAIN = INCY + "/llc.itdev.incy.MainActivity",
            ALIAS = INCY + "/llc.itdev.incy.MainActivityDefault";

    static AppPick.Activity act(String component, boolean leanback) {
        return new AppPick.Activity(AppPick.pkgOf(component), component, leanback);
    }

    /** PackageManager's launch intents as a map: package -> leanback component, main component. */
    static AppPick.Launch sys(String... pkgLeanbackMain) {
        java.util.Map<String, String[]> m = new java.util.HashMap<>();
        for (int i = 0; i < pkgLeanbackMain.length; i += 3) {
            m.put(pkgLeanbackMain[i], new String[] {pkgLeanbackMain[i + 1], pkgLeanbackMain[i + 2]});
        }
        return new AppPick.Launch() {
            public String leanback(String pkg) { return m.containsKey(pkg) ? m.get(pkg)[0] : null; }
            public String main(String pkg) { return m.containsKey(pkg) ? m.get(pkg)[1] : null; }
        };
    }

    static List<String> comps(List<AppPick.Activity> l) {
        ArrayList<String> out = new ArrayList<>();
        for (AppPick.Activity a : l) out.add(a.component + (a.leanback ? "" : " phone"));
        return out;
    }

    /** One entry per package (INCY VPN: two MAIN + LEANBACK_LAUNCHER activities showed two tiles on a test Z9X). */
    static void appPick() {
        List<AppPick.Activity> both = Arrays.asList(act("yt/yt.Main", true), act(MAIN, true), act(ALIAS, true),
                act("phone/phone.Main", false));
        List<AppPick.Activity> o = AppPick.onePerPackage(both, sys(INCY, ALIAS, ALIAS, "yt", "yt/yt.Main", null));
        eq(comps(o), Arrays.asList("yt/yt.Main", ALIAS, "phone/phone.Main phone"),
                "two leanback activities in one package: one entry, on the system's leanback launch activity");
        o = AppPick.onePerPackage(both, sys());
        eq(comps(o), Arrays.asList("yt/yt.Main", MAIN, "phone/phone.Main phone"), "no launch intent: the first resolved");
        o = AppPick.onePerPackage(Arrays.asList(act(MAIN, true), act(ALIAS, true)), sys(INCY, null, ALIAS));
        eq(comps(o), Arrays.asList(ALIAS), "no leanback launch intent: the main launch intent");
        o = AppPick.onePerPackage(Arrays.asList(act("p/p.A", false)), sys("p", null, "p/p.Info"));
        eq(comps(o), Arrays.asList("p/p.Info phone"), "the system's choice wins even when it is not in the list");
        // the app switches its icon: the alias is disabled, the other activity enabled (and back)
        List<String> keep = Arrays.asList("yt", INCY);
        for (String on : new String[] {MAIN, ALIAS, MAIN}) {
            o = AppPick.onePerPackage(Arrays.asList(act("yt/yt.Main", true), act(on, true)), sys(INCY, on, on));
            eq(comps(o), Arrays.asList("yt/yt.Main", on), "icon switch to " + on + ": still one entry, the enabled one");
            ArrayList<AppOrder.App> apps = new ArrayList<>();
            for (AppPick.Activity a : o) apps.add(new AppOrder.App(a.pkg, a.pkg));
            ArrayList<String> pk = new ArrayList<>();
            for (AppOrder.App a : AppOrder.order(apps, Arrays.asList(INCY, "yt"), new HashSet<>(), new HashSet<>(),
                    new HashSet<>(), Collections.emptyList(), Locale.ROOT)) pk.add(a.pkg);
            eq(pk, Arrays.asList(INCY, "yt"), "icon switch to " + on + ": the user's order holds");
            check(AppOrder.order(apps, Collections.emptyList(), new HashSet<>(Arrays.asList(INCY)), new HashSet<>(),
                    new HashSet<>(), Collections.emptyList(), Locale.ROOT).size() == 1, "icon switch: hidden stays hidden");
        }
        // stored keys of Home before this change: flattened components
        List<String> old = Arrays.asList("a/a.X", MAIN, "b/b.Y", ALIAS, "c");
        check(AppPick.oldKeys(old) && !AppPick.oldKeys(Arrays.asList("a", INCY)), "component keys are found");
        eq(AppPick.order(old), Arrays.asList("a", INCY, "b", "c"), "migrated order: every app keeps its first place");
        eq(AppPick.order(AppPick.order(old)), AppPick.order(old), "migration runs once, a second pass changes nothing");
        List<AppPick.Activity> now = Arrays.asList(act(MAIN, true), act(ALIAS, true), act("v/v.Main", true));
        eq(AppPick.hidden(new HashSet<>(Arrays.asList(MAIN)), now), new HashSet<String>(),
                "one of two duplicate tiles hidden: the app stays, once");
        eq(AppPick.hidden(new HashSet<>(Arrays.asList(MAIN, ALIAS)), now), new HashSet<>(Arrays.asList(INCY)),
                "every tile of the app hidden: the app is hidden");
        eq(AppPick.hidden(new HashSet<>(Arrays.asList("v/v.Main", "gone/gone.Main", "pkg")), now),
                new HashSet<>(Arrays.asList("v", "gone", "pkg")), "single tile, uninstalled app and package keys");
        eq(AppPick.shown(new HashSet<>(Arrays.asList("s/s.A", "t"))), new HashSet<>(Arrays.asList("s", "t")),
                "shown again: by package");
        ArrayList<String> pk = new ArrayList<>();
        for (AppOrder.App a : AppOrder.order(Arrays.asList(app("b", "B"), app(INCY, "INCY")), Arrays.asList(ALIAS, "b/b.Y"),
                new HashSet<>(), new HashSet<>(), new HashSet<>(), Collections.emptyList(), Locale.ROOT)) pk.add(a.pkg);
        eq(pk, Arrays.asList(INCY, "b"), "an order not yet migrated still places each app");
    }

    /** Home lists apps through AppPick, keys cards, hiding and the order by package and migrates stored keys. */
    static void appPickWiring() {
        String dir = System.getProperty("home.src");
        if (dir == null) return;
        String src = read(dir + "/data/AppSource.java"), loader = read(dir + "/data/HomeLoader.java"),
                act = read(dir + "/HomeActivity.java"), safe = read(dir + "/SafeModeView.java");
        check(src.contains("AppPick.onePerPackage(") && src.contains("getLeanbackLaunchIntentForPackage(")
                && src.contains("getLaunchIntentForPackage("), "wiring: AppSource picks one activity per package");
        check(loader.contains("migrateAppKeys(p, found.resolved);") && loader.contains("new Card(Card.APP, a.cn.getPackageName(),"),
                "wiring: HomeLoader migrates stored keys and keys app cards by package");
        check(act.contains("toggleInSet(Prefs.K_HIDDEN_APPS, k.pkg, true)") && !act.contains("K_HIDDEN_APPS, k.intent")
                && act.contains("order.add(k.pkg)"), "wiring: hide and move store packages");
        check(safe.contains("AppSource.load(c)") && !safe.contains("queryIntentActivities"),
                "wiring: safe mode lists the same one-per-package apps");
    }

    /** The real default list (2026-11-19): Google's Android TV Remote Service tile is off Home, the user decides. */
    static void defaultHidden() {
        Set<String> d = AppOrder.DEFAULT_HIDDEN, none = new HashSet<>();
        String remote = "com.google.android.tv.remote.service";
        for (String p : new String[] {remote, "org.lumen.home", "org.lumen.setup", "org.lumen.updater",
                "com.google.android.tungsten.setupwraith", "com.google.android.katniss", "com.google.android.gms",
                "com.google.android.gsf", "com.google.android.backdrop"}) {
            check(d.contains(p), "hidden by default: " + p);
        }
        for (String p : new String[] {"com.android.vending", "com.google.android.youtube.tv", "ru.kinopoisk.tv",
                "com.xgimiru.player", "top.rootu.lampa"}) {
            check(!d.contains(p), "a user app stays on Home: " + p);
        }
        List<AppOrder.App> apps = Arrays.asList(app(remote, "Android TV Remote Service"), app("com.android.vending", "Google Play"),
                app("com.google.android.youtube.tv", "YouTube"));
        List<AppOrder.App> o = AppOrder.order(apps, Collections.emptyList(), none, none, d, Collections.emptyList(), new Locale("ru"));
        check(o.size() == 2 && !o.get(0).pkg.equals(remote) && !o.get(1).pkg.equals(remote), "no remote service tile by default");
        eq(AppOrder.hiddenOnes(apps, none, none, d, new Locale("ru")).size(), 1, "the remote service is in Hidden apps to show again");
        o = AppOrder.order(apps, Collections.emptyList(), none, new HashSet<>(Arrays.asList(remote)), d,
                Collections.emptyList(), new Locale("ru"));
        check(o.size() == 3, "shown again by the user: the tile comes back and stays");
        o = AppOrder.order(apps, Collections.emptyList(), new HashSet<>(Arrays.asList("com.android.vending")), none, d,
                Collections.emptyList(), new Locale("ru"));
        check(o.size() == 1, "the user's own hidden apps stay hidden");
        boolean ro;
        try {
            d.add("x");
            ro = false;
        } catch (UnsupportedOperationException e) {
            ro = true;
        }
        check(ro, "the default list cannot be changed at run time");
    }

    static FocusMap.Row cards(int n) { return new FocusMap.Row(n, 248, 40); }

    static FocusMap.Row dock(int n) { return new FocusMap.Row(n, 248, 40); }

    static FocusMap.Row bottom(int inputs) { return new FocusMap.Row(Vitrina.bottomCenters(inputs)); }

    static void focusMap() {
        // the owner's page: hero buttons, dock of 6, the bottom line (2 chips, All apps, Settings)
        FocusMap f = new FocusMap();
        f.setLayout(2, new FocusMap.Row[] { dock(6), bottom(2) });
        f.home();
        eq(f.zone, FocusMap.HERO, "home lands on the hero");
        eq(f.heroBtn, 0, "on Продолжить");
        eq(f.move(FocusMap.UP), FocusMap.NONE, "no header: UP from the hero does nothing");
        eq(f.move(FocusMap.LEFT), FocusMap.HERO_PREV, "LEFT on the first button pages the hero");
        eq(f.move(FocusMap.RIGHT), FocusMap.MOVED, "to Подробнее");
        eq(f.move(FocusMap.RIGHT), FocusMap.HERO_NEXT, "RIGHT on the last button pages the hero");
        eq(f.move(FocusMap.BACK), FocusMap.NONE, "BACK on the hero does nothing");
        f.move(FocusMap.DOWN);
        check(f.zone == FocusMap.ROWS && f.row == 0 && f.item(0) == 0, "DOWN into the dock, first app");
        eq(f.move(FocusMap.LEFT), FocusMap.NONE, "LEFT on the first app does nothing");
        for (int i = 0; i < 5; i++) f.move(FocusMap.RIGHT);
        eq(f.item(0), 5, "last app");
        eq(f.first(0), 0, "6 apps never slide");
        eq(f.move(FocusMap.RIGHT), FocusMap.NONE, "RIGHT on the last app does nothing, no jump");
        f.move(FocusMap.DOWN);
        // app 5 centre 116 + 5 * 288 + 124 = 1680: the nearest of the bottom line is All apps (1636 + 36 = 1672)
        eq(f.row, 1, "bottom line");
        eq(f.item(1), 2 + Vitrina.ROUND_APPS, "app 5 lands on All apps");
        f.move(FocusMap.RIGHT);
        eq(f.item(1), 2 + Vitrina.ROUND_SETTINGS, "then Settings");
        eq(f.move(FocusMap.DOWN), FocusMap.NONE, "nothing under the bottom line");
        f.focusItem(1, 0);
        f.move(FocusMap.UP);
        eq(f.item(0), 0, "HDMI 1 (centre 312) goes up to the first app (240)");
        f.focusItem(1, 1);
        f.move(FocusMap.UP);
        eq(f.item(0), 2, "HDMI 2 (centre 744) goes up to the third app (816)");
        eq(f.move(FocusMap.BACK), FocusMap.MOVED, "BACK from a row");
        check(f.zone == FocusMap.HERO && f.heroBtn == 0, "BACK goes to the hero in one press");
        // many apps: the dock slides with one app of look ahead
        f.setLayout(2, new FocusMap.Row[] { dock(14), bottom(2) });
        f.focusItem(0, 0);
        for (int i = 0; i < 4; i++) f.move(FocusMap.RIGHT);
        eq(f.first(0), 0, "app 4 of 14: no slide yet");
        f.move(FocusMap.RIGHT);
        eq(f.first(0), 1, "app 5: the dock slides one, app 6 peeks");
        for (int i = 0; i < 20; i++) f.move(FocusMap.RIGHT);
        eq(f.item(0), 13, "last of 14");
        eq(f.first(0), 8, "the end stays filled");
        f.move(FocusMap.LEFT);
        eq(f.first(0), 8, "LEFT inside the view does not slide");
        // 3+ Continue items: no hero buttons, a Continue row first
        FocusMap c = new FocusMap();
        c.setLayout(0, new FocusMap.Row[] { cards(4), dock(6), bottom(2) });
        c.home();
        check(c.zone == FocusMap.ROWS && c.row == 0 && c.item(0) == 0, "Continue row: home on the first card");
        eq(c.move(FocusMap.UP), FocusMap.NONE, "Continue row: UP does nothing");
        c.move(FocusMap.RIGHT);
        c.move(FocusMap.RIGHT);
        c.move(FocusMap.DOWN);
        eq(c.row, 1, "to the dock");
        eq(c.item(1), 2, "card 2 over app 2");
        eq(c.item(0), 2, "the row keeps its card (the shelf keeps showing it)");
        eq(c.move(FocusMap.BACK), FocusMap.MOVED, "BACK without hero buttons");
        check(c.row == 0 && c.item(0) == 0, "BACK to the first card");
        eq(c.move(FocusMap.BACK), FocusMap.NONE, "BACK at the start does nothing");
        // calm screen: no hero
        FocusMap q = new FocusMap();
        q.setLayout(0, new FocusMap.Row[] { dock(8), bottom(3) });
        q.home();
        check(q.zone == FocusMap.ROWS && q.row == 0 && q.item(0) == 0, "calm: home on the first app");
        // content changes under the focus
        f.focusItem(1, 3);
        f.setLayout(2, new FocusMap.Row[] { dock(3) });
        check(f.row == 0 && f.item(0) <= 2, "focus clamped when rows go away");
        f.setLayout(0, new FocusMap.Row[0]);
        eq(f.zone, FocusMap.HERO, "nothing left: the zone falls back");
        eq(f.move(FocusMap.DOWN), FocusMap.NONE, "and keys do nothing");
    }

    /** The owner's screen: hero buttons, a dock of 6, the bottom line (2 chips, All apps, Settings), 6 tiles on Apps. */
    static final int[] SIZES = { 6, 4 };
    static final int GRID = 6;

    /** How many elements show the focus when the activity paints them all from the map (HomeActivity.applyFocus). */
    static int lit(FocusMap f, int gridIndex) {
        int n = 0;
        for (int b = 0; b < 2; b++) if (f.heroFocus() == b) n++;
        for (int r = 0; r < SIZES.length; r++) for (int i = 0; i < SIZES[r]; i++) if (f.itemFocused(r, i)) n++;
        for (int i = 0; i < GRID; i++) if (f.gridFocused(i, gridIndex)) n++;
        return n;
    }

    /**
     * Elements wearing the focus ring at rest (FocusRing.alpha with the full focus value, as TileView and ShelfView call
     * it); -1 when any ring sits on an element that is not the focused one, or the focused one lacks it.
     */
    static int rings(FocusMap f, int gridIndex) {
        ArrayList<Boolean> focused = new ArrayList<>();
        for (int b = 0; b < 2; b++) focused.add(f.heroFocus() == b);
        for (int r = 0; r < SIZES.length; r++) for (int i = 0; i < SIZES[r]; i++) focused.add(f.itemFocused(r, i));
        for (int i = 0; i < GRID; i++) focused.add(f.gridFocused(i, gridIndex));
        int n = 0;
        for (boolean on : focused) {
            boolean ring = FocusRing.alpha(on, 1f) > 0f;
            if (ring != on) return -1;
            if (ring) n++;
        }
        return n;
    }

    static final int ALL_APPS = 2 + Vitrina.ROUND_APPS, GEAR = 2 + Vitrina.ROUND_SETTINGS;

    static void focusPaint() {
        FocusMap f = new FocusMap();
        f.setLayout(2, new FocusMap.Row[] { dock(6), bottom(2) });
        f.home();
        check(lit(f, 0) == 1 && f.heroFocus() == 0, "paint: Продолжить alone");
        f.move(FocusMap.RIGHT);
        check(lit(f, 0) == 1 && f.heroFocus() == 1, "paint: Подробнее alone");
        f.move(FocusMap.DOWN);
        check(lit(f, 0) == 1 && f.heroFocus() == -1 && f.itemFocused(0, 0), "paint: the dock, no hero button");
        f.move(FocusMap.DOWN);
        check(lit(f, 0) == 1 && f.itemFocused(1, 0), "paint: HDMI 1 chip alone");
        check(rings(f, 0) == 1, "ring: on the HDMI 1 chip only");
        f.move(FocusMap.RIGHT);
        check(lit(f, 0) == 1 && f.itemFocused(1, 1) && !f.itemFocused(1, 0), "paint: HDMI 2 chip, HDMI 1 off");
        f.move(FocusMap.RIGHT);
        check(lit(f, 0) == 1 && f.itemFocused(1, ALL_APPS), "paint: All apps alone");
        check(rings(f, 0) == 1, "ring: on All apps only");
        // OK on All apps: the Apps page (HomeActivity.setPage), only its first tile
        f.page = FocusMap.H_APPS;
        check(lit(f, 0) == 1 && f.gridFocused(0, 0) && !f.itemFocused(1, ALL_APPS), "paint: Apps page, All apps off");
        check(lit(f, 3) == 1 && f.gridFocused(3, 3), "paint: Apps page tile 3");
        check(rings(f, 3) == 1, "ring: Apps page tile 3 only, not All apps");
        // BACK (HomeActivity.backFromApps): Home, the focus on All apps
        f.page = FocusMap.H_HOME;
        f.focusItem(1, ALL_APPS);
        check(lit(f, 3) == 1 && f.itemFocused(1, ALL_APPS) && !f.gridFocused(3, 3), "paint: back on All apps alone");
        f.move(FocusMap.RIGHT);
        check(lit(f, 3) == 1 && f.itemFocused(1, GEAR) && !f.itemFocused(1, ALL_APPS), "paint: the gear, All apps off");
        check(rings(f, 3) == 1, "ring: on the gear only");
        // leaving and returning (onPause / onResume repaint) and a theme change change nothing in the map
        FocusMap before = f;
        check(lit(before, 3) == 1 && before.itemFocused(1, GEAR), "paint: after onResume the gear alone");
        // a data reload with the same rows keeps the gear
        f.setLayout(2, new FocusMap.Row[] { dock(6), bottom(2) });
        check(lit(f, 3) == 1 && f.itemFocused(1, GEAR), "paint: after a reload the gear alone");
        // HOME from the Apps page (HomeActivity.handleIntent): the first hero button alone
        f.page = FocusMap.H_APPS;
        f.home();
        check(lit(f, 3) == 1 && f.heroFocus() == 0, "paint: HOME from Apps, Продолжить alone");
        check(rings(f, 3) == 1, "ring: on Продолжить only");
        // a reload that takes the hero buttons away (3 Continue items) moves the focus into the rows, still one
        f.setLayout(0, new FocusMap.Row[] { dock(6), bottom(2) });
        check(lit(f, 3) == 1 && f.heroFocus() == -1, "paint: hero buttons gone, one row item");
        // every key in every zone keeps exactly one lit
        FocusMap w = new FocusMap();
        w.setLayout(2, new FocusMap.Row[] { dock(6), bottom(2) });
        w.home();
        int[] keys = { FocusMap.DOWN, FocusMap.RIGHT, FocusMap.RIGHT, FocusMap.DOWN, FocusMap.RIGHT, FocusMap.RIGHT,
                FocusMap.RIGHT, FocusMap.UP, FocusMap.LEFT, FocusMap.BACK, FocusMap.RIGHT, FocusMap.LEFT, FocusMap.DOWN,
                FocusMap.DOWN, FocusMap.LEFT, FocusMap.UP, FocusMap.UP, FocusMap.UP };
        boolean one = true;
        for (int k : keys) {
            w.move(k);
            one &= lit(w, 0) == 1 && rings(w, 0) == 1;
        }
        check(one, "paint: exactly one lit and one ring after every key of a walk over all zones");
        // mid move: the element that lost the focus drops its ring at once, the new one fades in, never two rings
        check(FocusRing.alpha(false, 0.6f) == 0f && FocusRing.alpha(true, 0.4f) == 0.4f
                && FocusRing.alpha(true, 0f) == 0f && FocusRing.alpha(true, 1.2f) == 1f, "ring: one ring during a focus move");
        // the ring keeps 3 px on screen over the 1.06 focus scale, the 3 px gap stays clear
        float px = 1f, s = 1.06f, rw = FocusRing.width(3, px, s), out = FocusRing.outset(3, 3, px, s);
        check(Math.abs(rw * s - 3f) < 1e-4 && Math.abs((out - rw / 2f) * s - 3f) < 1e-4, "ring: 3 px wide, 3 px gap on screen");
    }

    /** The view half (Android, not on the host): the wiring that makes the paint follow the map. */
    static void focusWiring() {
        String dir = System.getProperty("home.src");
        if (dir == null) return;
        String tile = read(dir + "/ui/TileView.java"), act = read(dir + "/HomeActivity.java");
        int a = tile.indexOf("private void apply(float f) {");
        String body = a < 0 ? "" : tile.substring(a, tile.indexOf("\n    }", a));
        int lift = body.indexOf("if (lifts())"), inv = body.lastIndexOf("mArt.invalidate();");
        check(inv > 0 && inv > body.indexOf("} else {"), "wiring: TileView.apply redraws the face for every kind (pills too)");
        check(lift > 0, "wiring: apply still has the lift branch");
        int r = act.indexOf("protected void onResume()");
        check(r > 0 && act.substring(r, act.indexOf("\n    }", r)).contains("applyFocus(false)"), "wiring: onResume repaints");
        int af = act.indexOf("private void applyFocus(boolean animate) {");
        String ab = af < 0 ? "" : act.substring(af, act.indexOf("\n    }", af));
        check(ab.contains("mMap.gridFocused(") && ab.contains("mMap.heroFocus()") && ab.contains("mMap.itemFocused(")
                && ab.indexOf("mMap.itemFocused(") < ab.indexOf("H_APPS"), "wiring: applyFocus paints all elements on both pages");
        // the accent ring: every tile kind draws it in both paths, only by its own focus state; the hero buttons by mFocus
        int rd = tile.indexOf("private void ring(Canvas c");
        String rb = rd < 0 ? "" : tile.substring(rd, tile.indexOf("\n    }", rd));
        int dd = tile.indexOf("protected void dispatchDraw("), sd = tile.indexOf("private void drawShowcase(");
        String db = dd < 0 ? "" : tile.substring(dd, tile.indexOf("\n    }", dd));
        String sb = sd < 0 ? "" : tile.substring(sd, tile.indexOf("\n    }", sd));
        check(rb.contains("FocusRing.alpha(mFocused, mFocus)") && db.contains("ring(c, w, h, r);") && sb.contains("ring(c, w, h, r);"),
                "wiring: TileView rings every kind (cards, banners, inputs, dock, chips, round buttons) by its focus state");
        check(!tile.contains("Theme.RING_GAP") && !tile.contains("Tokens.RING_W"), "wiring: TileView has no second ring");
        String shelf = read(dir + "/ui/ShelfView.java");
        int bf = shelf.indexOf("public void setButtonFocus(");
        String bb = bf < 0 ? "" : shelf.substring(bf, shelf.indexOf("\n    }", bf));
        check(shelf.contains("FocusRing.alpha(k == mFocus, 1f)") && bb.contains("b.invalidate();"),
                "wiring: the hero buttons ring only the focused one and redraw on every focus change");
    }

    // ------------------------------------------------------------------ startup (home-startup)

    /**
     * A weather (or TMDB) server that never answers must not delay the screen's data: the network lane hangs, the data
     * lane publishes at once, a second fetch of the same kind is not stacked behind the hanging one.
     */
    static void lanes() {
        Lanes l = Lanes.plain();
        java.util.concurrent.CountDownLatch never = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch weatherIn = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger weatherRuns = new java.util.concurrent.atomic.AtomicInteger();
        Runnable hangingWeather = () -> {
            weatherRuns.incrementAndGet();
            weatherIn.countDown();
            try {
                never.await();
            } catch (InterruptedException ignored) {
            }
        };
        check(l.once(Lanes.NET, "weather", hangingWeather), "lanes: the weather fetch is queued");
        try {
            check(weatherIn.await(2, java.util.concurrent.TimeUnit.SECONDS), "lanes: the weather fetch runs (and hangs)");
        } catch (InterruptedException e) {
            check(false, "lanes: interrupted");
        }
        check(!l.once(Lanes.NET, "weather", hangingWeather), "lanes: a second weather fetch is not stacked behind the hanging one");
        check(l.busy(Lanes.NET, "weather"), "lanes: the hanging fetch counts as busy");
        l.once(Lanes.NET, "trending", () -> { });
        java.util.concurrent.atomic.AtomicReference<HomeData> published = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        long t0 = System.nanoTime();
        l.post(Lanes.LOAD, () -> {
            HomeData d = new HomeData();
            d.apps.add(new Card(Card.APP, "a/b", "App"));
            d.plan = RowPlan.of(0, 0, 1);
            published.set(d);
            done.countDown();
        });
        boolean in = false;
        try {
            in = done.await(1, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        check(in && published.get() != null && published.get().apps.size() == 1,
                "lanes: the screen's data is published while the weather server hangs (" + ms + " ms)");
        check(ms < 500, "lanes: published within 500 ms while the weather hangs, took " + ms);
        check(weatherRuns.get() == 1, "lanes: the weather fetch ran once, got " + weatherRuns.get());
        never.countDown();
        for (int i = 0; i < 100 && l.busy(Lanes.NET, "weather"); i++) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
            }
        }
        check(!l.busy(Lanes.NET, "weather"), "lanes: the key is free again once the fetch ended");
        check(l.once(Lanes.NET, "weather", () -> { }), "lanes: a new weather fetch is queued after the old one ended");
    }

    /** Every Card field set to something not default, so a field the codec forgets fails here. */
    static Card fullCard(int salt) {
        Card k = new Card();
        int i = 0;
        for (java.lang.reflect.Field f : Card.class.getDeclaredFields()) {
            int m = f.getModifiers();
            if (java.lang.reflect.Modifier.isStatic(m) || java.lang.reflect.Modifier.isFinal(m)) continue;
            i++;
            try {
                Class<?> t = f.getType();
                if (t == int.class) f.setInt(k, 1000 * salt + i);
                else if (t == long.class) f.setLong(k, 1_000_000L * salt + i);
                else if (t == boolean.class) f.setBoolean(k, ((salt + i) & 1) == 0);
                else if (t == String.class) f.set(k, "v" + salt + "_" + i + "_\u0451");
                else check(false, "snapshot: Card field of an unknown type " + f.getName());
            } catch (IllegalAccessException e) {
                check(false, "snapshot: " + e);
            }
        }
        return k;
    }

    static String dump(Card k) {
        StringBuilder sb = new StringBuilder();
        for (java.lang.reflect.Field f : Card.class.getDeclaredFields()) {
            int m = f.getModifiers();
            if (java.lang.reflect.Modifier.isStatic(m) || java.lang.reflect.Modifier.isFinal(m)) continue;
            try {
                sb.append(f.getName()).append('=').append(f.get(k)).append(';');
            } catch (IllegalAccessException e) {
                sb.append("?");
            }
        }
        return sb.toString();
    }

    static HomeData fullData() {
        HomeData d = new HomeData();
        int s = 1;
        for (int i = 0; i < 6; i++) d.apps.add(fullCard(s++));
        d.hidden.add(fullCard(s++));
        d.cont.add(fullCard(s++));
        for (int i = 0; i < 3; i++) d.hero.add(fullCard(s++));
        d.inputs.add(fullCard(s++));
        d.inputs.add(fullCard(s++));
        Card nulls = new Card(Card.TILE, "miracast", "Mirror");
        nulls.image = null;
        nulls.intent = null;
        d.inputs.add(nulls);
        d.online = true;
        d.plan = RowPlan.of(d.hero.size(), d.cont.size(), d.apps.size());
        return d;
    }

    static void snapshot() {
        HomeData d = fullData();
        byte[] b = HomeSnapshot.encode(d, 42L, "ru-RU");
        HomeData r = HomeSnapshot.decode(b, 42L, "ru-RU");
        check(r != null, "snapshot: reads back");
        if (r == null) return;
        List<List<Card>> a = Arrays.asList(d.apps, d.hidden, d.cont, d.hero, d.inputs);
        List<List<Card>> z = Arrays.asList(r.apps, r.hidden, r.cont, r.hero, r.inputs);
        for (int i = 0; i < a.size(); i++) {
            eq(z.get(i).size(), a.get(i).size(), "snapshot: list " + i + " size");
            for (int j = 0; j < Math.min(a.get(i).size(), z.get(i).size()); j++) {
                eq(dump(z.get(i).get(j)), dump(a.get(i).get(j)), "snapshot: list " + i + " card " + j + " every field");
            }
        }
        eq(r.online, true, "snapshot: online");
        check(r.plan != null && r.plan.rows.equals(d.plan.rows) && r.plan.calm == d.plan.calm
                && r.plan.heroContinue == d.plan.heroContinue, "snapshot: the plan is the loader's plan");
        check(HomeSnapshot.same(d, r), "snapshot: the data read back is the same as shown");
        check(HomeSnapshot.decode(b, 43L, "ru-RU") == null, "snapshot: another APK build is not used (resource ids)");
        check(HomeSnapshot.decode(b, 42L, "en-US") == null, "snapshot: another locale is not used");
        check(HomeSnapshot.decode(Arrays.copyOf(b, b.length - 3), 42L, "ru-RU") == null, "snapshot: a cut file is not used");
        byte[] more = Arrays.copyOf(b, b.length + 1);
        check(HomeSnapshot.decode(more, 42L, "ru-RU") == null, "snapshot: trailing bytes are not used");
        byte[] junk = b.clone();
        junk[0] ^= 0x55;
        check(HomeSnapshot.decode(junk, 42L, "ru-RU") == null, "snapshot: a foreign file is not used");
        check(HomeSnapshot.decode(new byte[0], 42L, "ru-RU") == null && HomeSnapshot.decode(null, 42L, "ru-RU") == null,
                "snapshot: empty or none");
        // same(): any one field of any one card changes it
        int changed = 0, fields = 0;
        for (java.lang.reflect.Field f : Card.class.getDeclaredFields()) {
            int m = f.getModifiers();
            if (java.lang.reflect.Modifier.isStatic(m) || java.lang.reflect.Modifier.isFinal(m)) continue;
            fields++;
            HomeData e = HomeSnapshot.decode(b, 42L, "ru-RU");
            Card k = e.cont.get(0);
            try {
                Class<?> t = f.getType();
                if (t == int.class) f.setInt(k, f.getInt(k) + 1);
                else if (t == long.class) f.setLong(k, f.getLong(k) + 1);
                else if (t == boolean.class) f.setBoolean(k, !f.getBoolean(k));
                else f.set(k, f.get(k) + "x");
            } catch (IllegalAccessException ex) {
                check(false, "snapshot: " + ex);
            }
            if (!HomeSnapshot.same(d, e)) changed++;
            else check(false, "snapshot: same() misses a change of Card." + f.getName());
        }
        check(fields > 30 && changed == fields, "snapshot: same() sees every card field (" + changed + "/" + fields + ")");
        HomeData o = HomeSnapshot.decode(b, 42L, "ru-RU");
        o.online = false;
        check(!HomeSnapshot.same(d, o), "snapshot: same() sees online");
        HomeData moved = HomeSnapshot.decode(b, 42L, "ru-RU");
        Collections.swap(moved.apps, 0, 1);
        check(!HomeSnapshot.same(d, moved), "snapshot: same() sees a new app order");
        check(!HomeSnapshot.same(null, d) && HomeSnapshot.same(null, null), "snapshot: same() with nothing shown");
        Card big = new Card(Card.PROGRAM, "p", "T");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30000; i++) sb.append('\u0416');
        big.desc = sb.toString();
        HomeData bd = new HomeData();
        bd.cont.add(big);
        HomeData br = HomeSnapshot.decode(HomeSnapshot.encode(bd, 1L, "ru"), 1L, "ru");
        check(br != null && br.cont.get(0).desc.length() == 8000, "snapshot: a huge text is cut, the save never fails");
    }

    /** The view half: the first frame waits for the snapshot (bounded), the data and the network lanes are apart. */
    static void startupWiring() {
        String dir = System.getProperty("home.src");
        if (dir == null) return;
        String act = read(dir + "/HomeActivity.java"), loader = read(dir + "/data/HomeLoader.java");
        int oc = act.indexOf("protected void onCreate(");
        String ob = oc < 0 ? "" : act.substring(oc, act.indexOf("\n    }", oc));
        check(ob.indexOf("readStart();") > 0 && ob.indexOf("readStart();") < ob.indexOf("build();")
                && ob.indexOf("showStart();") > ob.indexOf("build();"), "startup: the snapshot is read while the views are built, shown before the first frame");
        check(act.contains("mStart.get(SNAPSHOT_WAIT_MS, TimeUnit.MILLISECONDS)"), "startup: the main thread waits for the snapshot only a bounded time");
        int ld = act.indexOf("private void load(boolean network) {");
        String lb = ld < 0 ? "" : act.substring(ld, act.indexOf("\n    }", ld));
        check(lb.contains("lanes().post(Lanes.LOAD") && !lb.contains("io()"), "startup: the screen's data loads on its own lane");
        int rw = act.indexOf("private void refreshWeather(boolean force) {");
        String wb = rw < 0 ? "" : act.substring(rw, act.indexOf("\n    }", rw));
        check(wb.contains("once(Lanes.NET, \"weather\"") && !wb.contains("io()"), "startup: the weather fetch runs on the network lane, never stacked");
        check(!loader.contains("TmdbNet") && !loader.contains("Trending.refresh") && !loader.contains("Weather.")
                && loader.contains("Trending.load(c)"), "startup: the loader reads caches only, no server");
        check(act.contains("HomeSnapshot.same(mData, d)"), "startup: data equal to the shown screen is not bound again");
        check(act.contains("reportFullyDrawn()"), "startup: the first real content reports fully drawn");
    }

    static String read(String p) {
        try {
            return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(p)), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            check(false, "read " + p);
            return "";
        }
    }

    static void grid() {
        eq(FocusMap.grid(2, 14, 6, FocusMap.UP), -1, "grid first line UP leaves");
        eq(FocusMap.grid(8, 14, 6, FocusMap.UP), 2, "grid UP");
        eq(FocusMap.grid(2, 14, 6, FocusMap.DOWN), 8, "grid DOWN");
        eq(FocusMap.grid(10, 14, 6, FocusMap.DOWN), 13, "grid DOWN into a short last line");
        eq(FocusMap.grid(13, 14, 6, FocusMap.DOWN), 13, "grid last line stays");
        eq(FocusMap.grid(6, 14, 6, FocusMap.LEFT), 6, "grid LEFT at a line start stays");
        eq(FocusMap.grid(5, 14, 6, FocusMap.RIGHT), 5, "grid RIGHT at a line end stays");
        eq(FocusMap.grid(13, 14, 6, FocusMap.RIGHT), 13, "grid RIGHT on the last item stays");
    }

    static void vitrina() {
        int g = Grid.GUTTER;
        eq(Grid.x(1), 116, "column 1 on the margin");
        eq(Grid.x(12) + Grid.COL, 1804, "column 12 ends on the right margin");
        eq(6 * Grid.DOCK_W + 5 * g, Grid.span(12), "6 dock tiles fill the grid");
        eq(Grid.DOCK_W, Grid.span(2), "a dock tile is 2 columns");
        eq(Grid.CHIP_W, Grid.span(3), "a chip is 3 columns");
        check(Math.abs(Grid.UPNEXT_W / (double) Grid.UPNEXT_H - 16 / 9.0) < 0.02, "a Continue card is 16:9");
        eq(Vitrina.roundX(Vitrina.ROUND_SETTINGS) + Grid.ROUND, Grid.W - Grid.MARGIN, "Settings on the right margin");
        eq(Vitrina.roundX(Vitrina.ROUND_APPS), 1636, "All apps 24 left of Settings (the mock)");
        eq(Vitrina.chipX(1), 548, "HDMI 2 on column 4");
        check(Vitrina.chipX(Grid.MAX_CHIPS - 1) + Grid.CHIP_W < Vitrina.roundX(0), "3 chips never touch the round buttons");
        eq(Grid.CHIPS_Y + Grid.CHIP_H, Grid.BOTTOM, "the bottom line ends on the bottom margin");
        eq(Vitrina.chips(2), 2, "2 inputs: 2 chips");
        eq(Vitrina.chips(5), 3, "5 inputs: 3 chips");
        check(!Vitrina.moreChip(3) && Vitrina.moreChip(4), "the last chip lists them all from 4 inputs");
        eq(Vitrina.bottomCenters(2).length, 4, "2 chips and 2 round buttons");
        // one screen: shelf, (Continue row), dock, bottom line never overlap
        eq(Vitrina.shelfBottom(false) - ShelfH.WITH_BUTTONS, 312, "the full shelf starts at 312 (the mock)");
        check(Vitrina.shelfBottom(false) < Grid.DOCK_Y, "hero buttons above the dock");
        eq(Vitrina.shelfBottom(true) + 40, Grid.UPNEXT_Y, "with a Continue row the shelf ends 40 above the cards");
        check(Vitrina.shelfBottom(true) - ShelfH.NO_BUTTONS > Grid.STATUS_Y + Grid.STATUS_H, "and starts under the clock");
        check(Grid.UPNEXT_Y + Grid.UPNEXT_H + 40 <= Grid.DOCK_Y, "Continue cards above the dock, focus lift included");
        check(Grid.DOCK_Y + Grid.DOCK_H + Grid.DOCK_LABEL_GAP + 32 <= Grid.CHIPS_Y, "the dock name above the chips");
        eq(Vitrina.stillY(false), 320, "the frame beside the hero buttons (the mock)");
        check(Vitrina.stillY(true) + 302 + 40 <= Grid.UPNEXT_Y, "the frame ends 40 above a Continue row");
        check(Vitrina.stillY(true) > Grid.STATUS_Y + Grid.STATUS_H, "and stays under the clock");
        eq(Vitrina.visible(Grid.DOCK_W, g), 6, "6 dock tiles on screen");
        eq(Vitrina.scroll(0, 3, 4, 6), 0, "a short row never slides");
        eq(Vitrina.scroll(3, 3, 14, 6), 2, "LEFT onto the first shown keeps one of look ahead");
        eq(Vitrina.gridY(6, 6), Grid.APPS_GRID_Y + Grid.APPS_PITCH, "apps page: second line");
        eq(Vitrina.gridOffset(0, 40, 6), 0, "apps page: first line, no scroll");
        check(Vitrina.gridY(36, 6) - Vitrina.gridOffset(36, 40, 6) + Grid.DOCK_H < Grid.BOTTOM, "apps page: the last line comes up");
        check(Vitrina.console("PlayStation 5") && Vitrina.console("Xbox Series X") && Vitrina.console("PS5"), "consoles");
        check(!Vitrina.console("Apple TV") && !Vitrina.console(null) && !Vitrina.console("Upstairs"), "not consoles");
    }

    /** Shelf heights (ShelfView.designHeight): eyebrow, title, season line, progress line, buttons. */
    static final class ShelfH {
        static final int NO_BUTTONS = 32 + 16 + 88 + 16 + 36 + 8 + 32, WITH_BUTTONS = NO_BUTTONS + 28 + 64;
    }

    static void artRule() {
        int bw = 536, bh = 302;
        // the mock: TMDB backdrop 1920x1080 first, the episode still second
        ArtRule.Pick p = ArtRule.pick(new int[] { 1920, 1080, 1920, 1080 }, bw, bh);
        check(p.mode == ArtRule.FULL && p.index == 0, "the show's backdrop fills the screen: " + p);
        p = ArtRule.pick(new int[] { 0, 0, 1280, 720 }, bw, bh);
        check(p.mode == ArtRule.FULL && p.index == 1, "no backdrop: a 1280 still fills: " + p);
        p = ArtRule.pick(new int[] { 1000, 562, 1280, 720 }, bw, bh);
        check(p.mode == ArtRule.FULL && p.index == 1, "a backdrop under 1280 gives way to a 1280 still: " + p);
        p = ArtRule.pick(new int[] { 1279, 720 }, bw, bh);
        check(p.mode == ArtRule.FRAME, "1279 wide is not enough: " + p);
        // a test unit today: only Lumen Play's 640x360 frame grab
        p = ArtRule.pick(new int[] { 640, 360, 0, 0 }, bw, bh);
        check(p.mode == ArtRule.FRAME && p.index == 0, "frame grab: the frame, " + p);
        check(p.w <= 640 && p.h <= 360, "never enlarged");
        eq(p.w + "x" + p.h, "536x301", "shown at the box (smaller than its pixels)");
        p = ArtRule.pick(new int[] { 640, 360 }, 1072, 603);
        eq(p.w + "x" + p.h, "640x360", "4K box: at its own size, not stretched");
        p = ArtRule.pick(new int[] { 320, 180, 640, 360 }, bw, bh);
        eq(p.index, 1, "the larger small art wins the frame");
        p = ArtRule.pick(new int[] { 460, 690 }, bw, bh);
        check(p.mode == ArtRule.FRAME && p.h == 302 && p.w == 201, "a poster keeps the box height: " + p);
        p = ArtRule.pick(new int[] { 2000, 3000 }, bw, bh);
        check(p.mode == ArtRule.FRAME, "a big portrait never fills: " + p);
        eq(ArtRule.pick(new int[] { 0, 0, 0, 0 }, bw, bh).mode, ArtRule.NONE, "nothing: the bare field");
        eq(ArtRule.pick(new int[0], bw, bh).mode, ArtRule.NONE, "no candidates");
        eq(ArtRule.pick(null, bw, bh).mode, ArtRule.NONE, "null");
    }

    static void greeting() {
        eq(Greeting.of(5), Greeting.MORNING, "05 morning");
        eq(Greeting.of(13), Greeting.DAY, "13 day");
        eq(Greeting.of(21), Greeting.EVENING, "21 evening");
        eq(Greeting.of(23), Greeting.NIGHT, "23 night");
        eq(Greeting.of(2), Greeting.NIGHT, "02 night");
    }

    /** A fake hero: counts binds and every badge visibility change (0 none, 1 empty slot, 2 icon). */
    static final class FakeHero implements HeroBind.Target<String> {
        int items, features, loads, toggles, vis = -1;
        String badge;

        public void item(Card k, boolean buttons, int page, int pages) { items++; }
        public void feature(Card k, int page, int pages) { features++; }
        public void loadBadge(String pkg) { loads++; }

        public void badge(String b, boolean slot) {
            int v = b != null ? 2 : slot ? 1 : 0;
            if (vis >= 0 && v != vis) toggles++;
            vis = v;
            badge = b;
        }
    }

    static Card heroCard(String pkg, String title, String season) {
        Card k = new Card();
        k.pkg = pkg;
        k.appLabel = "Lumen Play";
        k.title = title;
        k.meta = season;
        return k;
    }

    static void heroBind() {
        FakeHero f = new FakeHero();
        HeroBind<String> h = new HeroBind<>(f);
        Card a = heroCard("org.lumen.play", "Ламиго", "Сезон 1, серия 3");
        check(h.item(a, true, 0, 2), "first show binds the hero");
        eq(f.vis, 1, "icon not loaded yet: the badge slot is kept, empty");
        h.badgeLoaded("org.lumen.play", "icon");
        eq(f.vis, 2, "icon arrived: shown in the kept slot");
        int toggles = f.toggles;
        for (int i = 0; i < 20; i++) check(!h.item(a, true, 0, 2), "focus move over the same card: no re-bind " + i);
        for (int i = 0; i < 5; i++) h.item(heroCard("org.lumen.play", "Ламиго", "Сезон 1, серия 3"), true, 0, 2);
        eq(f.items, 1, "same card 25 times (also a fresh copy after a reload): showItem once");
        eq(f.loads, 1, "the icon is asked for once");
        eq(f.toggles, toggles, "the badge never toggles on focus moves");
        check(h.item(heroCard("org.lumen.play", "Другой фильм", ""), false, 0, 1), "another card of the same app binds");
        eq(f.vis, 2, "same app: its kept icon shows at once, no empty slot");
        eq(f.loads, 1, "a kept icon is not loaded again");
        check(h.item(a, false, 0, 1), "the same item in the Continue row (no buttons) is a change");
        check(!h.item(a, false, 0, 1), "and then stays put");
        Card longer = heroCard("org.lumen.play", "Ламиго", "Сезон 1, серия 3");
        longer.left = "Осталось 5 мин";
        check(h.item(longer, false, 0, 1), "new time left after a reload re-binds");
        Card ep4 = heroCard("org.lumen.play", "Ламиго", "Сезон 1, серия 4");
        ep4.left = longer.left;
        check(h.item(ep4, false, 0, 1), "next episode (season line) re-binds");
        Card moved = heroCard("org.lumen.play", "Ламиго", "Сезон 1, серия 4");
        moved.left = longer.left;
        moved.progress = 400;
        check(h.item(moved, false, 0, 1), "new progress re-binds");
        Card renamed = heroCard("org.lumen.play", "Ламиго", "Сезон 1, серия 4");
        renamed.left = longer.left;
        renamed.progress = 400;
        renamed.appLabel = "Lumen Play 2";
        check(h.item(renamed, false, 0, 1), "new app name re-binds");
        eq(f.toggles, toggles, "no badge toggles across cards of one app");
        Card feat = heroCard("", "Дюна", "2024");
        feat.kind = Card.FEATURE;
        check(h.feature(feat, 1, 2), "showcase page binds");
        eq(f.vis, 0, "showcase page: no badge slot");
        check(!h.feature(feat, 1, 2), "showcase page: focus moves do not re-bind");
        eq(f.features, 1, "showFeature once");
        h.badgeLoaded("org.lumen.play", "late icon");
        eq(f.vis, 0, "a late icon of another app does not appear on the showcase page");
        check(h.item(heroCard("com.other", "Фильм", ""), true, 0, 2), "unknown app binds");
        eq(f.vis, 1, "unknown app: empty slot while loading");
        h.badgeLoaded("com.other", null);
        eq(f.vis, 1, "no icon: the slot stays, the name does not shift");
        check(h.item(heroCard("com.other", "Фильм 2", ""), true, 0, 2), "another title of the same app on the same page binds");
        h.badgeLoaded("com.other", "icon 2");
        h.badgeLoaded("com.other", null);
        eq(f.vis, 2, "a second load that failed does not drop the icon the first one gave");
    }
}
