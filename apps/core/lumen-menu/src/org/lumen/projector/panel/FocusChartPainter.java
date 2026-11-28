// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import org.lumen.menu.FocusChart;

/**
 * Paints {@link FocusChart} on a canvas: the page, a star in every corner (one quarter pattern mirrored into each
 * corner, as the stock drawable), the line across and the centre dial with its two turning rings. Shared by the manual
 * focus screens (FocusChartView) and the test chart of the geometry screens (EdgeFrameView, the keystone layer).
 */
public final class FocusChartPainter {
    private final String drawn;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private float turned;

    /** drawn: FocusChart.FULL or SHORT (FocusChart.drawn of the model's style). */
    public FocusChartPainter(String drawn) {
        this.drawn = drawn;
        stroke.setStyle(Paint.Style.STROKE);
    }

    /** One lens step: the outer ring turns with it (dir +1 or -1), the inner one against it. */
    public void turn(int dir) { turned += dir * FocusChart.TURN_STEP; }

    /** The chart over a w x h window (the 1920 x 1080 design scaled to it). */
    public void draw(Canvas c, int w, int h) {
        if (w <= 0 || h <= 0) return;
        float sx = w / FocusChart.DESIGN_W, sy = h / FocusChart.DESIGN_H, s = Math.min(sx, sy);
        int page = FocusChart.page(drawn);
        if (page != FocusChart.CLEAR) c.drawColor(page);
        for (int corner = 0; corner < 4; corner++) {
            c.save();
            c.translate((corner & 1) == 0 ? 0 : w, corner < 2 ? 0 : h);
            c.scale((corner & 1) == 0 ? s : -s, corner < 2 ? s : -s);
            drawStar(c);
            c.restore();
        }
        float[] l = FocusChart.line(drawn);
        if (l != null) {
            fill.setShader(null);
            fill.setColor(FocusChart.BLACK);
            c.drawRect(Math.round(l[0] * sx), Math.round(l[1] * sy), Math.round(l[2] * sx), Math.round(l[3] * sy), fill);
        }
        c.save();
        c.translate(w / 2f, h / 2f);
        c.scale(s, s);
        drawDial(c);
        c.restore();
    }

    /** One corner star in design px, the corner at 0,0 and the picture towards +x / +y. */
    private void drawStar(Canvas c) {
        fill.setShader(null);
        for (float[] r : FocusChart.rings(drawn)) {
            int ink = r[2] == 1 ? FocusChart.BLACK : FocusChart.WHITE;
            if (r[0] <= 0) {
                fill.setColor(ink);
                c.drawCircle(0, 0, r[1], fill);
            } else {
                ring(c, r[0], r[1], ink);
            }
            fill.setColor(r[2] == 1 ? FocusChart.WHITE : FocusChart.BLACK);
            for (float a : FocusChart.barAngles(r)) {
                c.save();
                c.rotate(a);
                c.drawRect(r[0], -r[3] / 2f, r[1], r[3] / 2f, fill);
                c.restore();
            }
        }
    }

    private void ring(Canvas c, float r0, float r1, int colour) {
        stroke.setShader(null);
        stroke.setStrokeCap(Paint.Cap.BUTT);
        stroke.setColor(colour);
        stroke.setStrokeWidth(r1 - r0);
        c.drawCircle(0, 0, (r0 + r1) / 2f, stroke);
    }

    /** The centre dial in design px around 0,0. */
    private void drawDial(Canvas c) {
        float[] r = FocusChart.DISC_R;
        float[] stops = new float[r.length];
        int[] colours = new int[r.length];
        for (int i = 0; i < r.length; i++) {
            stops[i] = r[i] / r[r.length - 1];
            colours[i] = FocusChart.DISC_ALPHA[i] << 24;
        }
        fill.setShader(new RadialGradient(0, 0, r[r.length - 1], colours, stops, Shader.TileMode.CLAMP));
        c.drawCircle(0, 0, r[r.length - 1], fill);
        fill.setShader(null);
        arcs(c, FocusChart.GREY_R0, FocusChart.GREY_R1, FocusChart.GREY, FocusChart.GREY_FIRST, FocusChart.GREY_SWEEP,
                FocusChart.GREY_STEP, Paint.Cap.BUTT);
        arcs(c, FocusChart.TICK_R0, FocusChart.TICK_R1, FocusChart.WHITE, FocusChart.TICK_FIRST, FocusChart.TICK_SWEEP,
                FocusChart.TICK_STEP, Paint.Cap.BUTT);
        stroke.setColor(FocusChart.WHITE);
        stroke.setStrokeWidth(FocusChart.CROSS_W);
        stroke.setStrokeCap(Paint.Cap.BUTT);
        c.drawCircle(0, 0, FocusChart.CROSS_R, stroke);
        c.drawLine(0, -FocusChart.MARK_R1, 0, -FocusChart.MARK_R0, stroke);
        c.drawLine(0, FocusChart.MARK_R0, 0, FocusChart.MARK_R1, stroke);
        float hw = FocusChart.OUTER_W / 2f, hi = FocusChart.INNER_W / 2f;
        arcs(c, FocusChart.OUTER_R - hw, FocusChart.OUTER_R + hw, FocusChart.WHITE, FocusChart.OUTER_FIRST + turned,
                FocusChart.OUTER_SWEEP, FocusChart.OUTER_STEP, Paint.Cap.ROUND);
        arcs(c, FocusChart.INNER_R - hi, FocusChart.INNER_R + hi, FocusChart.INNER, FocusChart.INNER_FIRST - turned,
                FocusChart.INNER_SWEEP, FocusChart.INNER_STEP, Paint.Cap.BUTT);
    }

    /** Arcs of one ring between r0 and r1, every step degrees from first round the circle. */
    private void arcs(Canvas c, float r0, float r1, int colour, float first, float sweep, float step, Paint.Cap cap) {
        float mid = (r0 + r1) / 2f;
        stroke.setColor(colour);
        stroke.setStrokeWidth(r1 - r0);
        stroke.setStrokeCap(cap);
        oval.set(-mid, -mid, mid, mid);
        for (float a = 0; a < 359.999f; a += step) c.drawArc(oval, first + a, sweep, false, stroke);
    }
}
