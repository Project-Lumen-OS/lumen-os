package org.lumen.home.ui;

import android.view.View;

import java.util.List;

/**
 * Screen-point tests for the hand-focused views of Lumen Home (mouse and touch, HomeActivity.tap): a view counts when
 * it is shown, laid out and not faded out (a tile under the header or a bar faded in browse mode is no target).
 */
public final class Hit {
    /** Below this alpha (the view times its parents) a view is faded out, not a target. */
    static final float MIN_ALPHA = 0.05f;

    private Hit() { }

    /** The point (screen pixels) lies on {@code v}. */
    public static boolean inside(View v, float x, float y) {
        if (v == null || !v.isShown() || v.getWidth() <= 0 || v.getHeight() <= 0 || alpha(v) < MIN_ALPHA) return false;
        int[] at = new int[2];
        v.getLocationOnScreen(at);
        return contains(at[0], at[1], v.getWidth() * v.getScaleX(), v.getHeight() * v.getScaleY(), x, y);
    }

    /** The index of the first view of {@code views} under the point, else -1. */
    public static int indexAt(List<? extends View> views, float x, float y) {
        for (int i = 0; i < views.size(); i++) if (inside(views.get(i), x, y)) return i;
        return -1;
    }

    /** Pure rectangle test (left / top inclusive, right / bottom exclusive). */
    static boolean contains(float left, float top, float w, float h, float x, float y) {
        return x >= left && x < left + w && y >= top && y < top + h;
    }

    private static float alpha(View v) {
        float a = 1f;
        for (Object p = v; p instanceof View; p = ((View) p).getParent()) a *= ((View) p).getAlpha();
        return a;
    }
}
