// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

import org.lumen.ui.Grid;

/**
 * Where the Витрина (Home A) puts its parts, in design px of the 1920x1080 UI. Pure; numbers from {@link Grid}.
 *
 * <p>One screen, nothing scrolls vertically: the shelf (eyebrow, title, season line, progress, buttons) bottom left
 * over the art, the app dock at {@link Grid#DOCK_Y}, the input chips and the round buttons All apps and Settings on the
 * bottom line. Three Continue items or more make a row of 16:9 cards in the shelf: the shelf moves up, its buttons go
 * (OK on a card resumes it) and the row sits between the shelf and the dock. A row longer than the screen slides
 * sideways and keeps one item of look ahead; the next item peeks at the right edge.
 */
public final class Vitrina {
    /**
     * The round buttons of the bottom line: All apps, Customize Home (4PDA 2026-11-22: the Home settings were only behind
     * a held OK, nobody found them) and Settings.
     */
    public static final int ROUND_APPS = 0, ROUND_LAYOUT = 1, ROUND_SETTINGS = 2, ROUNDS = 3;

    private Vitrina() {}

    /** Bottom of the shelf: under the hero buttons, or above a Continue row. */
    public static int shelfBottom(boolean continueRow) {
        return continueRow ? Grid.SHELF_ROW_BOTTOM : Grid.SHELF_BOTTOM;
    }

    /**
     * Top of the sharp frame of small art: at {@link Grid#STILL_Y} beside the hero buttons; above a Continue row it ends
     * 40 over the cards, so it never covers the row's right end.
     */
    public static int stillY(boolean continueRow) {
        int h = Math.round(Grid.span(Grid.STILL_COLS) * 9f / 16f);
        return continueRow ? Grid.UPNEXT_Y - 40 - h : Grid.STILL_Y;
    }

    /**
     * Input chips on the bottom line for {@code inputs} inputs of which {@code ports} are HDMI ports (they come first):
     * at most {@link Grid#MAX_CHIPS}, or four on a projector with three HDMI ports (X50 family), so every port keeps
     * its own chip next to the chip that lists the rest (Slava 2026-11-19: no HDMI 3 on the X50).
     */
    public static int chips(int inputs, int ports) {
        return Math.max(0, Math.min(inputs, limit(ports)));
    }

    /** More inputs than chips: the last chip lists them all (the source menu) instead of showing one. */
    public static boolean moreChip(int inputs, int ports) {
        return inputs > limit(ports);
    }

    private static int limit(int ports) {
        return ports >= Grid.MAX_CHIPS ? Grid.MAX_CHIPS + 1 : Grid.MAX_CHIPS;
    }

    /** Width of each of {@code n} chips: three columns, four chips share the line up to the round buttons. */
    public static int chipW(int n) {
        if (n <= Grid.MAX_CHIPS) return Grid.CHIP_W;
        return (roundX(0) - Grid.GUTTER - Grid.MARGIN - (n - 1) * Grid.GUTTER) / n;
    }

    /** x of chip {@code i} of {@code n}. */
    public static int chipX(int i, int n) {
        return n <= Grid.MAX_CHIPS ? Grid.x(1 + 3 * i) : Grid.MARGIN + i * (chipW(n) + Grid.GUTTER);
    }

    /** x of round button {@code i} ({@link #ROUND_APPS}, {@link #ROUND_SETTINGS}): right aligned on the margin. */
    public static int roundX(int i) {
        return Grid.W - Grid.MARGIN - Grid.ROUND - (ROUNDS - 1 - i) * (Grid.ROUND + Grid.ROUND_GAP);
    }

    /** Centres of the bottom line's items, chips then round buttons (D-pad targets). */
    public static int[] bottomCenters(int inputs, int ports) {
        int n = chips(inputs, ports);
        int[] c = new int[n + ROUNDS];
        for (int i = 0; i < n; i++) c[i] = chipX(i, n) + chipW(n) / 2;
        for (int i = 0; i < ROUNDS; i++) c[n + i] = roundX(i) + Grid.ROUND / 2;
        return c;
    }

    /** A device on an input that is a game console (its CEC name): the chip shows a gamepad, not the HDMI plug. */
    public static boolean console(String device) {
        if (device == null) return false;
        String d = device.toLowerCase(java.util.Locale.ROOT);
        return d.contains("playstation") || d.contains("xbox") || d.contains("nintendo") || d.matches(".*\\bps[345]\\b.*");
    }

    /** Items of a sliding row fully on screen: from the margin to the right safe edge. */
    public static int visible(int itemW, int gap) {
        return Math.max(1, (Grid.W - Grid.SAFE_Y - Grid.MARGIN + gap) / (itemW + gap));
    }

    /**
     * The first item shown of a sliding row after the focus moved to {@code index}: the row moves only when the focus
     * would reach its edge, and keeps one item of look ahead on both sides; the end stays filled.
     */
    public static int scroll(int first, int index, int count, int visible) {
        int max = Math.max(0, count - visible);
        int f = first;
        if (index < f + 1) f = index - 1;
        else if (index > f + visible - 2) f = index - visible + 2;
        return Math.max(0, Math.min(f, max));
    }

    /** Apps page: x and y of tile {@code i} of {@code cols} per line. */
    public static int gridX(int i, int cols) {
        return Grid.x(1 + 2 * (i % cols));
    }

    public static int gridY(int i, int cols) {
        return Grid.APPS_GRID_Y + (i / cols) * Grid.APPS_PITCH;
    }

    /**
     * Apps page scroll (design px, 0 or more) for the focused tile: its line stays above the bottom line with its
     * name; the first lines never scroll.
     */
    public static int gridOffset(int index, int count, int cols) {
        if (count <= 0) return 0;
        int line = index / cols;
        int bottom = gridY(line * cols, cols) + Grid.DOCK_H + Grid.DOCK_LABEL_GAP + 32;
        return Math.max(0, bottom - Grid.BOTTOM);
    }
}
