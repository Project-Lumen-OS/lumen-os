// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu.quick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The card of a quick menu, packed (DATA_MODEL section 5): the round items in the top row beside the clock, then the
 * lines in item order, then the footer buttons. Tiles: a row holds one wide tile and small ones up to four units (wide
 * 2, small 1), small tiles stay square (120, 124 in a row of four). Sliders take a line each, a run of sliders has 6 px
 * above and 18 px below (the slider of layout D in its 120 px row). List: one item per row. The card never scrolls: a
 * menu taller than {@link #MAX_H} does not fit, the editor refuses the change that would make it so. Sizes are design
 * px of a 1920 x 1080 screen.
 */
public final class QuickPacker {
    public static final int MAX_H = 1000, PAD = 24, HEADER = 124, GAP = 16, TILE = 120, TILE4 = 124, SLIDER = 96,
            SLIDER_TOP = 6, SLIDER_BOTTOM = 18, LIST_GAP = 8, LIST_ROW = 76, LIST_SLIDER = 92, FOOT = 68, UNITS = 4;
    /** Circles beside the clock at most (the clock and the date need the rest of the top row). */
    public static final int MAX_ROUND = 5;

    private QuickPacker() {}

    /** One line of the card: its kind, its items, top and height in design px from the card's top edge. */
    public static final class Line {
        public final LineKind kind;
        public final List<Placement> cells;
        public int top, height;

        Line(LineKind kind) {
            this.kind = kind;
            this.cells = new ArrayList<>();
        }

        /** A row of four small tiles: 124 px squares (the mock's row of four). */
        public boolean four() { return kind == LineKind.TILES && cells.size() == 4; }

        int units() {
            int u = 0;
            for (Placement p : cells) u += p.as == Presentation.WIDE ? 2 : 1;
            return u;
        }

        boolean hasWide() {
            for (Placement p : cells) if (p.as == Presentation.WIDE) return true;
            return false;
        }

        @Override public String toString() { return kind.name().toLowerCase() + " " + cells; }
    }

    /** The packed card. */
    public static final class Card {
        public final Density density;
        public final List<Placement> round, footer;
        public final List<Line> lines;
        /** Top of the footer row, design px (0 without footer). */
        public final int footerTop;
        public final int height;

        Card(Density density, List<Placement> round, List<Line> lines, List<Placement> footer, int footerTop, int height) {
            this.density = density;
            this.round = Collections.unmodifiableList(round);
            this.lines = Collections.unmodifiableList(lines);
            this.footer = Collections.unmodifiableList(footer);
            this.footerTop = footerTop;
            this.height = height;
        }

        /** True when the card fits the screen: never taller than {@link #MAX_H}, at most {@link #MAX_ROUND} circles. */
        public boolean fits() { return height <= MAX_H && round.size() <= MAX_ROUND; }

        /** The ids as the focus map rows: the circles, each line, the footer (empty rows left out). */
        public List<List<String>> keyRows() {
            List<List<String>> out = new ArrayList<>();
            out.add(ids(round));
            for (Line l : lines) out.add(ids(l.cells));
            if (!footer.isEmpty()) out.add(ids(footer));
            return out;
        }

        /** The line holding {@code id}, or -1. */
        public int lineOf(String id) {
            for (int i = 0; i < lines.size(); i++) for (Placement p : lines.get(i).cells) if (p.id.equals(id)) return i;
            return -1;
        }

        private static List<String> ids(List<Placement> ps) {
            List<String> out = new ArrayList<>();
            for (Placement p : ps) out.add(p.id);
            return out;
        }

        @Override public String toString() {
            StringBuilder b = new StringBuilder("top " + round + "\n");
            for (Line l : lines) b.append(l.top).append('+').append(l.height).append(' ').append(l).append('\n');
            if (!footer.isEmpty()) b.append(footerTop).append('+').append(FOOT).append(" footer ").append(footer).append('\n');
            return b.append("height ").append(height).toString();
        }
    }

    public static Card pack(QuickLayout l) {
        List<Placement> round = new ArrayList<>(), footer = new ArrayList<>();
        List<Line> lines = new ArrayList<>();
        Line cur = null;
        for (Placement p : l.items) {
            if (p.as == Presentation.ROUND) { round.add(p); continue; }
            if (p.as == Presentation.FOOTER) { footer.add(p); continue; }
            if (l.density == Density.LIST) {
                Line one = new Line(p.as == Presentation.SLIDER ? LineKind.LIST_SLIDER : LineKind.LIST);
                one.cells.add(p);
                lines.add(one);
                continue;
            }
            if (p.as == Presentation.SLIDER) {
                Line one = new Line(LineKind.SLIDER);
                one.cells.add(p);
                lines.add(one);
                cur = null;
                continue;
            }
            int u = p.as == Presentation.WIDE ? 2 : 1;
            if (cur == null || cur.units() + u > UNITS || p.as == Presentation.WIDE && cur.hasWide()) {
                cur = new Line(LineKind.TILES);
                lines.add(cur);
            }
            cur.cells.add(p);
        }
        boolean list = l.density == Density.LIST;
        int y = PAD + HEADER;
        for (int i = 0; i < lines.size(); i++) {
            Line ln = lines.get(i);
            boolean slider = ln.kind == LineKind.SLIDER;
            boolean runStart = slider && (i == 0 || lines.get(i - 1).kind != LineKind.SLIDER);
            boolean runEnd = slider && (i == lines.size() - 1 || lines.get(i + 1).kind != LineKind.SLIDER);
            if (i > 0) y += list ? LIST_GAP : GAP;
            if (runStart) y += SLIDER_TOP;
            ln.top = y;
            ln.height = ln.kind == LineKind.LIST ? LIST_ROW : ln.kind == LineKind.LIST_SLIDER ? LIST_SLIDER
                    : slider ? SLIDER : ln.four() ? TILE4 : TILE;
            y += ln.height;
            if (runEnd) y += SLIDER_BOTTOM;
        }
        int footerTop = 0;
        if (!footer.isEmpty()) {
            if (!lines.isEmpty()) y += list ? LIST_GAP : GAP;
            footerTop = y;
            y += FOOT;
        }
        return new Card(l.density, round, lines, footer, footerTop, y + PAD);
    }
}
