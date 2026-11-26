// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.widget.TextView;

import org.lumen.ui.LumenTheme;
import org.lumen.ui.Palette;
import org.lumen.ui.Tokens;

/**
 * The Lumen look of Files: the shared palette ({@link LumenTheme}: Sand light, dark, auto), Inter, sizes in px of the
 * 1920x1080 UI ("design px") scaled to the real display. The colour fields follow the palette on screen.
 */
public final class Ui {
    public static final int SAFE = Tokens.SAFE, MARGIN = 116;
    public static Palette P = LumenTheme.DARK;
    public static int TEXT1, TEXT2, TEXT3;
    public static Typeface REGULAR, MEDIUM, SEMIBOLD, DISPLAY;
    private static float sScale = 1f;

    private Ui() {}

    public static void init(Activity a) {
        DisplayMetrics m = a.getResources().getDisplayMetrics();
        sScale = Math.max(m.widthPixels, m.heightPixels) / 1920f;
        apply(LumenTheme.palette(a));
        if (REGULAR == null) {
            REGULAR = inter(a, 400);
            MEDIUM = inter(a, 500);
            SEMIBOLD = inter(a, 600);
            DISPLAY = inter(a, 680);
        }
    }

    public static void apply(Palette p) {
        P = p;
        TEXT1 = p.text1;
        TEXT2 = p.text2;
        TEXT3 = p.text3;
    }

    private static Typeface inter(Context c, int weight) {
        try {
            return new Typeface.Builder(c.getAssets(), "fonts/InterVariable.ttf")
                    .setFontVariationSettings("'wght' " + weight).build();
        } catch (Throwable t) {
            return Typeface.DEFAULT;
        }
    }

    public static float scale() {
        return sScale;
    }

    public static int px(float design) {
        return Math.round(design * sScale);
    }

    public static float pxf(float design) {
        return design * sScale;
    }

    public static void text(TextView t, float size, Typeface tf, int color) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, pxf(size));
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
    }

    /** One line, cut at the end. */
    public static TextView line(Context c, CharSequence s, float size, Typeface tf, int color) {
        TextView t = new TextView(c);
        text(t, size, tf, color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setText(s);
        return t;
    }

    public static GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(pxf(radius));
        return g;
    }
}
