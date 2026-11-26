// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.signal;

/**
 * Where the panel sits, in design px of the 1920x1080 interface (scaled to the screen by SignalWindow). Owner
 * 2026-11-18: a compact card with small, calm type (a 100 inch picture reads it well), inside the title safe area and
 * below an app's header (the first card covered the Lumen Home clock and gear). Pure, host tested.
 */
public final class SignalPlace {
    private SignalPlace() {}

    public static final int SCREEN_W = 1920, SCREEN_H = 1080;
    /** Title safe area (EBU R 95: 5 % of the width and of the height, lumen-ui Tokens.MARGIN / SAFE). */
    public static final int SAFE_X = 96, SAFE_Y = 54;
    /** The lowest app header we know: Lumen Home 2.0 (Tokens.HEADER_Y 48 + HEADER_H 64), plus a gap. */
    public static final int HEADER_BOTTOM = 112, HEADER_GAP = 24;
    /** The small card: top right, below the header. */
    public static final int CARD_W = 440, CARD_TOP = HEADER_BOTTOM + HEADER_GAP, CARD_RIGHT = SAFE_X;
    /** The full panel: right side, the whole title safe height (it takes the keys while open). */
    public static final int SHEET_W = 560, SHEET_TOP = SAFE_Y, SHEET_RIGHT = SAFE_X, SHEET_BOTTOM = SAFE_Y;

    /** {left, top, right, bottom} of the small card of height {@code h}. */
    public static int[] card(int h) {
        int right = SCREEN_W - CARD_RIGHT;
        return new int[] {right - CARD_W, CARD_TOP, right, CARD_TOP + h};
    }

    /** {left, top, right, bottom} of the full panel. */
    public static int[] sheet() {
        int right = SCREEN_W - SHEET_RIGHT;
        return new int[] {right - SHEET_W, SHEET_TOP, right, SCREEN_H - SHEET_BOTTOM};
    }

    /** The full panel's height on a screen {@code screenH} px high at scale {@code px}. */
    public static int sheetHeight(int screenH, float px) {
        return screenH - Math.round((SHEET_TOP + SHEET_BOTTOM) * px);
    }

    /** True when the rectangle lies inside the title safe area. */
    public static boolean safe(int[] r) {
        return r[0] >= SAFE_X && r[1] >= SAFE_Y && r[2] <= SCREEN_W - SAFE_X && r[3] <= SCREEN_H - SAFE_Y;
    }
}
