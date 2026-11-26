// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;

/**
 * The picture adjustment strip of every model (stock newsettings: a picture setting chosen on its page hides the big
 * menu, the picture comes back at full brightness and a small three row panel at the bottom centre adjusts it). The
 * middle row is the setting being changed (its title, its value, a slider's track or a choice's arrows), the rows above
 * and below are the page's neighbours that UP / DOWN reach. Drawing only: the menu hands in the three rows
 * ({@link #show}) and the row motion ({@link #setShift}); the keys stay with the menu's own rows.
 */
public final class AdjustStrip extends View {
    /** One row as the strip shows it. */
    public static final class Item {
        final String title, value;
        final int min, max, val;
        final boolean slider, enabled;

        /** A slider row: its range and value ({@code known} false: the value is not read yet). */
        public static Item slider(CharSequence title, int min, int max, int val, boolean known, CharSequence text, boolean enabled) {
            return new Item(title, known ? text : null, min, max, val, true, enabled && known);
        }

        /** A choice row: the option shown now (null: not read). */
        public static Item choice(CharSequence title, CharSequence value, boolean enabled) {
            return new Item(title, value, 0, 0, 0, false, enabled && value != null);
        }

        private Item(CharSequence title, CharSequence value, int min, int max, int val, boolean slider, boolean enabled) {
            this.title = title == null ? "" : title.toString();
            this.value = value == null ? UNKNOWN : value.toString();
            this.min = min;
            this.max = Math.max(min, max);
            this.val = Math.max(min, Math.min(this.max, val));
            this.slider = slider;
            this.enabled = enabled;
        }

        /** The track fill 0..1 of a slider. */
        float fraction() { return max == min ? 0f : (val - min) / (float) (max - min); }

        @Override public String toString() { return title + " = " + value + (enabled ? "" : " (closed)"); }
    }

    /** Design px at 1920 x 1080: the strip's width, the rows, the padding, the corner. */
    static final int W = 880, ROW_H = 64, CUR_H = 116, PAD = 18, RADIUS = 34, BOTTOM = 56;
    static final int H = 2 * PAD + 2 * ROW_H + CUR_H;
    private static final String UNKNOWN = "\u2026";

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF box = new RectF();
    private final TextPaint title, value, side, sideValue;
    private Item prev, cur, next;
    private float shift;

    public AdjustStrip(Context c) {
        super(c);
        title = QpdStyle.text(c, 30, 600, QpdStyle.INK);
        value = QpdStyle.text(c, 30, 600, QpdStyle.ACCENT);
        side = QpdStyle.text(c, 24, 400, QpdStyle.MUTE);
        sideValue = QpdStyle.text(c, 24, 400, QpdStyle.MUTE);
    }

    /** The three rows (null: none on that side); redraws. */
    public void show(Item prev, Item cur, Item next) {
        this.prev = prev;
        this.cur = cur;
        this.next = next;
        invalidate();
    }

    /** The row motion after UP (from -1) or DOWN (from 1) towards 0: the rows slide in from that side. */
    public void setShift(float s) {
        shift = Math.max(-1f, Math.min(1f, s));
        invalidate();
    }

    Item current() { return cur; }

    @Override
    protected void onDraw(Canvas c) {
        float k = getWidth() / (float) W, w = getWidth(), h = getHeight();
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(QpdStyle.PANEL);
        box.set(0, 0, w, h);
        c.drawRoundRect(box, RADIUS * k, RADIUS * k, fill);
        float dy = shift * ROW_H * k;
        int save = c.save();
        c.translate(0, dy);
        float top = PAD * k;
        side(c, prev, top, k, w);
        float curTop = top + ROW_H * k;
        fill.setColor(alpha(QpdStyle.ACCENT, 0x2E));
        box.set(PAD * k, curTop, w - PAD * k, curTop + CUR_H * k);
        c.drawRoundRect(box, (RADIUS - PAD / 2f) * k, (RADIUS - PAD / 2f) * k, fill);
        current(c, cur, curTop, k, w);
        side(c, next, curTop + CUR_H * k, k, w);
        c.restoreToCount(save);
    }

    /** A neighbour row: title left, value right, muted (dimmer when closed). */
    private void side(Canvas c, Item it, float top, float k, float w) {
        if (it == null) return;
        float base = top + ROW_H * k / 2f - (side.ascent() + side.descent()) / 2f;
        int col = it.enabled ? QpdStyle.MUTE : alpha(QpdStyle.MUTE, 0x50);
        side.setColor(col);
        sideValue.setColor(col);
        float vw = sideValue.measureText(it.value);
        c.drawText(fit(it.title, side, w - 2 * (PAD + 28) * k - vw - 24 * k), (PAD + 28) * k, base, side);
        c.drawText(it.value, w - (PAD + 28) * k - vw, base, sideValue);
    }

    /** The row being changed: title, value, and a slider's track or a choice's arrows. */
    private void current(Canvas c, Item it, float top, float k, float w) {
        if (it == null) return;
        int ink = it.enabled ? QpdStyle.INK : alpha(QpdStyle.INK, 0x66);
        int acc = it.enabled ? QpdStyle.ACCENT : alpha(QpdStyle.INK, 0x66);
        title.setColor(ink);
        value.setColor(acc);
        float l = (PAD + 28) * k, r = w - (PAD + 28) * k;
        float base = it.slider ? top + 22 * k - title.ascent() : top + CUR_H * k / 2f - (title.ascent() + title.descent()) / 2f;
        float vw = value.measureText(it.value);
        if (it.slider) {
            c.drawText(it.value, r - vw, base, value);
            c.drawText(fit(it.title, title, r - l - vw - 24 * k), l, base, title);
            float ty = top + 84 * k, th = 6 * k;
            fill.setColor(alpha(QpdStyle.INK, 0x33));
            box.set(l, ty - th / 2, r, ty + th / 2);
            c.drawRoundRect(box, th / 2, th / 2, fill);
            float x = l + (r - l) * it.fraction();
            fill.setColor(acc);
            box.set(l, ty - th / 2, x, ty + th / 2);
            c.drawRoundRect(box, th / 2, th / 2, fill);
            c.drawCircle(x, ty, 12 * k, fill);
        } else {
            float a = 10 * k, gap = 18 * k;
            float vx = r - vw - a - gap;
            c.drawText(it.value, vx, base, value);
            c.drawText(fit(it.title, title, vx - l - 2 * a - 2 * gap), l, base, title);
            float cy = base + (title.ascent() + title.descent()) / 2f;
            fill.setColor(acc);
            arrow(c, vx - gap, cy, -1, a);
            arrow(c, r - a / 2f, cy, 1, a);
        }
    }

    /** A small filled triangle pointing left (-1) or right (1) with its tip at x. */
    private void arrow(Canvas c, float x, float cy, int dir, float a) {
        path.reset();
        path.moveTo(x, cy);
        path.lineTo(x - dir * a, cy - a);
        path.lineTo(x - dir * a, cy + a);
        path.close();
        c.drawPath(path, fill);
    }

    /** {@code s} cut with an ellipsis to {@code room} px. */
    private static String fit(String s, TextPaint p, float room) {
        if (p.measureText(s) <= room) return s;
        String e = "\u2026";
        int n = s.length();
        while (n > 0 && p.measureText(s.substring(0, n) + e) > room) n--;
        return s.substring(0, n) + e;
    }

    private static int alpha(int color, int a) { return (color & 0x00FFFFFF) | (a << 24); }
}
