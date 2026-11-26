// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.ui.page;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.ui.Motion;
import org.lumen.ui.Palette;

/**
 * A running job on a {@link TwoPane}: what happens now, a bar and the step under it. The bar glides to each new value
 * (a step never looks frozen) and keeps a slow pulse while the value stands still.
 */
public final class PaneProgress extends LinearLayout {
    private final TextView title, sub;
    private final Bar bar;

    PaneProgress(Context c, Palette p) {
        super(c);
        setOrientation(VERTICAL);
        int pad = PaneStyle.px(c, PaneStyle.ROW_PAD);
        setPaddingRelative(pad, PaneStyle.px(c, PaneStyle.PROG_PAD_TOP), pad, PaneStyle.px(c, PaneStyle.PROG_PAD_BOTTOM));
        title = PaneStyle.text(c, PaneStyle.PROG_TITLE, PaneStyle.W_PROG, p.text1, 0);
        addView(title);
        bar = new Bar(c, p);
        LayoutParams blp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, PaneStyle.px(c, PaneStyle.BAR_H));
        blp.topMargin = PaneStyle.px(c, PaneStyle.BAR_GAP);
        addView(bar, blp);
        sub = PaneStyle.text(c, PaneStyle.PROG_SUB, 400, p.text3, 0);
        LayoutParams slp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = PaneStyle.px(c, PaneStyle.PROG_SUB_GAP);
        addView(sub, slp);
    }

    /** {@code done} 0..1. */
    public void set(CharSequence what, CharSequence step, float done) {
        if (!String.valueOf(what).contentEquals(title.getText())) title.setText(what);
        if (!String.valueOf(step).contentEquals(sub.getText())) sub.setText(step);
        bar.to(Math.max(0f, Math.min(1f, done)));
    }

    public float shown() {
        return bar.value;
    }

    private static final class Bar extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int track, ink;
        float value, target = -1f;
        private ValueAnimator glide;
        private final ValueAnimator pulse;

        Bar(Context c, Palette p) {
            super(c);
            track = p.panelLine;
            ink = p.text1;
            pulse = ValueAnimator.ofFloat(1f, 0.7f, 1f);
            pulse.setDuration(1600);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.addUpdateListener(a -> invalidate());
        }

        void to(float v) {
            if (v == target) return;
            target = v;
            if (glide != null) glide.cancel();
            if (Motion.reduced(getContext())) {
                value = v;
                invalidate();
                return;
            }
            glide = ValueAnimator.ofFloat(value, v);
            glide.setDuration(420);
            glide.setInterpolator(Motion.STANDARD);
            glide.addUpdateListener(a -> {
                value = (float) a.getAnimatedValue();
                invalidate();
            });
            glide.start();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!Motion.reduced(getContext())) pulse.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            pulse.cancel();
            if (glide != null) glide.cancel();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), r = h / 2f;
            paint.setColor(track);
            cv.drawRoundRect(0, 0, w, h, r, r, paint);
            float fw = Math.max(h, w * value);
            float a = pulse.isRunning() ? (float) pulse.getAnimatedValue() : 1f;
            paint.setColor(Palette.alpha(ink, a));
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            if (rtl) cv.drawRoundRect(w - fw, 0, w, h, r, r, paint);
            else cv.drawRoundRect(0, 0, fw, h, r, r, paint);
        }
    }
}
