package org.lumen.home.weather;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** CitySearch rules (test/weather/run.sh, plain JDK). */
public final class CitySearchTest {
    private static int failed, passed;

    public static void main(String[] a) {
        queries();
        transliteration();
        merge();
        System.out.println("CitySearchTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static void queries() {
        eq("too short", "[]", show(CitySearch.queries("M", "en")));
        eq("null", "[]", show(CitySearch.queries(null, "en")));
        eq("Latin on an English UI", "[Moskva/en]", show(CitySearch.queries(" Moskva ", "en")));
        eq("Cyrillic on an English UI: also Russian", "[Москва/en, Москва/ru]", show(CitySearch.queries("Москва", "en")));
        eq("Cyrillic on a Russian UI: once", "[Москва/ru]", show(CitySearch.queries("Москва", "ru")));
        eq("Ukrainian letters: Ukrainian", "[Київ/en, Київ/uk]", show(CitySearch.queries("Київ", "en")));
        eq("Kazakh letters: Kazakh", "[Өскемен/de, Өскемен/kk]", show(CitySearch.queries("Өскемен", "de")));
        eq("Belarusian ў", "[Магілёў/en, Магілёў/be]", show(CitySearch.queries("Магілёў", "en")));
        eq("Cyrillic on a Ukrainian UI: no Russian added", "[Москва/uk]", show(CitySearch.queries("Москва", "uk")));
        eq("empty UI language is English", "[Paris/en]", show(CitySearch.queries("Paris", "")));
    }

    static void transliteration() {
        eq("Moskva", "Москва", CitySearch.toCyrillic("Moskva"));
        eq("Ekaterinburg", "Екатеринбург", CitySearch.toCyrillic("Ekaterinburg"));
        eq("Yekaterinburg", "Екатеринбург", CitySearch.toCyrillic("Yekaterinburg"));
        eq("Rostov-na-Donu", "Ростов-на-дону", CitySearch.toCyrillic("Rostov-na-Donu"));
        eq("Sankt-Peterburg", "Санкт-петербург", CitySearch.toCyrillic("Sankt-Peterburg"));
        eq("<city>", "<city>", CitySearch.toCyrillic("<city>"));
        eq("Kazan'", "Казань", CitySearch.toCyrillic("Kazan'"));
        eq("Khabarovsk", "Хабаровск", CitySearch.toCyrillic("Khabarovsk"));
        eq("Irkutsk keeps тск", "Иркутск", CitySearch.toCyrillic("Irkutsk"));
        eq("Chelyabinsk", "Челябинск", CitySearch.toCyrillic("Chelyabinsk"));
        eq("Shchelkovo", "Щелково", CitySearch.toCyrillic("Shchelkovo"));
        eq("Syktyvkar", "Сыктывкар", CitySearch.toCyrillic("Syktyvkar"));
        eq("Yaroslavl'", "Ярославль", CitySearch.toCyrillic("Yaroslavl'"));
        eq("Tsaritsyn", "Царицын", CitySearch.toCyrillic("Tsaritsyn"));
        eq("transliterated pair, English UI", "Ekaterinburg/ru ->Екатеринбург",
                pair(CitySearch.transliterated("Ekaterinburg", "en"), "Ekaterinburg"));
        eq("transliterated pair, Ukrainian UI", "Moskva/uk ->Москва",
                pair(CitySearch.transliterated("Moskva", "uk"), "Moskva"));
        eq("no transliteration of Cyrillic", null, CitySearch.transliterated("Москва", "en"));
        eq("no transliteration of a half Cyrillic name", null, CitySearch.transliterated("Mosква", "en"));
        eq("y before a vowel is й", "Кйив", CitySearch.toCyrillic("Kyiv"));
        eq("no transliteration of digits", null, CitySearch.transliterated("12345", "en"));
        eq("no transliteration of one letter", null, CitySearch.transliterated("M", "en"));
        eq("needs more: nothing found", true, CitySearch.needsMore(Collections.emptyList()));
        eq("needs more: only places without people", true, CitySearch.needsMore(Arrays.asList(0L, 0L)));
        eq("no more: a city found", false, CitySearch.needsMore(Arrays.asList(0L, 1495066L)));
    }

    static void merge() {
        List<long[]> a = Arrays.asList(new long[]{1, 0}, new long[]{2, 25060});
        List<long[]> b = Arrays.asList(new long[]{3, 10381222}, new long[]{1, 0}, new long[]{4, 25060});
        List<List<long[]>> both = new ArrayList<>();
        both.add(a);
        both.add(b);
        eq("merged once, most populous first, equals keep order", "3 2 4 1",
                ids(CitySearch.merge(both, x -> x[0], x -> x[1], 8)));
        eq("count cuts", "3 2", ids(CitySearch.merge(both, x -> x[0], x -> x[1], 2)));
        eq("nothing", "", ids(CitySearch.merge(new ArrayList<List<long[]>>(), x -> x[0], x -> x[1], 8)));
    }

    static String ids(List<long[]> l) {
        StringBuilder b = new StringBuilder();
        for (long[] x : l) b.append(b.length() > 0 ? " " : "").append(x[0]);
        return b.toString();
    }

    static String show(List<String[]> l) {
        List<String> s = new ArrayList<>();
        for (String[] x : l) s.add(x[0] + "/" + x[1]);
        return s.toString();
    }

    static String pair(String[] p, String q) {
        return p == null ? null : q + "/" + p[1] + " ->" + p[0];
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
