// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
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

    /**
     * A tile label at baseline y in {@code maxW} px: smaller first (LabelFit), cut only below its smallest size. Returns
     * the width drawn.
     */
    public static float fitLine(Canvas c, CharSequence s, float x, float baseline, float maxW, TextPaint p) {
        if (s == null || s.length() == 0) return 0f;
        float size = p.getTextSize(), w = p.measureText(s, 0, s.length());
        p.setTextSize(size * LabelFit.scale(w, maxW));
        CharSequence t = TextUtils.ellipsize(s, p, maxW, TextUtils.TruncateAt.END);
        float drawn = Math.min(maxW, p.measureText(t, 0, t.length()));
        c.drawText(t, 0, t.length(), x, baseline, p);
        p.setTextSize(size);
        return drawn;
    }

    /** The width a label takes at its fitted size (for centring it). */
    public static float fitWidth(CharSequence s, float maxW, TextPaint p) {
        if (s == null || s.length() == 0) return 0f;
        float w = p.measureText(s, 0, s.length());
        return Math.min(maxW, w * LabelFit.scale(w, maxW));
    }

    /** One line at baseline y, cut with an ellipsis to {@code maxW} px. */
    public static void line(Canvas c, CharSequence s, float x, float baseline, float maxW, TextPaint p) {
        if (s == null || s.length() == 0) return;
        CharSequence t = TextUtils.ellipsize(s, p, maxW, TextUtils.TruncateAt.END);
        c.drawText(t, 0, t.length(), x, baseline, p);
    }
}
