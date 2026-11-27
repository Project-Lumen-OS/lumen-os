// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

import java.util.ArrayList;
import java.util.List;

/**
 * The picture edge frame of the stock geometry screens (newsettings keystone windows on the Z9X, HB, X50 and Titan
 * builds, byte-equal drawables): the window is opaque black (color_33 #000000) and its background drawable runs along
 * the window's outer pixels. The window is composed before the projector scales, shifts or warps the picture, so the
 * frame is always where the picture ends on the wall, live. Two stock drawables, measured in px of the 1920 x 1080
 * design; our own drawing, no stock asset:
 *   GUIDES = keystone_zoom_move_bk, the zoom and shift screens of a consumer unit (ZoomMoveView, ZoomOnlyView,
 *            TiltShiftOnlyView): a 4 px white edge and faint dashed guide lines (6 px on, 6 off);
 *   FRAME  = keystone_zoom_bk, rotation, picture displacement only and the zoom window (RotateWind,
 *            DigitalShiftOnlyViewNew, ZoomWind): 2 px black, 2 px grey #858585, then 4 px white.
 */
public final class EdgeFrame {
    public static final String GUIDES = "guides", FRAME = "frame";
    public static final float DESIGN_W = 1920f, DESIGN_H = 1080f;
    public static final int PAGE = 0xFF000000;
    public static final float DASH = 6f, GAP = 6f;

    /** Edge bands from the window's outer pixel inwards: {from, to} with the colour at the same index. */
    public static final float[][] FRAME_BANDS = {{2f, 4f}, {4f, 8f}};
    public static final int[] FRAME_COLOURS = {0xFF858585, 0xFFFFFFFF};
    public static final float[][] GUIDES_BANDS = {{0f, 4f}};
    public static final int[] GUIDES_COLOURS = {0xFFFFFFFF};

    public static final int GUIDE = 0x4DFFFFFF, GUIDE_CENTRE = 0x27FFFFFF;
    /**
     * Dashed guides of GUIDES: {vertical (1) or horizontal (0), position, thickness, from, to, dash phase, centre (1)
     * = the fainter 2 px centre lines}. A dash starts wherever (start - phase) is a multiple of DASH + GAP.
     */
    public static final float[][] GUIDE_LINES = {
        {0, 135, 1, 9, 1911, 9, 0},
        {0, 270, 1, 9, 755, 9, 0}, {0, 270, 1, 1165, 1911, 9, 0},
        {0, 539, 2, 4, 617, 11, 1}, {0, 539, 2, 1299, 1916, 11, 1},
        {0, 809, 1, 9, 755, 9, 0}, {0, 809, 1, 1165, 1911, 9, 0},
        {0, 944, 1, 9, 1911, 9, 0},
        {1, 240, 1, 9, 1071, 9, 0},
        {1, 1679, 1, 9, 1071, 9, 0},
        {1, 960, 2, 10, 196, 10, 1}, {1, 960, 2, 879, 1072, 10, 1},
    };

    private EdgeFrame() {}

    public static float[][] bands(String kind) { return GUIDES.equals(kind) ? GUIDES_BANDS : FRAME_BANDS; }

    public static int[] colours(String kind) { return GUIDES.equals(kind) ? GUIDES_COLOURS : FRAME_COLOURS; }

    public static float[][] guides(String kind) { return GUIDES.equals(kind) ? GUIDE_LINES : new float[0][]; }

    /** The dashes of one guide line inside [from, to): {start, end} pairs, the ends cut by the line's own ends. */
    public static List<float[]> dashes(float from, float to, float phase) {
        List<float[]> out = new ArrayList<>();
        float period = DASH + GAP;
        float s = phase + (float) Math.floor((from - phase) / period) * period;
        for (; s < to; s += period) {
            float a = Math.max(s, from), b = Math.min(s + DASH, to);
            if (b > a) out.add(new float[] {a, b});
        }
        return out;
    }
}
