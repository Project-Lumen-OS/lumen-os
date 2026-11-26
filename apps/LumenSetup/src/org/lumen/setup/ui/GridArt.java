// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.setup.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import org.lumen.ui.Palette;

/**
 * Picture step field: the theme's field with a 120 px grid (text1 at 7 % on the light field,
 * 10 % on the dark one) and 64 px corner marks 24 px from the edges of the projection, 4 px wide, text1 at 85 %.
 */
public class GridArt extends View {
    private final Paint mLine = new Paint();
    private final Paint mMark = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GridArt(Context c) {
        super(c);
        mLine.setColor(Palette.alpha(Ui.TEXT, Ui.P.dark ? 0.10f : 0.07f));
        mLine.setStrokeWidth(Math.max(1f, Ui.pxf(2)));
        mMark.setColor(Palette.alpha(Ui.TEXT, 0.85f));
        mMark.setStrokeWidth(Math.max(2f, Ui.pxf(4)));
        mMark.setStrokeCap(Paint.Cap.SQUARE);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), step = Ui.pxf(120);
        for (float x = 0; x <= w; x += step) c.drawLine(x, 0, x, h, mLine);
        for (float y = 0; y <= h; y += step) c.drawLine(0, y, w, y, mLine);
        float in = Ui.pxf(26), len = Ui.pxf(64);
        c.drawLine(in, in, in + len, in, mMark);
        c.drawLine(in, in, in, in + len, mMark);
        c.drawLine(w - in, in, w - in - len, in, mMark);
        c.drawLine(w - in, in, w - in, in + len, mMark);
        c.drawLine(in, h - in, in + len, h - in, mMark);
        c.drawLine(in, h - in, in, h - in - len, mMark);
        c.drawLine(w - in, h - in, w - in - len, h - in, mMark);
        c.drawLine(w - in, h - in, w - in, h - in - len, mMark);
    }
}
