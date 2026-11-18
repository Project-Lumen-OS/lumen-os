package org.lumen.setup.ui;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import org.lumen.ui.Motion;
import org.lumen.ui.Palette;
import org.lumen.ui.Tokens;

/**
 * Background of a pill button or a list row: the idle fill (or nothing) at rest, the focus fill with
 * the theme's focus shadow when focused, cross-faded in 160 ms (120 ms out). The shadow draws past the bounds, so the
 * parents of a focusable must not clip it (Ui.vbox / Ui.hbox, TvFocus.unclip).
 */
public final class FocusFill extends Drawable {
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mR = new RectF();
    private final int mIdle, mIdleStroke;
    private final float mRadius;
    private boolean mFocused;
    private float mT;
    private ValueAnimator mAnim;

    public FocusFill(int idle, int idleStroke, float radiusDesign) {
        mIdle = idle;
        mIdleStroke = idleStroke;
        mRadius = Ui.pxf(radiusDesign);
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeWidth(Math.max(1f, Ui.pxf(2)));
    }

    @Override
    public boolean isStateful() { return true; }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean f = false;
        for (int s : state) if (s == android.R.attr.state_focused) f = true;
        if (f == mFocused) return false;
        mFocused = f;
        if (mAnim != null) mAnim.cancel();
        mAnim = ValueAnimator.ofFloat(mT, f ? 1f : 0f);
        mAnim.setDuration(f ? Tokens.MS_FOCUS_IN : Tokens.MS_FOCUS_OUT);
        mAnim.setInterpolator(Motion.EMPHASIZED);
        mAnim.addUpdateListener(a -> {
            mT = (float) a.getAnimatedValue();
            invalidateSelf();
        });
        mAnim.start();
        return true;
    }

    @Override
    public void jumpToCurrentState() {
        if (mAnim != null) mAnim.cancel();
        mT = mFocused ? 1f : 0f;
        invalidateSelf();
    }

    @Override
    public void draw(Canvas c) {
        mR.set(getBounds());
        Palette p = Ui.P;
        if (mT > 0f) {
            mFill.setColor(Palette.alpha(Ui.FOCUS, mT));
            mFill.setShadowLayer(Ui.pxf(p.shadowBlur) / 2f, 0f, Ui.pxf(p.shadowY) / 2f,
                    Palette.alpha(p.shadow, ((p.shadow >>> 24) / 255f) * mT));
            c.drawRoundRect(mR, mRadius, mRadius, mFill);
            mFill.clearShadowLayer();
        }
        if (mT < 1f) {
            if ((mIdle >>> 24) != 0) {
                mFill.setColor(Palette.alpha(mIdle, ((mIdle >>> 24) / 255f) * (1f - mT)));
                c.drawRoundRect(mR, mRadius, mRadius, mFill);
            }
            if ((mIdleStroke >>> 24) != 0) {
                mStroke.setColor(Palette.alpha(mIdleStroke, ((mIdleStroke >>> 24) / 255f) * (1f - mT)));
                float h = mStroke.getStrokeWidth() / 2f;
                mR.inset(h, h);
                c.drawRoundRect(mR, mRadius, mRadius, mStroke);
            }
        }
    }

    @Override
    public void setAlpha(int alpha) {}

    @Override
    public void setColorFilter(ColorFilter cf) {}

    @Override
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
