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
