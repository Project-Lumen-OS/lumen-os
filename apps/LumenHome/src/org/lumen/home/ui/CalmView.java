package org.lumen.home.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.ui.Tokens;

/**
 * The calm screen's block (CalmBlock): greeting, the big clock (168/200, tabular digits), the day and
 * the weather. Shown when there is nothing to watch and no showcase (no network, no cache).
 */
public class CalmView extends LinearLayout {
    private final TextView mGreet, mClock, mDay, mWx;
    private final ImageView mWxIcon;
    private final View mDot;

    public CalmView(Context c) {
        super(c);
        setOrientation(VERTICAL);
        mGreet = new TextView(c);
        addView(mGreet);
        mClock = new TextView(c);
        LayoutParams cp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        cp.topMargin = Theme.px(6);
        addView(mClock, cp);
        LinearLayout day = new LinearLayout(c);
        day.setOrientation(HORIZONTAL);
        day.setGravity(Gravity.CENTER_VERTICAL);
        mDay = new TextView(c);
        day.addView(mDay);
        mDot = new View(c) {
            private final Paint mP = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas cv) {
                mP.setColor(Theme.TEXT3);
                cv.drawCircle(getWidth() / 2f, getHeight() / 2f, getWidth() / 2f, mP);
            }
        };
        LayoutParams dp = new LayoutParams(Theme.px(6), Theme.px(6));
        dp.leftMargin = Theme.px(14);
        dp.rightMargin = Theme.px(14);
        day.addView(mDot, dp);
        mWxIcon = new ImageView(c);
        day.addView(mWxIcon, new LayoutParams(Theme.px(32), Theme.px(32)));
        mWx = new TextView(c);
        LayoutParams wp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        wp.leftMargin = Theme.px(10);
        day.addView(mWx, wp);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = Theme.px(12);
        addView(day, lp);
        restyle();
    }

    public void restyle() {
        Theme.style(mGreet, 30, 520, Theme.TEXT2, 0);
        Theme.style(mClock, Tokens.TYPE_CLOCK, Tokens.W_CLOCK, Theme.TEXT1, -6f);
        mClock.setFontFeatureSettings("tnum");
        mClock.setLineHeight(Theme.px(Tokens.TYPE_CLOCK));
        Theme.style(mDay, 26, 520, Theme.TEXT2, 0);
        Theme.style(mWx, 26, 520, Theme.TEXT2, 0);
        mWxIcon.setColorFilter(Theme.TEXT2);
        mDot.invalidate();
    }

    public void set(String greeting, String clock, String day) {
        mGreet.setText(greeting);
        mClock.setText(clock);
        mDay.setText(day);
    }

    public void setWeather(int icon, String text) {
        boolean on = icon != 0 && text != null && !text.isEmpty();
        mDot.setVisibility(on ? VISIBLE : GONE);
        mWxIcon.setVisibility(on ? VISIBLE : GONE);
        mWx.setVisibility(on ? VISIBLE : GONE);
        if (on) {
            mWxIcon.setImageResource(icon);
            mWx.setText(text);
        }
    }
}
