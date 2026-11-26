// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The looks of Home (4PDA 2026-11-22: "several themes"): each look is one data row of {@code assets/home/looks.tsv},
 * never code. A look is a preset of the layout: how big the hero is, the big clock of the calm screen, Continue
 * watching, the recommendations of the hero and how many rows of apps. Picking a look writes its values into the
 * user's layout ({@link HomeLayout#apply}); changing one of them afterwards makes the layout the user's own
 * ({@link #matching} gives null). Pure.
 */
public final class Looks {
    public static final String CLASSIC = "classic";
    /** Hero sizes: the full shelf over the art, the small shelf (title, line, buttons), none (apps from the top). */
    public static final int FULL = 0, SMALL = 1, NONE = 2;

    private Looks() {}

    /** One look: its id (the string name is home_look_&lt;id&gt;) and the layout values it sets. */
    public static final class Look {
        public final String id;
        public final int hero, rows;
        public final boolean clock, cont, showcase;

        public Look(String id, int hero, boolean clock, boolean cont, boolean showcase, int rows) {
            this.id = id;
            this.hero = hero;
            this.clock = clock;
            this.cont = cont;
            this.showcase = showcase;
            this.rows = HomeLayout.rows(rows);
        }
    }

    /** The look Home always has, also when the data file is missing or broken: the Витрина as it shipped. */
    public static final Look BUILT_IN = new Look(CLASSIC, FULL, true, true, true, 1);

    /**
     * The looks of a looks.tsv text: lines {@code id hero clock continue showcase rows} (tabs or spaces, '#' starts a
     * comment), hero full|small|none, switches 1|0. A broken line is left out, a second line with the same id too;
     * Classic is always there and first.
     */
    public static List<Look> parse(String tsv) {
        ArrayList<Look> out = new ArrayList<>();
        out.add(BUILT_IN);
        if (tsv == null) return Collections.unmodifiableList(out);
        for (String raw : tsv.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\\s+");
            if (f.length != 6 || !f[0].matches("[a-z][a-z0-9_]{0,23}")) continue;
            int hero = hero(f[1]);
            Boolean clock = flag(f[2]), cont = flag(f[3]), show = flag(f[4]);
            int rows;
            try {
                rows = Integer.parseInt(f[5]);
            } catch (NumberFormatException e) {
                continue;
            }
            if (hero < 0 || clock == null || cont == null || show == null || rows < HomeLayout.MIN_ROWS || rows > HomeLayout.MAX_ROWS) continue;
            Look l = new Look(f[0], hero, clock, cont, show, rows);
            if (CLASSIC.equals(l.id)) {
                out.set(0, l);
                continue;
            }
            if (of(out, l.id) == null) out.add(l);
        }
        return Collections.unmodifiableList(out);
    }

    public static int hero(String s) {
        switch (s == null ? "" : s) {
            case "full": return FULL;
            case "small": return SMALL;
            case "none": return NONE;
            default: return -1;
        }
    }

    public static String heroName(int h) {
        return h == SMALL ? "small" : h == NONE ? "none" : "full";
    }

    private static Boolean flag(String s) {
        return "1".equals(s) ? Boolean.TRUE : "0".equals(s) ? Boolean.FALSE : null;
    }

    public static Look of(List<Look> looks, String id) {
        for (Look l : looks) if (l.id.equals(id)) return l;
        return null;
    }

    /** The look whose values the layout has now, or null: the user changed one of them (his own layout). */
    public static Look matching(List<Look> looks, HomeLayout l) {
        Look named = of(looks, l.look);
        if (named != null && same(named, l)) return named;
        return null;
    }

    static boolean same(Look k, HomeLayout l) {
        return k.hero == l.hero && k.clock == l.clock && k.cont == l.cont && k.showcase == l.showcase && k.rows == l.rows;
    }
}
