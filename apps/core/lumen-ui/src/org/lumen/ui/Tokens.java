// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.ui;

/**
 * The tokens of the Lumen TV look, plain Java (ARGB ints and design px), so code without resources (overlay windows,
 * toasts, dialogs styled at run time) and host tests use the same values as apps/common/tokens.xml.
 *
 * <p>Lumen 2.0 (decisions 2026-11-18): ONE token set for Home, Setup and the menus, two palettes
 * ({@link #LIGHT} the default, {@link #DARK}) picked by the shared theme switch {@link LumenTheme} (Светлая, Тёмная,
 * Авто). Sizes are design px of the 1920x1080 interface (half a dp on the 320 dpi projectors).
 *
 * <p>The 1.x constants below the 2.0 block (ACCENT ... BODY) are the dark look the installers and the X50 apps still
 * use (decision D3, 2026-11-08); they move to the palettes when those apps are restyled.
 */
public final class Tokens {
    // ------------------------------------------------------------------ 2.0 colours
    public static final Palette LIGHT = LumenTheme.LIGHT, DARK = LumenTheme.DARK;

    // ------------------------------------------------------------------ 2.0 type (px, weight); Inter, opsz = size
    public static final int TYPE_DISPLAY = 72, W_DISPLAY = 700;      // hero title, line height 80, tracking -1.6
    public static final int TYPE_DISPLAY_S = 56, W_DISPLAY_S = 700;  // hero title of the low hero, line 64, one line
    public static final int TYPE_NAV = 24, W_NAV = 600;              // header items, hero buttons
    public static final int TYPE_CLOCK_S = 30, W_CLOCK_S = 600;      // header clock, tabular digits
    public static final int TYPE_CLOCK = 168, W_CLOCK = 200;         // calm screen clock, tabular digits, tracking -6
    public static final int TYPE_SETUP_TITLE = 56, W_SETUP_TITLE = 680;
    public static final int TYPE_TITLE_L = 30, W_TITLE_L = 600;      // season and episode line
    public static final int TYPE_TITLE = 28, W_TITLE = 620;          // row titles
    public static final int TYPE_BODY_L = 26, W_BODY_L = 420;
    public static final int TYPE_BODY = 24, W_BODY = 400;            // descriptions, meta, line height 1.45
    public static final int TYPE_LABEL = 22, W_LABEL = 600;          // buttons, nav, list titles, tile names
    public static final int TYPE_CAPTION = 20, W_CAPTION = 520;      // never smaller than this

    // ------------------------------------------------------------------ 2.0 radii (px)
    public static final int R_CARD = 20, R_BANNER = 16, R_TILE = 20, R_TILE_SETUP = 24, R_BUTTON = 28,
            R_BUTTON_SETUP = 32, R_PANEL = 28, R_HERO_CARD = 28, R_ROW = 20, R_FIELD = 16, R_FRAME = 16, R_NAV = 32;

    // ------------------------------------------------------------------ 2.0 spacing and sizes (px)
    /**
     * MARGIN: the 5 % title safe margin Setup lays out on. Home lays out on {@link Grid} (margin 116, 12 columns of
     * 104, gutters 40); the Home sizes below are its numbers.
     */
    public static final int MARGIN = 96, SAFE = Grid.SAFE_Y, HEADER_Y = Grid.HEADER_Y, HEADER_H = Grid.HEADER_H,
            ROW_TITLE_GAP = Grid.ROW_TITLE_GAP, CARD_GAP = Grid.GUTTER, BANNER_GAP = Grid.GUTTER, ROW_GAP = Grid.ROW_GAP,
            PANEL_PAD = 12, LIST_ROW_H = 84, LIST_ROW_H_SHORT = 62, SETUP_SPLIT_X = 880;
    public static final int CARD_W = Grid.CARD_W, CARD_H = Grid.CARD_H, BANNER_W = Grid.BANNER_W,
            BANNER_H = Grid.BANNER_H, INPUT_W = Grid.INPUT_W, INPUT_H = Grid.INPUT_H, POSTER_W = Grid.POSTER_W,
            POSTER_H = Grid.POSTER_H, BUTTON_H = 56, BUTTON_H_SETUP = 64, NAV_H = 52, NAV_PILL_H = 64, ICON_BUTTON = 56,
            PANEL_W = 540, PANEL_ROW_H = 80;

    // ------------------------------------------------------------------ 2.0 focus
    public static final float SCALE_CARD = 1.06f, SCALE_BANNER = 1.06f, SCALE_TILE = 1.06f, SCALE_BUTTON = 1.05f,
            SCALE_HERO_BUTTON = 1.04f, SCALE_ICON = 1.06f, SCALE_ROW = 1.02f;
    public static final int RING_W = 4, RING_GAP = 4;
    /**
     * The focus ring of Home (2026-11-19, both themes): 3 px wide, 3 px clear gap to the element, on top of the
     * focus scale and shadow, in {@link Palette#focusRing} (#3B78E7 dark, #2F62D0 light for 3:1 on the light field).
     * {@link FocusRing} says who wears it, {@link RingPaint} draws it.
     */
    public static final int FOCUS_RING_W = 3, FOCUS_RING_GAP = 3;
    /** Resting elevation of tiles in the light theme (colour {@link Palette#elevation}): offset, blur. */
    public static final int E1_Y = 6, E1_BLUR = 16;
    /** The card under a QR code stays white in both themes: phone cameras read black on white best. */
    public static final int QR_CARD = 0xFFFFFFFF;

    // ------------------------------------------------------------------ 2.0 motion (ms; curves in Motion)
    public static final int MS_FOCUS_IN = 160, MS_FOCUS_OUT = 120, MS_ROW_SCROLL = 200, MS_KEY_REPEAT = 90,
            MS_PAGE_SCROLL = 280, MS_BACKDROP_DWELL = 250, MS_BACKDROP_FADE = 400, MS_HERO_IN = 600,
            MS_HEADER = 180, MS_PANEL = 280, MS_SETUP_STEP = 320, MS_THEME = 350, MS_REDUCED = 120;
    public static final int PANEL_SLIDE = 24, STEP_SLIDE = 24;
    /** Home A "Витрина": focus, lift and colour changes (Motion.SHOWCASE). */
    public static final int MS_SHOWCASE = 260;
    /**
     * A hero page stays this long after it appeared or after the last key press before the next one comes (8 s:
     * Baymard 5 to 7 s for a title with a few labels, NN/g 3 words a second plus the picture, a full screen picture
     * changes less often than a card carousel's 5 s).
     */
    public static final int MS_HERO_PAGE = 8_000;
    /** The hero page change: one cross fade of the picture, the shelf text out and in within it. */
    public static final int MS_HERO_FADE = 800, MS_HERO_TEXT_OUT = 220, MS_HERO_TEXT_IN = 480;

    // ------------------------------------------------------------------ 1.x (installers, X50 apps)
    /** The accent: focused buttons and rows, progress, selection. */
    public static final int ACCENT = 0xFF3B78E7;
    /** The accent while pressed (0.82 of ACCENT per channel). */
    public static final int ACCENT_PRESSED = 0xFF3062BD;
    /** An unfocused button and its text. */
    public static final int IDLE = 0xFF262D38, IDLE_TEXT = 0xFFA9B2BF;
    /** A disabled button and its text. */
    public static final int OFF = 0xFF1A1E25, OFF_TEXT = 0xFF5D6570;
    /** Cards, dialogs, notices. */
    public static final int PANEL = 0xFF161A21;
    /** Text on a PANEL: titles and body. */
    public static final int TITLE = 0xFFE6E9EE, BODY = 0xFFB4BCC8;

    private Tokens() {}
}
