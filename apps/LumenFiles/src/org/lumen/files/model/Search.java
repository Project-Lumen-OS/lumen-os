// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

import java.text.Normalizer;
import java.util.Locale;

/** Name search: case, accents, "ё" and separators do not matter; every typed word must be in the name. */
public final class Search {
    private Search() {}

    public static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        n = n.toLowerCase(Locale.ROOT).replace('\u0451', '\u0435');
        return n.replaceAll("[\\s_.\\-]+", " ").trim();
    }

    /** {@code query} already through {@link #norm}. */
    public static boolean matches(String name, String query) {
        if (query == null || query.isEmpty()) return false;
        String n = norm(name);
        for (String w : query.split(" ")) if (!n.contains(w)) return false;
        return true;
    }
}
