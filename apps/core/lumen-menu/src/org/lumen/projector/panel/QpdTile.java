// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
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
 * does not fit), a small tile (icon only, the label slides up on focus: its state when it has one, else its title,
 * smaller where that fits, else running like a wide tile's title; a dot in the corner while its feature is on) or a
 * round header button. Focus as stock: scale 1.08 (round 1.12),
 * accent colour, 300 ms ease-out, plus the icon's own animation (AnimatedVectorDrawable, started on focus and reset on
 * blur, so an idle panel draws static vectors only; none while the system animations are off).
 */
final class QpdTile extends View {
    private static final String TAG = "Z9xPanel";
    static final int WIDE = 0, SMALL = 1, ROUND = 2;
    /** One row of the list look (pencil editor): icon, name, value on the right; All settings as a footer button too. */
    static final int LIST = 3;

    final String key;
    final int kind;
    private final Runnable onOk;
    /** A held OK (eye protection: its popup), run on the release. Null: a held OK is a plain OK. */
    private Runnable onLongOk;
    private boolean longOk;
    private QpdStyle.Icon icon;
    private CharSequence title;
    private CharSequence value;
    /** A small tile whose feature is on now (the 3D tile in a 3D mode): the dot in its corner. */
    private boolean active;
    /** A small tile's state (the 3D mode): its label on focus instead of the title, null = the title. */
    private CharSequence state;

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotP = new Paint(Paint.ANTI_ALIAS_FLAG);
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
        radius = Theme.pxf(c, kind == ROUND ? QpdLayout.ROUND / 2f : kind == LIST ? QpdLayout.ROW_RADIUS : QpdLayout.TILE_RADIUS);
        titleP = QpdStyle.text(c, 22, 600, QpdStyle.INK);
        titleP.setLetterSpacing(-0.2f / 22f);
        valueP = QpdStyle.text(c, 18, 450, QpdStyle.MUTE);
        labelP = QpdStyle.text(c, 15, 600, 0xFFFFFFFF);
        labelP.setTextAlign(Paint.Align.CENTER);
        setIcon(iconRes);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), kind == ROUND ? v.getHeight() / 2f : radius);
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
        if (getParent() instanceof QpdCard) ((QpdCard) getParent()).titleChanged(this);   // the clock line follows
    }

    CharSequence title() { return title; }

    void setValue(CharSequence v) {
        if (TextUtils.equals(v, value)) return;
        value = v;
        invalidate();
    }

    CharSequence value() { return value; }

    /** The 3D tile: the mode on, shown as the focused label (other small tiles keep their title). */
    void setState(CharSequence s) {
        if (s == null ? state == null : state != null && s.toString().equals(state.toString())) return;
        state = s;
        invalidate();
    }

    CharSequence state() { return state; }

    void setActive(boolean on) {
        if (active == on) return;
        active = on;
        invalidate();
    }

    boolean active() { return active; }

    /** A tile of a row the full menu greys or hides now (a signal rule, the picture mode): dimmed like that row. */
    void setUsable(boolean on) {
        float a = on ? 1f : USABLE_OFF_ALPHA;
        if (getAlpha() != a) setAlpha(a);
    }

    /** The dimming of a greyed row (Row.setRowEnabled). */
    static final float USABLE_OFF_ALPHA = 0.4f;

    /** The icon's motion once more (a new value on a focused or lit tile), never while animations are off. */
    void playIcon() {
        if (isFocused() || held) startIcon();
    }

    void setOnLongOk(Runnable r) { onLongOk = r; }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return (icon != null && icon.is(who)) || super.verifyDrawable(who);
    }

    // ------------------------------------------------------------------ keys
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev) {
        if (isOk(keyCode)) {
            // the first repeat marks a held OK; it acts on the release, so the UP never lands on a popup row
            if (ev.getRepeatCount() == 0) longOk = false;
            else if (ev.getRepeatCount() == 1 && onLongOk != null) longOk = true;
            return true;
        }
        return super.onKeyDown(keyCode, ev);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent ev) {
        if (isOk(keyCode)) {
            Runnable r = longOk ? onLongOk : onOk;
            longOk = false;
            if (!ev.isCanceled() && r != null) {
                try {
                    r.run();
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
            float s = 1f + (focusScale() - 1f) * v;
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

    /** Stock focus scale: 1.12 for a circle, 1.04 for a list row (it spans the card), 1.08 for a tile. */
    private float focusScale() {
        return kind == ROUND ? QpdLayout.ROUND_FOCUS_SCALE : kind == LIST ? QpdLayout.LIST_FOCUS_SCALE : QpdLayout.FOCUS_SCALE;
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
        float s = f ? focusScale() : 1f;
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
        float rad = kind == ROUND ? h / 2f : radius;
        c.drawRoundRect(rect, rad, rad, bg);
        if (kind == WIDE) drawWide(c, w, h);
        else if (kind == SMALL) drawSmall(c, w, h);
        else if (kind == LIST) drawList(c, w, h);
        else drawIcon(c, w / 2f, h / 2f, px(28), 1f);
        if (mark != 0) {                                   // the editor: the item chosen (ring) or moving (white ring)
            markP.setColor(mark == MARK_MOVING ? 0xFFFFFFFF : QpdStyle.SELECTED);
            float in = markP.getStrokeWidth() / 2f;
            rect.set(in, in, w - in, h - in);
            c.drawRoundRect(rect, rad - in, rad - in, markP);
        }
    }

    static final int MARK_SELECTED = 1, MARK_MOVING = 2;
    private int mark;
    private final Paint markP = new Paint(Paint.ANTI_ALIAS_FLAG);
    {
        markP.setStyle(Paint.Style.STROKE);
        markP.setStrokeWidth(Theme.pxf(getContext(), 3));
    }

    /** The pencil editor's mark on this tile: 0, {@link #MARK_SELECTED} or {@link #MARK_MOVING}. */
    void setMark(int m) {
        if (mark == m) return;
        mark = m;
        invalidate();
    }

    /** A list row: icon, name, the value right aligned (the list look of the pencil editor, All settings below). */
    /** A row that opens a panel shows a chevron at its end (list look, mock 06). */
    private boolean chevron;
    private final Paint chevronP = new Paint(Paint.ANTI_ALIAS_FLAG);

    void setChevron(boolean on) {
        if (chevron == on) return;
        chevron = on;
        invalidate();
    }

    private void drawList(Canvas c, int w, int h) {
        float pad = px(22), iconSize = px(30), gap = px(18);
        if (chevron) {
            chevronP.setStyle(Paint.Style.STROKE);
            chevronP.setStrokeWidth(px(2.5f));
            chevronP.setStrokeCap(Paint.Cap.ROUND);
            chevronP.setStrokeJoin(Paint.Join.ROUND);
            chevronP.setColor(QpdStyle.lerp(QpdStyle.MUTE, 0xFFFFFFFF, focus));
            float cx = w - pad - px(4), cy = h / 2f, d = px(6);
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            float s = rtl ? -1f : 1f;
            if (rtl) cx = pad + px(4);
            c.drawLine(cx - s * d, cy - d, cx, cy, chevronP);
            c.drawLine(cx, cy, cx - s * d, cy + d, chevronP);
            pad += px(24);
        }
        drawIcon(c, pad + iconSize / 2f, h / 2f, iconSize, 1f);
        float x0 = pad + iconSize + gap;
        titleP.setColor(QpdStyle.lerp(QpdStyle.INK, 0xFFFFFFFF, focus));
        float size = titleP.getTextSize();
        titleP.setTextSize(px(21));
        float base = h / 2f - (titleP.descent() + titleP.ascent()) / 2f;
        float vw = 0f;
        if (value != null && value.length() > 0) {
            valueP.setColor(QpdStyle.lerp(QpdStyle.MUTE, QpdStyle.VALUE_FOCUSED, focus));
            vw = Math.min(valueP.measureText(value, 0, value.length()), (w - x0 - pad) * 0.5f);
            QpdStyle.drawFaded(c, value, valueP, w - pad - vw, vw, 0, h, h / 2f - (valueP.descent() + valueP.ascent()) / 2f,
                    0f, px(QpdLayout.FADE));
            vw += px(16);
        }
        QpdStyle.drawFaded(c, title, titleP, x0, Math.max(1f, w - pad - vw - x0), 0, h, base, 0f, px(QpdLayout.FADE));
        titleP.setTextSize(size);
    }

    private void drawIcon(Canvas c, float cx, float cy, float size, float scale) {
        Drawable icon = this.icon == null ? null : this.icon.drawable();
        if (icon == null) return;
        int half = Math.round(size / 2f);
        icon.setBounds(0, 0, 2 * half, 2 * half);
        icon.setTint(QpdStyle.lerp(QpdStyle.ICON, 0xFFFFFFFF, focus));
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
        if (active) {
            dotP.setColor(QpdStyle.lerp(QpdStyle.ACCENT, 0xFFFFFFFF, focus));
            c.drawCircle(w - px(QpdLayout.ACTIVE_DOT_INSET), px(QpdLayout.ACTIVE_DOT_INSET), px(QpdLayout.ACTIVE_DOT) / 2f, dotP);
        }
        if (label <= 0.001f) return;
        CharSequence title = state != null && state.length() > 0 ? state : this.title;
        float a = Math.min(1f, label * 300f / 220f);
        labelP.setAlpha(Math.round(255 * a));
        float room = w - px(12);
        labelP.setTextSize(px(QpdLayout.LABEL));
        float tw = labelP.measureText(title, 0, title.length());
        labelP.setTextSize(px(QpdLayout.LABEL) * QpdLayout.labelScale(tw, room));
        float base = h - px(15) - labelP.descent() + px(10) * (1f - label);
        if (!QpdLayout.labelRuns(tw, room)) {
            c.drawText(title, 0, title.length(), w / 2f, base, labelP);
            return;
        }
        float dx = 0f;
        if (marqueeStart >= 0) {
            long t = SystemClock.uptimeMillis() - marqueeStart;
            dx = QpdLayout.marqueeShift(tw, room) * QpdLayout.marqueePhase(Math.max(0, t),
                    QpdLayout.marqueeMs(tw, room, Theme.scale(getContext())));
            postInvalidateOnAnimation();
        }
        labelP.setTextAlign(Paint.Align.LEFT);
        QpdStyle.drawFaded(c, title, labelP, (w - room) / 2f, room, base + labelP.ascent() - px(4),
                base + labelP.descent() + px(4), base, dx, px(QpdLayout.FADE));
        labelP.setTextAlign(Paint.Align.CENTER);
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
