package org.lumen.projector.panel;

import java.util.Arrays;
import java.util.List;

/**
 * Host test of the quick panel layout D logic (QpdLayout: focus map, marquee decision, popup placement, Boost slider
 * mapping): sh test/qpd/run.sh. Plain Java, no Android.
 */
public final class QpdLayoutTest {
    private static int pass, fail;

    private static void check(String name, boolean ok) {
        if (ok) pass++;
        else {
            fail++;
            System.out.println("FAIL " + name);
        }
    }

    private static void eq(String name, Object want, Object got) {
        boolean ok = want == null ? got == null : want.equals(got);
        if (!ok) System.out.println("     want " + want + " got " + got);
        check(name, ok);
    }

    private static String span(float[] s) { return s[0] + ".." + s[1]; }

    private static String at(List<List<String>> rows, int[] rc) {
        return rc == null ? "STAY" : (rc[2] == QpdLayout.OPEN ? "OPEN:" : "") + rows.get(rc[0]).get(rc[1]);
    }

    /**
     * The Z9X grid (MenuSpec.cardRows, 2026-11-13): header theme button + gear, four rows of one wide and two
     * small tiles, the slider; {@code mirroring}: the receiver is installed and takes projection's place.
     */
    static List<List<String>> z9x(boolean mirroring) {
        return grid(Arrays.asList("theme", "general"), mirroring ? "mirroring" : "projection_mode");
    }

    /** The grid with {@code header} buttons and {@code beforeEye} in the middle of the last row. */
    static List<List<String>> grid(List<String> header, String beforeEye) {
        List<String> smalls = new java.util.ArrayList<>(
                Arrays.asList("keystone", "zoom", "sound", "focus", "bluetooth", "network", beforeEye, "eye"));
        return QpdLayout.rows(header, Arrays.asList("autocorrect", "picture", "input", "hdmi"), smalls, true);
    }

    static final java.util.Set<String> POPUPS = new java.util.HashSet<>(Arrays.asList(
            "general", "keystone", "picture", "sound", "focus", "hdmi", "projection_mode", "mirroring"));

    private static String mv(List<List<String>> rows, String from, int dir) {
        for (int r = 0; r < rows.size(); r++) {
            int c = rows.get(r).indexOf(from);
            if (c >= 0) return at(rows, QpdLayout.move(rows, r, c, dir, POPUPS::contains));
        }
        return "NOT FOUND " + from;
    }

    /** The same move on a menu placed on the right side (MenuLook). */
    private static String mvRight(List<List<String>> rows, String from, int dir) {
        for (int r = 0; r < rows.size(); r++) {
            int c = rows.get(r).indexOf(from);
            if (c >= 0) return at(rows, QpdLayout.move(rows, r, c, dir, POPUPS::contains, QpdLayout.SLIDER::equals, true));
        }
        return "NOT FOUND " + from;
    }

    private static List<Integer> box(int[] m) {
        return m == null ? null : Arrays.asList(m[0], m[1], m[2]);
    }

    public static void main(String[] a) {
        List<List<String>> g = z9x(false);
        // ---- rows: the requested order, no hole
        eq("rows count", 6, g.size());
        eq("row0", Arrays.asList("theme", "general"), g.get(0));
        eq("row1", Arrays.asList("autocorrect", "keystone", "zoom"), g.get(1));
        eq("row2", Arrays.asList("picture", "sound", "focus"), g.get(2));
        eq("row3", Arrays.asList("input", "bluetooth", "network"), g.get(3));
        eq("row4", Arrays.asList("hdmi", "projection_mode", "eye"), g.get(4));
        eq("row5", Arrays.asList("lamp"), g.get(5));
        check("slider row", QpdLayout.isSliderRow(g));
        check("no USB tile anywhere", g.stream().noneMatch(r -> r.contains("usb")));

        // ---- screen mirroring in projection's place, eye protection stays the last tile
        List<List<String>> gm = z9x(true);
        eq("mirroring: row4", Arrays.asList("hdmi", "mirroring", "eye"), gm.get(4));
        eq("mirroring: right goes on to eye protection", "eye", mv(gm, "mirroring", QpdLayout.RIGHT));
        eq("mirroring: left goes to the game tile", "hdmi", mv(gm, "mirroring", QpdLayout.LEFT));
        eq("mirroring: up goes to bluetooth", "bluetooth", mv(gm, "mirroring", QpdLayout.UP));
        eq("mirroring: down to the slider", "lamp", mv(gm, "mirroring", QpdLayout.DOWN));
        eq("settled: the read back wins", Boolean.TRUE, QpdLayout.settled(Boolean.TRUE, Boolean.FALSE));
        eq("settled: a read back off wins", Boolean.FALSE, QpdLayout.settled(Boolean.FALSE, Boolean.TRUE));
        eq("settled: no answer keeps the old value", Boolean.FALSE, QpdLayout.settled(null, Boolean.FALSE));
        eq("settled: no answer, nothing known", null, QpdLayout.settled(null, null));

        // ---- eye protection tile: OK switches, the mode used last comes back, RIGHT / held OK only with modes
        List<String> sw = Arrays.asList("off", "on"), modes = Arrays.asList("off", "follow", "rest");
        eq("switch on -> off", "off", QpdLayout.eyeToggle("on", sw, null));
        eq("switch off -> on", "on", QpdLayout.eyeToggle("off", sw, null));
        eq("switch off -> on, a stale mode ignored", "on", QpdLayout.eyeToggle("off", sw, "rest"));
        eq("modes: follow -> off", "off", QpdLayout.eyeToggle("follow", modes, null));
        eq("modes: rest -> off", "off", QpdLayout.eyeToggle("rest", modes, "follow"));
        eq("modes: off -> the mode used last", "rest", QpdLayout.eyeToggle("off", modes, "rest"));
        eq("modes: off, nothing used yet -> the first mode", "follow", QpdLayout.eyeToggle("off", modes, null));
        eq("modes: off, last mode off -> the first mode", "follow", QpdLayout.eyeToggle("off", modes, "off"));
        eq("unknown value: nothing sent", null, QpdLayout.eyeToggle(null, sw, null));
        eq("a value outside the options: nothing sent", null, QpdLayout.eyeToggle("2", sw, null));
        eq("no options: nothing sent", null, QpdLayout.eyeToggle("on", java.util.Collections.emptyList(), null));
        check("switch: no popup", !QpdLayout.eyeHasPopup(sw));
        check("modes: popup", QpdLayout.eyeHasPopup(modes));
        java.util.Set<String> withEye = new java.util.HashSet<>(POPUPS);
        withEye.add("eye");
        eq("eye: right without modes stays", "STAY", mv(g, "eye", QpdLayout.RIGHT));
        int[] eyeCell = {4, 2};
        eq("eye: right with modes opens its popup", "OPEN:eye",
                at(g, QpdLayout.move(g, eyeCell[0], eyeCell[1], QpdLayout.RIGHT, withEye::contains)));

        // ---- header with the gear only (no recogniser on the image): every column goes up to it
        List<List<String>> ng = grid(Arrays.asList("general"), "projection_mode");
        eq("no theme: up from the wide tile -> gear", "general", mv(ng, "autocorrect", QpdLayout.UP));
        eq("no theme: up from a small tile -> gear", "general", mv(ng, "zoom", QpdLayout.UP));
        eq("no theme: down from the gear -> col 0", "autocorrect", mv(ng, "general", QpdLayout.DOWN));

        // ---- focus map (prototype keydown handler)
        eq("down from theme -> wide", "autocorrect", mv(g, "theme", QpdLayout.DOWN));
        eq("down from gear -> col 1", "keystone", mv(g, "general", QpdLayout.DOWN));
        eq("up from wide -> theme", "theme", mv(g, "autocorrect", QpdLayout.UP));
        eq("up from small col1 -> gear", "general", mv(g, "keystone", QpdLayout.UP));
        eq("up from small col2 -> gear", "general", mv(g, "zoom", QpdLayout.UP));
        eq("up from header stays", "STAY", mv(g, "theme", QpdLayout.UP));
        eq("down in column", "focus", mv(g, "zoom", QpdLayout.DOWN));
        eq("down to slider", "lamp", mv(g, "eye", QpdLayout.DOWN));
        eq("down from wide to slider", "lamp", mv(g, "hdmi", QpdLayout.DOWN));
        eq("up from slider -> wide of the last row", "hdmi", mv(g, "lamp", QpdLayout.UP));
        eq("down from slider stays", "STAY", mv(g, "lamp", QpdLayout.DOWN));
        eq("left/right on slider: the slider steps", "STAY", mv(g, "lamp", QpdLayout.LEFT));
        eq("right on slider stays", "STAY", mv(g, "lamp", QpdLayout.RIGHT));
        check("right on slider stays even if every cell had a popup",
                QpdLayout.move(g, g.size() - 1, 0, QpdLayout.RIGHT, k -> true) == null);
        check("left on slider stays even if every cell had a popup",
                QpdLayout.move(g, g.size() - 1, 0, QpdLayout.LEFT, k -> true) == null);
        eq("right in row", "keystone", mv(g, "autocorrect", QpdLayout.RIGHT));
        eq("left in row", "picture", mv(g, "sound", QpdLayout.LEFT));
        eq("left at row start stays", "STAY", mv(g, "picture", QpdLayout.LEFT));
        eq("right on last cell with popup opens it", "OPEN:focus", mv(g, "focus", QpdLayout.RIGHT));
        eq("right on last cell without popup stays", "STAY", mv(g, "zoom", QpdLayout.RIGHT));
        eq("right on gear opens all settings", "OPEN:general", mv(g, "general", QpdLayout.RIGHT));
        eq("right on theme -> gear", "general", mv(g, "theme", QpdLayout.RIGHT));
        // a menu on the right side (MenuLook): the tiles keep their order, the popup opens towards the screen middle
        eq("right side: right in row", "keystone", mvRight(g, "autocorrect", QpdLayout.RIGHT));
        eq("right side: left in row", "picture", mvRight(g, "sound", QpdLayout.LEFT));
        eq("right side: left on the first cell with a popup opens it", "OPEN:picture", mvRight(g, "picture", QpdLayout.LEFT));
        eq("right side: left on the first cell without a popup stays", "STAY", mvRight(g, "autocorrect", QpdLayout.LEFT));
        eq("right side: right on the last cell with a popup stays", "STAY", mvRight(g, "focus", QpdLayout.RIGHT));
        eq("right side: left on the theme button opens nothing", "STAY", mvRight(g, "theme", QpdLayout.LEFT));
        eq("right side: left on the slider steps it", "STAY", mvRight(g, "lamp", QpdLayout.LEFT));
        eq("right side: up and down as on the left", mv(g, "picture", QpdLayout.DOWN), mvRight(g, "picture", QpdLayout.DOWN));
        eq("right side: the open cell is the cell itself", Arrays.asList(2, 0, QpdLayout.OPEN),
                box(QpdLayout.move(g, 2, 0, QpdLayout.LEFT, POPUPS::contains, QpdLayout.SLIDER::equals, true)));
        eq("left side: the open cell is the cell itself", Arrays.asList(2, 2, QpdLayout.OPEN),
                box(QpdLayout.move(g, 2, 2, QpdLayout.RIGHT, POPUPS::contains, QpdLayout.SLIDER::equals, false)));
        eq("pane keys on the left: LEFT", QpdLayout.LEFT, QpdLayout.toward(QpdLayout.LEFT, false));
        eq("pane keys on the left: RIGHT", QpdLayout.RIGHT, QpdLayout.toward(QpdLayout.RIGHT, false));
        eq("pane keys on the right: LEFT is RIGHT", QpdLayout.RIGHT, QpdLayout.toward(QpdLayout.LEFT, true));
        eq("pane keys on the right: RIGHT is LEFT", QpdLayout.LEFT, QpdLayout.toward(QpdLayout.RIGHT, true));
        eq("pane keys on the right: UP stays", QpdLayout.UP, QpdLayout.toward(QpdLayout.UP, true));
        eq("pane keys on the right: DOWN stays", QpdLayout.DOWN, QpdLayout.toward(QpdLayout.DOWN, true));
        // the card clock fits its box (a 12 hour time next to four round buttons): it shrinks, never grows
        eq("clock: a time that fits keeps its size", 1f, QpdLayout.clockFit(200f, 252f));
        eq("clock: a time as wide as the box keeps its size", 1f, QpdLayout.clockFit(252f, 252f));
        eq("clock: a wide time shrinks to the box", 0.9f, QpdLayout.clockFit(280f, 252f));
        eq("clock: no box yet draws at the design size", 1f, QpdLayout.clockFit(280f, 0f));
        List<List<String>> nu = QpdLayout.rows(Arrays.asList("general"), Arrays.asList("autocorrect", "hdmi"),
                Arrays.asList("keystone", "zoom", "projection_mode"), true);
        eq("short last row: down from zoom keeps the column in range", "projection_mode", mv(nu, "zoom", QpdLayout.DOWN));
        eq("short last row: right on projection opens", "OPEN:projection_mode", mv(nu, "projection_mode", QpdLayout.RIGHT));
        check("bad row stays", QpdLayout.move(g, 9, 0, QpdLayout.UP, null) == null);
        check("bad col stays", QpdLayout.move(g, 1, 7, QpdLayout.UP, null) == null);
        // every cell can reach the slider and back to the header with UP / DOWN only
        for (int r = 0; r < g.size(); r++) {
            for (int c = 0; c < g.get(r).size(); c++) {
                int[] p = {r, c, 0};
                int steps = 0;
                while (p != null && !"lamp".equals(g.get(p[0]).get(p[1])) && steps++ < 10)
                    p = QpdLayout.move(g, p[0], p[1], QpdLayout.DOWN, null);
                check("reach slider from " + g.get(r).get(c), p != null && "lamp".equals(g.get(p[0]).get(p[1])));
            }
        }

        // ---- marquee decision
        check("fits: no marquee", !QpdLayout.needsMarquee(100f, 100f));
        check("2 px over: still no marquee", !QpdLayout.needsMarquee(102f, 100f));
        check("3 px over: marquee", QpdLayout.needsMarquee(103f, 100f));
        eq("shift = over + 4, to the left", -34f, QpdLayout.marqueeShift(130f, 100f));
        eq("no shift when it fits", 0f, QpdLayout.marqueeShift(90f, 100f));
        eq("duration 1.6 s + 25 ms per design px", 1600L + 750L, QpdLayout.marqueeMs(130f, 100f, 1f));
        eq("duration in design px on a 4K UI", 1600L + 750L, QpdLayout.marqueeMs(160f, 100f, 2f));
        eq("rest before it runs", 0f, QpdLayout.marqueePhase(500, 2000));
        eq("hold at the start (12 %)", 0f, QpdLayout.marqueePhase(600 + 200, 2000));
        eq("at the end", 1f, QpdLayout.marqueePhase(600 + 1900, 2000));
        check("half way", Math.abs(QpdLayout.marqueePhase(600 + 1000, 2000) - 0.5f) < 0.01f);
        check("comes back (alternate)", Math.abs(QpdLayout.marqueePhase(600 + 3000, 2000) - 0.5f) < 0.01f);
        eq("back at the start", 0f, QpdLayout.marqueePhase(600 + 3950, 2000));

        // ---- small tile label: 15 px, smaller down to 11 px where that fits, else full size and it runs (no ellipsis)
        eq("label that fits: full size", 1f, QpdLayout.labelScale(100f, 108f));
        check("label that fits does not run", !QpdLayout.labelRuns(108f, 108f));
        eq("a little too long: smaller to fit", 108f / 120f, QpdLayout.labelScale(120f, 108f));
        check("a little too long: does not run", !QpdLayout.labelRuns(120f, 108f));
        float edge = 108f * QpdLayout.LABEL / QpdLayout.LABEL_MIN;      // fits exactly at 11 px
        check("fits at 11 px: shrunk to 11, not running", Math.abs(QpdLayout.labelScale(edge, 108f) * QpdLayout.LABEL - 11f) < 0.01f
                && !QpdLayout.labelRuns(edge, 108f));
        eq("too long even at 11 px: full size", 1f, QpdLayout.labelScale(edge + 1f, 108f));
        check("too long even at 11 px: it runs", QpdLayout.labelRuns(edge + 1f, 108f));
        check("never below 11 px", QpdLayout.labelScale(edge * 0.999f, 108f) * QpdLayout.LABEL >= QpdLayout.LABEL_MIN - 0.001f);
        eq("prototype size 15, smallest 11", "15/11", QpdLayout.LABEL + "/" + QpdLayout.LABEL_MIN);
        // English "3D to 2D (Top and Bottom)" (about 196 px at 15 px in a 108 px label box) runs instead of an ellipsis
        check("the long English 3D mode runs, at full size", QpdLayout.labelRuns(196f, 108f) && QpdLayout.labelScale(196f, 108f) == 1f);
        check("a running label really overflows its box (the marquee moves it)", QpdLayout.marqueeShift(196f, 108f) < 0f);

        // ---- popup focus: radio lists land on the checked row, also when it is read after the open
        eq("untouched popup: the checked row", 3, QpdLayout.popupFocus(true, 3, 0));
        eq("untouched popup, nothing checked yet: where it is", 0, QpdLayout.popupFocus(true, -1, 0));
        eq("after a key: where the user put it", 0, QpdLayout.popupFocus(false, 3, 0));
        eq("after a key, focus unknown: where it is", -1, QpdLayout.popupFocus(false, 3, -1));

        // ---- popup placement (design px, screen 1080, margin 40)
        eq("beside the tile, 20 px above", 280, QpdLayout.popupTop(300, 400));
        eq("never above the margin", 40, QpdLayout.popupTop(30, 400));
        eq("pushed up to stay on screen", 1080 - 40 - 500, QpdLayout.popupTop(900, 500));
        eq("taller than the screen starts at the margin", 40, QpdLayout.popupTop(600, 1200));
        eq("max height", 1000, QpdLayout.popupMaxH());
        eq("popup right of the card with a gap", true, QpdLayout.POP_X > QpdLayout.CARD_X + QpdLayout.CARD_W);
        eq("grid fills the card", QpdLayout.CARD_W - 2 * QpdLayout.CARD_PAD,
                QpdLayout.WIDE_W + 2 * QpdLayout.SMALL_W + 2 * QpdLayout.GAP);
        check("window holds card and popup", QpdLayout.WINDOW_W >= QpdLayout.POP_X + QpdLayout.POP_W);

        // ---- popup width: the prototype's 440, wider up to 520 for a long row, never narrower, never wider
        eq("short rows keep 440", 440, QpdLayout.popupWidth(300f));
        eq("a row of 420 needs 452", 452, QpdLayout.popupWidth(420f));
        eq("never above 520", 520, QpdLayout.popupWidth(900f));
        eq("no rows", 440, QpdLayout.popupWidth(0f));
        check("popup still right of the card at its widest", QpdLayout.POP_X + QpdLayout.POP_W_MAX <= QpdLayout.WINDOW_W);
        // ---- popup grows out of its tile: pivot on the tile's centre, kept inside the popup
        eq("pivot on the tile centre", 120f, QpdLayout.popupPivotY(400f, 280f, 600f, true));
        eq("pivot clamped to the popup top", 0f, QpdLayout.popupPivotY(100f, 280f, 600f, true));
        eq("pivot clamped to the popup bottom", 600f, QpdLayout.popupPivotY(1000f, 280f, 600f, true));
        eq("no tile: the middle", 300f, QpdLayout.popupPivotY(400f, 280f, 600f, false));
        // ---- row titles: one line, two lines, or a running line on focus; never cut
        eq("fits: one line", 1, QpdLayout.titleMode(200f, 300f, 1));
        eq("too long: two lines", 2, QpdLayout.titleMode(400f, 300f, 2));
        eq("longer than two lines: runs", 0, QpdLayout.titleMode(900f, 300f, 3));
        eq("row min height", 72f, QpdLayout.rowHeight(29f, 72f, 14f));
        eq("two-line row grows", 86f, QpdLayout.rowHeight(58f, 72f, 14f));
        // ---- All settings: the rail fits the window, UP / DOWN without wrapping
        eq("all settings right edge", 40 + 360 + 16 + 620 + 16 + 420,
                QpdLayout.ALL_X + QpdLayout.RAIL_W + QpdLayout.ALL_GAP + QpdLayout.LIST_W + QpdLayout.ALL_GAP + QpdLayout.TIP_W);
        check("window holds all settings", QpdLayout.WINDOW_W >= QpdLayout.ALL_X + QpdLayout.RAIL_W + QpdLayout.LIST_W
                + QpdLayout.TIP_W + 2 * QpdLayout.ALL_GAP);
        check("seven categories fit the rail", QpdLayout.RAIL_PAD * 2 + 7 * (QpdLayout.ITEM_H + QpdLayout.ITEM_GAP)
                <= QpdLayout.SCREEN_H - 2 * QpdLayout.ALL_Y);
        eq("rail down", 3, QpdLayout.railMove(2, 7, QpdLayout.DOWN));
        eq("rail up", 1, QpdLayout.railMove(2, 7, QpdLayout.UP));
        eq("rail top stays", -1, QpdLayout.railMove(0, 7, QpdLayout.UP));
        eq("rail bottom stays", -1, QpdLayout.railMove(6, 7, QpdLayout.DOWN));
        eq("rail empty", -1, QpdLayout.railMove(0, 0, QpdLayout.DOWN));
        eq("rail left is not a move", -1, QpdLayout.railMove(2, 7, QpdLayout.LEFT));
        // ---- All settings list: greyed rows never take focus, the list shows its end at the last usable row
        boolean[] hb = {false, true, true, true, false, false, false, false};   // note, 3 rows, header, 3 greyed rows
        eq("list down to the next usable row", 2, QpdLayout.listTarget(hb, 1, true));
        eq("list down at the last usable row stays", 3, QpdLayout.listTarget(hb, 3, true));
        eq("list up at the first usable row stays", 1, QpdLayout.listTarget(hb, 1, false));
        eq("list skips greyed rows", 4, QpdLayout.listTarget(new boolean[]{true, false, false, false, true}, 0, true));
        check("the last usable row shows the end below it", QpdLayout.listEnd(hb, 3, true));
        check("a middle row does not jump to the end", !QpdLayout.listEnd(hb, 2, true));
        check("the first usable row shows the top above it", QpdLayout.listEnd(hb, 1, false));
        check("a middle row does not jump to the top", !QpdLayout.listEnd(hb, 2, false));
        // ---- prototype sizes (proto_d3.py .po / .tg / .dot / #pop)
        eq("row 72 / 24 px / radius 22 / padding 18", "72/24/22/18",
                QpdLayout.ROW_MIN_H + "/" + QpdLayout.ROW_TITLE + "/" + QpdLayout.ROW_RADIUS + "/" + QpdLayout.ROW_PAD);
        eq("switch 56 x 32, knob 26", "56/32/26", QpdLayout.SWITCH_W + "/" + QpdLayout.SWITCH_H + "/" + QpdLayout.KNOB);
        eq("radio dot 22, ring 7 when chosen", "22/7", QpdLayout.DOT + "/" + QpdLayout.DOT_SEL);
        eq("rows come in 22 ms apart over 260 ms", "22/260", QpdLayout.ROW_STAGGER_MS + "/" + QpdLayout.ROW_IN_MS);

        // ---- brightness slider and Boost: the track spans the model's own range (min on the left edge, max on the
        // right edge), Boost is full
        eq("level 1 of 1..10 = left edge", 0f, QpdLayout.fill(1, 1, 10));
        eq("level 10 of 1..10 = right edge", 1f, QpdLayout.fill(10, 1, 10));
        eq("level 4 of 1..10 = a third", 1f / 3f, QpdLayout.fill(4, 1, 10));
        eq("Boost (the top of the range while on) = full", 1f, QpdLayout.fill(11, 1, 11));
        eq("unknown = empty", 0f, QpdLayout.fill(null, 1, 10));
        eq("below the range clamps to the left edge", 0f, QpdLayout.fill(0, 1, 10));
        eq("above the range stays full", 1f, QpdLayout.fill(12, 1, 10));
        eq("an empty range draws nothing", 0f, QpdLayout.fill(5, 5, 5));
        eq("level 0 of 0..5 = left edge", 0f, QpdLayout.fill(0, 0, 5));
        // x50-picture-stock: a two-way slider (dynamic black level tuning -40..40, stock TwoTowardProgressComponent)
        // fills from 0 towards the knob, a one-way slider from the left edge
        eq("one-way: from the left edge", "0.0..0.33333334", span(QpdLayout.fillSpan(4, 1, 10)));
        eq("one-way 0..5: from the left edge", "0.0..0.4", span(QpdLayout.fillSpan(2, 0, 5)));
        eq("two-way 0: nothing filled", "0.5..0.5", span(QpdLayout.fillSpan(0, -40, 40)));
        eq("two-way -20: from the knob to 0", "0.25..0.5", span(QpdLayout.fillSpan(-20, -40, 40)));
        eq("two-way +20: from 0 to the knob", "0.5..0.75", span(QpdLayout.fillSpan(20, -40, 40)));
        eq("two-way -40: from the left edge to 0", "0.0..0.5", span(QpdLayout.fillSpan(-40, -40, 40)));
        eq("two-way +40: from 0 to the right edge", "0.5..1.0", span(QpdLayout.fillSpan(40, -40, 40)));
        eq("two-way out of range clamps", "0.5..1.0", span(QpdLayout.fillSpan(99, -40, 40)));
        eq("all below 0 is one-way", "0.0..0.5", span(QpdLayout.fillSpan(-5, -10, 0)));
        eq("two-way unknown: nothing filled", "0.0..0.0", span(QpdLayout.fillSpan(null, -40, 40)));
        eq("label level", "7", QpdLayout.label(7, 1, 10, 11, "Boost"));
        eq("label 10", "10", QpdLayout.label(10, 1, 10, 11, "Boost"));
        eq("label below the range", "1", QpdLayout.label(0, 1, 10, 11, "Boost"));
        eq("label Boost", "Boost", QpdLayout.label(11, 1, 11, 11, "Boost"));
        eq("label without Boost", "10", QpdLayout.label(11, 1, 10, -1, "Boost"));
        eq("label unknown", "", QpdLayout.label(null, 1, 10, 11, "Boost"));
        check("10 is not Boost", !QpdLayout.isBoost(10, 11));
        check("11 is Boost", QpdLayout.isBoost(11, 11));
        check("no Boost on a model without it", !QpdLayout.isBoost(11, -1));
        // every profile's range (test/qpd/run.sh passes device:min:max:boost of every devices/*/menu.json lamp)
        String ranges = System.getProperty("lamp.ranges", "");
        check("the profiles' lamp ranges were passed", !ranges.isEmpty());
        for (String r : ranges.split(",")) {
            if (r.isEmpty()) continue;
            String[] f = r.split(":");
            int min = Integer.parseInt(f[1]), max = Integer.parseInt(f[2]), boost = Integer.parseInt(f[3]);
            eq(f[0] + ": min " + min + " on the left edge", 0f, QpdLayout.fill(min, min, max));
            eq(f[0] + ": max " + max + " on the right edge", 1f, QpdLayout.fill(max, min, max));
            check(f[0] + ": min + 1 moves the knob", QpdLayout.fill(min + 1, min, max) > 0f);
            check(f[0] + ": max - 1 is short of the edge", QpdLayout.fill(max - 1, min, max) < 1f);
            if (boost > 0) eq(f[0] + ": Boost full", 1f, QpdLayout.fill(boost, min, boost));
        }
        check("notice when Boost turns on", QpdLayout.boostNoticeDue(false, true));
        check("no notice when it was on at open", !QpdLayout.boostNoticeDue(null, true));
        check("no notice while it stays on", !QpdLayout.boostNoticeDue(true, true));
        check("no notice when it turns off", !QpdLayout.boostNoticeDue(true, false));
        check("no notice on a failed read", !QpdLayout.boostNoticeDue(false, null));

        // ---- x50-iris-quick: the iris slider tile right under the brightness slider, each slider its own row
        List<String> sl = Arrays.asList("lamp", "picture.iris");
        java.util.function.Predicate<String> isSlider = sl::contains;
        List<List<String>> gi = QpdLayout.rows(Arrays.asList("theme", "general"), Arrays.asList("autocorrect", "picture"),
                Arrays.asList("keystone", "zoom", "sound", "focus"), sl);
        eq("iris: the brightness row", Arrays.asList("lamp"), gi.get(gi.size() - 2));
        eq("iris: the iris row under it", Arrays.asList("picture.iris"), gi.get(gi.size() - 1));
        int li = gi.size() - 2, ii = gi.size() - 1;
        eq("iris: DOWN from the brightness slider", Arrays.asList(ii, 0, 0), box(QpdLayout.move(gi, li, 0, QpdLayout.DOWN, POPUPS::contains, isSlider)));
        eq("iris: UP back to the brightness slider", Arrays.asList(li, 0, 0), box(QpdLayout.move(gi, ii, 0, QpdLayout.UP, POPUPS::contains, isSlider)));
        check("iris: LEFT steps the slider itself", QpdLayout.move(gi, ii, 0, QpdLayout.LEFT, POPUPS::contains, isSlider) == null);
        check("iris: RIGHT steps the slider itself, no popup", QpdLayout.move(gi, ii, 0, QpdLayout.RIGHT, k -> true, isSlider) == null);
        check("iris: brightness LEFT / RIGHT still its own", QpdLayout.move(gi, li, 0, QpdLayout.RIGHT, k -> true, isSlider) == null
                && QpdLayout.move(gi, li, 0, QpdLayout.LEFT, k -> true, isSlider) == null);
        check("iris: DOWN at the iris stays (the last row)", QpdLayout.move(gi, ii, 0, QpdLayout.DOWN, POPUPS::contains, isSlider) == null);
        eq("iris: DOWN from a tile row lands on the brightness slider", Arrays.asList(li, 0, 0),
                box(QpdLayout.move(gi, li - 1, 1, QpdLayout.DOWN, POPUPS::contains, isSlider)));
        eq("one slider list = the old boolean grid", QpdLayout.rows(Arrays.asList("general"), Arrays.asList("a"), Arrays.asList("b"), true),
                QpdLayout.rows(Arrays.asList("general"), Arrays.asList("a"), Arrays.asList("b"), Arrays.asList("lamp")));
        eq("label box: the prototype's 64 for a level", 64f, QpdLayout.labelBox(40f, 1f));
        eq("label box: wider for a long option name", 150f, QpdLayout.labelBox(300f, 2f));

        System.out.println("QpdLayoutTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
