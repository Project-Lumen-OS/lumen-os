package org.lumen.setup.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import org.lumen.ui.Tokens;

/**
 * Background of a tile, a card preview or an input field: the fill sits {@link #INSET} design px
 * inside the bounds, and when focused a 4 px gap in the field colour and a 4 px ring go around it in that margin. An
 * error adds a 3 px error line right at the fill first, then the gap and the ring move out. Nothing draws past the
 * bounds, so no parent clip can cut the ring.
 */
public final class Ring extends Drawable {
    /** Design px between the bounds and the fill: error line + gap + ring. */
    public static final int INSET = 3 + Tokens.RING_GAP + Tokens.RING_W;

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mR = new RectF();
    private int mColor;
    private final int mHairline;
    private final float mRadius;
    private boolean mFocused, mError;

    public Ring(int fill, int hairline, float radiusDesign) {
        mColor = fill;
        mHairline = hairline;
        mRadius = Ui.pxf(radiusDesign);
        mLine.setStyle(Paint.Style.STROKE);
    }

    public void setError(boolean e) {
        if (mError == e) return;
        mError = e;
        invalidateSelf();
    }

    public void setFill(int c) {
        mColor = c;
        invalidateSelf();
    }

    @Override
    public boolean isStateful() { return true; }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean f = false;
        for (int s : state) if (s == android.R.attr.state_focused) f = true;
        if (f == mFocused) return false;
        mFocused = f;
        invalidateSelf();
        return true;
    }

    @Override
    public void draw(Canvas c) {
        mR.set(getBounds());
        float in = Ui.pxf(INSET);
        mR.inset(in, in);
        mFill.setColor(mColor);
        c.drawRoundRect(mR, mRadius, mRadius, mFill);
        if ((mHairline >>> 24) != 0 && !mFocused && !mError) {
            stroke(c, mHairline, Math.max(1f, Ui.pxf(1)), 0f);
        }
        float out = 0f;
        if (mError) {
            stroke(c, Ui.ERROR, Ui.pxf(3), 0f);
            out = Ui.pxf(3);
        }
        if (mFocused) {
            stroke(c, Ui.RING_GAP, Ui.pxf(Tokens.RING_GAP), out);
            stroke(c, Ui.RING, Ui.pxf(Tokens.RING_W), out + Ui.pxf(Tokens.RING_GAP));
        }
    }

    /** A line of width w whose inner edge sits {@code from} px outside the fill. */
    private void stroke(Canvas c, int color, float w, float from) {
        mLine.setColor(color);
        mLine.setStrokeWidth(w);
        RectF r = new RectF(mR);
        float o = from + w / 2f;
        r.inset(-o, -o);
        float rad = mRadius + o;
        c.drawRoundRect(r, rad, rad, mLine);
    }

    @Override
    public void setAlpha(int alpha) {}

    @Override
    public void setColorFilter(ColorFilter cf) {}

    @Override
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
