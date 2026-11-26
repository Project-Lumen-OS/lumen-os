// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import java.util.List;

/**
 * Quick panel layout D (owner's choice 2026-11-11, prototype qp_design/proto_d3.py): the plain-Java part, so host
 * tests can check it without Android. Sizes are design px of a 1920 x 1080 screen (Theme.px scales them).
 *
 * Grid: row 0 = the round buttons (the theme button, all settings), rows 1..n = one wide
 * tile and up to two small ones, last row = the brightness slider. Rows are built from the tiles this projector
 * really has, so a missing tile leaves no hole and no placeholder.
 */
final class QpdLayout {
    private QpdLayout() {}

    /**
     * Text and icons of a disabled row (a picture mode the content does not allow, a row stock keeps closed): an
     * opaque neutral grey per theme, never a transparent copy of the ink (that took the warm Sand tint on the light
     * menu and the video through the dark card). Chosen so the grey stays readable on the card yet sits far from the
     * enabled title and value colours (QpdLayoutTest: contrast limits on both themes).
     */
    static final int DISABLED_DARK = 0xFF616161, DISABLED_LIGHT = 0xFF828282;

    static int disabled(boolean light) { return light ? DISABLED_LIGHT : DISABLED_DARK; }

    // ---- card (prototype #qp)
    static final int CARD_X = 40, CARD_Y = 40, CARD_W = 592, CARD_PAD = 24, CARD_RADIUS = 44;
    static final int WIDE_W = 272, SMALL_W = 120, TILE_H = 120, GAP = 16, TILE_RADIUS = 26;
    static final int SLIDER_H = 96, SLIDER_TOP = 6;
    static final int ROUND = 60, ROUND_GAP = 12;
    /** The dot of a small tile whose feature is on (the 3D tile in a 3D mode): diameter, centre inset from the corner. */
    static final int ACTIVE_DOT = 10, ACTIVE_DOT_INSET = 18;
    // ---- popup (prototype #pop) and screen
    static final int POP_X = 640, POP_W = 440, POP_W_MAX = 520, POP_PAD = 16, POP_RADIUS = 36, POP_TOP_OFFSET = 20;
    static final int SCREEN_H = 1080, MARGIN = 40;
    /** The scrim reaches 60 % of the screen (prototype .scrim). */
    static final int SCRIM_W = 1152;
    /** Darkness of the scrim at the menu's edge (prototype .scrim 0 %: 0.5) and the number of gradient stops. */
    static final float SCRIM_EDGE = 0.5f;
    static final int SCRIM_STOPS = 17;

    /**
     * The scrim's darkness at {@code t} (0 at the menu's side, 1 where it ends): an eased fall whose slope is 0 where
     * it ends, so no line is seen there (X50 tester 2026-11-23: the straight ramp ended in a visible vertical edge on
     * a dark picture, the eye sees the bend of a linear ramp as a line).
     */
    static float scrimAlpha(float t) {
        float x = Math.max(0f, Math.min(1f, t));
        return SCRIM_EDGE * (1f - x * x * (3f - 2f * x));
    }

    /** The stops of the scrim gradient: {positions, ARGB black with {@link #scrimAlpha}}. */
    static int[] scrimColors() {
        int[] c = new int[SCRIM_STOPS];
        for (int i = 0; i < SCRIM_STOPS; i++) c[i] = Math.round(255f * scrimAlpha(i / (SCRIM_STOPS - 1f))) << 24;
        return c;
    }

    static float[] scrimStops() {
        float[] f = new float[SCRIM_STOPS];
        for (int i = 0; i < SCRIM_STOPS; i++) f[i] = i / (SCRIM_STOPS - 1f);
        return f;
    }
    /** Window width: the All settings screen, its right edge 40 + 360 + 16 + 620 + 16 + 420 = 1472. */
    static final int WINDOW_W = 1480;
    /** Popup motion (prototype #pop and rowin): fade 220 ms, grow 320 ms, rows 260 ms, 22 ms apart, 8 px in. */
    static final long POP_FADE_MS = 220, POP_GROW_MS = 320, ROW_IN_MS = 260, ROW_STAGGER_MS = 22;
    static final float POP_SCALE_FROM = 0.96f, POP_SHIFT = 14, ROW_SHIFT = 8;

    // ---- popup rows (prototype .po) and All settings rows (.r), design px
    static final int ROW_MIN_H = 72, ROW_PAD = 18, ROW_RADIUS = 22, ROW_VPAD = 14, ROW_TITLE = 24, ROW_VALUE = 19;
    static final int LIST_ROW_MIN_H = 76, LIST_ROW_TITLE = 23, LIST_ROW_VALUE = 20;
    static final int DOT = 22, DOT_RING = 2, DOT_SEL = 7, DOT_GAP = 16;
    static final int SWITCH_W = 56, SWITCH_H = 32, KNOB = 26, KNOB_INSET = 3;
    static final long SWITCH_MS = 280, ROW_FOCUS_MS = 200;
    static final int CHEVRON = 22, VALUE_GAP = 12, CHEVRON_GAP = 6, TITLE_LINES = 2;
    /** Soft edge of a running (marquee) title, design px. */
    static final int FADE = 28;

    // ---- All settings (prototype #all: rail of round category buttons, the list, the hint card)
    static final int ALL_X = 40, ALL_Y = 40, ALL_GAP = 16;
    static final int RAIL_W = 360, RAIL_PAD = 18, ITEM_H = 96, ITEM_GAP = 6, ITEM_RADIUS = 30, ITEM_CIRCLE = 64,
            ITEM_ICON = 30, ITEM_TEXT = 23;
    static final int LIST_W = 620, LIST_PAD_H = 30, LIST_PAD_V = 26, LIST_TITLE = 30;
    static final int TIP_W = 420, TIP_PAD = 22;
    /** Focus motion (stock misckey values): scale, duration. */
    static final float FOCUS_SCALE = 1.08f, ROUND_FOCUS_SCALE = 1.12f, SLIDER_FOCUS_SCALE = 1.03f, LIST_FOCUS_SCALE = 1.04f;
    /**
     * The panel right of the card (2026-11-18, decision 1: every popup is this full height panel, one look
     * everywhere; the pencil editor uses the same place): 24 px right of the card, the screen height minus the margins.
     */
    static final int SIDE_X = CARD_X + CARD_W + 24, SIDE_W = 600, SIDE_H = SCREEN_H - 2 * MARGIN, SIDE_PAD_H = 32,
            SIDE_PAD_TOP = 30, SIDE_PAD_BOTTOM = 28, SIDE_RADIUS = 44;
    static final long FOCUS_MS = 300;
    /** Remote brightness bar: hides 2.5 s after the last key. */
    static final long BAR_HIDE_MS = 2_500;

    static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;
    static final String OFF = "off";
    /** move(): stay where you are. */
    static final int[] STAY = null;
    /** move() result marker in [2]: 1 = open the focused tile's popup instead of moving. */
    static final int OPEN = 1;

    /**
     * The eye protection tile's OK (2026-11-13): on turns it off, off turns on the mode used last (a model
     * with modes, X50 follow / rest) or the first one after off. Null while the value is unknown (nothing is sent).
     */
    static String eyeToggle(String now, List<String> options, String lastOn) {
        if (now == null || options.size() < 2 || !options.contains(now)) return null;
        if (!OFF.equals(now)) return OFF;
        return lastOn != null && !OFF.equals(lastOn) && options.contains(lastOn) ? lastOn : options.get(1);
    }

    /** The eye protection tile opens its popup (RIGHT, long OK) only where the page has more than the switch. */
    static boolean eyeHasPopup(List<String> options) { return options.size() > 2; }

    /**
     * A screen mirroring switch after a change: the value the receiver reports back; when it did not answer, the
     * value from before (the switch never shows a state nobody confirmed).
     */
    static Boolean settled(Boolean readBack, Boolean before) {
        return readBack != null ? readBack : before;
    }

    /**
     * D-pad move on the grid with the light slider as the only slider row ({@link #SLIDER}).
     */
    static int[] move(List<List<String>> rows, int r, int c, int dir, java.util.function.Predicate<String> hasPopup) {
        return move(rows, r, c, dir, hasPopup, SLIDER::equals);
    }

    /**
     * D-pad move on the grid. {@code rows.get(r)} = the cell keys of row r (row 0 = round buttons, the last rows = the
     * slider tiles, {@code slider} accepts their keys: a slider handles LEFT / RIGHT itself). Returns {row, col, open}:
     * open = 1 when RIGHT on the last cell of a row asks for that cell's popup ({@code hasPopup}); null = no move.
     */
    static int[] move(List<List<String>> rows, int r, int c, int dir, java.util.function.Predicate<String> hasPopup,
                      java.util.function.Predicate<String> slider) {
        return move(rows, r, c, dir, hasPopup, slider, false);
    }

    /**
     * The grid move of a menu on the {@code right} side (MenuLook): the tiles keep their order, only the popup opens
     * towards it, so LEFT on the first cell of a row asks for that cell's popup and RIGHT on the last one does not.
     */
    static int[] move(List<List<String>> rows, int r, int c, int dir, java.util.function.Predicate<String> hasPopup,
                      java.util.function.Predicate<String> slider, boolean right) {
        if (rows == null || r < 0 || r >= rows.size()) return STAY;
        List<String> row = rows.get(r);
        if (c < 0 || c >= row.size()) return STAY;
        int last = rows.size() - 1;
        switch (dir) {
            case UP: {
                if (r == 0) return STAY;
                List<String> up = rows.get(r - 1);
                if (up.isEmpty()) return STAY;
                int nc;
                // into the header from a grid row: the wide column goes to the left button, the small ones to the right
                if (r - 1 == 0 && up.size() == 2) nc = c == 0 ? 0 : 1;
                else nc = Math.min(c, up.size() - 1);
                return new int[]{r - 1, nc, 0};
            }
            case DOWN: {
                if (r >= last) return STAY;
                List<String> dn = rows.get(r + 1);
                if (dn.isEmpty()) return STAY;
                return new int[]{r + 1, Math.min(c, dn.size() - 1), 0};
            }
            case LEFT:
                if (slider.test(row.get(c))) return STAY;            // a slider steps itself
                if (c > 0) return new int[]{r, c - 1, 0};
                return right ? opens(r, c, row, hasPopup) : STAY;
            case RIGHT:
                if (slider.test(row.get(c))) return STAY;
                if (c < row.size() - 1) return new int[]{r, c + 1, 0};
                return right ? STAY : opens(r, c, row, hasPopup);
            default:
                return STAY;
        }
    }

    /** The cell's popup on a key towards it, when the cell has one. */
    private static int[] opens(int r, int c, List<String> row, java.util.function.Predicate<String> hasPopup) {
        return hasPopup != null && hasPopup.test(row.get(c)) ? new int[]{r, c, OPEN} : STAY;
    }

    /**
     * A key between the menu's panes (card, popup, All settings rail and list) on a menu on the {@code right} side
     * (MenuLook): the panes are mirrored, so LEFT and RIGHT swap. Rows that step a value keep the plain keys.
     */
    static int toward(int dir, boolean right) {
        if (!right) return dir;
        return dir == LEFT ? RIGHT : dir == RIGHT ? LEFT : dir;
    }

    static final String SLIDER = "lamp";

    /** Width of the clock and date beside {@code buttons} round buttons (at least the two of the prototype). */
    static int clockWidth(int buttons) {
        int n = Math.max(2, buttons);
        return CARD_W - 2 * CARD_PAD - n * ROUND - (n - 1) * ROUND_GAP - 16;
    }

    /**
     * The clock's size factor: a time wider than its box (a 12 hour "10:59 AM" next to four round buttons) shrinks
     * until it fits, so it never runs under the buttons; a time that fits keeps the design size.
     */
    static float clockFit(float textW, float boxW) {
        return textW > boxW && boxW > 0 ? boxW / textW : 1f;
    }

    static boolean isSliderRow(List<List<String>> rows) {
        if (rows.isEmpty()) return false;
        List<String> l = rows.get(rows.size() - 1);
        return l.size() == 1 && SLIDER.equals(l.get(0));
    }

    /** {@link #rows(List, List, List, List)} with the light slider alone, or no slider. */
    static java.util.List<java.util.List<String>> rows(List<String> header, List<String> wides, List<String> smalls,
                                                       boolean slider) {
        return rows(header, wides, smalls, slider ? java.util.Collections.singletonList(SLIDER) : java.util.Collections.emptyList());
    }

    /**
     * Builds the grid rows: header buttons, then per row one wide tile (in order) with the next small tiles, two at
     * most, then one row per slider tile, top to bottom (the light level, the iris under it). A row without a wide
     * tile takes up to three small ones (never happens on the Z9X).
     */
    static java.util.List<java.util.List<String>> rows(List<String> header, List<String> wides, List<String> smalls,
                                                       List<String> sliders) {
        java.util.List<java.util.List<String>> out = new java.util.ArrayList<>();
        out.add(new java.util.ArrayList<>(header));
        int s = 0;
        for (String w : wides) {
            java.util.List<String> row = new java.util.ArrayList<>();
            row.add(w);
            for (int k = 0; k < 2 && s < smalls.size(); k++) row.add(smalls.get(s++));
            out.add(row);
        }
        while (s < smalls.size()) {
            java.util.List<String> row = new java.util.ArrayList<>();
            for (int k = 0; k < 3 && s < smalls.size(); k++) row.add(smalls.get(s++));
            out.add(row);
        }
        for (String k : sliders) out.add(new java.util.ArrayList<>(java.util.Collections.singletonList(k)));
        return out;
    }

    // ------------------------------------------------------------------ marquee (wide tile titles)
    /** Title wider than its box by more than 2 px: it runs left and right (prototype marquee()). */
    static boolean needsMarquee(float textW, float boxW) {
        return textW - boxW > 2f;
    }

    /** Travel of the running title, px (negative = to the left). */
    static float marqueeShift(float textW, float boxW) {
        return needsMarquee(textW, boxW) ? -(textW - boxW + 4f) : 0f;
    }

    /** One way of the running title, ms: 1.6 s plus 25 ms per px (prototype --md). */
    static long marqueeMs(float textW, float boxW, float pxPerDesign) {
        float over = Math.max(0f, textW - boxW) / Math.max(0.01f, pxPerDesign);
        return 1600L + Math.round(over * 25f);
    }

    /**
     * Marquee position 0..1 at {@code t} ms after the focus settled: 600 ms rest, then 12 % hold, travel, 12 % hold,
     * and back the same way (CSS alternate with keyframes 0,12 % / 88,100 %).
     */
    static float marqueePhase(long t, long oneWayMs) {
        if (t < 600 || oneWayMs <= 0) return 0f;
        long u = (t - 600) % (2 * oneWayMs);
        boolean back = u >= oneWayMs;
        float f = (float) (back ? u - oneWayMs : u) / oneWayMs;
        float p;
        if (f <= 0.12f) p = 0f;
        else if (f >= 0.88f) p = 1f;
        else p = (f - 0.12f) / 0.76f;
        return back ? 1f - p : p;
    }

    // ------------------------------------------------------------------ small tile label (on focus)
    /** Label size of a small tile, design px (prototype 15), and the smallest size it shrinks to before it runs. */
    static final int LABEL = 15, LABEL_MIN = 11;

    /**
     * Size factor of a small tile's label {@code textW} wide at {@link #LABEL} in {@code room}: 1 when it fits, smaller
     * down to {@link #LABEL_MIN} where that makes it fit (German "Trapezkorrektur"), else 1 again and it runs.
     */
    static float labelScale(float textW, float room) {
        if (textW <= room) return 1f;
        float k = room / textW;
        return k * LABEL >= LABEL_MIN ? k : 1f;
    }

    /**
     * True when the label does not fit even at {@link #LABEL_MIN}: it runs left and right at full size like a wide
     * tile's lines, never an ellipsis (stock has no short form of "3D to 2D (Top and Bottom)").
     */
    static boolean labelRuns(float textW, float room) {
        return textW > room && room / textW * LABEL < LABEL_MIN;
    }

    // ------------------------------------------------------------------ popup focus
    /**
     * Row to focus in a popup after a read-back (stock Android TV lists open on the checked item): the checked row
     * while no key was pressed since the popup opened (its value may be read only after the open), else where the
     * focus is. -1 = where it is.
     */
    static int popupFocus(boolean untouched, int checked, int focused) {
        return untouched && checked >= 0 ? checked : focused;
    }

    // ------------------------------------------------------------------ popup placement
    /**
     * Popup top (design px): 20 px above the tile, kept inside the screen with a 40 px margin; a popup taller than
     * the screen allows starts at the margin (it scrolls).
     */
    static int popupTop(int tileTop, int popH) {
        int top = tileTop - POP_TOP_OFFSET;
        int max = SCREEN_H - MARGIN - popH;
        if (top > max) top = max;
        if (top < MARGIN) top = MARGIN;
        return top;
    }

    /** Max popup height (design px). */
    static int popupMaxH() { return SCREEN_H - 2 * MARGIN; }

    /**
     * Popup width (design px): the prototype's 440, wider up to 520 when a row needs it on one line
     * ({@code rowNeed} = the widest row's single-line width, padding included); rows that still do not fit wrap.
     */
    static int popupWidth(float rowNeed) {
        int w = (int) Math.ceil(rowNeed) + 2 * POP_PAD;
        return Math.max(POP_W, Math.min(POP_W_MAX, w));
    }

    /**
     * Vertical pivot of a popup (px from its top) so it grows out of its tile: the tile's centre, kept inside the
     * popup. No tile: the popup's middle.
     */
    static float popupPivotY(float tileCentre, float popTop, float popH, boolean hasTile) {
        if (!hasTile) return popH / 2f;
        return Math.max(0f, Math.min(popH, tileCentre - popTop));
    }

    /** Title lines of a row: 1 when it fits, 2 when two lines hold it, 0 = more: one running line on focus. */
    static int titleMode(float textW, float avail, int wrappedLines) {
        if (textW <= avail) return 1;
        return wrappedLines <= TITLE_LINES ? 2 : 0;
    }

    /** Row height (design px): the prototype's minimum or the text plus its padding, whichever is taller. */
    static float rowHeight(float textH, float minH, float vpad) {
        return Math.max(minH, textH + 2 * vpad);
    }

    /** All settings rail: UP / DOWN move the category without wrapping; -1 = stay. */
    static int railMove(int cur, int count, int dir) {
        if (count <= 0) return -1;
        int n = dir == UP ? cur - 1 : dir == DOWN ? cur + 1 : cur;
        if (n < 0 || n >= count || n == cur) return -1;
        return n;
    }

    /**
     * All settings list, UP / DOWN from row {@code at}: the next row that takes focus that way, or {@code at} when there
     * is none (greyed rows, notes and headers never take focus).
     */
    static int listTarget(boolean[] focusable, int at, boolean down) {
        for (int i = at + (down ? 1 : -1); i >= 0 && i < focusable.length; i += down ? 1 : -1) if (focusable[i]) return i;
        return at;
    }

    /**
     * True when no row past {@code target} that way takes focus: the list then scrolls to that end, so the rows that
     * cannot take focus there (a greyed section at the bottom, a header above the first row) come into view.
     */
    static boolean listEnd(boolean[] focusable, int target, boolean down) {
        return listTarget(focusable, target, down) == target;
    }

    // ------------------------------------------------------------------ brightness slider
    /** Boost is the profile's level after the top one; 0 = the model has no Boost. */
    static boolean isBoost(int value, int boostLevel) { return boostLevel > 0 && value >= boostLevel; }

    /**
     * Filled part of the track 0..1 over the slider's own range: min = empty (the knob on the left edge), max = full
     * (the right edge; Boost is the top of the range while it is on). Unknown (null) or an empty range = empty.
     */
    static float fill(Integer value, int min, int max) {
        if (value == null || max <= min) return 0f;
        return (clamp(value, min, max) - min) / (float) (max - min);
    }

    /**
     * The filled part of a slider track as fractions {from, to}: from the left edge, or from 0 on a two-way slider
     * (min below 0 and max above, stock TwoTowardProgressComponent: the dynamic black level tuning).
     */
    static float[] fillSpan(Integer value, int min, int max) {
        float at = fill(value, min, max);
        if (value == null || min >= 0 || max <= 0) return new float[]{0f, at};
        float zero = fill(0, min, max);
        return new float[]{Math.min(zero, at), Math.max(zero, at)};
    }

    /**
     * Width of the text box right of a card slider's track, design px: the 64 px of the prototype (the light level, its
     * Boost word), wider when the widest label of a choice slider (the iris "Dynamic") needs it, so the track never
     * jumps while the knob moves.
     */
    static float labelBox(float widestLabel, float pxPerDesign) {
        return Math.max(64f, widestLabel / Math.max(0.01f, pxPerDesign));
    }

    /** Number at the right of the slider: the level inside the range, the Boost word, or empty while unknown. */
    static String label(Integer value, int min, int max, int boostLevel, String boostWord) {
        if (value == null) return "";
        return isBoost(value, boostLevel) ? boostWord : Integer.toString(clamp(value, min, max));
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    /** Boost notice shown once when the read-back turns Boost on (not on every refresh while it stays on). */
    static boolean boostNoticeDue(Boolean before, Boolean now) {
        return Boolean.TRUE.equals(now) && Boolean.FALSE.equals(before);
    }
}
