// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.setup.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.LinearInterpolator;

import org.lumen.setup.R;
import org.lumen.ui.Palette;
import org.lumen.ui.Tokens;

/**
 * Live preview of the theme step: the real Lumen Home picture in the light and in the dark theme (the launcher step's
 * res/drawable{,-ru}-nodpi previews), split by a diagonal. {@link #show} moves the split to the focused choice in
 * Tokens.MS_THEME (350 ms, linear here, the theme cross fade time): all light, all dark, or half and half for Авто.
 */
public class ThemePreview extends View {
    private static final float RADIUS = 24f, SLANT = 0.25f;
    private final Bitmap mLight, mDark;
    private final Paint mBmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint mLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mClip = new Path();
    private final RectF mDst = new RectF();
    private float mShare = -1f;
    private ValueAnimator mAnim;

    public ThemePreview(Context c) {
        super(c);
        mLight = BitmapFactory.decodeResource(c.getResources(), R.drawable.setup_home_preview_light);
        mDark = BitmapFactory.decodeResource(c.getResources(), R.drawable.setup_home_preview_dark);
        mLine.setStyle(Paint.Style.STROKE);
        mLine.setStrokeWidth(Math.max(1f, Ui.pxf(2)));
        mLine.setColor(Palette.alpha(Ui.TEXT, 0.35f));
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.pxf(RADIUS));
            }
        });
        setElevation(Ui.pxf(10));
    }

    /** The share of the light picture (ThemeChoice.lightShare); animated unless {@code animate} is false. */
    public void show(float share, boolean animate) {
        if (mAnim != null) mAnim.cancel();
        mAnim = null;
        if (!animate || mShare < 0f || Ui.reduced(getContext())) {
            mShare = share;
            invalidate();
            return;
        }
        mAnim = ValueAnimator.ofFloat(mShare, share);
        mAnim.setDuration(Tokens.MS_THEME);
        mAnim.setInterpolator(new LinearInterpolator());
        mAnim.addUpdateListener(a -> {
            mShare = (float) a.getAnimatedValue();
            invalidate();
        });
        mAnim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mAnim != null) mAnim.cancel();
        mAnim = null;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), s = Math.max(0f, mShare);
        mDst.set(0, 0, w, h);
        if (s < 1f && mDark != null) c.drawBitmap(mDark, null, mDst, mBmp);
        if (s <= 0f || mLight == null) return;
        // the light part: left of a line from (top, 0) to (top - slant, h); at 1 it covers all, at 0.5 half
        float d = SLANT * w, top = s * (w + d), bottom = top - d;
        mClip.rewind();
        mClip.moveTo(0, 0);
        mClip.lineTo(top, 0);
        mClip.lineTo(bottom, h);
        mClip.lineTo(0, h);
        mClip.close();
        int save = c.save();
        c.clipPath(mClip);
        c.drawBitmap(mLight, null, mDst, mBmp);
        c.restoreToCount(save);
        if (s < 1f) c.drawLine(top, 0, bottom, h, mLine);
    }
}
