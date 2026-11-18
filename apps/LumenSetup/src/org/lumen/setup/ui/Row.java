package org.lumen.setup.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * List row: [start icon 32] title 24 / subtitle 20 [end: radio, switch, chevron or value].
 * 84 px tall, radius 20, the focus fill with its shadow and scale 1.02 on focus. Children use duplicateParentState
 * so text and icons flip with focus.
 */
public class Row extends LinearLayout {
    public final TextView title;
    public final TextView sub;
    private View mStart;
    private View mEnd;
    private TextView mValue;
    private boolean mBusy;

    public Row(Context c, CharSequence titleText) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setFocusable(true);
        setClickable(true);
        setMinimumHeight(Ui.px(84));
        setPadding(Ui.px(28), Ui.px(12), Ui.px(28), Ui.px(12));
        setBackground(Ui.focusBg(0x00000000, org.lumen.ui.Tokens.R_ROW, 0));
        setClipChildren(false);
        LinearLayout texts = Ui.vbox(c);
        texts.setDuplicateParentStateEnabled(true);
        title = Ui.text(c, 24, 0, 600);
        title.setTextColor(Ui.focusText());
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setDuplicateParentStateEnabled(true);
        title.setText(titleText);
        sub = Ui.text(c, 20, 0, Ui.W_CAPTION);
        sub.setTextColor(Ui.focusTextDim());
        sub.setMaxLines(2);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        sub.setDuplicateParentStateEnabled(true);
        sub.setVisibility(GONE);
        texts.addView(title, Ui.lp(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        texts.addView(sub, Ui.lpTop(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 4));
        addView(texts, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
    }

    /** The short row of a summary (62 px size.list_row). */
    public Row compact() {
        setMinimumHeight(Ui.px(62));
        setPadding(Ui.px(28), Ui.px(6), Ui.px(28), Ui.px(6));
        return this;
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(gainFocus, direction, previous);
        Ui.focusScale(this, gainFocus, org.lumen.ui.Tokens.SCALE_ROW);
    }

    public Row subtitle(CharSequence s) {
        sub.setText(s);
        sub.setVisibility(s == null || s.length() == 0 ? GONE : VISIBLE);
        return this;
    }

    /** Lets the subtitle wrap to n lines (a note that must be read in full, such as a disclosure). */
    public Row subtitleLines(int n) {
        sub.setMaxLines(n);
        return this;
    }

    public Row startIcon(View v, float sizeDesign) {
        if (mStart != null) removeView(mStart);
        mStart = v;
        v.setDuplicateParentStateEnabled(true);
        LayoutParams p = new LayoutParams(Ui.px(sizeDesign), Ui.px(sizeDesign));
        p.setMarginEnd(Ui.px(22));
        addView(v, 0, p);
        return this;
    }

    /** Replaces the end widget (check, switch, spinner, icon). */
    public Row end(View v, int w, int h) {
        if (mEnd != null) removeView(mEnd);
        mEnd = v;
        if (v != null) {
            v.setDuplicateParentStateEnabled(true);
            LayoutParams p = new LayoutParams(w, h);
            p.setMarginStart(Ui.px(20));
            addView(v, p);
        }
        return this;
    }

    /** A value text at the end (22 px, weight 560, e.g. "Connected"). */
    public Row value(CharSequence s) {
        if (mValue == null) {
            mValue = Ui.text(getContext(), 22, 0, 560);
            mValue.setTextColor(Ui.focusText());
            mValue.setSingleLine(true);
            mValue.setEllipsize(TextUtils.TruncateAt.END);
            mValue.setMaxWidth(Ui.px(380));
            mValue.setDuplicateParentStateEnabled(true);
            LayoutParams p = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            p.setMarginStart(Ui.px(20));
            addView(mValue, mEnd == null ? getChildCount() : indexOfChild(mEnd), p);
        }
        mValue.setText(s);
        mValue.setVisibility(s == null || s.length() == 0 ? GONE : VISIBLE);
        return this;
    }

    /**
     * A summary row: the title becomes the key (22 / 520, text3, 280 wide) and the value follows
     * it (22 / 600, text1), the end widget stays at the right.
     */
    public Row kv(CharSequence v) {
        compact();
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.pxf(22));
        title.setTypeface(Ui.font(Ui.W_CAPTION, 22));
        title.setTextColor(Ui.focusTextDim());
        View texts = (View) title.getParent();
        texts.setLayoutParams(new LayoutParams(Ui.px(280), LayoutParams.WRAP_CONTENT));
        if (mValue == null) {
            mValue = Ui.text(getContext(), 22, 0, 600);
            mValue.setTextColor(Ui.focusText());
            mValue.setSingleLine(true);
            mValue.setEllipsize(TextUtils.TruncateAt.END);
            mValue.setDuplicateParentStateEnabled(true);
            LayoutParams vp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            vp.setMarginStart(Ui.px(22));   // the spec row's 22 px gap between the key column and the value
            addView(mValue, indexOfChild(texts) + 1, vp);
        }
        mValue.setText(v);
        return this;
    }

    public void setBusy(boolean busy) {
        mBusy = busy;
        setAlpha(busy ? 0.45f : 1f);
    }

    @Override
    public boolean performClick() {
        if (mBusy) return true;
        return super.performClick();
    }
}
