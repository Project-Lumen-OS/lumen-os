// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.weather;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * Which Open-Meteo geocoding searches a typed city name needs (pure Java, test/weather/run.sh). The service matches
 * a name only in the language it is asked in (plus English): "Москва" with language=en finds nothing, and
 * transliterations people type ("Ekaterinburg", "Rostov-na-Donu", "Sankt-Peterburg") match no Latin name at all.
 * So: the UI language first; a Cyrillic name also in its own language; a Latin name that found no real city
 * also in Cyrillic letters. The IP location plays no part: a manual city works behind any VPN.
 */
public final class CitySearch {
    private CitySearch() {
    }

    /** The first searches: {name, language} pairs, the UI language first. */
    public static List<String[]> queries(String q, String uiLang) {
        List<String[]> out = new ArrayList<>();
        String n = q == null ? "" : q.trim();
        if (n.length() < 2) return out;
        String ui = uiLang == null || uiLang.isEmpty() ? "en" : uiLang;
        out.add(new String[]{n, ui});
        String own = cyrillicLanguage(n);
        if (own != null && !own.equals(ui) && !isCyrillicLanguage(ui)) out.add(new String[]{n, own});
        return out;
    }

    /**
     * The search after the first ones found no place with people living there (a Latin name typed for a Russian
     * city): the name in Cyrillic letters, in the UI language when that is written in Cyrillic, else Russian.
     * Null when the name has no Latin letters.
     */
    public static String[] transliterated(String q, String uiLang) {
        String n = q == null ? "" : q.trim();
        if (n.length() < 2 || !hasLatin(n) || hasCyrillic(n)) return null;
        return new String[]{toCyrillic(n), isCyrillicLanguage(uiLang) ? uiLang : "ru"};
    }

    /** True when none of the places found so far has a population (only villages, airports or nothing). */
    public static boolean needsMore(List<Long> populations) {
        for (Long p : populations) if (p != null && p > 0) return false;
        return true;
    }

    /** All found places once (by id), the most populous first, each list's own order kept among equals. */
    public static <T> List<T> merge(List<List<T>> lists, ToLongFunction<T> id, ToLongFunction<T> population, int count) {
        Map<Long, T> seen = new LinkedHashMap<>();
        for (List<T> l : lists) for (T t : l) seen.putIfAbsent(id.applyAsLong(t), t);
        List<T> out = new ArrayList<>(seen.values());
        out.sort((a, b) -> Long.compare(population.applyAsLong(b), population.applyAsLong(a)));
        return out.size() > count ? new ArrayList<>(out.subList(0, count)) : out;
    }

    public static boolean hasCyrillic(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.UnicodeBlock.of(s.charAt(i)) == Character.UnicodeBlock.CYRILLIC) return true;
        }
        return false;
    }

    static boolean hasLatin(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c) && Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN) return true;
        }
        return false;
    }

    /** The Cyrillic language a name is written in by its letters (Ukrainian і ї є ґ, Belarusian ў, Kazakh ә ң ...). */
    static String cyrillicLanguage(String s) {
        if (!hasCyrillic(s)) return null;
        String l = s.toLowerCase(Locale.ROOT);
        if (l.matches(".*[әңғүұқөһ].*")) return "kk";
        if (l.indexOf('ў') >= 0) return "be";
        if (l.matches(".*[іїєґ].*")) return l.matches(".*[їєґ].*") || l.indexOf('ы') < 0 ? "uk" : "be";
        return "ru";
    }

    static boolean isCyrillicLanguage(String lang) {
        return "ru".equals(lang) || "uk".equals(lang) || "be".equals(lang) || "kk".equals(lang)
                || "bg".equals(lang) || "sr".equals(lang) || "mk".equals(lang);
    }

    private static final String[][] MULTI = {
        {"shch", "щ"}, {"sch", "щ"}, {"tsk", "тск"}, {"zh", "ж"}, {"kh", "х"}, {"ts", "ц"}, {"tz", "ц"}, {"ch", "ч"}, {"sh", "ш"},
        {"yu", "ю"}, {"ju", "ю"}, {"iu", "ю"}, {"ya", "я"}, {"ja", "я"}, {"ia", "я"}, {"yo", "ё"}, {"jo", "ё"},
        {"ye", "е"}, {"je", "е"}, {"iy", "ий"}, {"yy", "ый"}, {"ey", "ей"}, {"ay", "ай"}, {"oy", "ой"},
        {"uy", "уй"}, {"ck", "к"}, {"ph", "ф"},
    };

    /** Common Latin spellings of Russian names in Cyrillic letters (Moskva Москва, <city> Нижний, Kazan' Казань). */
    public static String toCyrillic(String s) {
        String l = s.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        int i = 0;
        outer:
        while (i < l.length()) {
            for (String[] m : MULTI) {
                if (l.startsWith(m[0], i)) {
                    out.append(m[1]);
                    i += m[0].length();
                    continue outer;
                }
            }
            char c = l.charAt(i);
            boolean wordStart = i == 0 || !Character.isLetter(l.charAt(i - 1));
            boolean nextVowel = i + 1 < l.length() && "aeiou".indexOf(l.charAt(i + 1)) >= 0;
            out.append(one(c, wordStart, nextVowel));
            i++;
        }
        return capitalWords(out.toString());
    }

    private static String one(char c, boolean wordStart, boolean nextVowel) {
        switch (c) {
            case 'a': return "а";
            case 'b': return "б";
            case 'v': case 'w': return "в";
            case 'g': return "г";
            case 'd': return "д";
            case 'e': return "е";
            case 'z': return "з";
            case 'i': return "и";
            case 'j': return "й";
            case 'k': case 'q': return "к";
            case 'l': return "л";
            case 'm': return "м";
            case 'n': return "н";
            case 'o': return "о";
            case 'p': return "п";
            case 'r': return "р";
            case 's': return "с";
            case 't': return "т";
            case 'u': return "у";
            case 'f': return "ф";
            case 'h': return "х";
            case 'c': return "к";
            case 'x': return "кс";
            case 'y': return wordStart || nextVowel ? "й" : "ы";
            case '\'': case '\u2019': case '\u02bc': return "ь";
            default: return String.valueOf(c);
        }
    }

    private static String capitalWords(String s) {
        StringBuilder b = new StringBuilder(s.length());
        boolean start = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append(start ? Character.toUpperCase(c) : c);
            start = c == ' ';
        }
        return b.toString();
    }
}
