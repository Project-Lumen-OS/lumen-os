package org.lumen.projector;

/**
 * HB beta6: the manual-focus test chart, plain Java geometry for the host test (test/beta6). Stock HB SystemUI
 * FocusAdjust.initFocusAdjustView (SystemUI_release_838 focus/FocusAdjust.java:186-240) opens manual focus as a
 * full-screen window (WindowManager.LayoutParams defaults MATCH_PARENT, gravity 81) whose background is the chart
 * drawable cp01_focus_bk (1920x1080: white, black spoke wheels cut by the four corners, one black line across at
 * y 852) on a non-commercial unit without persist.vendor.brand.simple=1 and support_af_type != 2 (the G0130 config
 * has neither), with the focus graphic in the middle (focus_bg). The chart is there from the first frame; every key
 * but LEFT / RIGHT / BACK and the service keys closes the window (FocusAdjust.newVontrolAf). The XGIMI drawable is
 * not copied: this is a clean drawing of the same layout, the middle graphic replaced by a full spoke wheel so the
 * centre can be judged like the corners.
 */
public final class FocusPattern {
    /** Stock chart colours. */
    public static final int BACKGROUND = 0xFFFFFFFF;
    public static final int INK = 0xFF000000;
    /** Black wedges per full wheel (each 180 / SPOKES degrees, the same white gap after it). */
    public static final int SPOKES = 60;
    /** Wheel radii and the line as fractions of the stock 1920x1080 drawable. */
    public static final float CORNER_R = 230f / 1920f;
    public static final float CENTER_R = 170f / 1920f;
    public static final float LINE_Y = 852f / 1080f;
    public static final float LINE_H = 4f / 1080f;

    private FocusPattern() {}

    /** Wheels as {cx, cy, r}: the four corners (top-left, top-right, bottom-left, bottom-right), then the centre. */
    public static float[][] wheels(int w, int h) {
        if (w <= 0 || h <= 0) throw new IllegalArgumentException(w + "x" + h);
        float rc = CORNER_R * w, rm = CENTER_R * w;
        return new float[][] {
            {0, 0, rc}, {w, 0, rc}, {0, h, rc}, {w, h, rc}, {w / 2f, h / 2f, rm},
        };
    }

    /** Start angle of black wedge i (degrees). */
    public static float wedgeStart(int i) {
        if (i < 0 || i >= SPOKES) throw new IllegalArgumentException("wedge " + i);
        return i * 360f / SPOKES;
    }

    /** Sweep of every black wedge (degrees): half the period, so black and white alternate evenly. */
    public static float wedgeSweep() { return 180f / SPOKES; }

    /** The line across: {left, top, right, bottom}. */
    public static float[] line(int w, int h) {
        float y = LINE_Y * h;
        return new float[] {0, y, w, y + Math.max(1f, LINE_H * h)};
    }
}
