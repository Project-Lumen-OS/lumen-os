// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Draws the focus ring ({@link Palette#focusRing} of the theme on screen) around a rounded box, following its corner radius, outside the box:
 * every parent keeps setClipChildren(false). One instance per view (it keeps its paint).
 */
public final class RingPaint {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mR = new RectF();

    public RingPaint() {
        mPaint.setStyle(Paint.Style.STROKE);
    }

    /**
     * The ring in {@code color} around l t r b with corner {@code radius}; {@code px} = screen px per design px, {@code scale} = the
     * scale the view is drawn at (the ring keeps its width on screen), {@code alpha} from {@link FocusRing#alpha}.
     */
    public void draw(Canvas c, int color, float l, float t, float r, float b, float radius, float px, float scale, float alpha) {
        if (alpha <= 0f) return;
        float o = FocusRing.outset(Tokens.FOCUS_RING_W, Tokens.FOCUS_RING_GAP, px, scale);
        mPaint.setStrokeWidth(FocusRing.width(Tokens.FOCUS_RING_W, px, scale));
        mPaint.setColor(Palette.alpha(color, alpha));
        mR.set(l - o, t - o, r + o, b + o);
        c.drawRoundRect(mR, radius + o, radius + o, mPaint);
    }
}
