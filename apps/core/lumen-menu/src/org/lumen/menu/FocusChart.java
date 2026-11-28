// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

/**
 * The test chart of the stock manual focus window (SystemUI FocusAdjust.initFocusAdjustView, the same code in the
 * Z9X and HB 838 and the X50 and Titan 848 builds). The window background is cp01_focus_bk: a white page, a black star
 * in each corner (rings cut by white bars, then spokes) and one black line across near the bottom. Where the model's
 * feature.xml has support_af_type 2 (the MIRA line) it is bg_manuel_focus_short instead: a clear page with white ringed
 * stars in the corners. In the middle sits the dial img_logo_focus3 with its two rings xgimi_focus_lefta_new and
 * righta_new, turned 10 degrees per lens step (FocusAdjust onKey rotationBy). The drawables are byte-equal in every
 * build named; the numbers here are measured from them in px of the 1920 x 1080 design. Our own drawing, no stock
 * asset, no logo. The model's profile picks the style (features "focus.manual" chart): "short", "off" (no stock
 * evidence of a chart, the window opens without it) or the full chart by default. OK in the window shows or hides it.
 */
public final class FocusChart {
    public static final float DESIGN_W = 1920f, DESIGN_H = 1080f;
    public static final String FULL = "full", SHORT = "short", OFF = "off";
    public static final int WHITE = 0xFFFFFFFF, BLACK = 0xFF000000, CLEAR = 0x00000000;
    /** Log tag of the chart switches (manual focus and the geometry screens' test chart). */
    public static final String TAG = "LumenChart";

    /**
     * Corner star rings, the corner as centre, from the inside out: {r0, r1, black (1) or white (0), bar width,
     * first bar angle, bar step}. Bars are white strips of constant width through the corner, drawn on the ring only
     * when the width is above 0; angles in degrees from the nearer horizontal edge towards the picture.
     */
    public static final float[][] FULL_RINGS = {
        {53f, 70.5f, 1, 0, 0, 0},
        {70.5f, 108.5f, 1, 10.5f, 0f, 15f},
        {108.5f, 123f, 1, 0, 0, 0},
        {123f, 230f, 1, 3.5f, 1f, 4f},
    };
    public static final float[][] SHORT_RINGS = {
        {0f, 85.5f, 0, 0, 0, 0},
        {85.5f, 102.5f, 1, 0, 0, 0},
        {102.5f, 134.5f, 1, 9.5f, 1.25f, 12.5f},
        {134.5f, 152.5f, 1, 0, 0, 0},
        {152.5f, 163.5f, 0, 0, 0, 0},
        {163.5f, 200.5f, 1, 0, 0, 0},
        {177f, 187f, 1, 10.6f, 0f, 90f / 13f},
        {200.5f, 211.5f, 0, 0, 0, 0},
    };
    /** The black line of the full chart: {left, top, right, bottom}. */
    public static final float[] FULL_LINE = {56f, 848f, 1864f, 856f};

    /** The dial's dark disc: black, alpha by radius (a soft edge). */
    public static final float[] DISC_R = {0f, 125f, 180f, 228f, 252f, 276f, 300f};
    public static final int[] DISC_ALPHA = {194, 194, 167, 129, 68, 14, 0};
    /** The grey ring: r0, r1, colour; 12 parts with a gap at every 30 degrees. */
    public static final float GREY_R0 = 126f, GREY_R1 = 149f, GREY_FIRST = 1.1f, GREY_SWEEP = 27.8f, GREY_STEP = 30f;
    public static final int GREY = 0xCD3F3F3F;
    /** The white ticks: 96 of them. */
    public static final float TICK_R0 = 147f, TICK_R1 = 178f, TICK_FIRST = 0.54f, TICK_SWEEP = 1.2f, TICK_STEP = 3.75f;
    /** The cross: a circle and the marks above and below it. */
    public static final float CROSS_R = 61f, CROSS_W = 4f, MARK_R0 = 49f, MARK_R1 = 79f;
    /** The outer turning ring (lefta_new): four arcs, round ends. */
    public static final float OUTER_R = 226.5f, OUTER_W = 10f, OUTER_FIRST = 67f, OUTER_SWEEP = 75.9f, OUTER_STEP = 90f;
    /** The inner turning ring (righta_new): 30 dashes, alpha 166. */
    public static final float INNER_R = 198.5f, INNER_W = 13f, INNER_FIRST = 8.8f, INNER_SWEEP = 6.7f, INNER_STEP = 12f;
    public static final int INNER = 0xA6FFFFFF;
    /** Degrees the two rings turn per lens step (the outer one with the step, the inner one against it). */
    public static final float TURN_STEP = 10f;

    private FocusChart() {}

    /** The model's chart style from its profile: SHORT, OFF or FULL (also for no profile or an unknown word). */
    public static String style(MenuProfile p) {
        String s = p == null ? null : p.text(FeatureId.FOCUS_MANUAL, "chart");
        return SHORT.equals(s) || OFF.equals(s) ? s : FULL;
    }

    /** Whether the chart is on screen when the window opens (stock: whenever the model has one). */
    public static boolean shownAtOpen(String style) { return !OFF.equals(style); }

    /** The chart OK shows: the model's own, the full one where the stock has none. */
    public static String drawn(String style) { return OFF.equals(style) ? FULL : style; }

    /**
     * The test chart of the geometry screens (zoom, shift, rotation, keystone): the same chart as the model's manual
     * focus screen, the full one where it has none. Stock shows no chart there, so it is hidden at open and OK (the
     * keystone: MENU) shows it.
     */
    public static String geometry(MenuProfile p) { return drawn(style(p)); }

    public static float[][] rings(String drawn) { return SHORT.equals(drawn) ? SHORT_RINGS : FULL_RINGS; }

    /** The page behind the stars: white for the full chart, clear (the picture shows) for the short one. */
    public static int page(String drawn) { return SHORT.equals(drawn) ? CLEAR : WHITE; }

    /** The line across, or null. */
    public static float[] line(String drawn) { return SHORT.equals(drawn) ? null : FULL_LINE; }

    /** Bar angles of a ring that fall in the corner's quarter (0 .. 90 degrees, a bar on an edge counts). */
    public static float[] barAngles(float[] ring) {
        if (ring[3] <= 0) return new float[0];
        int n = 0;
        for (float a = ring[4]; a <= 90.0001f; a += ring[5]) n++;
        float[] out = new float[n];
        for (int i = 0; i < n; i++) out[i] = ring[4] + i * ring[5];
        return out;
    }
}
