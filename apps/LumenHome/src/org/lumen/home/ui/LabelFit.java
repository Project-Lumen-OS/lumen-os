// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.ui;

/**
 * How a tile's label fits its place in every language (X50 live audit 2026-11-24: "Install Lumen OS fo..." under the
 * installer tile, "HDMI 1 (ARC) No..." on an input chip, even when focused): a label that is too wide first gets
 * smaller, down to {@link #MIN_SCALE} of its size, and is cut with an ellipsis only below that. Plain Java (Ink draws
 * with it, test/label/LabelFitTest measures every language with the real Inter font).
 */
public final class LabelFit {
    /** The smallest size a label takes before it is cut: 78 % stays readable at TV distance for the 22 to 24 px labels. */
    public static final float MIN_SCALE = 0.78f;

    private LabelFit() {}

    /** The size factor for a text {@code width} wide in a place {@code maxW} wide: 1 when it fits, never below MIN. */
    public static float scale(float width, float maxW) {
        if (width <= maxW || width <= 0f) return 1f;
        return Math.max(MIN_SCALE, maxW / width);
    }

    /** True when the text fits at its scale, so nothing is cut. */
    public static boolean fits(float width, float maxW) {
        return width * scale(width, maxW) <= maxW + 0.5f;
    }
}
