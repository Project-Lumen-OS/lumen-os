package org.lumen.home.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.TextUtils;

/** Canvas text of the 2.0 type scale: a paint per style, single lines ellipsized to a width. */
public final class Ink {
    private Ink() {}

    public static TextPaint paint(float sizeDesign, int weight, int color) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(Theme.font(weight, sizeDesign));
        p.setTextSize(Theme.pxf(sizeDesign));
        p.setColor(color);
        return p;
    }

    /** One line at baseline y, cut with an ellipsis to {@code maxW} px. */
    public static void line(Canvas c, CharSequence s, float x, float baseline, float maxW, TextPaint p) {
        if (s == null || s.length() == 0) return;
        CharSequence t = TextUtils.ellipsize(s, p, maxW, TextUtils.TruncateAt.END);
        c.drawText(t, 0, t.length(), x, baseline, p);
    }
}
