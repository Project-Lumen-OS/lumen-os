package org.lumen.projector.panel;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextPaint;

import org.lumen.projector.R;
import org.lumen.projector.ui.SliderRow;
import org.lumen.projector.ui.Theme;

/**
 * The brightness slider of layout D (bottom of the card, and alone as the remote brightness bar). A SliderRow, so the
 * steps, the 200 ms commit, the Boost edge and every gate of QuickPanelController.onKey work on it unchanged; only
 * the look is new: sun icon, thin track with a knob that grows on focus, the level on the right, Boost in yellow.
 */
final class QpdLampRow extends SliderRow {
    private final Paint tile = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint num;
    private final RectF r = new RectF();
    private final QpdStyle.Icon sun;
    private final String boostWord;

    private boolean bar;
    private float focus, knob;
    private ValueAnimator focusAnim, knobAnim;
    // fill animation (200 ms ease) towards the level's fraction
    private float shown = -1f, animFrom, animTo = -1f;
    private long animAt;

    QpdLampRow(Context c, CharSequence title, OnSlide l) {
        super(c, title, 1, 10, l);
        boostWord = c.getString(R.string.panel_boost);
        tile.setColor(QpdStyle.TILE);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Theme.pxf(c, 2.5f));
        track.setColor(QpdStyle.TRACK);
        knobP.setShadowLayer(Theme.pxf(c, 8), 0, Theme.pxf(c, 2), 0x59000000);
        num = QpdStyle.text(c, 22, 600, QpdStyle.INK);
        num.setTextAlign(Paint.Align.RIGHT);
        num.setFontFeatureSettings("'tnum', 'cv11', 'ss03'");
        sun = new QpdStyle.Icon(this, R.drawable.qpd_ic_sun);
        setDefaultFocusHighlightEnabled(false);
    }

    /** Remote brightness bar: no tile of its own (the card is the bar), knob always shown, no focus ring. */
    void setBarMode(boolean on) {
        bar = on;
        settle();
    }

    boolean isBarMode() { return bar; }

    @Override
    protected float designHeight() { return QpdLayout.SLIDER_H; }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return sun.is(who) || super.verifyDrawable(who);
    }

    @Override
    protected void onFocusChanged(boolean gain, int direction, Rect prev) {
        super.onFocusChanged(gain, direction, prev);
        if (bar) return;
        if (focusAnim != null) focusAnim.cancel();
        if (knobAnim != null) knobAnim.cancel();
        if (!isAttachedToWindow()) { settle(); return; }
        focusAnim = ValueAnimator.ofFloat(focus, gain ? 1f : 0f);
        focusAnim.setDuration(QpdLayout.FOCUS_MS);
        focusAnim.setInterpolator(QpdStyle.ease());
        focusAnim.addUpdateListener(a -> {
            focus = (float) a.getAnimatedValue();
            float s = 1f + (QpdLayout.SLIDER_FOCUS_SCALE - 1f) * focus;
            setScaleX(s);
            setScaleY(s);
            invalidate();
        });
        focusAnim.start();
        knobAnim = ValueAnimator.ofFloat(knob, gain ? 1f : 0f);
        knobAnim.setDuration(260);
        knobAnim.setInterpolator(gain ? QpdStyle.spring() : QpdStyle.ease());
        knobAnim.addUpdateListener(a -> { knob = Math.max(0f, (float) a.getAnimatedValue()); invalidate(); });
        knobAnim.start();
        if (gain) sun.start();
        else sun.rest();
    }

    void settle() {
        if (focusAnim != null) focusAnim.cancel();
        if (knobAnim != null) knobAnim.cancel();
        boolean f = isFocused() && !bar;
        focus = f ? 1f : 0f;
        knob = bar || f ? 1f : 0f;
        float s = f ? QpdLayout.SLIDER_FOCUS_SCALE : 1f;
        setScaleX(s);
        setScaleY(s);
        invalidate();
    }

    private float px(float d) { return Theme.pxf(getContext(), d); }

    private Integer level() { return isKnown() ? getValue() : null; }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        Integer lv = level();
        boolean boost = lv != null && QpdLayout.isBoost(lv);
        float radius = px(QpdLayout.TILE_RADIUS);
        float padH = px(bar ? 30 : 26);
        if (!bar) {
            tile.setColor(QpdStyle.lerp(QpdStyle.TILE, QpdStyle.TILE_HI, focus));
            r.set(0, 0, w, h);
            c.drawRoundRect(r, radius, radius, tile);
            if (focus > 0.01f) {
                ring.setColor(boost ? QpdStyle.BOOST_RING : QpdStyle.RING);
                ring.setAlpha(Math.round(ring.getAlpha() * focus));
                float in = ring.getStrokeWidth() / 2f;
                r.set(in, in, w - in, h - in);
                c.drawRoundRect(r, radius - in, radius - in, ring);
            }
        }
        float icon = px(bar ? 34 : 36), gap = px(20);
        Drawable sun = this.sun.drawable();
        if (sun != null) {
            sun.setTintList(ColorStateList.valueOf(boost ? QpdStyle.BOOST_TEXT : 0xFFFFFFFF));
            int s = Math.round(icon);
            int save = c.save();
            c.translate(padH, (h - s) / 2f);
            sun.setBounds(0, 0, s, s);
            sun.draw(c);
            c.restoreToCount(save);
        }
        float numW = px(64);
        float tx0 = padH + icon + gap, tx1 = w - padH - numW - gap;
        float th = px(12), cy = h / 2f;
        r.set(tx0, cy - th / 2f, tx1, cy + th / 2f);
        c.drawRoundRect(r, th / 2f, th / 2f, track);
        float frac = animatedFill(QpdLayout.fill(lv));
        float fx = tx0 + (tx1 - tx0) * frac;
        if (frac > 0f) {
            if (boost) fillP.setShader(new LinearGradient(tx0, 0, tx1, 0, QpdStyle.BOOST_A, QpdStyle.BOOST_B, Shader.TileMode.CLAMP));
            else {
                fillP.setShader(null);
                fillP.setColor(0xFFFFFFFF);
            }
            r.set(tx0, cy - th / 2f, Math.max(tx0 + th, fx), cy + th / 2f);
            c.drawRoundRect(r, th / 2f, th / 2f, fillP);
        }
        if (knob > 0.01f && lv != null) {
            knobP.setColor(boost ? QpdStyle.BOOST_KNOB : 0xFFFFFFFF);
            c.drawCircle(fx, cy, px(13) * knob, knobP);
        }
        num.setColor(boost ? QpdStyle.BOOST_TEXT : 0xFFFFFFFF);
        String t = QpdLayout.label(lv, boostWord);
        c.drawText(t, w - padH, cy - (num.descent() + num.ascent()) / 2f, num);
    }

    private float animatedFill(float target) {
        long now = SystemClock.uptimeMillis();
        if (shown < 0f) { shown = target; animTo = target; return target; }
        if (target != animTo) { animFrom = shown; animTo = target; animAt = now; }
        float t = Math.min(1f, (now - animAt) / 200f);
        shown = animFrom + (animTo - animFrom) * QpdStyle.ease().getInterpolation(t);
        if (t < 1f) postInvalidateOnAnimation();
        else shown = animTo;
        return shown;
    }
}
