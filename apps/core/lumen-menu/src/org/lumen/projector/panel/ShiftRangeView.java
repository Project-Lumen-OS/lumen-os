package org.lumen.projector.panel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import org.lumen.menu.ShiftRange;
import org.lumen.projector.ui.Theme;

/**
 * The range picture of the lens shift page, drawn as the stock newsettings DisplacementCell (its 460 design px scene,
 * centred in a 520 px square so the arrows sit outside the travel): the axes with their ticks (horizontal every 18 px, the reachable 72 px bright, the rest at 30 %;
 * vertical every 8 px, the reachable 96 px bright), the picture as a 290 x 166 frame moved by the vendor's position
 * (ShiftRange: x * 72, y * 96) and the four arrows of FourTowardsCell, an arrow at the end of its travel spent (stock
 * tint color_grey_alpha_5). Lumen adds a faint dashed outline of the whole travel, so the end is visible before the
 * frame reaches it (tester 2026-11-14: no shift boundaries shown). Our own drawing, no stock asset.
 */
public final class ShiftRangeView extends View {
    static final float SIZE = 520, REACH_X = 72, REACH_Y = 96, PIC_W = 290, PIC_H = 166;
    static final int AXIS = 0x59FFFFFF, TICK = 0xFFFFFFFF, TICK_DIM = 0x4DFFFFFF, OUTLINE = 0x66FFFFFF,
            ARROW = 0xE6FFFFFF, ARROW_SPENT = 0x40FFFFFF, PIC_FILL = 0x2E3B78E7;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private ShiftRange at = ShiftRange.UNKNOWN;

    public ShiftRangeView(Context c) {
        super(c);
        line.setStyle(Paint.Style.STROKE);
    }

    /** The position read back now; redraws. */
    public void show(ShiftRange now) {
        at = now == null ? ShiftRange.UNKNOWN : now;
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int side = Math.round(Theme.pxf(getContext(), 300));
        setMeasuredDimension(side, side);
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()) / SIZE, m = SIZE / 2 * s;
        line.setPathEffect(null);
        line.setStrokeWidth(Math.max(1f, s * 2));
        line.setColor(AXIS);
        c.drawLine(m - 126 * s, m, m + 126 * s, m, line);
        c.drawLine(m, m - 160 * s, m, m + 160 * s, line);
        for (int i = 1; i <= 7; i++) {                           // stock drawCoordinateX: 18 px, 72 / 18 = 4 bright
            fill.setColor(i <= 4 ? TICK : TICK_DIM);
            c.drawRect(m + (18 * i - 1) * s, m - 11 * s, m + (18 * i + 1) * s, m, fill);
            c.drawRect(m - (18 * i + 1) * s, m - 11 * s, m - (18 * i - 1) * s, m, fill);
        }
        for (int i = 1; i <= 20; i++) {                          // stock drawCoordinateY: 8 px, 100 / 8 = 12 bright
            fill.setColor(i <= 12 ? TICK : TICK_DIM);
            float len = (i == 12 || i == 6 ? 13 : 11) * s;
            c.drawRect(m, m - (8 * i + 1) * s, m + len, m - (8 * i - 1) * s, fill);
            c.drawRect(m, m + (8 * i - 1) * s, m + len, m + (8 * i + 1) * s, fill);
        }
        line.setColor(OUTLINE);
        line.setPathEffect(new DashPathEffect(new float[]{8 * s, 6 * s}, 0));
        r.set(m - (PIC_W / 2 + REACH_X) * s, m - (PIC_H / 2 + REACH_Y) * s, m + (PIC_W / 2 + REACH_X) * s,
                m + (PIC_H / 2 + REACH_Y) * s);
        c.drawRect(r, line);
        line.setPathEffect(null);
        float x = at.known() ? at.x : 0f, y = at.known() ? at.y : 0f;
        float cx = m + x * REACH_X * s, cy = m - y * REACH_Y * s;
        r.set(cx - PIC_W / 2 * s, cy - PIC_H / 2 * s, cx + PIC_W / 2 * s, cy + PIC_H / 2 * s);
        fill.setColor(PIC_FILL);
        c.drawRect(r, fill);
        line.setColor(QpdStyle.ACCENT);
        line.setStrokeWidth(Math.max(1f, s * 4));
        c.drawRect(r, line);
        float d = 240 * s, a = 12 * s;
        arrow(c, m, m - d, 0, -1, a, at.atTop());
        arrow(c, m, m + d, 0, 1, a, at.atBottom());
        arrow(c, m - d, m, -1, 0, a, at.atLeft());
        arrow(c, m + d, m, 1, 0, a, at.atRight());
    }

    private void arrow(Canvas c, float x, float y, int dx, int dy, float a, boolean spent) {
        fill.setColor(spent ? ARROW_SPENT : ARROW);
        path.reset();
        path.moveTo(x + dx * a, y + dy * a);
        path.lineTo(x - dy * a - dx * a * 0.4f, y + dx * a - dy * a * 0.4f);
        path.lineTo(x + dy * a - dx * a * 0.4f, y - dx * a - dy * a * 0.4f);
        path.close();
        c.drawPath(path, fill);
    }
}
