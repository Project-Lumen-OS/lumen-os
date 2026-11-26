// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import org.lumen.ui.FocusRing;
import org.lumen.ui.Grid;
import org.lumen.ui.Motion;
import org.lumen.ui.Palette;
import org.lumen.ui.RingPaint;
import org.lumen.ui.Tokens;

/**
 * One focusable item of a Home row: a "Continue" card (art, app badge, ONE progress bar or a
 * "Next episode" tag), a "Popular" card (caption under it while focused), an app banner (hairline, never cropped
 * text), an input tile (icon, name, state). Focus: lift to 1.07 / 1.08 / 1.06 in 160 ms, the theme's shadow; 120 ms
 * back. Every kind wears the accent focus ring ({@link FocusRing}) around its face while focused. Ring, shadow and
 * caption are drawn outside the bounds: every parent keeps setClipChildren(false).
 *
 * <p>The Витрина (Home A) kinds: DOCK, an app tile of the dock (248 x 152, radius 22) and UPNEXT, a Continue card of the
 * shelf (248 x 140, radius 18), lift their face to 1.12 and 6 px up with a deep shadow and a sheen, the dock tile
 * shows its name 26 under it; CHIP, an input on the bottom line (392 x 72 pill: icon, port, device), and ROUND, a
 * round button (72), grow to 1.06 and turn white. Everything in 260 ms, ease-out cubic.
 */
public class TileView extends FrameLayout {
    public static final int CARD = 0, BANNER = 1, INPUT = 2, REC = 3, DOCK = 4, UPNEXT = 5, CHIP = 6, ROUND = 7;
    private static final float LIFT = 6, LIFT_SCALE = 1.12f, PILL_SCALE = 1.06f;

    public final int kind;
    private final int mW, mH, mRadius;
    private final float mScale;
    private final Art mArt;
    private final Paint mShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RingPaint mRing = new RingPaint();
    private final RectF mR = new RectF();
    private float mFocus;           // 0..1, animated
    private boolean mFocused, mMoving;
    private CharSequence mTitle = "", mSub = "", mTagText = "";
    private Bitmap mBitmap, mBadge;
    private int mProgress = -1, mPlaceholder = 0xFF2A2F38;
    private boolean mTintIcon = true;
    private Drawable mIcon;
    private boolean mOff;
    private android.animation.ValueAnimator mAnim;

    public TileView(Context c, int kind) {
        this(c, kind, 0);
    }

    /** {@code width}: the design width of a CHIP on a bottom line of four (Vitrina.chipW), 0 = the kind's own. */
    public TileView(Context c, int kind, int width) {
        super(c);
        this.kind = kind;
        switch (kind) {
            case BANNER:
                mW = Tokens.BANNER_W; mH = Tokens.BANNER_H; mRadius = Tokens.R_BANNER; mScale = Tokens.SCALE_BANNER;
                break;
            case INPUT:
                mW = Tokens.INPUT_W; mH = Tokens.INPUT_H; mRadius = Tokens.R_TILE; mScale = Tokens.SCALE_TILE;
                break;
            case DOCK:
                mW = Grid.DOCK_W; mH = Grid.DOCK_H; mRadius = 22; mScale = LIFT_SCALE;
                break;
            case UPNEXT:
                mW = Grid.UPNEXT_W; mH = Grid.UPNEXT_H; mRadius = 18; mScale = LIFT_SCALE;
                break;
            case CHIP:
                mW = width > 0 ? width : Grid.CHIP_W; mH = Grid.CHIP_H; mRadius = Grid.CHIP_H / 2; mScale = PILL_SCALE;
                break;
            case ROUND:
                mW = Grid.ROUND; mH = Grid.ROUND; mRadius = Grid.ROUND / 2; mScale = PILL_SCALE;
                break;
            default:
                mW = Tokens.CARD_W; mH = Tokens.CARD_H; mRadius = Tokens.R_CARD; mScale = Tokens.SCALE_CARD;
        }
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        mArt = new Art(c);
        addView(mArt, new LayoutParams(Theme.px(mW), Theme.px(mH)));
        setClickable(true);
        setFocusable(false);
    }

    public int designW() {
        return mW;
    }

    public int designH() {
        return mH;
    }

    @Override
    protected void onMeasure(int wms, int hms) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(Theme.px(mW), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Theme.px(mH), MeasureSpec.EXACTLY));
    }

    // ------------------------------------------------------------------ content

    public void setTitle(CharSequence title, CharSequence sub) {
        mTitle = title == null ? "" : title;
        mSub = sub == null ? "" : sub;
        mArt.invalidate();
        invalidate();
        setContentDescription(mTitle);
    }

    public void setBitmap(Bitmap b) {
        mBitmap = b;
        mArt.invalidate();
    }

    public boolean hasBitmap() {
        return mBitmap != null;
    }

    public void setPlaceholder(int color) {
        mPlaceholder = color;
        mArt.invalidate();
    }

    public void setBadge(Bitmap b) {
        mBadge = b;
        mArt.invalidate();
    }

    /** Watched part in permille (-1: no bar). */
    public void setProgress(int permille) {
        mProgress = permille;
        mArt.invalidate();
    }

    /** "Next episode" / "New episode" in place of the bar. */
    public void setTag(CharSequence t) {
        mTagText = t == null ? "" : t;
        mArt.invalidate();
    }

    /** An input with nothing on it: the chip in the quiet ink. */
    public void setOff(boolean off) {
        mOff = off;
        mArt.invalidate();
    }

    /** Kinds of the Витрина. */
    private boolean showcase() {
        return kind >= DOCK;
    }

    /** Kinds whose face lifts (the label and the shadow stay drawn by the tile itself). */
    private boolean lifts() {
        return kind == DOCK || kind == UPNEXT;
    }

    public void setIcon(Drawable d) {
        mIcon = d;
        mTintIcon = true;
        mArt.invalidate();
    }

    /** An app's own icon in its own colours (search \"Искать в\"): never tinted into a grey silhouette. */
    public void setAppIcon(Drawable d) {
        mIcon = d;
        mTintIcon = false;
        mArt.invalidate();
    }

    /** Colours changed (theme): redraw everything. */
    public void restyle() {
        mArt.invalidate();
        invalidate();
    }

    // ------------------------------------------------------------------ focus

    public void setFocusedState(boolean on, boolean animate) {
        if (on == mFocused && (mAnim == null || !mAnim.isRunning())) {
            // also with animate: a value left between rest and focus (a cancelled animation) is put right
            if (!animate || mFocus != (on ? 1f : 0f)) apply(on ? 1f : 0f);
            return;
        }
        mFocused = on;
        if (mAnim != null) mAnim.cancel();
        float to = on ? 1f : 0f;
        if (!animate) {
            apply(to);
            return;
        }
        mAnim = android.animation.ValueAnimator.ofFloat(mFocus, to);
        mAnim.setDuration(showcase() ? Tokens.MS_SHOWCASE : on ? Theme.FOCUS_IN_MS : Theme.FOCUS_OUT_MS);
        mAnim.setInterpolator(showcase() ? Motion.SHOWCASE : Theme.EMPHASIZED);
        mAnim.addUpdateListener(a -> apply((Float) a.getAnimatedValue()));
        mAnim.start();
    }

    public boolean focusedState() {
        return mFocused;
    }

    /** The banner being moved ("Переместить"): it stays lifted and shows arrows. */
    public void setMoving(boolean m) {
        mMoving = m;
        invalidate();
    }

    /**
     * The focus value onto both layers. The face is its own view with its own display list: the pill colour (chips,
     * round buttons) and the sheen are drawn there, so it is redrawn on every value, never only the tile (a redraw of
     * the tile alone kept the face recorded with an old value: the All apps button stayed white after focus left).
     */
    private void apply(float f) {
        mFocus = f;
        float s = 1f + (Theme.animations() ? (mScale - 1f) * f : 0f);
        if (lifts()) {
            mArt.setScaleX(s);
            mArt.setScaleY(s);
            mArt.setTranslationY(Theme.animations() ? -Theme.pxf(LIFT) * f : 0f);
        } else {
            setScaleX(s);
            setScaleY(s);
        }
        mArt.invalidate();
        setTranslationZ(f > 0 ? 1f : 0f);
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas c) {
        if (showcase()) {
            drawShowcase(c);
            return;
        }
        float w = getWidth(), h = getHeight(), r = Theme.pxf(mRadius);
        if (mFocus > 0f) {
            mShadow.setColor(Theme.alpha(Theme.BG, 1f));
            mShadow.setShadowLayer(Theme.pxf(Theme.P.shadowBlur) / 2f, 0, Theme.pxf(Theme.P.shadowY),
                    Theme.alpha(Theme.SHADOW, Color.alpha(Theme.SHADOW) / 255f * mFocus));
            mR.set(0, 0, w, h);
            c.drawRoundRect(mR, r, r, mShadow);
        } else if (!Theme.dark()) {
            // resting elevation e1 of the light theme: the depth of the field comes from here
            mShadow.setColor(Theme.SURFACE);
            mShadow.setShadowLayer(Theme.pxf(Tokens.E1_BLUR) / 2f, 0, Theme.pxf(Tokens.E1_Y), Theme.P.elevation);
            mR.set(0, 0, w, h);
            c.drawRoundRect(mR, r, r, mShadow);
        }
        super.dispatchDraw(c);
        ring(c, w, h, r);
        if (mFocus > 0f) {
            if (kind == REC && mTitle.length() > 0) caption(c, w, h);
            if (mMoving) arrows(c, w, h);
        }
    }

    /**
     * The Витрина kinds: the shadow under the (lifted) face, rest to focus: dark 0 10 24 / .28 to 0 34 60 / .50 black,
     * light 0 12 26 / .14 to 0 34 60 / .30 ink; pills 0 8 22 to 0 26 56; then the face, the dock tile's name.
     */
    private void drawShowcase(Canvas c) {
        float w = getWidth(), h = getHeight(), r = Theme.pxf(mRadius), f = mFocus;
        boolean dark = Theme.dark();
        float y0 = lifts() ? 10 : 8, b0 = lifts() ? 24 : 22, y1 = lifts() ? 34 : 26, b1 = lifts() ? 60 : 56;
        float a0 = lifts() ? (dark ? 0.28f : 0.14f) : (dark ? 0f : 0.08f), a1 = dark ? (lifts() ? 0.50f : 0.55f) : (lifts() ? 0.30f : 0.22f);
        float a = a0 + (a1 - a0) * f;
        if (a > 0.005f) {
            c.save();
            if (lifts()) {
                float s = mArt.getScaleX();
                c.translate(0, mArt.getTranslationY());
                c.scale(s, s, w / 2f, h / 2f);
            }
            mShadow.setColor(dark ? 0xFF000000 : Theme.SURFACE);
            mShadow.setShadowLayer(Theme.pxf(b0 + (b1 - b0) * f) / 2f, 0, Theme.pxf(y0 + (y1 - y0) * f),
                    Theme.alpha(dark ? 0xFF000000 : 0xFF101828, a));
            mR.set(0, 0, w, h);
            c.drawRoundRect(mR, r, r, mShadow);
            c.restore();
        }
        super.dispatchDraw(c);
        ring(c, w, h, r);
        if (kind == DOCK && f > 0f && mTitle.length() > 0) {
            TextPaint t = Ink.paint(24, 600, Theme.alpha(Theme.TEXT1, f));
            float tw = Ink.fitWidth(mTitle, w + Theme.pxf(80), t);
            Ink.fitLine(c, mTitle, (w - tw) / 2f, h + Theme.pxf(Grid.DOCK_LABEL_GAP) + Theme.pxf(24), w + Theme.pxf(80), t);
        }
        if (mMoving && f > 0f) arrows(c, w, h);
    }

    /**
     * The accent ring around the face, only while this tile holds the focus ({@link FocusRing#alpha}): around the
     * lifted face of a dock tile or card (its scale and lift), else around the tile (whose own scale it undoes).
     */
    private void ring(Canvas c, float w, float h, float r) {
        float s = lifts() ? mArt.getScaleX() : 1f, ty = lifts() ? mArt.getTranslationY() : 0f;
        float hw = w * s / 2f, hh = h * s / 2f;
        mRing.draw(c, Theme.P.focusRing, w / 2f - hw, h / 2f - hh + ty, w / 2f + hw, h / 2f + hh + ty, r * s, Theme.pxf(1f), getScaleX(),
                FocusRing.alpha(mFocused, mFocus));
    }

    private void caption(Canvas c, float w, float h) {
        TextPaint t = Ink.paint(Tokens.TYPE_LABEL, 620, Theme.alpha(Theme.TEXT1, mFocus));
        float y = h + Theme.pxf(22) + Theme.pxf(22);
        Ink.line(c, mTitle, 0, y, w * 1.4f, t);
        if (mSub.length() > 0) {
            TextPaint s = Ink.paint(Tokens.TYPE_CAPTION, 520, Theme.alpha(Theme.TEXT3, mFocus));
            Ink.line(c, mSub, 0, y + Theme.pxf(28), w * 1.4f, s);
        }
    }

    private void arrows(Canvas c, float w, float h) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Theme.RING);
        float s = Theme.pxf(12), cy = h / 2, gap = Theme.pxf(20);
        android.graphics.Path a = new android.graphics.Path();
        a.moveTo(-gap, cy - s); a.lineTo(-gap - s, cy); a.lineTo(-gap, cy + s); a.close();
        a.moveTo(w + gap, cy - s); a.lineTo(w + gap + s, cy); a.lineTo(w + gap, cy + s); a.close();
        c.drawPath(a, p);
    }

    /** The clipped face of the tile (rounded by its outline, so the ring outside stays sharp). */
    private final class Art extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Matrix mM = new Matrix();
        private final RectF mRr = new RectF();

        Art(Context c) {
            super(c);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Theme.pxf(mRadius));
                }
            });
            setClipToOutline(true);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            if (kind == INPUT) {
                drawInput(c, w, h);
                return;
            }
            if (kind == CHIP || kind == ROUND) {
                drawPill(c, w, h);
                return;
            }
            if (lifts()) {
                drawFace(c, w, h);
                return;
            }
            c.drawColor(mBitmap == null ? (kind == BANNER ? Theme.SURFACE : mPlaceholder) : Color.BLACK);
            if (mBitmap != null) {
                float bw = mBitmap.getWidth(), bh = mBitmap.getHeight();
                float s = Math.max(w / bw, h / bh);
                mM.setScale(s, s);
                mM.postTranslate((w - bw * s) / 2f, (h - bh * s) / 2f);
                c.drawBitmap(mBitmap, mM, mPaint);
            } else if (kind == BANNER || kind == REC || kind == CARD) {
                TextPaint t = Ink.paint(Tokens.TYPE_LABEL, 600, kind == BANNER ? Theme.TEXT1 : 0xE6FFFFFF);
                float tw = Math.min(t.measureText(mTitle.toString()), w - Theme.pxf(32));
                Ink.line(c, mTitle, (w - tw) / 2f, h / 2f + Theme.pxf(8), w - Theme.pxf(32), t);
            }
            if (kind == REC && mBadge != null) badge(c);
            if (kind == CARD) {
                if (mBadge != null || mProgress >= 0 || mTagText.length() > 0) {
                    mPaint.setShader(new android.graphics.LinearGradient(0, h * 0.58f, 0, h, 0x00000000, 0x8C000000,
                            android.graphics.Shader.TileMode.CLAMP));
                    c.drawRect(0, h * 0.58f, w, h, mPaint);
                    mPaint.setShader(null);
                }
                if (mBadge != null) badge(c);
                if (mProgress >= 0) {
                    float l = Theme.pxf(18), bh = Theme.pxf(6), y = h - Theme.pxf(18) - bh, rr = bh / 2;
                    mPaint.setColor(Theme.TRACK);
                    mRr.set(l, y, w - l, y + bh);
                    c.drawRoundRect(mRr, rr, rr, mPaint);
                    mPaint.setColor(Theme.PROGRESS);
                    mRr.set(l, y, l + (w - 2 * l) * mProgress / 1000f, y + bh);
                    c.drawRoundRect(mRr, rr, rr, mPaint);
                } else if (mTagText.length() > 0) {
                    TextPaint t = Ink.paint(Tokens.TYPE_CAPTION, 650, Color.WHITE);
                    float tw = t.measureText(mTagText.toString()), px = Theme.pxf(12), py = Theme.pxf(6);
                    float x = Theme.pxf(16), bottom = h - Theme.pxf(14);
                    float th = Theme.pxf(20) * 1.25f;
                    mPaint.setColor(0x80000000);
                    mRr.set(x, bottom - th - 2 * py, x + tw + 2 * px, bottom);
                    c.drawRoundRect(mRr, Theme.pxf(10), Theme.pxf(10), mPaint);
                    c.drawText(mTagText.toString(), x + px, bottom - py - Theme.pxf(5), t);
                }
            }
            if (kind == BANNER) {
                Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
                line.setStyle(Paint.Style.STROKE);
                line.setStrokeWidth(Math.max(1f, Theme.pxf(1)));
                line.setColor(Theme.BANNER_LINE);
                float r = Theme.pxf(mRadius), i = line.getStrokeWidth() / 2;
                mRr.set(i, i, w - i, h - i);
                c.drawRoundRect(mRr, r, r, line);
            }
        }

        /** The app's icon in the top left corner (a Continue card, a search result: where it comes from). */
        private void badge(Canvas c) {
            float b = Theme.pxf(40), x = Theme.pxf(14);
            mRr.set(x, x, x + b, x + b);
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(mRr, Theme.pxf(10), Theme.pxf(10), android.graphics.Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            c.drawBitmap(mBadge, null, mRr, mPaint);
            c.restore();
        }

        /**
         * A dock tile or a Continue card of the Витрина: the art covers the face (no art: the surface and the name),
         * a hairline, the card's bar or tag over a soft bottom shade, the sheen while focused.
         */
        private void drawFace(Canvas c, float w, float h) {
            c.drawColor(mBitmap == null ? (kind == DOCK ? Theme.SURFACE : mPlaceholder) : Color.BLACK);
            if (mBitmap != null) {
                float bw = mBitmap.getWidth(), bh = mBitmap.getHeight(), s = Math.max(w / bw, h / bh);
                mM.setScale(s, s);
                mM.postTranslate((w - bw * s) / 2f, (h - bh * s) / 2f);
                c.drawBitmap(mBitmap, mM, mPaint);
            } else {
                TextPaint t = Ink.paint(24, 620, kind == DOCK ? Theme.TEXT1 : 0xE6FFFFFF);
                float tw = Math.min(t.measureText(mTitle.toString()), w - Theme.pxf(32));
                Ink.line(c, mTitle, (w - tw) / 2f, h / 2f + Theme.pxf(9), w - Theme.pxf(32), t);
            }
            if (kind == UPNEXT && (mProgress >= 0 || mTagText.length() > 0)) {
                mPaint.setShader(new android.graphics.LinearGradient(0, h * 0.55f, 0, h, 0x00000000, 0x8C000000,
                        android.graphics.Shader.TileMode.CLAMP));
                c.drawRect(0, h * 0.55f, w, h, mPaint);
                mPaint.setShader(null);
                float l = Theme.pxf(14), bh = Theme.pxf(5), rr = bh / 2;
                if (mProgress >= 0) {
                    float y = h - l - bh;
                    mPaint.setColor(0x47FFFFFF);
                    mRr.set(l, y, w - l, y + bh);
                    c.drawRoundRect(mRr, rr, rr, mPaint);
                    mPaint.setColor(0xFFFFFFFF);
                    mRr.set(l, y, l + (w - 2 * l) * mProgress / 1000f, y + bh);
                    c.drawRoundRect(mRr, rr, rr, mPaint);
                } else {
                    TextPaint t = Ink.paint(Tokens.TYPE_CAPTION, 650, Color.WHITE);
                    Ink.line(c, mTagText, l, h - l, w - 2 * l, t);
                }
            }
            if (mFocus > 0f) {
                mPaint.setShader(new android.graphics.LinearGradient(w * 0.18f, 0, w * 0.82f, h, new int[] { 0x00FFFFFF,
                        Theme.alpha(0xFFFFFFFF, 0.22f * mFocus), 0x00FFFFFF }, new float[] { 0.30f, 0.48f, 0.64f },
                        android.graphics.Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, mPaint);
                mPaint.setShader(null);
            }
            Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(Math.max(1f, Theme.pxf(1)));
            line.setColor(Theme.dark() || mBitmap == null ? Theme.BANNER_LINE : 0x12FFFFFF);
            float r = Theme.pxf(mRadius), i = line.getStrokeWidth() / 2;
            mRr.set(i, i, w - i, h - i);
            c.drawRoundRect(mRr, r, r, line);
        }

        /**
         * An input chip (icon 32, port 24/620, device 22/480) or a round button (icon 32): the chip colour at rest
         * (white .07 dark, the surface light), focused white in the dark theme and the raised surface in the light one
         * (Sand mock: hi with the field ink); an input with nothing on it in the quiet ink.
         */
        private void drawPill(Canvas c, float w, float h) {
            float f = mFocus;
            boolean dark = Theme.dark();
            int rest = dark ? 0xFF1A1C20 : Theme.SURFACE;
            c.drawColor(Palette.mix(rest, dark ? 0xFFFFFFFF : Theme.SURFACE2, f));
            int t1 = Palette.mix(mOff ? Theme.TEXT3 : Theme.TEXT1, dark ? 0xFF0E1116 : Theme.TEXT1, f);
            int t3 = Palette.mix(Theme.TEXT3, dark ? 0xFF5A6170 : Theme.TEXT3, f);
            float icon = Theme.pxf(32);
            if (kind == ROUND) {
                if (mIcon != null) {
                    int s = Math.round(icon), x = Math.round((w - icon) / 2f), y = Math.round((h - icon) / 2f);
                    mIcon.setTint(t1);
                    mIcon.setBounds(x, y, x + s, y + s);
                    mIcon.draw(c);
                }
                return;
            }
            float x = Theme.pxf(24);
            if (mIcon != null) {
                int s = Math.round(icon), y = Math.round((h - icon) / 2f);
                mIcon.setTint(t1);
                mIcon.setBounds(Math.round(x), y, Math.round(x) + s, y + s);
                mIcon.draw(c);
                x += icon + Theme.pxf(16);
            }
            float right = w - Theme.pxf(28);
            TextPaint port = Ink.paint(24, 620, t1);
            float base = h / 2f + Theme.pxf(8.5f);
            TextPaint dev = Ink.paint(22, 480, t3);
            float gap = Theme.pxf(16), all = right - x;
            if (mSub.length() > 0) {                          // port and state fit together first, the state is not cut
                float pw0 = port.measureText(mTitle.toString()), dw0 = dev.measureText(mSub.toString());
                float k = LabelFit.scale(pw0 + gap + dw0, all);
                port.setTextSize(port.getTextSize() * k);
                dev.setTextSize(dev.getTextSize() * k);
            }
            float pw = Ink.fitLine(c, mTitle, x, base, all, port);
            if (mSub.length() > 0 && x + pw + gap < right) Ink.fitLine(c, mSub, x + pw + gap, base, right - x - pw - gap, dev);
        }

        /** Icon 40 at 28 from the left, centred; name (22/600) and state (20/520) beside it, centred as a pair. */
        private void drawInput(Canvas c, float w, float h) {
            c.drawColor(Theme.SURFACE);
            float pad = Theme.pxf(28), icon = Theme.pxf(40), x = pad;
            if (mIcon != null) {
                int s = Math.round(icon), y = Math.round((h - icon) / 2f);
                if (mTintIcon) mIcon.setTint(Theme.TEXT2);
                mIcon.setBounds(Math.round(pad), y, Math.round(pad) + s, y + s);
                mIcon.draw(c);
                x = pad + icon + Theme.pxf(20);
            }
            float maxW = w - x - pad;
            TextPaint sub = Ink.paint(Tokens.TYPE_CAPTION, 520, Theme.TEXT3);
            TextPaint t = Ink.paint(Tokens.TYPE_LABEL, 600, Theme.TEXT1);
            boolean two = mSub.length() > 0;
            float block = Theme.pxf(two ? 28 + 4 + 24 : 28), top = (h - block) / 2f;
            Ink.fitLine(c, mTitle, x, top + Theme.pxf(21), maxW, t);
            if (two) Ink.fitLine(c, mSub, x, top + Theme.pxf(28 + 4 + 18), maxW, sub);
            if (Theme.dark()) {
                Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
                line.setStyle(Paint.Style.STROKE);
                line.setStrokeWidth(Math.max(1f, Theme.pxf(1)));
                line.setColor(Theme.BANNER_LINE);
                float r = Theme.pxf(mRadius), i = line.getStrokeWidth() / 2;
                mRr.set(i, i, w - i, h - i);
                c.drawRoundRect(mRr, r, r, line);
            }
        }
    }
}
