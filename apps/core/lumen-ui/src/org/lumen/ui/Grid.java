// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.ui;

/**
 * The layout grid of the Lumen TV screens (Home first): the Android TV layout grid at 2x, plain Java so host tests and
 * pure layout rules use it. Design px of the 1920x1080 UI (half a dp on the 320 dpi projectors).
 *
 * <p>Margins 116 left and right, 56 top and bottom (58 x 28 dp); 12 columns of 104 with 40 gutters; positions, gaps
 * and heights on an 8 px baseline, type line heights and 16:9 card heights on 4. Every left edge sits on a column:
 * x(c) = 116 + (c - 1) x 144, a span of n columns is 144 n - 40 (104, 248, 392, 536 ... 1688).
 */
public final class Grid {
    public static final int W = 1920, H = 1080;
    /** Title safe area (5 %): nothing that must be read or focused is nearer the edge. */
    public static final int SAFE_X = 96, SAFE_Y = 54;
    public static final int MARGIN = 116, TOP = 56, COL = 104, GUTTER = 40, COLS = 12, BASELINE = 8;
    /** The usable bottom: the last row of a page ends here (1080 - 56). */
    public static final int BOTTOM = H - TOP;

    // ------------------------------------------------------------------ items (16:9 within 1 %, on columns)
    /** Landscape card, 3 columns: Continue (3 or more), Popular, app channels. */
    public static final int CARD_W = 392, CARD_H = 220;
    /** App banner, 2 columns: 6 fill the 12 columns. */
    public static final int BANNER_W = 248, BANNER_H = 140;
    /** Poster card, 2 columns, 2:3. */
    public static final int POSTER_W = 248, POSTER_H = 372;
    /** Input tile (HDMI, mirroring, USB), 3 columns. */
    public static final int INPUT_W = 392, INPUT_H = 120;

    // ------------------------------------------------------------------ rows of the other TV screens (Tokens)
    public static final int HEADER_Y = 56, HEADER_H = 64;
    /** Row title line, title to cards, between rows. */
    public static final int ROW_TITLE_H = 32, ROW_TITLE_GAP = 24, ROW_GAP = 48;

    // ------------------------------------------------------------------ Home A "Витрина" (owner approved 2026-11-18)
    // One screen, nothing scrolls: the art fills it, the shelf (title, season line, progress, buttons) sits bottom
    // left over the art, one dock of app tiles under it, the input chips and two round buttons on the bottom line.
    /**
     * The shelf: 9 columns from the margin, its BOTTOM on a fixed line (a missing season line never moves the buttons):
     * the hero buttons end at 632 (the full shelf starts at 312, the mock), above a Continue row it ends 40 over the cards.
     */
    public static final int SHELF_BOTTOM = 632, SHELF_ROW_BOTTOM = 464, SHELF_COLS = 9;
    /** The Continue row of the shelf (3 items or more): 16:9 cards of 2 columns. */
    public static final int UPNEXT_Y = 504, UPNEXT_W = 248, UPNEXT_H = 140;
    /** The app dock: tiles of 2 columns, 152 high, 6 on screen; the focused one's name 26 under it. */
    public static final int DOCK_Y = 720, DOCK_W = 248, DOCK_H = 152, DOCK_LABEL_GAP = 26;
    /** The bottom line: input chips of 3 columns (at most 3), the round buttons All apps and Settings at the right. */
    public static final int CHIPS_Y = 952, CHIP_W = 392, CHIP_H = 72, ROUND = 72, ROUND_GAP = 24, MAX_CHIPS = 3;
    /** Weather and clock, top right. */
    public static final int STATUS_Y = 56, STATUS_H = 48;
    /** The sharp frame of small art (poor-art fallback): column 9, 4 columns wide, 16:9, never above its pixels. */
    public static final int STILL_COL = 9, STILL_Y = 320, STILL_COLS = 4;
    /** How the art fades: the bottom blur 560..700, the field 600..790 (78 % at 690, 66 % in the light theme). */
    public static final int ART_BLUR_FROM = 560, ART_BLUR_TO = 700, ART_FADE_FROM = 600, ART_FADE_MID = 690,
            ART_FADE_TO = 790;
    /** The Apps page: title line, first line of tiles, line pitch (tile, name, gap). */
    public static final int APPS_TITLE_Y = 88, APPS_GRID_Y = 184, APPS_PITCH = 232;

    private Grid() {}

    /** x of column {@code c} (1..12). */
    public static int x(int c) {
        return MARGIN + (c - 1) * (COL + GUTTER);
    }

    /** Width of {@code n} columns and the gutters between them. */
    public static int span(int n) {
        return n * (COL + GUTTER) - GUTTER;
    }

    /** Height of a row: title line, gap, items. */
    public static int rowH(int itemH) {
        return ROW_TITLE_H + ROW_TITLE_GAP + itemH;
    }
}
