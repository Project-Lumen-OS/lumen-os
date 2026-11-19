// SPDX-License-Identifier: Apache-2.0
package org.lumen.signal;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Outline;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;

/**
 * The developer cards' open and fold motion (2026-11-19: smooth, calm): the full panel's rounded box grows out
 * of the small card's place and its content fades in, folding runs it backwards. One rounded clip that moves, no
 * scaling of text. Off when the user removed animations (animator duration scale 0): then the windows just swap.
 */
public final class PanelMotion {
    public static final long GROW_MS = 280L, FOLD_MS = 220L;
    private static final PathInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);

    private PanelMotion() {}

    /** False when Android's animations are removed (accessibility, developer options). */
    public static boolean on() { return ValueAnimator.areAnimatorsEnabled(); }

    /**
     * Where the small card sits in the full panel's box (box coordinates): its top right corner on the panel's right
     * edge, {@code dy} below the panel's top ({@code SignalPlace.CARD_TOP - SHEET_TOP} scaled; 0 for a card on the
     * other side of the screen, which then grows from the panel's own corner).
     */
    public static Rect cardIn(int boxW, int cardW, int cardH, int dy) {
        return new Rect(Math.max(0, boxW - cardW), dy, boxW, dy + cardH);
    }

    /** The box grows from {@code from} to its own size, its children fade in; {@code end} after (main thread). */
    public static void grow(View box, Rect from, float radius, Runnable end) {
        run(box, from, radius, true, GROW_MS, end);
    }

    /** The box folds into {@code to}, its children fade out; {@code end} after. */
    public static void fold(View box, Rect to, float radius, Runnable end) {
        run(box, to, radius, false, FOLD_MS, end);
    }

    private static void run(View box, Rect small, float radius, boolean grow, long ms, Runnable end) {
        final Rect now = new Rect(small);
        box.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(now, radius); }
        });
        box.setClipToOutline(true);
        if (grow && box instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) box;
            for (int i = 0; i < g.getChildCount(); i++) g.getChildAt(i).setAlpha(0f);
        }
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(ms);
        a.setInterpolator(EASE);
        a.addUpdateListener(an -> {
            float t = (float) an.getAnimatedValue(), f = grow ? t : 1f - t;
            int w = box.getWidth(), h = box.getHeight();
            now.set(lerp(small.left, 0, f), lerp(small.top, 0, f), lerp(small.right, w, f), lerp(small.bottom, h, f));
            box.invalidateOutline();
            float fade = grow ? Math.min(1f, Math.max(0f, (t - 0.15f) / 0.6f)) : Math.max(0f, 1f - t / 0.6f);
            if (box instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) box;
                for (int i = 0; i < g.getChildCount(); i++) g.getChildAt(i).setAlpha(fade);
            }
        });
        a.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator an) {
                box.setClipToOutline(false);
                box.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                if (end != null) end.run();
            }
        });
        a.start();
    }

    private static int lerp(int a, int b, float f) { return Math.round(a + (b - a) * f); }
}
