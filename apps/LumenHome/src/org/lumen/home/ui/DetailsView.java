// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.home.R;
import org.lumen.ui.Tokens;

/**
 * "Подробнее" over the showcase: the whole description, the facts line (year, genres, length, age), the TMDB rating
 * and where to watch, the watch button (the app it opens in, or Найти) and the TMDB notice the data source asks for.
 * LEFT and RIGHT move between the two buttons, OK acts, BACK closes.
 */
public class DetailsView extends FrameLayout {
    private final LinearLayout mCol;
    private final TextView mTitle, mMeta, mFacts, mBody, mNote;
    private final TextView[] mBtn = new TextView[2];
    private int mFocus;
    private boolean mOpen;
    private Runnable mWatch;

    public DetailsView(Context c) {
        super(c);
        mCol = new LinearLayout(c);
        mCol.setOrientation(LinearLayout.VERTICAL);
        mCol.setClipChildren(false);
        LayoutParams lp = new LayoutParams(Theme.px(1040), LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL);
        lp.leftMargin = Theme.px(Tokens.MARGIN + 48);
        addView(mCol, lp);
        mTitle = add(c, 0);
        mMeta = add(c, 18);
        mFacts = add(c, 10);
        mBody = add(c, 24);
        LinearLayout btns = new LinearLayout(c);
        btns.setClipChildren(false);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, Theme.px(Tokens.BUTTON_H));
        bl.topMargin = Theme.px(36);
        mCol.addView(btns, bl);
        int[] labels = { R.string.hero_watch, R.string.close };
        for (int i = 0; i < 2; i++) {
            TextView b = new TextView(c);
            b.setText(labels[i]);
            b.setGravity(Gravity.CENTER);
            b.setPadding(Theme.px(30), 0, Theme.px(30), 0);
            final int k = i;
            b.setOnClickListener(v -> {
                mFocus = k;
                act();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
            if (i > 0) p.leftMargin = Theme.px(16);
            btns.addView(b, p);
            mBtn[i] = b;
        }
        mNote = add(c, 40);
        setVisibility(GONE);
    }

    private TextView add(Context c, int top) {
        TextView t = new TextView(c);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        p.topMargin = Theme.px(top);
        mCol.addView(t, p);
        return t;
    }

    public boolean isOpen() {
        return mOpen;
    }

    /** {@code rating} and {@code where} are whole lines ("" = none), {@code watchLabel} the button (null = Смотреть). */
    public void open(String title, String meta, String body, String rating, String where, String watchLabel, Runnable watch) {
        setBackgroundColor(Theme.alpha(Theme.SCRIM, 0.92f));
        Theme.style(mTitle, Tokens.TYPE_SETUP_TITLE, 700, 0xFFFFFFFF, -1.2f);
        mTitle.setMaxLines(2);
        mTitle.setEllipsize(TextUtils.TruncateAt.END);
        Theme.style(mMeta, Tokens.TYPE_BODY, 520, 0xD6FFFFFF, 0);
        Theme.style(mFacts, Tokens.TYPE_BODY, 520, 0xD6FFFFFF, 0);
        mFacts.setMaxLines(2);
        mFacts.setEllipsize(TextUtils.TruncateAt.END);
        Theme.style(mBody, Tokens.TYPE_BODY_L, Tokens.W_BODY_L, 0xEBFFFFFF, 0);
        mBody.setLineHeight(Math.round(Theme.pxf(Tokens.TYPE_BODY_L) * 1.45f));
        mBody.setMaxLines(9);
        mBody.setEllipsize(TextUtils.TruncateAt.END);
        Theme.style(mNote, Tokens.TYPE_CAPTION, 520, 0x99FFFFFF, 0);
        mTitle.setText(title);
        mMeta.setText(meta);
        String facts = rating.isEmpty() ? where : where.isEmpty() ? rating : rating + "\n" + where;
        mFacts.setText(facts);
        mFacts.setVisibility(facts.isEmpty() ? GONE : VISIBLE);
        mBody.setText(body);
        if (watchLabel == null || watchLabel.isEmpty()) mBtn[0].setText(R.string.hero_watch);
        else mBtn[0].setText(watchLabel);
        mNote.setText(R.string.tmdb_attribution);
        mWatch = watch;
        mFocus = 0;
        mark();
        mOpen = true;
        setVisibility(VISIBLE);
        setAlpha(0f);
        animate().alpha(1f).setDuration(Theme.PANEL_MS).setInterpolator(Theme.EMPHASIZED).start();
    }

    public void close() {
        if (!mOpen) return;
        mOpen = false;
        animate().alpha(0f).setDuration(Theme.FOCUS_OUT_MS).withEndAction(() -> setVisibility(GONE)).start();
    }

    public void key(boolean right) {
        mFocus = right ? 1 : 0;
        mark();
    }

    public void act() {
        Runnable w = mWatch;
        close();
        if (mFocus == 0 && w != null) post(w);
    }

    private void mark() {
        for (int i = 0; i < 2; i++) {
            boolean f = i == mFocus;
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(Theme.pxf(Tokens.R_BUTTON));
            g.setColor(f ? 0xFFFFFFFF : 0x2EFFFFFF);
            mBtn[i].setBackground(g);
            Theme.style(mBtn[i], Tokens.TYPE_LABEL, 600, f ? 0xFF141A27 : 0xFFFFFFFF, 0);
            float s = f ? Tokens.SCALE_BUTTON : 1f;
            mBtn[i].animate().scaleX(s).scaleY(s).setDuration(Theme.FOCUS_IN_MS).start();
        }
    }
}
