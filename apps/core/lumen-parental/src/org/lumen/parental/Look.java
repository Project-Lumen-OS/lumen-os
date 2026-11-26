// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.parental;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The parental screens' look: Lumen's warm dark page, cream focus, rows in the Android TV settings shape. */
final class Look {
    static final int BG = 0xFF14110F, CARD = 0xFF221D1A, TEXT = 0xFFF2EDE6, SUB = 0xFFA79E94, FOCUS = 0xFFF3E3C3,
            FOCUS_TEXT = 0xFF1B1613, WARN = 0xFFE8A07A;

    private Look() {}

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    /** A settings row: title, optional line below, value or icon on the right; focus = cream pill, dark text. */
    static final class Row extends LinearLayout {
        final TextView title, sub, value;
        final ImageView icon;

        Row(Context c, CharSequence t) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setFocusable(true);
            setMinimumHeight(dp(c, 52));
            setPadding(dp(c, 18), dp(c, 8), dp(c, 18), dp(c, 8));
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(VERTICAL);
            title = text(c, t, 15, TEXT, true);
            sub = text(c, "", 12, SUB, false);
            sub.setVisibility(GONE);
            col.addView(title);
            col.addView(sub);
            addView(col, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            value = text(c, "", 14, SUB, false);
            addView(value);
            icon = new ImageView(c);
            icon.setVisibility(GONE);
            addView(icon, new LayoutParams(dp(c, 22), dp(c, 22)));
            setOnFocusChangeListener((v, f) -> paint(f));
            paint(false);
        }

        Row sub(CharSequence s) {
            sub.setText(s);
            sub.setVisibility(s == null || s.length() == 0 ? GONE : VISIBLE);
            return this;
        }

        Row value(CharSequence s) {
            value.setText(s);
            return this;
        }

        Row check(boolean on) {
            icon.setImageResource(on ? org.lumen.icons.R.drawable.lumen_ic_checkbox_on : org.lumen.icons.R.drawable.lumen_ic_checkbox_off);
            icon.setVisibility(VISIBLE);
            paint(hasFocus());
            return this;
        }

        private void paint(boolean f) {
            setBackground(round(f ? FOCUS : 0x00000000, dp(getContext(), 12)));
            title.setTextColor(f ? FOCUS_TEXT : TEXT);
            sub.setTextColor(f ? 0xFF5C5148 : SUB);
            value.setTextColor(f ? FOCUS_TEXT : SUB);
            icon.setColorFilter(f ? FOCUS_TEXT : TEXT);
        }
    }

    /** A pill button of a dialog screen. */
    static TextView button(Context c, CharSequence s) {
        TextView b = text(c, s, 15, TEXT, true);
        b.setFocusable(true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 28), dp(c, 10), dp(c, 28), dp(c, 10));
        View.OnFocusChangeListener l = (v, f) -> {
            b.setBackground(round(f ? FOCUS : CARD, dp(c, 22)));
            b.setTextColor(f ? FOCUS_TEXT : TEXT);
        };
        b.setOnFocusChangeListener(l);
        l.onFocusChange(b, false);
        return b;
    }
}
