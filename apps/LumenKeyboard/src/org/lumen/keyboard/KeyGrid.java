// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.keyboard;

/**
 * Key geometry of a page (pure Java, test/run.sh). AOSP worked from an 11 x 5 grid with a seven column space bar
 * written into the code; the keys' own rectangles work for every page: the bottom row with the two column language
 * key and the letter rows of 12 keys (ЙЦУКЕН: ъ, ї or the apostrophe after х).
 */
public final class KeyGrid {
    /** A D-pad step up or down aims this many key heights away (AOSP). */
    public static final float ROW_STEP = 1.25f;

    private KeyGrid() {
    }

    /**
     * Index of the key under (x, y), else of the key nearest to it (distance to its rectangle, the first of equals).
     * Rectangles are left, top, width, height per key; -1 when there is no key.
     */
    public static int nearest(int[] left, int[] top, int[] width, int[] height, float x, float y) {
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < left.length; i++) {
            double dx = Math.max(Math.max(left[i] - x, 0), x - (left[i] + width[i]));
            double dy = Math.max(Math.max(top[i] - y, 0), y - (top[i] + height[i]));
            double d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /**
     * The point a D-pad step from a key aims at; the key {@link #nearest} to it is the next focus (AOSP's rule).
     * Left or right: half a key height past that side. Up or down: {@link #ROW_STEP} key heights from the middle.
     * {@code x} is where the step starts across (the key's middle, the remembered x when leaving space).
     *
     * @return {x, y}
     */
    public static float[] aim(float left, float right, float centerY, float height, float x, int dx, int dy) {
        float slide = height / 2f;
        if (dx < 0) x = left - slide;
        else if (dx > 0) x = right + slide;
        return new float[]{x, centerY + dy * height * ROW_STEP};
    }

    /**
     * First key a long press popup of {@code count} keys covers: from the focused key on, moved left so that it
     * stays in the focused key's row (keys with the same top), whatever the row's length.
     */
    public static int popupStart(int[] top, int focus, int count) {
        int first = focus, last = focus;
        while (first > 0 && top[first - 1] == top[focus]) first--;
        while (last + 1 < top.length && top[last + 1] == top[focus]) last++;
        return Math.max(first, Math.min(focus, last + 1 - count));
    }
}
