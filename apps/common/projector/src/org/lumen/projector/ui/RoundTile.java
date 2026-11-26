// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

/**
 * One focusable Canvas tile of the round power menu (the long-press menu of Lumen OS 1.0, the stock-like look the
 * keeps): a disc with a line icon, the label, an optional value line. OK acts on the UP of a DOWN it received itself.
 * Shared by every power menu ({@code power.PowerMenu} of the gmpf and Play 6 families, lumen-menu {@code panel.PowerMenu}
 * of the X50 family) through {@link RoundTilePanel}. Main thread.
 */
public final class RoundTile extends View {
    public static final int ICON_POWER = 0, ICON_RESTART = 1, ICON_TIMER = 2, ICON_SLEEP = 3, ICON_SCREEN_OFF = 4;

    final int icon;
    final String label;
    /** The small line under the label (the running sleep timer), null for none. */
    public String value;
    private final Runnable onClick;
    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text;
    private final TextPaint small;
    private final RectF r = new RectF();
    private final Path p = new Path();
    private final float discR, iconR;
    private float focus;                         // 0..1, animated
    private ValueAnimator focusAnim;
    private boolean down;
    /** The label on one line, or on two when it is too wide for the tile (broken at a space). */
    private CharSequence[] shownLines;

    public RoundTile(Context c, int icon, String label, Runnable onClick) {
        super(c);
        this.icon = icon;
        this.label = label;
        this.onClick = onClick;
        setFocusable(true);
        setDefaultFocusHighlightEnabled(false);
        setWillNotDraw(false);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeWidth(Theme.pxf(c, 4.5f));
        ring = Theme.stroke(c, 0x24FFFFFF, 2);
        text = Theme.text(c, 28, Theme.TEXT_DIM, false);
        text.setTextAlign(Paint.Align.CENTER);
        small = Theme.text(c, 22, Theme.TEXT_FAINT, false);
        small.setTextAlign(Paint.Align.CENTER);
        discR = Theme.pxf(c, 70);
        iconR = Theme.pxf(c, 28);
        setContentDescription(label);
    }

    @Override protected void onFocusChanged(boolean gain, int dir, Rect prev) {
        super.onFocusChanged(gain, dir, prev);
        if (focusAnim != null) focusAnim.cancel();
        focusAnim = ValueAnimator.ofFloat(focus, gain ? 1f : 0f);
        focusAnim.setDuration(160);
        focusAnim.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        focusAnim.addUpdateListener(a -> { focus = (float) a.getAnimatedValue(); invalidate(); });
        focusAnim.start();
    }

    @Override public boolean onKeyDown(int code, KeyEvent ev) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (ev.getRepeatCount() == 0) down = true;
            return true;
        }
        return super.onKeyDown(code, ev);
    }

    @Override public boolean onKeyUp(int code, KeyEvent ev) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            boolean go = down && !ev.isCanceled();
            down = false;
            if (go) {
                playSoundEffect(android.view.SoundEffectConstants.CLICK);
                onClick.run();
            }
            return true;
        }
        return super.onKeyUp(code, ev);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        buildIcon(w / 2f, Theme.pxf(getContext(), 96), iconR);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth();
        float cx = w / 2f, cy = Theme.pxf(getContext(), 96);
        float f = focus;
        float k = 1f + 0.08f * f;
        // disc: translucent white -> white when focused
        int a = Math.round(0x24 + (0xFF - 0x24) * f);
        disc.setColor((a << 24) | 0xFFFFFF);
        c.drawCircle(cx, cy, discR * k, disc);
        if (f < 1f) {
            ring.setAlpha(Math.round(0x24 * (1f - f)));
            c.drawCircle(cx, cy, discR * k, ring);
        }
        line.setColor(blend(0xE5FFFFFF, 0xE5000000, f));
        c.save();
        c.scale(k, k, cx, cy);
        c.drawPath(p, line);
        c.restore();
        text.setColor(blend(Theme.TEXT_DIM, 0xFFFFFFFF, f));
        float maxW = w - Theme.pxf(getContext(), 8);
        if (shownLines == null) shownLines = lines(label, text, maxW);
        float ty = cy + discR * 1.08f + Theme.pxf(getContext(), 50);
        float lineH = Theme.pxf(getContext(), 36);
        for (int i = 0; i < shownLines.length; i++) {
            c.drawText(shownLines[i], 0, shownLines[i].length(), cx, ty + i * lineH, text);
        }
        if (value != null) {
            small.setColor(blend(Theme.TEXT_FAINT, Theme.TEXT_DIM, f));
            c.drawText(value, cx, ty + (shownLines.length - 1) * lineH + Theme.pxf(getContext(), 36), small);
        }
    }

    /** {@code s} on one line when it fits {@code maxW}, else on two broken at the last space that fits, the rest ellipsized. */
    static CharSequence[] lines(String s, TextPaint p, float maxW) {
        if (p.measureText(s) <= maxW) return new CharSequence[]{s};
        int cut = -1;
        for (int i = s.indexOf(' '); i > 0 && p.measureText(s, 0, i) <= maxW; i = s.indexOf(' ', i + 1)) cut = i;
        if (cut < 0) return new CharSequence[]{TextUtils.ellipsize(s, p, maxW, TextUtils.TruncateAt.END)};
        return new CharSequence[]{s.substring(0, cut),
                TextUtils.ellipsize(s.substring(cut + 1), p, maxW, TextUtils.TruncateAt.END)};
    }

    /** Line icon of radius s around (cx, cy), built once per size. */
    private void buildIcon(float cx, float cy, float s) {
        p.rewind();
        if (icon == ICON_POWER) {                                   // open ring + stem
            r.set(cx - s, cy - s + s * 0.1f, cx + s, cy + s + s * 0.1f);
            p.addArc(r, -55, 290);
            p.moveTo(cx, cy - s * 1.12f);
            p.lineTo(cx, cy - s * 0.12f);
        } else if (icon == ICON_RESTART) {                          // circular arrow, head at the gap
            r.set(cx - s, cy - s, cx + s, cy + s);
            p.addArc(r, -50, 290);
            double th = Math.toRadians(-50);
            float ex = cx + s * (float) Math.cos(th), ey = cy + s * (float) Math.sin(th);
            // travel direction at the start of a clockwise sweep, reversed: the head points back
            // along the tangent (counter-clockwise), like a "reload" glyph
            double dx = Math.sin(th), dy = -Math.cos(th);
            float len = s * 0.5f;
            for (int sign = -1; sign <= 1; sign += 2) {
                double a = Math.toRadians(35 * sign);
                double wx = dx * Math.cos(a) - dy * Math.sin(a), wy = dx * Math.sin(a) + dy * Math.cos(a);
                p.moveTo(ex, ey);
                p.lineTo(ex - (float) (wx * len), ey - (float) (wy * len));
            }
        } else if (icon == ICON_TIMER) {                            // clock: ring + two hands
            p.addCircle(cx, cy, s, Path.Direction.CW);
            p.moveTo(cx, cy - s * 0.62f);
            p.lineTo(cx, cy);
            p.lineTo(cx + s * 0.45f, cy + s * 0.22f);
        } else if (icon == ICON_SCREEN_OFF) {                       // screen with a stroke through it (Turn off screen)
            r.set(cx - s * 1.1f, cy - s * 0.72f, cx + s * 1.1f, cy + s * 0.62f);
            p.addRoundRect(r, s * 0.16f, s * 0.16f, Path.Direction.CW);
            p.moveTo(cx - s * 0.42f, cy + s * 0.98f);
            p.lineTo(cx + s * 0.42f, cy + s * 0.98f);
            p.moveTo(cx - s * 0.78f, cy - s * 1.05f);
            p.lineTo(cx + s * 0.78f, cy + s * 0.95f);
        } else {                                                    // crescent moon (Sleep)
            Path outer = new Path();
            outer.addCircle(cx - s * 0.05f, cy + s * 0.05f, s, Path.Direction.CW);
            Path bite = new Path();
            bite.addCircle(cx + s * 0.5f, cy - s * 0.42f, s * 0.82f, Path.Direction.CW);
            if (!p.op(outer, bite, Path.Op.DIFFERENCE)) p.set(outer);
        }
    }

    static int blend(int c0, int c1, float f) {
        int a = Math.round(((c0 >>> 24) & 0xFF) + (((c1 >>> 24) & 0xFF) - ((c0 >>> 24) & 0xFF)) * f);
        int rr = Math.round(((c0 >> 16) & 0xFF) + (((c1 >> 16) & 0xFF) - ((c0 >> 16) & 0xFF)) * f);
        int g = Math.round(((c0 >> 8) & 0xFF) + (((c1 >> 8) & 0xFF) - ((c0 >> 8) & 0xFF)) * f);
        int b = Math.round((c0 & 0xFF) + ((c1 & 0xFF) - (c0 & 0xFF)) * f);
        return (a << 24) | (rr << 16) | (g << 8) | b;
    }
}
