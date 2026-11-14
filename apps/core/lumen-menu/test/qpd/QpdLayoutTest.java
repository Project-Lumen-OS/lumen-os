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

    private static String at(List<List<String>> rows, int[] rc) {
        return rc == null ? "STAY" : (rc[2] == QpdLayout.OPEN ? "OPEN:" : "") + rows.get(rc[0]).get(rc[1]);
    }

    /** The Z9X grid: header eye + gear, four rows of one wide and two small tiles, the slider. */
    static List<List<String>> z9x(boolean usb) {
        return z9xWith(usb ? "usb" : null);
    }

    /** The Z9X grid with {@code last} (QpdLayout.lastSmallTile) as the last small tile, none when null. */
    static List<List<String>> z9xWith(String last) {
        List<String> smalls = new java.util.ArrayList<>(
                Arrays.asList("keystone", "zoom", "sound", "focus", "bluetooth", "network", "projection_mode"));
        if (last != null) smalls.add(last);
        return QpdLayout.rows(Arrays.asList("eye", "general"),
                Arrays.asList("autocorrect", "picture", "input", "hdmi"), smalls, true);
    }

    static final java.util.Set<String> POPUPS = new java.util.HashSet<>(Arrays.asList(
            "eye", "general", "keystone", "picture", "sound", "focus", "hdmi", "projection_mode", "mirroring"));

    private static String mv(List<List<String>> rows, String from, int dir) {
        for (int r = 0; r < rows.size(); r++) {
            int c = rows.get(r).indexOf(from);
            if (c >= 0) return at(rows, QpdLayout.move(rows, r, c, dir, POPUPS::contains));
        }
        return "NOT FOUND " + from;
    }

    public static void main(String[] a) {
        List<List<String>> g = z9x(true);
        // ---- rows: the requested order, no hole
        eq("rows count", 6, g.size());
        eq("row0", Arrays.asList("eye", "general"), g.get(0));
        eq("row1", Arrays.asList("autocorrect", "keystone", "zoom"), g.get(1));
        eq("row2", Arrays.asList("picture", "sound", "focus"), g.get(2));
        eq("row3", Arrays.asList("input", "bluetooth", "network"), g.get(3));
        eq("row4", Arrays.asList("hdmi", "projection_mode", "usb"), g.get(4));
        eq("row5", Arrays.asList("lamp"), g.get(5));
        check("slider row", QpdLayout.isSliderRow(g));
        List<List<String>> nu = z9x(false);
        eq("no usb: row4 shorter, no placeholder", Arrays.asList("hdmi", "projection_mode"), nu.get(4));

        // ---- last small slot (2026-11-11): screen mirroring in place of USB
        eq("mirroring installed: its tile", "mirroring", QpdLayout.lastSmallTile(true, true));
        eq("mirroring installed, no Files: still its tile", "mirroring", QpdLayout.lastSmallTile(true, false));
        eq("no mirroring: USB where Files exists", "usb", QpdLayout.lastSmallTile(false, true));
        eq("neither: no tile", null, QpdLayout.lastSmallTile(false, false));
        eq("tile keys", "mirroring", QpdLayout.TILE_MIRROR);
        eq("tile keys usb", "usb", QpdLayout.TILE_USB);
        List<List<String>> gm = z9xWith(QpdLayout.lastSmallTile(true, true));
        eq("mirroring: row4", Arrays.asList("hdmi", "projection_mode", "mirroring"), gm.get(4));
        check("mirroring: no USB tile anywhere", gm.stream().noneMatch(r -> r.contains("usb")));
        eq("mirroring: right on the tile opens its popup", "OPEN:mirroring", mv(gm, "mirroring", QpdLayout.RIGHT));
        eq("mirroring: left goes to projection", "projection_mode", mv(gm, "mirroring", QpdLayout.LEFT));
        eq("mirroring: up goes to network", "network", mv(gm, "mirroring", QpdLayout.UP));
        eq("mirroring: down to the slider", "lamp", mv(gm, "mirroring", QpdLayout.DOWN));
        eq("mirroring: down from network lands on it", "mirroring", mv(gm, "network", QpdLayout.DOWN));
        eq("settled: the read back wins", Boolean.TRUE, QpdLayout.settled(Boolean.TRUE, Boolean.FALSE));
        eq("settled: a read back off wins", Boolean.FALSE, QpdLayout.settled(Boolean.FALSE, Boolean.TRUE));
        eq("settled: no answer keeps the old value", Boolean.FALSE, QpdLayout.settled(null, Boolean.FALSE));
        eq("settled: no answer, nothing known", null, QpdLayout.settled(null, null));

        // ---- focus map (prototype keydown handler)
        eq("down from eye -> wide", "autocorrect", mv(g, "eye", QpdLayout.DOWN));
        eq("down from gear -> col 1", "keystone", mv(g, "general", QpdLayout.DOWN));
        eq("up from wide -> eye", "eye", mv(g, "autocorrect", QpdLayout.UP));
        eq("up from small col1 -> gear", "general", mv(g, "keystone", QpdLayout.UP));
        eq("up from small col2 -> gear", "general", mv(g, "zoom", QpdLayout.UP));
        eq("up from header stays", "STAY", mv(g, "eye", QpdLayout.UP));
        eq("down in column", "focus", mv(g, "zoom", QpdLayout.DOWN));
        eq("down to slider", "lamp", mv(g, "usb", QpdLayout.DOWN));
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
        eq("right on eye -> gear", "general", mv(g, "eye", QpdLayout.RIGHT));
        eq("no usb: down from network to the shorter row keeps the column in range", "projection_mode",
                mv(nu, "network", QpdLayout.DOWN));
        eq("no usb: right on projection opens", "OPEN:projection_mode", mv(nu, "projection_mode", QpdLayout.RIGHT));
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

        System.out.println("QpdLayoutTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
