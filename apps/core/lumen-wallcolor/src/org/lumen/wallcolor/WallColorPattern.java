// SPDX-License-Identifier: Apache-2.0
package org.lumen.wallcolor;

/**
 * The picture the vendor measures through its camera while the adaptation runs, as geometry (no drawing here, so the
 * host tests check it). The stock pattern of these models (feature support_wall_color_adjust_type 3, SystemUI
 * WallColorCore FullDialog + wall_color_lottie_dark) on a 1920 x 1080 frame:
 * <ol>
 * <li>start: a #343434 disc grows from the centre, scale 0.1 to 2.1 of the screen width in 500 ms (linear), then
 * hides 200 ms later;</li>
 * <li>loop (frames 81 to 143 at 60 fps, 62 frames): black, six 452 x 280 swatches (radius 16) in two rows, four white
 * corner marks of the measured area that fade out (frames 81 to 103), stay off (to 123) and fade in (to 143, 98 %), and
 * the pill with the running text at the bottom;</li>
 * <li>the vendor's trans (0..100) is the opacity of the loop; the black ground stays.</li>
 * </ol>
 * Redrawn by Lumen from the stock geometry; no XGIMI file is shipped.
 */
public final class WallColorPattern {
    /** The design frame. */
    public static final float W = 1920f, H = 1080f;
    /** The start disc: duration, scale from, scale to (of the screen width), its colour and the hide delay. */
    public static final long GROW_MS = 500, GROW_HIDE_MS = 200;
    public static final float GROW_FROM = 0.1f, GROW_TO = 2.1f;
    public static final int DISC = 0xFF343434, DISC_LIGHT = 0xFFFFFFFF;
    /** The loop: 60 fps, frames 81..143. */
    public static final int FPS = 60, LOOP_FIRST = 81, LOOP_LAST = 143;
    public static final long LOOP_MS = (LOOP_LAST - LOOP_FIRST) * 1000L / FPS;
    /** Swatches: size, corner radius, centres {x, y} and colours (stock order). */
    public static final float SWATCH_W = 452f, SWATCH_H = 280f, SWATCH_R = 16f;
    public static final float[][] SWATCH_AT = {{488, 390}, {960, 390}, {1432, 390}, {488, 690}, {960, 690}, {1432, 690}};
    public static final int[] SWATCH = {0xFF8E8E8E, 0xFF618996, 0xFF947E64, 0xFF907C76, 0xFF738C4B, 0xFF807A9A};
    /** Corner marks: centres {x, y, mirror x, mirror y}, white, the two merged L outlines of the top left mark. */
    public static final float[][] CORNER_AT = {{240, 228, 1, 1}, {1680, 228, -1, 1}, {240, 852, 1, -1}, {1680, 852, -1, -1}};
    public static final float[] CORNER_OUTER = {34, -42, 42, -30, -30, -30, -30, 42, -42, 34, -42, -42};
    public static final float[] CORNER_INNER = {32, -22, 32, -18, -18, -18, -18, 32, -22, 32, -22, -22};
    /** Corner opacity keys over the loop: {frame, percent}, linear between them. */
    static final int[][] CORNER_KEYS = {{81, 100}, {103, 0}, {123, 0}, {143, 98}};
    /** The running pill: centre, size (fully round), colour; the text 30 px white. */
    public static final float PILL_X = 960f, PILL_Y = 978f, PILL_W = 304f, PILL_H = 92f, TEXT_PX = 30f;
    public static final int PILL = 0xFF4076FF, TEXT = 0xFFFFFFFF;

    private WallColorPattern() {}

    /** The start disc's colour of a stock pattern type: grey for the dark types 3 and 4, white for every other type. */
    public static int disc(int type) {
        return type == 3 || type == 4 ? DISC : DISC_LIGHT;
    }

    /** The start disc's scale at {@code ms} after the pattern opened (GROW_TO after the growth). */
    public static float discScale(long ms) {
        if (ms <= 0) return GROW_FROM;
        if (ms >= GROW_MS) return GROW_TO;
        return GROW_FROM + (GROW_TO - GROW_FROM) * ms / GROW_MS;
    }

    /** True while the disc is drawn (until 200 ms after its growth). */
    public static boolean discShown(long ms) {
        return ms < GROW_MS + GROW_HIDE_MS;
    }

    /** True once the loop runs (the stock Lottie starts at the end of the growth). */
    public static boolean loopShown(long ms) {
        return ms >= GROW_MS;
    }

    /** The loop frame (81..143) at {@code ms} after the pattern opened. */
    public static float frame(long ms) {
        long t = Math.max(0, ms - GROW_MS) % LOOP_MS;
        return LOOP_FIRST + t * (float) FPS / 1000f;
    }

    /** The corner marks' opacity 0..1 at loop frame {@code f}. */
    public static float cornerAlpha(float f) {
        int[][] k = CORNER_KEYS;
        if (f <= k[0][0]) return k[0][1] / 100f;
        for (int i = 1; i < k.length; i++) {
            if (f <= k[i][0]) {
                float t = (f - k[i - 1][0]) / (k[i][0] - k[i - 1][0]);
                return (k[i - 1][1] + (k[i][1] - k[i - 1][1]) * t) / 100f;
            }
        }
        return k[k.length - 1][1] / 100f;
    }
}
