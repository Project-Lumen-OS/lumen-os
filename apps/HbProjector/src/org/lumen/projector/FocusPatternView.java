package org.lumen.projector;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** HB beta6: draws {@link FocusPattern} full screen (the stock manual-focus chart layout, own drawing). */
public final class FocusPatternView extends View {
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    public FocusPatternView(Context c) {
        super(c);
        ink.setColor(FocusPattern.INK);
        ink.setStyle(Paint.Style.FILL);
        setFocusable(false);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        c.drawColor(FocusPattern.BACKGROUND);
        float sweep = FocusPattern.wedgeSweep();
        for (float[] wh : FocusPattern.wheels(w, h)) {
            oval.set(wh[0] - wh[2], wh[1] - wh[2], wh[0] + wh[2], wh[1] + wh[2]);
            for (int i = 0; i < FocusPattern.SPOKES; i++) c.drawArc(oval, FocusPattern.wedgeStart(i), sweep, true, ink);
        }
        float[] l = FocusPattern.line(w, h);
        c.drawRect(l[0], l[1], l[2], l[3], ink);
    }
}
