package org.lumen.home.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.home.R;
import org.lumen.ui.Grid;
import org.lumen.ui.Motion;
import org.lumen.ui.Tokens;

/**
 * The shelf of the Витрина (Home A): bottom left over the art, 9 columns wide. Eyebrow (the app's icon, \"Lumen Play ·
 * Продолжить просмотр\", 22/600), the title once (80/88, 700, one line), the season line (28/36, 600), the progress
 * bar with the time left (24/32), the buttons Продолжить / Подробнее (64 high, pill). Over art every text is white
 * with a soft shadow; on the bare field (no art) it takes the theme's ink. A focused button turns white (on the bare
 * field the theme's focus colour), grows to 1.06 and casts a shadow, in 260 ms.
 */
public class ShelfView extends LinearLayout {
    public static final int BUTTON_H = 64;
    static final int EYEBROW_H = 32, BADGE = 32, TITLE = 80, TITLE_LINE = 88, META = 28, META_LINE = 36, LEFT = 24,
            LINE = 32, BAR_W = 248, BAR_H = 6;
    private static final float FOCUS_SCALE = 1.06f;

    private final LinearLayout mEyebrow, mProgressLine, mButtons;
    private final ImageView mBadge;
    private final TextView mEyebrowText, mTitle, mMeta, mLeft;
    private final TextView[] mBtn = new TextView[2];
    private final Bar mBar;
    private final Dots mDots;
    private static final int DOTS_W = 200;
    private int mFocus = -1;
    private boolean mOnArt = true;
    private Bitmap mBadgeBmp;
    private CharSequence mEyebrowApp = "", mEyebrowTail = "";

    public ShelfView(Context c, View.OnClickListener click) {
        super(c);
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        mEyebrow = new LinearLayout(c);
        mEyebrow.setOrientation(HORIZONTAL);
        mEyebrow.setGravity(Gravity.CENTER_VERTICAL);
        mBadge = new ImageView(c);
        mBadge.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mBadge.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Theme.pxf(8));
            }
        });
        mBadge.setClipToOutline(true);
        LayoutParams bp = new LayoutParams(Theme.px(BADGE), Theme.px(BADGE));
        bp.rightMargin = Theme.px(12);
        mEyebrow.addView(mBadge, bp);
        mEyebrowText = new TextView(c);
        mEyebrowText.setSingleLine(true);
        mEyebrow.addView(mEyebrowText);
        addView(mEyebrow, new LayoutParams(LayoutParams.WRAP_CONTENT, Theme.px(EYEBROW_H)));
        // Android puts a line's whole height under its first baseline, CSS splits it: 9 here is the mock's 16
        mTitle = text(c, 9);
        mMeta = text(c, 16);
        mProgressLine = new LinearLayout(c);
        mProgressLine.setOrientation(HORIZONTAL);
        mProgressLine.setGravity(Gravity.CENTER_VERTICAL);
        mBar = new Bar(c);
        LayoutParams barLp = new LayoutParams(Theme.px(BAR_W), Theme.px(BAR_H));
        barLp.rightMargin = Theme.px(16);
        mProgressLine.addView(mBar, barLp);
        mLeft = new TextView(c);
        mLeft.setSingleLine(true);
        mProgressLine.addView(mLeft);
        LayoutParams pl = new LayoutParams(LayoutParams.MATCH_PARENT, Theme.px(LINE));
        pl.topMargin = Theme.px(8);
        addView(mProgressLine, pl);
        mButtons = new LinearLayout(c);
        mButtons.setOrientation(HORIZONTAL);
        mButtons.setClipChildren(false);
        mButtons.setClipToPadding(false);
        LayoutParams blp = new LayoutParams(LayoutParams.WRAP_CONTENT, Theme.px(BUTTON_H));
        blp.topMargin = Theme.px(28);
        addView(mButtons, blp);
        int[] icons = { org.lumen.icons.R.drawable.lumen_ic_play, org.lumen.icons.R.drawable.lumen_ic_info };
        int[] labels = { R.string.hero_resume, R.string.hero_more };
        for (int i = 0; i < 2; i++) {
            TextView b = new TextView(c);
            b.setGravity(Gravity.CENTER_VERTICAL);
            b.setText(labels[i]);
            b.setSingleLine(true);
            b.setCompoundDrawablePadding(Theme.px(12));
            Drawable d = c.getDrawable(icons[i]).mutate();
            int s = Theme.px(i == 0 ? 34 : 30);
            d.setBounds(0, 0, s, s);
            b.setCompoundDrawablesRelative(d, null, null, null);
            b.setPadding(Theme.px(26), 0, Theme.px(34), 0);
            b.setTag(10 + i);
            b.setOnClickListener(click);
            b.setFocusable(false); // the focus is Home's own (FocusMap), the framework never holds it on a button
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
            if (i > 0) lp.leftMargin = Theme.px(16);
            mButtons.addView(b, lp);
            mBtn[i] = b;
        }
        mDots = new Dots(c);
        mDots.setVisibility(GONE);
        LayoutParams dl = new LayoutParams(Theme.px(DOTS_W), Theme.px(10));
        dl.leftMargin = Theme.px(32);
        dl.gravity = Gravity.CENTER_VERTICAL;
        mButtons.addView(mDots, dl);
        restyle();
    }

    private TextView text(Context c, int top) {
        TextView t = new TextView(c);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = Theme.px(top);
        addView(t, lp);
        return t;
    }

    /** White over art, the theme's ink on the bare field. */
    public void setInk(boolean onArt) {
        if (onArt == mOnArt) return;
        mOnArt = onArt;
        restyle();
    }

    public void restyle() {
        int t1 = mOnArt ? 0xFFFFFFFF : Theme.TEXT1, t2 = mOnArt ? 0xE0FFFFFF : Theme.TEXT2,
                t3 = mOnArt ? 0xD1FFFFFF : Theme.TEXT2;
        Theme.style(mEyebrowText, 22, 600, t2, 0);
        mEyebrowText.setLineHeight(Theme.px(28));
        setEyebrowText();
        Theme.style(mTitle, TITLE, 700, t1, -2.2f);
        mTitle.setLineHeight(Theme.px(TITLE_LINE));
        Theme.style(mMeta, META, 600, t1, -0.2f);
        mMeta.setLineHeight(Theme.px(META_LINE));
        Theme.style(mLeft, LEFT, 450, t3, 0);
        mLeft.setLineHeight(Theme.px(LINE));
        mLeft.setFontFeatureSettings("tnum");
        if (mOnArt) {
            mTitle.setShadowLayer(Theme.pxf(12), 0, Theme.pxf(2), 0x40000000);
            mMeta.setShadowLayer(Theme.pxf(8), 0, Theme.pxf(1), 0x33000000);
        } else {
            mTitle.setShadowLayer(0, 0, 0, 0);
            mMeta.setShadowLayer(0, 0, 0, 0);
        }
        for (TextView b : mBtn) Theme.style(b, 25, 620, 0xFFFFFFFF, 0);
        mBar.invalidate();
        mDots.invalidate();
        setButtonFocus(mFocus, false);
    }

    private void setEyebrowText() {
        SpannableStringBuilder b = new SpannableStringBuilder(mEyebrowApp);
        if (mEyebrowTail.length() > 0) {
            if (b.length() > 0) {
                int s = b.length();
                b.append("  ·  ");
                int col = mOnArt ? 0x73FFFFFF : Theme.TEXT3;
                b.setSpan(new ForegroundColorSpan(col), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            b.append(mEyebrowTail);
        }
        mEyebrowText.setText(b);
    }

    /**
     * A Continue item: the app (its icon comes through {@link #setBadge}), \"Продолжить просмотр\", the title ONCE,
     * the season line, the time left beside the bar; the buttons only on a hero page (not over a Continue row).
     */
    public void showItem(String app, String tail, String title, String season, String left, int progress, boolean buttons,
                         boolean resume) {
        mEyebrowApp = app == null ? "" : app;
        mEyebrowTail = tail == null ? "" : tail;
        setEyebrowText();
        mTitle.setText(title);
        mMeta.setText(season);
        mMeta.setVisibility(season == null || season.isEmpty() ? GONE : VISIBLE);
        mLeft.setText(left);
        mBar.set(progress);
        mBar.setVisibility(progress >= 0 ? VISIBLE : GONE);
        boolean line = progress >= 0 || (left != null && !left.isEmpty());
        mProgressLine.setVisibility(line ? VISIBLE : GONE);
        mBtn[0].setText(resume ? R.string.hero_resume : R.string.hero_watch);
        mButtons.setVisibility(buttons ? VISIBLE : GONE);
    }

    /** A showcase page (TMDB trending): \"Сейчас популярно\", the title, its line, Смотреть / Подробнее. */
    public void showFeature(String eyebrow, String title, String meta) {
        mEyebrowApp = "";
        mEyebrowTail = eyebrow == null ? "" : eyebrow;
        setEyebrowText();
        mTitle.setText(title);
        mMeta.setText(meta);
        mMeta.setVisibility(meta == null || meta.isEmpty() ? GONE : VISIBLE);
        mProgressLine.setVisibility(GONE);
        mBtn[0].setText(R.string.hero_watch);
        mButtons.setVisibility(VISIBLE);
    }

    /**
     * The app icon before the eyebrow: the icon, an empty slot of the same size while it loads ({@code slot}), or none
     * (a showcase page). Unchanged values touch nothing, so the eyebrow never shifts.
     */
    public void setBadge(Bitmap b, boolean slot) {
        if (b != mBadgeBmp) {
            mBadgeBmp = b;
            mBadge.setImageBitmap(b);
        }
        int v = b != null ? VISIBLE : slot ? INVISIBLE : GONE;
        if (mBadge.getVisibility() != v) mBadge.setVisibility(v);
    }

    /** Button in focus: -1 none, 0 Продолжить / Смотреть, 1 Подробнее. */
    public void setButtonFocus(int i, boolean animate) {
        mFocus = i;
        for (int k = 0; k < 2; k++) {
            TextView b = mBtn[k];
            boolean f = k == i;
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(Theme.pxf(BUTTON_H / 2f));
            int bg, fg;
            if (mOnArt) {
                bg = f ? 0xFFFFFFFF : 0x29FFFFFF;
                fg = f ? 0xFF0E1116 : 0xFFFFFFFF;
            } else {
                bg = f ? Theme.FOCUS : Theme.dark() ? 0x1FFFFFFF : Theme.SURFACE;
                fg = f ? Theme.ON_FOCUS : Theme.TEXT1;
            }
            g.setColor(bg);
            b.setBackground(g);
            b.setTextColor(fg);
            Drawable d = b.getCompoundDrawablesRelative()[0];
            if (d != null) d.setTint(fg);
            b.setElevation(f ? Theme.pxf(14) : !mOnArt && !Theme.dark() ? Theme.pxf(2) : 0);
            float s = f && Theme.animations() ? FOCUS_SCALE : 1f;
            b.animate().scaleX(s).scaleY(s).setDuration(animate ? Tokens.MS_SHOWCASE : 0).setInterpolator(Motion.SHOWCASE)
                    .start();
        }
    }

    /** The title's width in columns: 9, or 7 beside the sharp frame of small art (column 9), so they never touch. */
    public void setTitleColumns(int cols) {
        int w = Theme.px(Grid.span(cols));
        if (mTitle.getMaxWidth() != w) mTitle.setMaxWidth(w);
    }

    /** The hero's pages: dots after the buttons (10 x 10, the current one 34 wide), only from 2 pages. */
    public void setPages(int page, int pages) {
        mDots.set(page, pages);
    }

    /** The text appears in 600 ms (a new page). */
    public void fadeInText() {
        if (!Theme.animations()) return;
        setAlpha(0f);
        animate().alpha(1f).setDuration(Theme.HERO_FADE_MS).setInterpolator(Theme.DECEL).start();
    }

    /** Design height of the shelf with its buttons (for the Continue row variant without them, minus the buttons). */
    public static int designHeight(boolean buttons) {
        int h = EYEBROW_H + 16 + TITLE_LINE + 16 + META_LINE + 8 + LINE; // the mock's boxes
        return buttons ? h + 28 + BUTTON_H : h;
    }

    /** Width of the shelf: 9 columns. */
    public static int designWidth() {
        return Grid.span(Grid.SHELF_COLS);
    }

    /** Page dots: white over art (others at 45 %), the theme's ink on the bare field. */
    private final class Dots extends View {
        private int mPage, mPages;
        private final Paint mP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mR = new RectF();

        Dots(Context c) {
            super(c);
        }

        void set(int page, int pages) {
            mPage = page;
            mPages = pages;
            setVisibility(pages > 1 ? VISIBLE : GONE);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight(), r = h / 2, x = 0;
            for (int i = 0; i < mPages; i++) {
                float w = Theme.pxf(i == mPage ? 34 : 10);
                int on = mOnArt ? 0xFFFFFFFF : Theme.TEXT1, off = mOnArt ? 0x73FFFFFF : Theme.alpha(Theme.TEXT1, 0.28f);
                mP.setColor(i == mPage ? on : off);
                mR.set(x, 0, x + w, h);
                c.drawRoundRect(mR, r, r, mP);
                x += w + Theme.pxf(10);
            }
        }
    }

    /** The progress bar: 248 x 6, round ends; white over art, the theme's progress colour on the bare field. */
    private final class Bar extends View {
        private int mPermille = -1;
        private final Paint mP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mR = new RectF();

        Bar(Context c) {
            super(c);
        }

        void set(int p) {
            mPermille = p;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = h / 2f;
            mP.setColor(mOnArt ? 0x47FFFFFF : Theme.alpha(Theme.TEXT1, 0.14f));
            mR.set(0, 0, w, h);
            c.drawRoundRect(mR, r, r, mP);
            if (mPermille < 0) return;
            mP.setColor(mOnArt ? 0xFFFFFFFF : Theme.PROGRESS);
            mR.set(0, 0, w * Math.min(1000, mPermille) / 1000f, h);
            c.drawRoundRect(mR, r, r, mP);
        }
    }
}
