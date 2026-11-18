package org.lumen.home.ui;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.lumen.home.model.FocusMap;
import org.lumen.ui.Tokens;

import java.util.ArrayList;
import java.util.List;

/**
 * A Home row: its title (28/620 on a 32 line) and the tiles side by side, 24 under it (a focused tile grows 15 px and
 * must not touch it), gutters of the grid between tiles. The focused tile sits at the left edge and the row slides
 * under it in 200 ms (the end of the row stays filled, FocusMap.first); nothing is clipped. The Витрина's rows (the
 * dock, the Continue cards) have no title: the tiles start at the row's top.
 */
public class RowView extends FrameLayout {
    public static final int TITLE_H = org.lumen.ui.Grid.ROW_TITLE_H;

    private final TextView mTitle;
    private final FrameLayout mStrip;
    private final ArrayList<TileView> mTiles = new ArrayList<>();
    private int mItemW, mGap, mItemH, mFirst = -1;
    private boolean mHideBefore;
    private CharSequence mTitleText = "", mTitleSub = "";

    public RowView(Context c) {
        super(c);
        setClipChildren(false);
        setClipToPadding(false);
        mTitle = new TextView(c);
        mTitle.setSingleLine(true);
        addView(mTitle, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        mStrip = new FrameLayout(c);
        mStrip.setClipChildren(false);
        mStrip.setClipToPadding(false);
        addView(mStrip, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    public void setTitle(CharSequence title, CharSequence sub) {
        mTitleText = title == null ? "" : title;
        mTitleSub = sub == null ? "" : sub;
        restyle();
    }

    /** The tiles (one kind per row). */
    public void setTiles(List<TileView> tiles) {
        mStrip.removeAllViews();
        mTiles.clear();
        mTiles.addAll(tiles);
        if (tiles.isEmpty()) return;
        TileView t0 = tiles.get(0);
        mItemW = t0.designW();
        mItemH = t0.designH();
        mGap = org.lumen.ui.Grid.GUTTER;
        for (int i = 0; i < tiles.size(); i++) {
            TileView t = tiles.get(i);
            LayoutParams lp = new LayoutParams(Theme.px(mItemW), Theme.px(mItemH));
            lp.leftMargin = Theme.px(i * (mItemW + mGap));
            mStrip.addView(t, lp);
        }
        ((LayoutParams) mStrip.getLayoutParams()).topMargin = mTitleText.length() == 0 ? 0 : Theme.px(TITLE_H + Tokens.ROW_TITLE_GAP);
        mFirst = -1;
    }

    public List<TileView> tiles() {
        return mTiles;
    }

    public TileView tile(int i) {
        return i >= 0 && i < mTiles.size() ? mTiles.get(i) : null;
    }

    public FocusMap.Row geometry() {
        return new FocusMap.Row(mTiles.size(), mItemW, mGap);
    }

    /** Design height of the tiles alone. */
    public int itemHeight() {
        return mItemH;
    }

    /** Design height: title, gap, tiles. */
    public int designHeight() {
        return TITLE_H + Tokens.ROW_TITLE_GAP + mItemH;
    }

    /** Tiles left of the first one shown fade out (the Витрина's rows: only the right end peeks). */
    public void setHideBefore(boolean on) {
        mHideBefore = on;
    }

    public void scrollTo(int first, boolean animate) {
        if (first == mFirst) return;
        mFirst = first;
        if (mHideBefore) {
            for (int i = 0; i < mTiles.size(); i++) {
                float a = i < first ? 0f : 1f;
                TileView t = mTiles.get(i);
                if (t.getAlpha() == a) continue;
                if (animate && Theme.animations()) t.animate().alpha(a).setDuration(Theme.ROW_SCROLL_MS).start();
                else t.setAlpha(a);
            }
        }
        float x = -Theme.pxf(first * (mItemW + mGap));
        mStrip.animate().cancel();
        if (animate && Theme.animations()) {
            mStrip.animate().translationX(x).setDuration(Theme.ROW_SCROLL_MS).setInterpolator(Theme.EMPHASIZED).start();
        } else {
            mStrip.setTranslationX(x);
        }
    }

    public void restyle() {
        mTitle.setVisibility(mTitleText.length() == 0 ? GONE : VISIBLE);
        Theme.style(mTitle, Tokens.TYPE_TITLE, Tokens.W_TITLE, Theme.TEXT1, -0.3f);
        mTitle.setLineHeight(Theme.px(TITLE_H));
        if (mTitleSub.length() > 0) {
            SpannableStringBuilder b = new SpannableStringBuilder(mTitleText).append("  ");
            int s = b.length();
            b.append(mTitleSub);
            b.setSpan(new AbsoluteSizeSpan(Theme.px(22)), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new ForegroundColorSpan(Theme.TEXT3), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            mTitle.setText(b);
        } else {
            mTitle.setText(mTitleText);
        }
        for (TileView t : mTiles) t.restyle();
    }
}
