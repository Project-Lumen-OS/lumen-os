// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.ui.FocusRing;
import org.lumen.ui.Motion;
import org.lumen.ui.RingPaint;
import org.lumen.ui.Tokens;

import java.util.ArrayList;
import java.util.List;

/**
 * Every focusable of Files: a rounded fill (rest, picked, focused), the Lumen focus ring outside it (not in panels),
 * an optional lift on focus, and the colours of its texts and icons per state. Parents keep setClipChildren(false).
 */
public class Box extends LinearLayout {
    private final RingPaint mRing = new RingPaint();
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mR = new RectF();
    private final List<View> mInk = new ArrayList<>();
    private ValueAnimator mAnim;
    private float mFocus;
    int fill, focusFill, pickFill, ink, focusInk;
    float radius = 16, lift = 1f;
    boolean ring = true, picked;

    public Box(Context c) {
        super(c);
        setFocusable(true);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setDefaultFocusHighlightEnabled(false);
    }

    /** Rest fill, focused fill, the ink of texts and icons at rest and focused. */
    public Box look(int fill, int focusFill, int ink, int focusInk) {
        this.fill = fill;
        this.focusFill = focusFill;
        this.ink = ink;
        this.focusInk = focusInk;
        paintInk();
        invalidate();
        return this;
    }

    /** A text or icon that takes the box's ink. */
    public <T extends View> T inked(T v) {
        mInk.add(v);
        paintInk();
        return v;
    }

    public void setPicked(boolean on, int fill) {
        picked = on;
        pickFill = fill;
        invalidate();
    }

    private void paintInk() {
        int c = isFocused() ? focusInk : ink;
        for (View v : mInk) {
            if (v instanceof TextView) ((TextView) v).setTextColor(c);
            else if (v instanceof ImageView) ((ImageView) v).setImageTintList(ColorStateList.valueOf(c));
        }
    }

    @Override
    protected void onFocusChanged(boolean focused, int dir, Rect prev) {
        super.onFocusChanged(focused, dir, prev);
        if (mAnim != null) mAnim.cancel();
        mAnim = ValueAnimator.ofFloat(mFocus, focused ? 1f : 0f);
        mAnim.setDuration(focused ? Tokens.MS_FOCUS_IN : Tokens.MS_FOCUS_OUT);
        mAnim.setInterpolator(Motion.STANDARD);
        mAnim.addUpdateListener(a -> {
            mFocus = (float) a.getAnimatedValue();
            float s = 1f + (lift - 1f) * mFocus;
            setScaleX(s);
            setScaleY(s);
            invalidate();
        });
        mAnim.start();
        paintInk();
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas c) {
        int col = isFocused() ? focusFill : picked ? pickFill : fill;
        if ((col >>> 24) != 0) {
            mFill.setColor(col);
            mR.set(0, 0, getWidth(), getHeight());
            c.drawRoundRect(mR, Ui.pxf(radius), Ui.pxf(radius), mFill);
        }
        super.dispatchDraw(c);
        if (ring) {
            mRing.draw(c, Ui.P.focusRing, 0, 0, getWidth(), getHeight(), Ui.pxf(radius), Ui.scale(), getScaleX(),
                    FocusRing.alpha(isFocused(), mFocus));
        }
    }
}
