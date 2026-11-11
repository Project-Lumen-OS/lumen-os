package org.lumen.projector.panel;

import java.util.List;

/**
 * Quick panel layout D (owner's choice 2026-11-11, prototype qp_design/proto_d3.py): the plain-Java part, so host
 * tests can check it without Android. Sizes are design px of a 1920 x 1080 screen (Theme.px scales them).
 *
 * Grid: row 0 = the two round buttons (eye protection, all settings), rows 1..n = one wide tile and up to two small
 * ones, last row = the brightness slider. Rows are built from the tiles this projector really has, so a missing tile
 * leaves no hole and no placeholder.
 */
final class QpdLayout {
    private QpdLayout() {}

    // ---- card (prototype #qp)
    static final int CARD_X = 40, CARD_Y = 40, CARD_W = 592, CARD_PAD = 24, CARD_RADIUS = 44;
    static final int WIDE_W = 272, SMALL_W = 120, TILE_H = 120, GAP = 16, TILE_RADIUS = 26;
    static final int SLIDER_H = 96, SLIDER_TOP = 6;
    static final int ROUND = 60, ROUND_GAP = 12;
    // ---- popup (prototype #pop) and screen
    static final int POP_X = 640, POP_W = 440, POP_W_MAX = 520, POP_PAD = 16, POP_RADIUS = 36, POP_TOP_OFFSET = 20;
    static final int SCREEN_H = 1080, MARGIN = 40;
    /** The scrim reaches 60 % of the screen (prototype .scrim). */
    static final int SCRIM_W = 1152;
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
    static final float FOCUS_SCALE = 1.08f, ROUND_FOCUS_SCALE = 1.12f, SLIDER_FOCUS_SCALE = 1.03f;
    static final long FOCUS_MS = 300;
    /** Remote brightness bar: hides 2.5 s after the last key. */
    static final long BAR_HIDE_MS = 2_500;

    static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;
    /** move(): stay where you are. */
    static final int[] STAY = null;
    /** move() result marker in [2]: 1 = open the focused tile's popup instead of moving. */
    static final int OPEN = 1;

    /** Keys of the tiles that may take the last small slot (2026-11-11: screen mirroring in place of USB). */
    static final String TILE_MIRROR = "mirroring", TILE_USB = "usb";

    /**
     * The last small tile of the grid: screen mirroring where its receiver (org.lumen.miracast) is installed, it took
     * the USB tile's place (USB files stay in the Input popup and in Lumen Home); else USB where Lumen Home's files
     * screen exists; else none (the row is one tile shorter, no placeholder).
     */
    static String lastSmallTile(boolean mirroringInstalled, boolean usbFiles) {
        if (mirroringInstalled) return TILE_MIRROR;
        return usbFiles ? TILE_USB : null;
    }

    /**
     * A screen mirroring switch after a change: the value the receiver reports back; when it did not answer, the
     * value from before (the switch never shows a state nobody confirmed).
     */
    static Boolean settled(Boolean readBack, Boolean before) {
        return readBack != null ? readBack : before;
    }

    /**
     * D-pad move on the grid. {@code rows.get(r)} = the cell keys of row r (row 0 = round buttons, last row = slider,
     * which handles LEFT / RIGHT itself). Returns {row, col, open}: open = 1 when RIGHT on the last cell of a row asks
     * for that cell's popup ({@code hasPopup}); null = no move.
     */
    static int[] move(List<List<String>> rows, int r, int c, int dir, java.util.function.Predicate<String> hasPopup) {
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
                if (r == last && isSliderRow(rows)) return STAY;    // the slider steps itself
                return c > 0 ? new int[]{r, c - 1, 0} : STAY;
            case RIGHT:
                if (r == last && isSliderRow(rows)) return STAY;
                if (c < row.size() - 1) return new int[]{r, c + 1, 0};
                return hasPopup != null && hasPopup.test(row.get(c)) ? new int[]{r, c, OPEN} : STAY;
            default:
                return STAY;
        }
    }

    static final String SLIDER = "lamp";

    static boolean isSliderRow(List<List<String>> rows) {
        if (rows.isEmpty()) return false;
        List<String> l = rows.get(rows.size() - 1);
        return l.size() == 1 && SLIDER.equals(l.get(0));
    }

    /**
     * Builds the grid rows: header buttons, then per row one wide tile (in order) with the next small tiles, two at
     * most, then the slider. A row without a wide tile takes up to three small ones (never happens on the Z9X).
     */
    static java.util.List<java.util.List<String>> rows(List<String> header, List<String> wides, List<String> smalls,
                                                       boolean slider) {
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
        if (slider) {
            java.util.List<String> l = new java.util.ArrayList<>();
            l.add(SLIDER);
            out.add(l);
        }
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

    // ------------------------------------------------------------------ brightness slider
    /** Level shown for Boost (QuickPanelController.BOOST_LEVEL). */
    static final int BOOST_LEVEL = 11;

    static boolean isBoost(int value) { return value >= BOOST_LEVEL; }

    /** Filled part of the track 0..1: level / 10 (1 = 10 %), Boost = full. Unknown (null) = empty. */
    static float fill(Integer value) {
        if (value == null) return 0f;
        if (isBoost(value)) return 1f;
        int v = Math.max(1, Math.min(10, value));
        return v / 10f;
    }

    /** Number at the right of the slider: the level, the Boost word, or empty while unknown. */
    static String label(Integer value, String boostWord) {
        if (value == null) return "";
        return isBoost(value) ? boostWord : Integer.toString(Math.max(1, Math.min(10, value)));
    }

    /** Boost notice shown once when the read-back turns Boost on (not on every refresh while it stays on). */
    static boolean boostNoticeDue(Boolean before, Boolean now) {
        return Boolean.TRUE.equals(now) && Boolean.FALSE.equals(before);
    }
}
