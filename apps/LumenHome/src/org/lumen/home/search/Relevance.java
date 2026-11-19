package org.lumen.home.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * How well a result title answers the query, for the results the apps publish. Exact title first, then the query as a
 * phrase in the title, then titles that hold most of the query's words. A title that shares only one word of a longer
 * query ("Доктор Хаус" for "Мстители: Доктор Дум") scores 0 and is dropped. Plain Java (test/watch).
 */
public final class Relevance {
    public static final int EXACT = 100, PREFIX = 90, PHRASE = 80, WITHIN = 70;
    /** Share of the query's words a title must hold to stay. */
    static final double MIN_WORDS = 0.66;

    private Relevance() {}

    static String norm(String s) {
        if (s == null) return "";
        String l = s.toLowerCase(Locale.ROOT).replace('ё', 'е');
        StringBuilder b = new StringBuilder(l.length());
        for (int i = 0; i < l.length(); i++) {
            char c = l.charAt(i);
            b.append(Character.isLetterOrDigit(c) ? c : ' ');
        }
        return b.toString().trim().replaceAll(" +", " ");
    }

    static List<String> words(String normed) {
        ArrayList<String> out = new ArrayList<>();
        for (String w : normed.split(" ")) if (w.length() > 1 || (w.length() == 1 && Character.isDigit(w.charAt(0)))) out.add(w);
        return out;
    }

    private static boolean same(String a, String b) {
        if (a.equals(b)) return true;
        int n = Math.min(a.length(), b.length());
        return n >= 4 && (a.startsWith(b) || b.startsWith(a)); // Доктора / доктор, Мстители / мстителей
    }

    private static int held(List<String> want, List<String> in) {
        int n = 0;
        for (String w : want) {
            for (String t : in) {
                if (same(w, t)) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    /** 0 = not relevant (dropped), else higher is better. */
    public static int score(String query, String title) {
        String q = norm(query), t = norm(title);
        if (q.isEmpty() || t.isEmpty()) return 0;
        if (t.equals(q)) return EXACT;
        if (t.startsWith(q + " ")) return PREFIX;
        if ((" " + t + " ").contains(" " + q + " ")) return PHRASE;
        List<String> qw = words(q), tw = words(t);
        if (qw.isEmpty() || tw.isEmpty()) return 0;
        if (tw.size() >= 2 && (" " + q + " ").contains(" " + t + " ") && tw.size() >= qw.size() * MIN_WORDS) return WITHIN;
        double share = held(qw, tw) / (double) qw.size();
        if (share < MIN_WORDS) return 0;
        return 30 + (int) Math.round(30 * share);
    }

    /** The relevant items, best first (equal scores keep their order). */
    public static <T> List<T> rank(List<T> items, Function<T, String> title, String query) {
        HashMap<T, Integer> s = new HashMap<>();
        ArrayList<T> out = new ArrayList<>();
        HashSet<T> seen = new HashSet<>();
        for (T it : items) {
            int v = score(query, title.apply(it));
            if (v > 0 && seen.add(it)) {
                s.put(it, v);
                out.add(it);
            }
        }
        out.sort((a, b) -> Integer.compare(s.get(b), s.get(a)));
        return out;
    }
}
