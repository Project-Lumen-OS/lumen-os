package org.lumen.projector.panel;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;

import org.lumen.projector.ui.Theme;

/**
 * One cell of the quick panel layout D: a wide tile (icon, title, value line; the title runs left and right when it
 * does not fit), a small tile (icon only, the label slides up on focus) or a round header button. Focus as stock:
 * scale 1.08 (round 1.12), accent colour, 300 ms ease-out, plus the icon's own animation (AnimatedVectorDrawable,
 * started on focus and reset on blur, so an idle panel draws static vectors only).
 */
final class QpdTile extends View {
    private static final String TAG = "Z9xPanel";
    static final int WIDE = 0, SMALL = 1, ROUND = 2;

    final String key;
    final int kind;
    private final Runnable onOk;
    private QpdStyle.Icon icon;
    private CharSequence title;
    private CharSequence value;

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titleP, valueP, labelP;
    private final RectF rect = new RectF();
    private final float radius;

    /** focus 0..1 (colour, scale), lift 0..1 (small icon up, spring), label 0..1 (small label in). */
    private float focus, lift, label;
    private ValueAnimator focusAnim, liftAnim, labelAnim;
    /** Marquee: start of the current focus (uptime ms), -1 while unfocused. */
    private long marqueeStart = -1;

    QpdTile(Context c, String key, int kind, int iconRes, CharSequence title, Runnable onOk) {
        super(c);
        this.key = key;
        this.kind = kind;
        this.onOk = onOk;
        this.title = title == null ? "" : title;
        setFocusable(true);
        setFocusableInTouchMode(false);
        setDefaultFocusHighlightEnabled(false);
        setContentDescription(this.title);
        radius = Theme.pxf(c, kind == ROUND ? QpdLayout.ROUND / 2f : QpdLayout.TILE_RADIUS);
        titleP = QpdStyle.text(c, 22, 600, QpdStyle.INK);
        titleP.setLetterSpacing(-0.2f / 22f);
        valueP = QpdStyle.text(c, 18, 450, QpdStyle.MUTE);
        labelP = QpdStyle.text(c, 15, 600, 0xFFFFFFFF);
        labelP.setTextAlign(Paint.Align.CENTER);
        setIcon(iconRes);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        setOutlineSpotShadowColor(QpdStyle.SHADOW_ACCENT);
        setOutlineAmbientShadowColor(QpdStyle.SHADOW_ACCENT);
    }

    void setIcon(int res) {
        if (icon != null) icon.release();
        icon = new QpdStyle.Icon(this, res);
        if (isFocused()) startIcon();
        requestLayout();
        invalidate();
    }

    void setTitle(CharSequence t) {
        title = t == null ? "" : t;
        setContentDescription(title);
        invalidate();
    }

    CharSequence title() { return title; }

    void setValue(CharSequence v) {
        if (TextUtils.equals(v, value)) return;
        value = v;
        invalidate();
    }

    CharSequence value() { return value; }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return (icon != null && icon.is(who)) || super.verifyDrawable(who);
    }

    // ------------------------------------------------------------------ keys
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev) {
        if (isOk(keyCode)) return true;
        return super.onKeyDown(keyCode, ev);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent ev) {
        if (isOk(keyCode)) {
            if (!ev.isCanceled() && onOk != null) {
                try {
                    onOk.run();
                } catch (Throwable t) {
                    Log.e(TAG, "tile " + key + " failed", t);
                }
            }
            return true;
        }
        return super.onKeyUp(keyCode, ev);
    }

    private static boolean isOk(int k) {
        return k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    // ------------------------------------------------------------------ focus motion
    @Override
    protected void onFocusChanged(boolean gain, int direction, Rect prev) {
        super.onFocusChanged(gain, direction, prev);
        if (!gain && held) return;                       // its popup is open: the tile keeps its focused look
        animateFocus(gain);
    }

    /** True while this tile's popup is open (prototype: the tile stays lit beside its popup). */
    private boolean held;

    void setHeld(boolean h) {
        if (held == h) return;
        held = h;
        // a popup opened directly (KEYSTONE key, BACK from zoom) lights its tile, which never had the focus
        if (!isFocused()) animateFocus(h);
    }

    private void animateFocus(boolean on) {
        focusAnim = run(focusAnim, focus, on ? 1f : 0f, QpdLayout.FOCUS_MS, QpdStyle.ease(), v -> {
            focus = v;
            float s = 1f + ((kind == ROUND ? QpdLayout.ROUND_FOCUS_SCALE : QpdLayout.FOCUS_SCALE) - 1f) * v;
            setScaleX(s);
            setScaleY(s);
            setElevation(kind == ROUND ? 0f : Theme.pxf(getContext(), 14) * v);
        });
        if (kind == SMALL) {
            liftAnim = run(liftAnim, lift, on ? 1f : 0f, on ? 360 : 220, on ? QpdStyle.spring() : QpdStyle.ease(),
                    v -> lift = v);
            labelAnim = run(labelAnim, label, on ? 1f : 0f, on ? 300 : 160, QpdStyle.ease(), v -> label = v);
        }
        if (on) {
            startIcon();
            marqueeStart = SystemClock.uptimeMillis() + QpdLayout.FOCUS_MS + 20;
        } else {
            resetIcon();
            marqueeStart = -1;
        }
        invalidate();
    }

    private interface F { void set(float v); }

    private ValueAnimator run(ValueAnimator old, float from, float to, long ms, android.animation.TimeInterpolator in, F f) {
        if (old != null) old.cancel();
        if (!isAttachedToWindow()) {
            f.set(to);
            invalidate();
            return null;
        }
        ValueAnimator a = ValueAnimator.ofFloat(from, to);
        a.setDuration(ms);
        a.setInterpolator(in);
        a.addUpdateListener(x -> {
            f.set((float) x.getAnimatedValue());
            invalidate();
        });
        a.start();
        return a;
    }

    private void startIcon() {
        if (icon != null) icon.start();
    }

    /** Back to the resting icon (the vector's own values), not the animation's first frame. */
    private void resetIcon() {
        if (icon != null) icon.rest();
    }

    /** Panel closed or rebuilt: no running animation left behind. */
    void settle() {
        for (ValueAnimator a : new ValueAnimator[]{focusAnim, liftAnim, labelAnim}) if (a != null) a.cancel();
        boolean f = isFocused();
        focus = f ? 1f : 0f;
        lift = label = kind == SMALL && f ? 1f : 0f;
        float s = f ? (kind == ROUND ? QpdLayout.ROUND_FOCUS_SCALE : QpdLayout.FOCUS_SCALE) : 1f;
        setScaleX(s);
        setScaleY(s);
        setElevation(f && kind != ROUND ? Theme.pxf(getContext(), 14) : 0f);
        if (!f) resetIcon();
        invalidate();
    }

    // ------------------------------------------------------------------ drawing
    private float px(float design) { return Theme.pxf(getContext(), design); }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        bg.setColor(QpdStyle.lerp(QpdStyle.TILE, QpdStyle.ACCENT, focus));
        rect.set(0, 0, w, h);
        c.drawRoundRect(rect, radius, radius, bg);
        if (kind == WIDE) drawWide(c, w, h);
        else if (kind == SMALL) drawSmall(c, w, h);
        else drawIcon(c, w / 2f, h / 2f, px(28), 1f);
    }

    private void drawIcon(Canvas c, float cx, float cy, float size, float scale) {
        Drawable icon = this.icon == null ? null : this.icon.drawable();
        if (icon == null) return;
        int half = Math.round(size / 2f);
        icon.setBounds(0, 0, 2 * half, 2 * half);
        int save = c.save();
        c.translate(cx, cy);
        if (scale != 1f) c.scale(scale, scale);
        c.translate(-half, -half);
        icon.draw(c);
        c.restoreToCount(save);
    }

    private void drawSmall(Canvas c, int w, int h) {
        float iconSize = px(36);
        drawIcon(c, w / 2f, h / 2f - px(13) * lift, iconSize, 1f - 0.1f * lift);
        if (label <= 0.001f) return;
        float a = Math.min(1f, label * 300f / 220f);
        labelP.setAlpha(Math.round(255 * a));
        // a long label (German "Trapezkorrektur") gets a smaller size before it would be cut (prototype: 15 px)
        float room = w - px(12);
        labelP.setTextSize(px(15));
        float tw = labelP.measureText(title, 0, title.length());
        if (tw > room) labelP.setTextSize(Math.max(px(11), px(15) * room / tw));
        float base = h - px(15) - labelP.descent() + px(10) * (1f - label);
        CharSequence t = TextUtils.ellipsize(title, labelP, room, TextUtils.TruncateAt.END);
        c.drawText(t, 0, t.length(), w / 2f, base, labelP);
    }

    private void drawWide(Canvas c, int w, int h) {
        float pad = px(22), iconSize = px(36), gap = px(16);
        drawIcon(c, pad + iconSize / 2f, h / 2f, iconSize, 1f);
        float x0 = pad + iconSize + gap;
        float boxW = Math.max(1f, w - pad - x0);
        boolean hasValue = value != null && value.length() > 0;
        float titleH = -titleP.ascent() + titleP.descent();
        float valueH = hasValue ? px(5) - valueP.ascent() + valueP.descent() : 0f;
        float top = (h - titleH - valueH) / 2f;
        float tBase = top - titleP.ascent();
        titleP.setColor(QpdStyle.lerp(QpdStyle.INK, 0xFFFFFFFF, focus));
        float tw = titleP.measureText(title, 0, title.length());
        int save = c.save();
        c.clipRect(x0, 0, x0 + boxW, h);
        if (QpdLayout.needsMarquee(tw, boxW)) {
            if (marqueeStart >= 0) {
                long t = SystemClock.uptimeMillis() - marqueeStart;
                long oneWay = QpdLayout.marqueeMs(tw, boxW, Theme.scale(getContext()));
                float dx = QpdLayout.marqueeShift(tw, boxW) * QpdLayout.marqueePhase(Math.max(0, t), oneWay);
                titleP.setShader(null);
                // soft edges where the running title is cut (prototype mask), never a hard clip
                QpdStyle.drawFaded(c, title, titleP, x0, boxW, 0, h, tBase, dx, px(QpdLayout.FADE));
                postInvalidateOnAnimation();
            } else {
                // unfocused: the end fades out (prototype mask from 80 %)
                titleP.setShader(new LinearGradient(x0 + boxW * 0.8f, 0, x0 + boxW, 0, titleP.getColor(),
                        titleP.getColor() & 0x00FFFFFF, Shader.TileMode.CLAMP));
                c.drawText(title, 0, title.length(), x0, tBase, titleP);
                titleP.setShader(null);
            }
        } else {
            c.drawText(title, 0, title.length(), x0, tBase, titleP);
        }
        c.restoreToCount(save);
        if (hasValue) {
            valueP.setColor(QpdStyle.lerp(QpdStyle.MUTE, QpdStyle.VALUE_FOCUSED, focus));
            float vBase = tBase + titleP.descent() + px(5) - valueP.ascent();
            float vw = valueP.measureText(value, 0, value.length());
            float dx = 0f;
            if (QpdLayout.needsMarquee(vw, boxW) && marqueeStart >= 0) {
                // a long value runs like the title (never an ellipsis), soft edges
                long t = SystemClock.uptimeMillis() - marqueeStart;
                dx = QpdLayout.marqueeShift(vw, boxW) * QpdLayout.marqueePhase(Math.max(0, t),
                        QpdLayout.marqueeMs(vw, boxW, Theme.scale(getContext())));
                postInvalidateOnAnimation();
            }
            QpdStyle.drawFaded(c, value, valueP, x0, boxW, vBase + valueP.ascent() - px(4), vBase + valueP.descent() + px(4),
                    vBase, dx, px(QpdLayout.FADE));
        }
    }
}
