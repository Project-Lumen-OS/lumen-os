// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

import org.lumen.ui.Grid;

/**
 * Where the parts of the Витрина go for a layout (4PDA 2026-11-22: more rows of apps, the hero smaller or gone). Pure,
 * design px of the 1920x1080 UI. Everything is anchored at the bottom as in the shipped Витрина: the bottom line of
 * inputs and round buttons, above it the rows of apps ({@link Grid#APPS_PITCH} apart, the last one at
 * {@link Grid#DOCK_Y}), above them the Continue row, above that the shelf. With one row of apps and the full hero the
 * numbers are the shipped ones (Classic is unchanged). What does not fit gives way in this order: the full shelf becomes
 * the small one, the small one goes, then rows of apps go until the Continue row fits.
 */
public final class LayoutPlan {
    /** Nothing above this line but the corner (weather, network, clock). */
    public static final int TOP_ROOM = Grid.STATUS_Y + Grid.STATUS_H + 32;
    /** Room the shelves need above their bottom: the full one (eyebrow, title, season line, progress, buttons), the small one. */
    public static final int FULL_H = Grid.SHELF_ROW_BOTTOM - TOP_ROOM, SMALL_H = 230;
    /** Apps on one row of the dock that is not the last (the last one slides with the rest). */
    public static final int PER_LINE = Vitrina.visible(Grid.DOCK_W, Grid.GUTTER);

    /** The hero shown: {@link Looks#FULL}, {@link Looks#SMALL} or {@link Looks#NONE}. */
    public final int hero;
    /**
     * The big clock (greeting, clock, date and weather) is shown: on the calm screen of the full hero as shipped, and in
     * place of the hero in a look without one (Minimal) wherever it fits above the rows.
     */
    public final boolean calmClock;
    /** Top of the big clock: the shipped place, or higher so it ends {@link #STILL_GAP} above the first row. */
    public final int calmTop;
    public final int shelfBottom;
    /** Top of the Continue row, -1 without one. */
    public final int upnextY;
    /** Top of each row of apps, first row first. */
    public final int[] dockY;
    /** First dock item of each row and how many items it holds (the last row holds the rest and slides). */
    public final int[] rowStart, rowCount;

    private LayoutPlan(int hero, boolean calmClock, int calmTop, int shelfBottom, int upnextY, int[] dockY, int[] start, int[] count) {
        this.hero = hero;
        this.calmClock = calmClock;
        this.calmTop = calmTop;
        this.shelfBottom = shelfBottom;
        this.upnextY = upnextY;
        this.dockY = dockY;
        this.rowStart = start;
        this.rowCount = count;
    }

    /**
     * @param want      the layout's hero ({@link Looks})
     * @param pages     the hero has something to show (Continue items or recommendations)
     * @param calm      nothing to show: the calm screen
     * @param clock     the layout shows the big clock on the calm screen
     * @param contRow   three Continue items or more make their own row
     * @param rowsWant  rows of apps the layout asks for
     * @param items     items on the top level of the dock (apps and folders)
     */
    public static LayoutPlan of(int want, boolean pages, boolean calm, boolean clock, boolean contRow, int rowsWant, int items) {
        int rows = items <= 0 ? 0 : Math.max(1, Math.min(HomeLayout.rows(rowsWant), (items + PER_LINE - 1) / PER_LINE));
        while (contRow && rows > 1 && top(rows) - (Grid.DOCK_Y - Grid.UPNEXT_Y) < TOP_ROOM) rows--;
        int top = top(rows);
        int up = contRow ? top - (Grid.DOCK_Y - Grid.UPNEXT_Y) : -1;
        int shelf = contRow ? up - (Grid.UPNEXT_Y - Grid.SHELF_ROW_BOTTOM) : top - (Grid.DOCK_Y - Grid.SHELF_BOTTOM);
        int room = shelf - TOP_ROOM;
        int hero;
        if (!(pages || (calm && clock))) hero = Looks.NONE;
        else if (want == Looks.FULL && room >= FULL_H) hero = Looks.FULL;
        else if (want != Looks.NONE && room >= SMALL_H && !calm) hero = Looks.SMALL;
        else hero = Looks.NONE;
        int below = up >= 0 ? up : rows > 0 ? Grid.DOCK_Y - (rows - 1) * Grid.APPS_PITCH : Grid.CHIPS_Y;
        int calmTop = hero == Looks.FULL ? CALM_TOP : Math.min(CALM_TOP, below - STILL_GAP - CALM_H);
        boolean calmClock = clock && (hero == Looks.FULL ? calm : hero == Looks.NONE && calmTop >= TOP_ROOM);
        int[] y = new int[rows], start = new int[rows], count = new int[rows];
        for (int i = 0; i < rows; i++) {
            y[i] = Grid.DOCK_Y - (rows - 1 - i) * Grid.APPS_PITCH;
            start[i] = i * PER_LINE;
            count[i] = i < rows - 1 ? PER_LINE : items - i * PER_LINE;
        }
        return new LayoutPlan(hero, calmClock, Math.max(TOP_ROOM, calmTop), shelf, up, y, start, count);
    }

    /**
     * Top of the sharp frame of small art: the shipped places with the full shelf ({@link Vitrina#stillY}), else on the
     * shelf's bottom and never into the corner. A shelf is only shown with room for it, so the frame always ends at
     * least {@link #STILL_GAP} above the row under it (LayoutTest).
     */
    public int stillY(boolean contRow) {
        if (hero == Looks.FULL && dockY.length <= 1) return Vitrina.stillY(contRow);
        int h = Math.round(Grid.span(Grid.STILL_COLS) * 9f / 16f);
        return Math.max(TOP_ROOM, shelfBottom - h);
    }

    /** The shipped top of the big clock and its height as drawn (greeting, the 168 clock with its line, date and weather). */
    public static final int CALM_TOP = 264, CALM_H = 300;

    /** Room between the art frame and the row under it (the Continue row keeps 40 in the shipped Витрина too). */
    public static final int STILL_GAP = 40;

    private static int top(int rows) {
        return rows <= 0 ? Grid.CHIPS_Y : Grid.DOCK_Y - (rows - 1) * Grid.APPS_PITCH;
    }

    /** The row of the dock that holds item {@code i}. */
    public int rowOf(int i) {
        for (int r = rowStart.length - 1; r >= 0; r--) if (i >= rowStart[r]) return r;
        return 0;
    }
}
