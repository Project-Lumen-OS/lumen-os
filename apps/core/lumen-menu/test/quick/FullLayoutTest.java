// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu.quick;

import org.lumen.menu.FeatureId;

import java.util.Arrays;
import java.util.List;

/** FullLayout (All settings as the user arranged it): hide, reorder inside a group, reset, JSON. Plain Java. */
public final class FullLayoutTest {
    private static int bad;

    static void ok(String what, boolean good) {
        if (!good) {
            bad++;
            System.out.println("FAIL " + what);
        }
    }

    public static void main(String[] a) {
        FeatureId[] v = FeatureId.values();
        FeatureId r1 = v[0], r2 = v[1], r3 = v[2], r4 = v[3], r5 = v[4];
        List<FeatureId> g1 = Arrays.asList(r1, r2, r3), g2 = Arrays.asList(r4, r5);
        List<List<FeatureId>> groups = Arrays.asList(g1, g2);
        String sec = "picture", other = "sound";

        FullLayout e = FullLayout.EMPTY;
        ok("empty keeps the stock order", e.arrange(sec, g1).equals(g1) && e.ordered(sec, g2).equals(g2));
        ok("empty shows every row", e.shown(sec, r1) && e.isEmpty());

        FullLayout h = e.toggle(sec, r2);
        ok("a hidden row leaves the page", h.arrange(sec, g1).equals(Arrays.asList(r1, r3)) && !h.shown(sec, r2));
        ok("a hidden row stays in the editor's list", h.ordered(sec, g1).equals(g1));
        ok("hiding is per section", h.shown(other, r2) && h.arrange(other, g1).equals(g1));
        ok("toggle twice shows it again", h.toggle(sec, r2).arrange(sec, g1).equals(g1) && h.toggle(sec, r2).isEmpty());
        ok("layouts are immutable", e.isEmpty() && e.shown(sec, r2));

        FullLayout m = e.move(sec, groups, r1, 1);
        ok("down swaps with the next row", m != null && m.arrange(sec, g1).equals(Arrays.asList(r2, r1, r3)));
        ok("other groups keep their order", m != null && m.arrange(sec, g2).equals(g2));
        ok("other sections keep their order", m != null && m.arrange(other, g1).equals(g1));
        ok("up at the top of a group stays", e.move(sec, groups, r1, -1) == null);
        ok("down at the end of a group stays (never into the next group)", e.move(sec, groups, r3, 1) == null);
        ok("a row of no group stays", e.move(sec, Arrays.asList(g1), r4, 1) == null);
        ok("delta 0 is no move", e.move(sec, groups, r2, 0) == null);
        FullLayout m2 = m.move(sec, groups, r1, 1);
        ok("moves add up", m2 != null && m2.arrange(sec, g1).equals(Arrays.asList(r2, r3, r1)));
        ok("a big delta moves one place", e.move(sec, groups, r1, 5).arrange(sec, g1).equals(Arrays.asList(r2, r1, r3)));
        FullLayout mh = m.toggle(sec, r1);
        ok("hidden and moved together", mh.arrange(sec, g1).equals(Arrays.asList(r2, r3)) && mh.ordered(sec, g1).equals(Arrays.asList(r2, r1, r3)));

        List<FeatureId> grown = Arrays.asList(r1, r2, r3, v[5]);
        ok("a row that came with an update stands at the end of its group",
                m.arrange(sec, grown).equals(Arrays.asList(r2, r1, r3, v[5])));
        ok("a row the projector lacks is never shown", m.arrange(sec, Arrays.asList(r1, r3)).equals(Arrays.asList(r1, r3)));

        FullLayout two = m.toggle(other, r4);
        FullLayout rs = two.reset(sec);
        ok("reset takes one page back", rs.arrange(sec, g1).equals(g1) && !rs.shown(other, r4));
        ok("reset of the last change is empty", m.reset(sec).isEmpty());
        FullLayout both = m.move(other, groups, r4, 1);
        ok("reset keeps the order of other pages", both != null && both.reset(sec).arrange(other, g2).equals(Arrays.asList(r5, r4))
                && both.reset(sec).arrange(sec, g1).equals(g1));

        String w = mh.toggle(other, r5).write();
        FullLayout back = FullLayout.parse(w);
        ok("JSON round trip " + w, back != null && back.write().equals(w));
        ok("JSON keeps the order and the hidden rows", back != null && back.arrange(sec, g1).equals(Arrays.asList(r2, r3)) && !back.shown(other, r5));
        ok("nothing stored is null", FullLayout.parse(null) == null && FullLayout.parse("  ") == null);
        ok("broken JSON is null", FullLayout.parse("{\"v\": 1, \"order\": ") == null && FullLayout.parse("[1]") == null);
        ok("a newer version is never read", FullLayout.parse("{\"v\": 2, \"order\": {}, \"hidden\": []}") == null);
        ok("no version is never read", FullLayout.parse("{\"order\": {}, \"hidden\": []}") == null);
        FullLayout junk = FullLayout.parse("{\"v\": 1, \"order\": {\"picture\": [\"no.such.row\", \"" + r2.id + "\", \"" + r2.id + "\", 7]},"
                + " \"hidden\": [\"picture/no.such.row\", \"nosection\", 5, \"picture/" + r3.id + "\"]}");
        ok("unknown rows, doubles and non text are dropped", junk != null && junk.arrange(sec, g1).equals(Arrays.asList(r2, r1)));
        ok("unknown rows are not written back", junk != null && !junk.write().contains("no.such.row") && !junk.write().contains("nosection"));
        String q = e.toggle("a\"b\\c", r1).write();
        ok("section names are escaped " + q, FullLayout.parse(q) != null && !FullLayout.parse(q).shown("a\"b\\c", r1));

        System.out.println(bad == 0 ? "FullLayoutTest OK" : "FullLayoutTest: " + bad + " failed");
        if (bad != 0) System.exit(1);
    }
}
