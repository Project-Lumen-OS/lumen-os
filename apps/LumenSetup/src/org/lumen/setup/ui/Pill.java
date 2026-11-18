package org.lumen.setup.ui;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

/**
 * Pill button of the wizard: 64 high, radius 32, label 24 / 620; the quiet pill fill of the theme at
 * rest, the focus fill with its shadow and scale 1.05 when focused. An optional 28 px icon before the label.
 */
public class Pill extends TextView {
    private boolean mBusy;

    public Pill(Context c, CharSequence text, boolean primary) {
        super(c);
        setText(text);
        setFocusable(true);
        setFocusableInTouchMode(false);
        setClickable(true);
        setGravity(Gravity.CENTER);
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);
        setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.pxf(24));
        setTypeface(Ui.font(Ui.W_BUTTON, 24));
        setIncludeFontPadding(false);
        setTextColor(Ui.focusText());
        setMinHeight(Ui.px(org.lumen.ui.Tokens.BUTTON_H_SETUP));
        setPadding(Ui.px(34), 0, Ui.px(34), 0);
        setBackground(Ui.focusBg(Ui.BUTTON_IDLE, org.lumen.ui.Tokens.R_BUTTON_SETUP, 0));
    }

    /** A 28 px icon of the Lumen set before the label (Icon type), in the label's colour. */
    public Pill icon(int type) {
        android.graphics.drawable.Drawable d = getContext().getDrawable(Icon.res(type, 4)).mutate();
        d.setTintList(Ui.focusText());
        d.setBounds(0, 0, Ui.px(28), Ui.px(28));
        setCompoundDrawablesRelative(d, null, null, null);
        setCompoundDrawablePadding(Ui.px(12));
        return this;
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(gainFocus, direction, previous);
        Ui.focusScale(this, gainFocus, org.lumen.ui.Tokens.SCALE_BUTTON);
    }

    /** A busy pill keeps its focus but ignores clicks and looks dimmed. */
    public void setBusy(boolean busy) {
        mBusy = busy;
        setAlpha(busy ? 0.45f : 1f);
    }

    public boolean isBusy() { return mBusy; }

    @Override
    public boolean performClick() {
        if (mBusy) return true;
        return super.performClick();
    }
}
