package org.lumen.keyboard;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.lumen.keyboard.KeyboardLanguage.ARABIC;
import static org.lumen.keyboard.KeyboardLanguage.BELARUSIAN;
import static org.lumen.keyboard.KeyboardLanguage.KAZAKH;
import static org.lumen.keyboard.KeyboardLanguage.LATIN;
import static org.lumen.keyboard.KeyboardLanguage.RUSSIAN;
import static org.lumen.keyboard.KeyboardLanguage.UKRAINIAN;

/** Host tests of the pure keyboard rules and of the D-pad on every shipped page (test/run.sh APP_DIR). */
public final class KeyboardTest {
    private static int failed, passed;

    public static void main(String[] a) throws Exception {
        table();
        firstStart();
        systemChanged();
        next();
        turnOff();
        names();
        grid();
        aim();
        popups();
        pages(new File(a.length > 0 ? a[0] : "."));
        System.out.println("KeyboardTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static void table() {
        eq("of ru", RUSSIAN, KeyboardLanguage.of("ru"));
        eq("of ru-RU", RUSSIAN, KeyboardLanguage.of("ru-RU"));
        eq("of uk_UA", UKRAINIAN, KeyboardLanguage.of("uk_UA"));
        eq("of EN", LATIN, KeyboardLanguage.of("EN"));
        eq("of kk", KAZAKH, KeyboardLanguage.of("kk"));
        eq("of be", BELARUSIAN, KeyboardLanguage.of("be"));
        eq("of ar", ARABIC, KeyboardLanguage.of("ar"));
        eq("of de (no own page)", null, KeyboardLanguage.of("de"));
        eq("of zh (no page)", null, KeyboardLanguage.of("zh"));
        eq("of null", null, KeyboardLanguage.of(null));
        eq("id ru", RUSSIAN, KeyboardLanguage.ofSubtypeId(0x4c4b0002));
        eq("id unknown", null, KeyboardLanguage.ofSubtypeId(1802354156));
        Set<Integer> ids = new java.util.HashSet<>();
        for (KeyboardLanguage l : KeyboardLanguage.values()) ids.add(l.subtypeId);
        eq("ids unique", KeyboardLanguage.values().length, ids.size());
        eq("only Latin is ascii", Collections.singletonList(LATIN), latinOnes());
        eq("table order", Arrays.asList(LATIN, RUSSIAN, UKRAINIAN, BELARUSIAN, KAZAKH, ARABIC),
                Arrays.asList(KeyboardLanguage.values()));
    }

    static List<KeyboardLanguage> latinOnes() {
        List<KeyboardLanguage> l = new ArrayList<>();
        for (KeyboardLanguage k : KeyboardLanguage.values()) if (k.latin) l.add(k);
        return l;
    }

    static void firstStart() {
        eq("first start, English UI", set(LATIN, RUSSIAN), LanguagePolicy.firstStart(list("en"), set()));
        eq("first start, Russian UI", set(LATIN, RUSSIAN), LanguagePolicy.firstStart(list("ru"), set()));
        eq("first start, Ukrainian UI", set(LATIN, RUSSIAN, UKRAINIAN), LanguagePolicy.firstStart(list("uk"), set()));
        eq("first start, German UI", set(LATIN, RUSSIAN), LanguagePolicy.firstStart(list("de"), set()));
        eq("first start, two system languages", set(LATIN, RUSSIAN, KAZAKH, ARABIC),
                LanguagePolicy.firstStart(list("kk", "ar"), set()));
        eq("first start keeps what is on", set(LATIN, RUSSIAN, BELARUSIAN),
                LanguagePolicy.firstStart(list("en"), set(BELARUSIAN)));
        eq("first start, no system list", set(LATIN, RUSSIAN), LanguagePolicy.firstStart(null, null));
    }

    static void systemChanged() {
        eq("en to uk adds uk", set(LATIN, RUSSIAN, UKRAINIAN),
                LanguagePolicy.systemChanged(list("en"), list("uk"), set(LATIN, RUSSIAN)));
        eq("change keeps a user removal of Russian", set(LATIN, UKRAINIAN),
                LanguagePolicy.systemChanged(list("en"), list("uk"), set(LATIN)));
        eq("change to a language without page adds nothing", set(LATIN),
                LanguagePolicy.systemChanged(list("en"), list("zh"), set(LATIN)));
        eq("language already in the old list is not added again", set(LATIN),
                LanguagePolicy.systemChanged(list("ru", "en"), list("en", "ru"), set(LATIN)));
        eq("empty list starts from Latin", set(LATIN, KAZAKH),
                LanguagePolicy.systemChanged(list("en"), list("kk"), set()));
        eq("nothing removed when the old language goes", set(LATIN, RUSSIAN, ARABIC),
                LanguagePolicy.systemChanged(list("ru"), list("ar"), set(LATIN, RUSSIAN)));
    }

    static void next() {
        eq("next en -> ru", RUSSIAN, LanguagePolicy.next(set(LATIN, RUSSIAN), LATIN));
        eq("next ru -> en (round)", LATIN, LanguagePolicy.next(set(LATIN, RUSSIAN), RUSSIAN));
        eq("next walks the table order", UKRAINIAN, LanguagePolicy.next(set(ARABIC, UKRAINIAN, RUSSIAN), RUSSIAN));
        eq("next from the last goes to the first", RUSSIAN, LanguagePolicy.next(set(ARABIC, UKRAINIAN, RUSSIAN), ARABIC));
        eq("next from a language that is off", LATIN, LanguagePolicy.next(set(LATIN, RUSSIAN), KAZAKH));
        eq("next with one", LATIN, LanguagePolicy.next(set(LATIN), LATIN));
        eq("next with none", KAZAKH, LanguagePolicy.next(set(), KAZAKH));
        eq("ordered", Arrays.asList(LATIN, RUSSIAN, ARABIC), LanguagePolicy.ordered(set(ARABIC, LATIN, RUSSIAN)));
    }

    static void turnOff() {
        eq("turn off one of two", true, LanguagePolicy.canTurnOff(set(LATIN, RUSSIAN), RUSSIAN));
        eq("last stays on", false, LanguagePolicy.canTurnOff(set(RUSSIAN), RUSSIAN));
        eq("off one is not turned off", false, LanguagePolicy.canTurnOff(set(LATIN, RUSSIAN), ARABIC));
    }

    static void names() {
        Locale ru = new Locale("ru"), de = Locale.GERMAN, en = Locale.US, zh = Locale.SIMPLIFIED_CHINESE;
        eq("latin page for ru UI", Locale.US, LanguagePolicy.latinLocale(ru));
        eq("latin page for de UI", de, LanguagePolicy.latinLocale(de));
        eq("latin page for zh UI", Locale.US, LanguagePolicy.latinLocale(zh));
        eq("latin page for none", Locale.US, LanguagePolicy.latinLocale(null));
        eq("written in latin de", true, LanguagePolicy.writtenInLatin(de));
        eq("written in latin ru", false, LanguagePolicy.writtenInLatin(ru));
        eq("key EN on ru UI", "EN", LanguagePolicy.keyLabel(LATIN, ru));
        eq("key DE on de UI", "DE", LanguagePolicy.keyLabel(LATIN, de));
        eq("key RU", "RU", LanguagePolicy.keyLabel(RUSSIAN, en));
        eq("key KK", "KK", LanguagePolicy.keyLabel(KAZAKH, en));
        eq("name ru", "Русский", LanguagePolicy.ownName(RUSSIAN, en));
        eq("name uk", "Українська", LanguagePolicy.ownName(UKRAINIAN, en));
        eq("name be", "Беларуская", LanguagePolicy.ownName(BELARUSIAN, en));
        eq("name en on ru UI", "English", LanguagePolicy.ownName(LATIN, ru));
        eq("name latin on de UI", "Deutsch", LanguagePolicy.ownName(LATIN, de));
        eq("name kk starts with Қ", true, LanguagePolicy.ownName(KAZAKH, en).startsWith("Қ"));
        eq("name ar", "العربية", LanguagePolicy.ownName(ARABIC, en));
    }

    static void grid() {
        // two rows: 3 keys of 28 wide with 12 gap, then a 68 wide key and a 108 wide key (the bottom row shape)
        int[] l = {0, 40, 80, 0, 80}, t = {0, 0, 0, 36, 36}, w = {28, 28, 28, 68, 28}, h = {28, 28, 28, 28, 28};
        eq("inside the first key", 0, KeyGrid.nearest(l, t, w, h, 10, 10));
        eq("inside the third key", 2, KeyGrid.nearest(l, t, w, h, 90, 5));
        eq("in the gap, nearer the second", 1, KeyGrid.nearest(l, t, w, h, 70, 10));
        eq("wide key, right half", 3, KeyGrid.nearest(l, t, w, h, 60, 50));
        eq("below the row, nearest", 3, KeyGrid.nearest(l, t, w, h, 20, 200));
        eq("right of everything", 4, KeyGrid.nearest(l, t, w, h, 500, 50));
        eq("equal distance takes the first", 0, KeyGrid.nearest(l, t, w, h, 34, 10));
        eq("no keys", -1, KeyGrid.nearest(new int[0], new int[0], new int[0], new int[0], 1, 1));
    }

    static void aim() {
        float[] p = KeyGrid.aim(40, 68, 14, 28, 54, -1, 0);
        eq("aim left: half a key height past its left side", 26f, p[0]);
        eq("aim left stays in the row", 14f, p[1]);
        eq("aim right", 82f, KeyGrid.aim(40, 68, 14, 28, 54, 1, 0)[0]);
        p = KeyGrid.aim(40, 68, 14, 28, 54, 0, -1);
        eq("aim up keeps x", 54f, p[0]);
        eq("aim up: 1.25 key heights", -21f, p[1]);
        eq("aim down", 49f, KeyGrid.aim(40, 68, 14, 28, 54, 0, 1)[1]);
        p = KeyGrid.aim(40, 68, 14, 28, 54, 1, 1);
        eq("aim down right", "82.0 49.0", p[0] + " " + p[1]);
    }

    static void popups() {
        int[] t = {0, 0, 0, 0, 36, 36, 36};
        eq("popup from the key", 1, KeyGrid.popupStart(t, 1, 2));
        eq("popup up to the row's end", 2, KeyGrid.popupStart(t, 2, 2));
        eq("popup at the row's end moves left", 2, KeyGrid.popupStart(t, 3, 2));
        eq("popup of 3 at the row's end", 1, KeyGrid.popupStart(t, 3, 3));
        eq("popup in the next row starts at its key", 4, KeyGrid.popupStart(t, 4, 3));
        eq("popup at the last key stays in its row", 4, KeyGrid.popupStart(t, 6, 3));
        eq("popup longer than the row starts at the row", 0, KeyGrid.popupStart(t, 1, 5));
    }

    /** Every shipped page: rows end together, D-pad steps to the neighbours, every key reachable, popups in row. */
    static void pages(File app) throws Exception {
        File xml = new File(app, "res/xml");
        Map<String, Float> dimens = PageModel.dimens(new File(app, "res/values/dimens.xml"));
        String[] names = xml.list((d, n) -> n.matches("(lumen_[a-z]{2}|qwerty[a-z_]*|qwertz[a-z_]*|azerty|sym[a-z_]*)\\.xml"));
        Arrays.sort(names);
        eq("pages: 5 own, 16 Latin, 6 symbols", 27, names.length);
        int wide = 0;
        String bottom = new PageModel(xml, "qwerty_us.xml", dimens).bottom();
        for (String n : names) {
            PageModel p = new PageModel(xml, n, dimens);
            page(p);
            eq(n + ": bottom row at the same place as on every page (a page change keeps the focus)", bottom, p.bottom());
            for (int[] r : p.rows) if (r[1] - r[0] == 12) wide++;
        }
        eq("rows of 12 keys (ru, uk, be, kk)", 4, wide);
        PageModel ru = new PageModel(xml, "lumen_ru.xml", dimens);
        int x = ru.indexOf("х"), hard = ru.indexOf("ъ"), e = ru.indexOf("э");
        eq("ru: ъ after х", x + 1, hard);
        if (hard < 0 || e < 0) return;
        eq("ru: right from х", hard, ru.step(x, 1, 0));
        eq("ru: right from ъ is the action button", PageModel.ACTION, ru.step(hard, 1, 0));
        eq("ru: down from ъ", e, ru.step(hard, 0, 1));
        eq("ru: up from э", hard, ru.step(e, 0, -1));
    }

    /** The D-pad rules on one page, every broken one listed. */
    static void page(PageModel p) {
        List<String> bad = new ArrayList<>();
        for (int r = 0; r < p.rows.size(); r++) {
            int first = p.rows.get(r)[0], last = p.rows.get(r)[1] - 1;
            if (!p.leftEdge[first] || !p.rightEdge[last]) bad.add("row " + r + " edges");
            int end = p.left[last] + p.width[last];
            if (Math.abs(end - p.totalWidth) > 4) bad.add("row " + r + " ends at " + end + " of " + p.totalWidth);
            for (int i = first; i <= last; i++) {
                if (p.step(i, 1, 0) != (i == last ? PageModel.ACTION : i + 1)) bad.add("right from " + p.label[i]);
                if (p.step(i, -1, 0) != (i == first ? i : i - 1)) bad.add("left from " + p.label[i]);
                int up = p.step(i, 0, -1), down = p.step(i, 0, 1);
                if (r == 0 ? up != PageModel.NORTH : p.rowOf(up) != r - 1) bad.add("up from " + p.label[i]);
                if (r < p.rows.size() - 1 && p.rowOf(down) != r + 1) bad.add("down from " + p.label[i]);
                int n = p.popupCount[i];
                if (n > 0) {
                    int s = KeyGrid.popupStart(p.top, i, n);
                    if (p.rowOf(s) != r || p.rowOf(s + n - 1) != r || s > i || s + n <= i) bad.add("popup of " + p.label[i]);
                }
            }
        }
        boolean[] seen = p.reachable();
        for (int i = 0; i < seen.length; i++) if (!seen[i]) bad.add(p.label[i] + " unreachable");
        eq("D-pad on " + p.name, "[]", bad.toString());
    }

    static Set<KeyboardLanguage> set(KeyboardLanguage... ls) {
        Set<KeyboardLanguage> s = EnumSet.noneOf(KeyboardLanguage.class);
        s.addAll(Arrays.asList(ls));
        return s;
    }

    static List<String> list(String... s) {
        return Arrays.asList(s);
    }

    static void eq(String what, Object want, Object got) {
        if (want == null ? got == null : want.equals(got)) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL " + what + ": want " + want + ", got " + got);
        }
    }
}
