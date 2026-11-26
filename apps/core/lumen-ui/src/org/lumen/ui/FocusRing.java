// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.ui;

/**
 * Who wears the focus ring and how big it is on screen ({@link Palette#focusRing}, drawn by {@link RingPaint}). Plain
 * Java, so host tests check the rule: only the focused element shows the ring (fading in with its focus value), an
 * element that lost the focus drops it at once, so two rings never show during a focus move.
 */
public final class FocusRing {
    /** Ring opacity 0..1 of an element: its focus state and its animated focus value. */
    public static float alpha(boolean focused, float value) {
        return focused ? Math.max(0f, Math.min(1f, value)) : 0f;
    }

    /** Stroke width in canvas px: {@code w} design px, {@code px} screen px per design px, the view's own scale. */
    public static float width(int w, float px, float scale) {
        return w * px / scale;
    }

    /** From the element's edge to the middle of the stroke, in canvas px (the gap stays clear). */
    public static float outset(int w, int gap, float px, float scale) {
        return (gap + w / 2f) * px / scale;
    }

    private FocusRing() {}
}
