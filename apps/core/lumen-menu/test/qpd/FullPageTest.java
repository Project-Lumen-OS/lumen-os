package org.lumen.projector.panel;

import org.lumen.menu.FeatureId;
import org.lumen.menu.FeatureKind;
import org.lumen.menu.MenuProfile;
import org.lumen.menu.MenuSpec;
import org.lumen.menu.MenuState;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The All settings page as a list model, for every page of every devices/&lt;c&gt;/menu.json: the lines MenuController
 * builds (headers, rows, hint lines), every combination of greyed rows walked with UP / DOWN through QpdLayout's list
 * rules (what QpdAll.moveInList runs), and every combination of hidden rows through MenuSpec.headerShown (what
 * MenuController.apply runs). Plain Java: java org.lumen.projector.panel.FullPageTest &lt;gsi root&gt;.
 */
public final class FullPageTest {
    private static final int HEADER = 0, ROW = 1, TEXT = 2;
    /** Feature-level combinations are enumerated in full up to this many rows on a page, sampled above. */
    private static final int FULL_BITS = 14;
    private static final Set<FeatureId> INLINE_RADIOS = new HashSet<>(Arrays.asList(FeatureId.PROJECTION_MODE));
    private static int pass, fail;

    private static void check(boolean ok, String what) {
        if (ok) pass++;
        else {
            fail++;
            if (fail < 40) System.out.println("FAIL " + what);
        }
    }

    /** One line of the list: its kind, the feature of a row (null for a header or a hint line), its group. */
    private static final class Line {
        final int kind, group;
        final FeatureId feature;

        Line(int kind, FeatureId feature, int group) {
            this.kind = kind;
            this.feature = feature;
            this.group = group;
        }

        @Override public String toString() { return kind == HEADER ? "--" : kind == TEXT ? "text" : String.valueOf(feature); }
    }

    /** The lines of a section page in MenuController.buildSection order. */
    private static List<Line> lines(MenuProfile p, MenuSpec spec, String sec) {
        return lines(p, spec.sections.get(sec), sec);
    }

    /**
     * The lines of a list of groups (a section page, or a page of the menu's own rows in buildPage order, sec null): the
     * light level is a slider row outside the light bar's own section (the stock image page starts with it).
     */
    private static List<Line> lines(MenuProfile p, List<MenuSpec.Group> groups, String sec) {
        List<Line> out = new ArrayList<>();
        int g = 0;
        for (MenuSpec.Group gr : groups) {
            if (gr.header != null) out.add(new Line(HEADER, null, g));
            for (FeatureId f : gr.rows) {
                if (FeatureId.BOOST == f || FeatureId.LAMP == f && MenuSpec.LAMP.equals(sec)) out.add(new Line(TEXT, f, g));
                else if (FeatureId.PICTURE_PQ == f) for (String it : p.items(f)) out.add(new Line(ROW, FeatureId.pq(it), g));
                else if (f.kind == FeatureKind.CHOICE && INLINE_RADIOS.contains(f)) for (String o : p.options(f)) out.add(new Line(ROW, f, g));
                else out.add(new Line(ROW, f, g));
            }
            g++;
        }
        return out;
    }

    /** UP / DOWN through the page with the rows of {@code greyed} unusable: every usable row in order, the end shown. */
    private static void walk(String where, List<Line> ls, Set<FeatureId> greyed) {
        boolean[] can = new boolean[ls.size()];
        List<Integer> usable = new ArrayList<>();
        for (int i = 0; i < ls.size(); i++) {
            can[i] = ls.get(i).kind == ROW && !greyed.contains(ls.get(i).feature);
            if (can[i]) usable.add(i);
        }
        if (usable.isEmpty()) return;
        int first = usable.get(0), last = usable.get(usable.size() - 1);
        List<Integer> seen = new ArrayList<>();
        seen.add(first);
        boolean endShown = QpdLayout.listEnd(can, first, true);
        for (int at = first, n = 0; n <= ls.size(); n++) {
            int to = QpdLayout.listTarget(can, at, true);
            if (to == at) break;
            seen.add(to);
            boolean end = QpdLayout.listEnd(can, to, true);
            check(end == (to == last), where + ": DOWN onto " + ls.get(to) + " scrolls to the end only at the last usable row");
            endShown |= end;
            at = to;
        }
        check(seen.equals(usable), where + ": DOWN visits every usable row in order " + seen + " vs " + usable);
        check(endShown, where + ": the rows under the last usable row (" + ls.subList(last + 1, ls.size()) + ") never come into view");
        check(QpdLayout.listTarget(can, last, true) == last, where + ": DOWN at the last usable row stays in the list");
        check(QpdLayout.listEnd(can, last, true), where + ": DOWN again at the last usable row shows the end");
        boolean topShown = QpdLayout.listEnd(can, last, false);
        int at = last;
        for (int n = 0; n <= ls.size(); n++) {
            int to = QpdLayout.listTarget(can, at, false);
            if (to == at) break;
            topShown |= QpdLayout.listEnd(can, to, false);
            at = to;
        }
        check(at == first && topShown, where + ": UP back to the first usable row shows the top (headers above it)");
    }

    /**
     * Rows of {@code hidden} gone: a header is shown exactly while its group keeps a row (never a header alone), a line
     * only with a row above it too (never at the top).
     */
    private static void headers(String where, MenuSpec spec, List<Line> ls, Set<FeatureId> hidden, List<MenuSpec.Group> groups) {
        MenuState.Builder b = MenuState.builder();
        for (FeatureId f : hidden) b.hide(f);
        MenuState st = b.build();
        for (int i = 0; i < ls.size(); i++) {
            Line l = ls.get(i);
            if (l.kind != HEADER) continue;
            MenuSpec.Group g = groups.get(l.group);
            boolean line = MenuSpec.LINE.equals(g.header);
            List<FeatureId> above = new ArrayList<>();
            for (int k = 0; k < l.group; k++) above.addAll(groups.get(k).rows);
            boolean shown = line ? spec.lineShown(above, g.rows, st) : spec.headerShown(g.rows, st);
            boolean rowsBelow = false, rowsAbove = false;
            for (int k = i + 1; k < ls.size() && ls.get(k).group == l.group; k++) {
                rowsBelow |= ls.get(k).kind == ROW && !st.hidden(ls.get(k).feature);     // MenuController.apply's rows
            }
            for (int k = 0; k < i; k++) rowsAbove |= ls.get(k).kind == ROW && !st.hidden(ls.get(k).feature);
            check(shown == (rowsBelow && (!line || rowsAbove)), where + ": header " + g.header + " shown " + shown
                    + " with rows " + rowsBelow + (line ? ", rows above " + rowsAbove : ""));
        }
    }

    private static List<FeatureId> features(List<Line> ls) {
        List<FeatureId> out = new ArrayList<>();
        for (Line l : ls) {
            FeatureId f = l.kind == ROW ? l.feature : null;
            if (f != null && !out.contains(f)) out.add(f);
        }
        return out;
    }

    private static Set<FeatureId> subset(List<FeatureId> fs, long mask) {
        Set<FeatureId> s = new HashSet<>();
        for (int i = 0; i < fs.size(); i++) if ((mask >> i & 1) != 0) s.add(fs.get(i));
        return s;
    }

    private static List<Long> masks(int n) {
        List<Long> out = new ArrayList<>();
        if (n <= FULL_BITS) { for (long m = 0; m < 1L << n; m++) out.add(m); return out; }
        java.util.Random r = new java.util.Random(7);
        for (int k = 0; k < n; k++) { out.add(1L << k); out.add(((1L << n) - 1) & ~((1L << k) - 1)); }
        for (int k = 0; k < 4096; k++) out.add(r.nextLong() & ((1L << n) - 1));
        return out;
    }

    public static void main(String[] a) throws Exception {
        Path root = Paths.get(a[0]);
        int pages = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(root.resolve("devices"))) {
            for (Path d : ds) {
                Path j = d.resolve("menu.json");
                if (!Files.isRegularFile(j)) continue;
                MenuProfile p = MenuProfile.parse(new String(Files.readAllBytes(j), StandardCharsets.UTF_8));
                if (!p.ok()) { check(false, d.getFileName() + ": profile problems " + p.problems()); continue; }
                MenuSpec spec = MenuSpec.of(p);
                for (String sec : spec.sections.keySet()) {
                    String where = d.getFileName() + "/" + sec;
                    List<Line> ls = lines(p, spec, sec);
                    List<FeatureId> fs = features(ls);
                    for (long m : masks(fs.size())) {
                        walk(where, ls, subset(fs, m));
                        headers(where, spec, ls, subset(fs, m), spec.sections.get(sec));
                    }
                    pages++;
                }
                // the pages of the menu's own rows too (x50-picture-stock: the stock picture settings page has headers)
                for (Map.Entry<FeatureId, List<MenuSpec.Group>> e : spec.pages.entrySet()) {
                    String where = d.getFileName() + "/page " + e.getKey();
                    List<Line> ls = lines(p, e.getValue(), null);
                    List<FeatureId> fs = features(ls);
                    for (long m : masks(fs.size())) {
                        walk(where, ls, subset(fs, m));
                        headers(where, spec, ls, subset(fs, m), e.getValue());
                    }
                    pages++;
                }
            }
        }
        check(pages > 30, "pages walked: " + pages);

        // the owner's photo: Z9X HB All settings, Picture, off HDMI: the three HDMI rows are greyed under the header
        MenuProfile hb = MenuProfile.parse(new String(Files.readAllBytes(root.resolve("devices/goat_elegant/menu.json")),
                StandardCharsets.UTF_8));
        MenuSpec spec = MenuSpec.of(hb);
        List<Line> ls = lines(hb, spec, MenuSpec.PICTURE);
        Set<FeatureId> offHdmi = new HashSet<>(Arrays.asList(FeatureId.HDMI_RANGE, FeatureId.HDMI_EDID, FeatureId.PICTURE_ASPECT));
        boolean[] can = new boolean[ls.size()];
        int resetAll = -1;
        for (int i = 0; i < ls.size(); i++) {
            can[i] = ls.get(i).kind == ROW && !offHdmi.contains(ls.get(i).feature);
            if (ls.get(i).feature == FeatureId.PICTURE_RESET_ALL) resetAll = i;
        }
        check(resetAll >= 0 && ls.get(resetAll + 1).kind == HEADER && ls.size() == resetAll + 5,
                "HB picture: Reset all, then the HDMI header and its three rows " + ls);
        check(QpdLayout.listTarget(can, resetAll - 1, true) == resetAll && QpdLayout.listEnd(can, resetAll, true),
                "HB picture: DOWN onto Reset all scrolls to the end, the greyed HDMI rows come into view");
        MenuState off = MenuState.builder().disable(FeatureId.HDMI_RANGE).build();
        check(spec.headerShown(spec.sections.get(MenuSpec.PICTURE).get(1).rows, off), "HB picture: greyed HDMI rows keep their header");

        System.out.println("FullPageTest: " + pages + " pages, " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
