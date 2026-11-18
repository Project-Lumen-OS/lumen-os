package org.lumen.home.model;

import org.lumen.home.data.Card;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.lumen.ui.Grid;

/**
 * Host tests of Home's pure rules: rows (Витрина: 1, 2, 3 and many Continue items), Continue texts, one item per
 * series, app order, the D-pad map, which element shows the focus (exactly one, through leaving and returning),
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
        focusMap();
        focusPaint();
        focusWiring();
        grid();
        greeting();
        vitrina();
        artRule();
        heroBind();
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
        return new AppOrder.App(pkg + "/.Main", pkg, label);
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
        f.move(FocusMap.RIGHT);
        check(lit(f, 0) == 1 && f.itemFocused(1, 1) && !f.itemFocused(1, 0), "paint: HDMI 2 chip, HDMI 1 off");
        f.move(FocusMap.RIGHT);
        check(lit(f, 0) == 1 && f.itemFocused(1, ALL_APPS), "paint: All apps alone");
        // OK on All apps: the Apps page (HomeActivity.setPage), only its first tile
        f.page = FocusMap.H_APPS;
        check(lit(f, 0) == 1 && f.gridFocused(0, 0) && !f.itemFocused(1, ALL_APPS), "paint: Apps page, All apps off");
        check(lit(f, 3) == 1 && f.gridFocused(3, 3), "paint: Apps page tile 3");
        // BACK (HomeActivity.backFromApps): Home, the focus on All apps
        f.page = FocusMap.H_HOME;
        f.focusItem(1, ALL_APPS);
        check(lit(f, 3) == 1 && f.itemFocused(1, ALL_APPS) && !f.gridFocused(3, 3), "paint: back on All apps alone");
        f.move(FocusMap.RIGHT);
        check(lit(f, 3) == 1 && f.itemFocused(1, GEAR) && !f.itemFocused(1, ALL_APPS), "paint: the gear, All apps off");
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
            one &= lit(w, 0) == 1;
        }
        check(one, "paint: exactly one lit after every key of a walk over all zones");
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
