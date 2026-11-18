package org.lumen.home.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The Витрина's corner: weather (icon 32, 26/500) and the clock (34/600, tabular digits), right aligned on the
 * margin at y 56. White with a soft shadow over art, the theme's ink on the bare field.
 */
public class StatusView extends LinearLayout {
    private final ImageView mWxIcon;
    private final TextView mWx, mClock;
    private boolean mOnArt = true;

    public StatusView(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        mWxIcon = new ImageView(c);
        addView(mWxIcon, new LayoutParams(Theme.px(32), Theme.px(32)));
        mWx = new TextView(c);
        LayoutParams wp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        wp.leftMargin = Theme.px(10);
        wp.rightMargin = Theme.px(24);
        addView(mWx, wp);
        mClock = new TextView(c);
        addView(mClock);
        restyle();
    }

    public void setInk(boolean onArt) {
        if (onArt == mOnArt) return;
        mOnArt = onArt;
        restyle();
    }

    public void restyle() {
        int t1 = mOnArt ? 0xFFFFFFFF : Theme.TEXT1, t2 = mOnArt ? 0xE6FFFFFF : Theme.TEXT2;
        Theme.style(mWx, 26, 500, t2, 0);
        mWx.setFontFeatureSettings("tnum");
        Theme.style(mClock, 34, 600, t1, -0.6f);
        mClock.setFontFeatureSettings("tnum");
        mWxIcon.setColorFilter(t2);
        float r = mOnArt ? Theme.pxf(5) : 0;
        mWx.setShadowLayer(r, 0, mOnArt ? Theme.pxf(1) : 0, mOnArt ? 0x59000000 : 0);
        mClock.setShadowLayer(r, 0, mOnArt ? Theme.pxf(1) : 0, mOnArt ? 0x59000000 : 0);
    }

    public void setClock(String s) {
        mClock.setText(s);
    }

    /** Weather icon and temperature; none (0, null) hides both. */
    public void setWeather(int icon, String temp) {
        boolean on = icon != 0 && temp != null;
        mWxIcon.setVisibility(on ? VISIBLE : GONE);
        mWx.setVisibility(on ? VISIBLE : GONE);
        if (!on) return;
        mWxIcon.setImageResource(icon);
        mWx.setText(temp);
    }
}
