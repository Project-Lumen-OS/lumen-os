package org.lumen.home.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.widget.FrameLayout;

import org.lumen.home.model.ArtRule;
import org.lumen.ui.Grid;

/**
 * The Витрина's art (Home A, the approved mock home-v3 A_vitrina): the whole screen behind the shelf, chosen by
 * {@link ArtRule}.
 * <ul>
 *   <li>FULL: the art covers the screen, sharp, anchored at the top; its blurred copy takes over from y 560 to 700
 *   (a progressive blur), a dark scrim from the left keeps the white text readable, a short one at the top keeps the
 *   clock readable, and from y 600 the art fades into the field (78 % at 690 dark, 66 % light, solid at 790).</li>
 *   <li>FRAME: small art (Lumen Play's frame grab) is never enlarged: its blur, desaturated and dimmed, is the field,
 *   and the art itself sits sharp at its size on the right (column 9, 16:9, radius 20) with the progress bar.</li>
 *   <li>NONE: the plain field; the shelf then writes in the theme's ink.</li>
 * </ul>
 * New art cross fades over the old in 400 ms. The Apps page lays the field over it ({@link #setVeil}).
 */
public class BackdropView extends FrameLayout {
    /** The left scrim: rgba(8, 9, 11) from 80 % to nothing at 70 % of the width (FRAME: 62 % to 20 %). */
    private static final int SCRIM = 0x08090B;
    private static final int[] SCRIM_FULL = { a(0.80f), a(0.55f), a(0.12f), a(0f) };
    private static final float[] SCRIM_FULL_AT = { 0f, 0.30f, 0.56f, 0.70f };
    private static final int[] SCRIM_FRAME = { a(0.62f), a(0.35f), a(0.20f) };
    private static final float[] SCRIM_FRAME_AT = { 0f, 0.45f, 1f };
    private static final int TOP_SCRIM = 0x6B000000, TOP_SCRIM_H = 200;
    private static final float FADE_MID_DARK = 0.78f, FADE_MID_LIGHT = 0.66f;
    private static final float FRAME_BLUR_ZOOM = 1.15f, FRAME_SATURATION = 0.65f, FRAME_BRIGHTNESS = 0.85f;
    private static final int STILL_R = 20, BAR_INSET = 20, BAR_H = 6;

    private final Face[] mFace = new Face[2];
    private int mFront;
    private float mVeil;
    private int mStillY = Grid.STILL_Y;
    private ValueAnimator mVeilAnim;
    private final Paint mVeilPaint = new Paint();

    public BackdropView(Context c) {
        super(c);
        setWillNotDraw(false);
        for (int i = 0; i < 2; i++) {
            mFace[i] = new Face(c);
            mFace[i].setAlpha(i == 0 ? 1f : 0f);
            addView(mFace[i], new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
    }

    private static int a(float alpha) {
        return Math.round(alpha * 255f) << 24 | SCRIM;
    }

    /** The mode on screen (the newest art): the shelf picks its ink from it. */
    public int mode() {
        return mFace[mFront].mMode;
    }

    /**
     * New art. FULL: {@code sharp} is the art (any size, covers the screen); FRAME: {@code sharp} is shown at
     * {@code frameW} x {@code frameH} real px (never above its own pixels); {@code blur} is the small blurred copy.
     */
    public void set(int mode, Bitmap sharp, Bitmap blur, int frameW, int frameH, int progress, boolean animate) {
        Face front = mFace[mFront], back = mFace[1 - mFront];
        if (front.same(mode, sharp, blur, frameW, frameH)) {
            front.setProgress(progress);
            return;
        }
        front.animate().cancel();
        back.animate().cancel();
        if (!animate || !Theme.animations()) {
            front.set(mode, sharp, blur, frameW, frameH, progress);
            front.setAlpha(1f);
            back.setAlpha(0f);
            back.set(ArtRule.NONE, null, null, 0, 0, -1);
            return;
        }
        back.set(mode, sharp, blur, frameW, frameH, progress);
        back.bringToFront();
        back.setAlpha(0f);
        back.animate().alpha(1f).setDuration(Theme.BACKDROP_MS).setInterpolator(Theme.LINEAR).withLayer()
                .withEndAction(() -> front.setAlpha(0f)).start();
        mFront = 1 - mFront;
    }

    /** Top of the sharp frame (design px): lower with the hero buttons, higher above a Continue row. */
    public void setStillY(int designY) {
        if (designY == mStillY) return;
        mStillY = designY;
        for (Face f : mFace) {
            f.mStillY = designY;
            f.invalidate();
        }
    }

    /** The blurred copy arrived after the sharp art: it joins the face on screen without a new fade. */
    public void setBlur(Bitmap sharp, Bitmap blur) {
        Face f = mFace[mFront];
        if (f.mSharp == sharp && f.mBlur != blur && blur != null) {
            f.mBlur = blur;
            f.invalidate();
        }
    }

    public void setProgress(int permille) {
        mFace[mFront].setProgress(permille);
    }

    /** The theme changed: the fade and the field follow. */
    public void restyle() {
        for (Face f : mFace) f.invalidate();
        invalidate();
    }

    /** The field over the art (0: none, 1: the Apps page), in 280 ms or at once. */
    public void setVeil(float to, boolean animate) {
        if (mVeilAnim != null) mVeilAnim.cancel();
        if (!animate || !Theme.animations()) {
            mVeil = to;
            invalidate();
            return;
        }
        mVeilAnim = ValueAnimator.ofFloat(mVeil, to);
        mVeilAnim.setDuration(Theme.PAGE_SCROLL_MS);
        mVeilAnim.setInterpolator(Theme.DECEL);
        mVeilAnim.addUpdateListener(v -> {
            mVeil = (Float) v.getAnimatedValue();
            invalidate();
        });
        mVeilAnim.start();
    }

    @Override
    protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
        if (mVeil > 0f) {
            mVeilPaint.setColor(Theme.alpha(Theme.BG, 0.92f * mVeil));
            c.drawRect(0, 0, getWidth(), getHeight(), mVeilPaint);
        }
    }

    /** One face: the art in its mode, the scrims and the fade into the field. */
    private static final class Face extends View {
        private int mMode = ArtRule.NONE, mFrameW, mFrameH, mProgress = -1, mStillY = Grid.STILL_Y;
        private Bitmap mSharp, mBlur;
        private final Matrix mM = new Matrix();
        private final Paint mP = new Paint(Paint.ANTI_ALIAS_FLAG);
        /** Bitmaps only: never carries the alpha or shader of a gradient drawn with mP. */
        private final Paint mBmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint mDim = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint mMask = new Paint();
        private final RectF mR = new RectF();
        private final Path mClip = new Path();

        Face(Context c) {
            super(c);
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(FRAME_SATURATION);
            ColorMatrix dim = new ColorMatrix();
            dim.setScale(FRAME_BRIGHTNESS, FRAME_BRIGHTNESS, FRAME_BRIGHTNESS, 1f);
            cm.postConcat(dim);
            mDim.setColorFilter(new ColorMatrixColorFilter(cm));
            mMask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        }

        boolean same(int mode, Bitmap sharp, Bitmap blur, int fw, int fh) {
            return mode == mMode && sharp == mSharp && blur == mBlur && fw == mFrameW && fh == mFrameH;
        }

        void set(int mode, Bitmap sharp, Bitmap blur, int fw, int fh, int progress) {
            mMode = sharp == null && blur == null ? ArtRule.NONE : mode;
            mSharp = sharp;
            mBlur = blur;
            mFrameW = fw;
            mFrameH = fh;
            mProgress = progress;
            invalidate();
        }

        void setProgress(int p) {
            if (p == mProgress) return;
            mProgress = p;
            if (mMode == ArtRule.FRAME) invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            mP.setAlpha(255);   // the frame's hairline leaves mP at 12 %: every gradient here is opaque
            c.drawColor(Theme.BG);
            if (mMode == ArtRule.NONE) return;
            if (mMode == ArtRule.FULL) {
                if (mSharp != null) cover(c, mSharp, w, h, 1f, false, mBmp);
                if (mBlur != null) {
                    float y0 = Theme.pxf(Grid.ART_BLUR_FROM), y1 = Theme.pxf(Grid.ART_BLUR_TO);
                    int sc = c.saveLayer(0, y0, w, h, null);
                    cover(c, mBlur, w, h, 1f, false, mBmp);
                    mMask.setShader(new LinearGradient(0, y0, 0, y1, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
                    c.drawRect(0, y0, w, h, mMask);
                    mMask.setShader(null);
                    c.restoreToCount(sc);
                }
                scrim(c, w, SCRIM_FULL, SCRIM_FULL_AT, h);
            } else {
                Bitmap field = mBlur != null ? mBlur : mSharp;
                if (field != null) cover(c, field, w, h, FRAME_BLUR_ZOOM, true, mDim);
                scrim(c, w, SCRIM_FRAME, SCRIM_FRAME_AT, h);
            }
            mP.setShader(new LinearGradient(0, 0, 0, Theme.pxf(TOP_SCRIM_H), TOP_SCRIM, 0, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, Theme.pxf(TOP_SCRIM_H), mP);
            fade(c, w, h);
            if (mMode == ArtRule.FRAME && mSharp != null) still(c);
        }

        /** The bitmap covering w x h, centred across, from the top (centred both ways when {@code centre}). */
        private void cover(Canvas c, Bitmap b, float w, float h, float zoom, boolean centre, Paint p) {
            float bw = b.getWidth(), bh = b.getHeight(), s = Math.max(w / bw, h / bh) * zoom;
            mM.setScale(s, s);
            mM.postTranslate((w - bw * s) / 2f, centre ? (h - bh * s) / 2f : 0f);
            c.drawBitmap(b, mM, p);
        }

        private void scrim(Canvas c, float w, int[] colors, float[] at, float h) {
            mP.setShader(new LinearGradient(0, 0, w, 0, colors, at, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, mP);
            mP.setShader(null);
        }

        /** From y 600 the art fades into the field: 78 % (66 % light) at 690, solid from 790. */
        private void fade(Canvas c, float w, float h) {
            float y0 = Theme.pxf(Grid.ART_FADE_FROM), y2 = Theme.pxf(Grid.ART_FADE_TO);
            float mid = (Grid.ART_FADE_MID - Grid.ART_FADE_FROM) / (float) (Grid.ART_FADE_TO - Grid.ART_FADE_FROM);
            int bg = Theme.BG;
            int[] col = { Theme.alpha(bg, 0f), Theme.alpha(bg, Theme.dark() ? FADE_MID_DARK : FADE_MID_LIGHT), bg };
            mP.setShader(new LinearGradient(0, y0, 0, y2, col, new float[] { 0f, mid, 1f }, Shader.TileMode.CLAMP));
            c.drawRect(0, y0, w, h, mP);
            mP.setShader(null);
        }

        /** The sharp frame: right edge on column 12, centred in the 16:9 box at y 320, shadow, hairline, bar. */
        private void still(Canvas c) {
            float boxW = Theme.pxf(Grid.span(Grid.STILL_COLS)), boxH = Math.round(boxW * 9f / 16f);
            float right = Theme.pxf(Grid.x(Grid.STILL_COL) + Grid.span(Grid.STILL_COLS));
            float fw = mFrameW > 0 ? mFrameW : boxW, fh = mFrameH > 0 ? mFrameH : boxH;
            float x = right - fw, y = Theme.pxf(mStillY) + (boxH - fh) / 2f, r = Theme.pxf(STILL_R);
            mR.set(x, y, x + fw, y + fh);
            mP.setColor(0xFF000000);
            mP.setShadowLayer(Theme.pxf(35), 0, Theme.pxf(30), 0x73000000);
            c.drawRoundRect(mR, r, r, mP);
            mP.clearShadowLayer();
            mClip.reset();
            mClip.addRoundRect(mR, r, r, Path.Direction.CW);
            c.save();
            c.clipPath(mClip);
            float bw = mSharp.getWidth(), bh = mSharp.getHeight(), s = Math.max(fw / bw, fh / bh);
            mM.setScale(s, s);
            mM.postTranslate(x + (fw - bw * s) / 2f, y + (fh - bh * s) / 2f);
            c.drawBitmap(mSharp, mM, mBmp);
            if (mProgress >= 0) {
                float l = Theme.pxf(BAR_INSET), bh2 = Theme.pxf(BAR_H), by = y + fh - l - bh2, rr = bh2 / 2;
                mP.setColor(0x47FFFFFF);
                mR.set(x + l, by, x + fw - l, by + bh2);
                c.drawRoundRect(mR, rr, rr, mP);
                mP.setColor(0xFFFFFFFF);
                mR.set(x + l, by, x + l + (fw - 2 * l) * mProgress / 1000f, by + bh2);
                c.drawRoundRect(mR, rr, rr, mP);
            }
            c.restore();
            mP.setStyle(Paint.Style.STROKE);
            mP.setStrokeWidth(Math.max(1f, Theme.pxf(1)));
            mP.setColor(0x1FFFFFFF);
            mR.set(x, y, x + fw, y + fh);
            c.drawRoundRect(mR, r, r, mP);
            mP.setStyle(Paint.Style.FILL);
            mP.setAlpha(255);
        }
    }
}
