// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

import org.lumen.home.data.Card;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Which Watch Next items make the "Continue watching" row. Pure. */
public final class ContinueRules {
    public static final int MAX = 12;

    private ContinueRules() {}

    /**
     * Newest engagement first, ONE item per series (the newest episode wins) and per film, at most {@code max}. A
     * series is keyed by its app and normalised title; a film by its app and id.
     */
    public static List<Card> pick(List<Card> watchNext, int max) {
        ArrayList<Card> all = new ArrayList<>(watchNext);
        all.sort((a, b) -> Long.compare(b.engaged, a.engaged));
        HashSet<String> seen = new HashSet<>();
        ArrayList<Card> out = new ArrayList<>();
        for (Card k : all) {
            if (out.size() >= max) break;
            if (!seen.add(key(k))) continue;
            out.add(k);
        }
        return out;
    }

    static String key(Card k) {
        String t = norm(k.title);
        if (k.episodic && !t.isEmpty()) return k.pkg + "|s|" + t;
        return k.pkg + "|i|" + (k.contentId != null && !k.contentId.isEmpty() ? k.contentId : k.id);
    }

    /** Case, "ё", punctuation and spaces do not count. */
    public static String norm(String s) {
        if (s == null) return "";
        String n = s.toLowerCase(Locale.ROOT).replace('ё', 'е');
        StringBuilder b = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (Character.isLetterOrDigit(c)) b.append(c);
        }
        return b.toString();
    }
}
