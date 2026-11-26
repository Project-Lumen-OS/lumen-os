// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.setup.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.ui.LumenTheme;
import org.lumen.ui.Motion;
import org.lumen.ui.Palette;
import org.lumen.ui.Tokens;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Lumen 2.0 look of the wizard (the same tokens as Home: apps/core/lumen-ui). Sizes are design
 * px of a 1920-wide screen, scaled by displayWidth / 1920 (1 design px = 1 real px at 1080p, 1.33 at 2K, 2 at 4K).
 * Colours are the shared {@link Palette} of the theme on screen ({@link LumenTheme}, light by default); no colour
 * value lives in this app. Text is Inter Variable with the optical size equal to the text size.
 */
public final class Ui {
    private Ui() {}

    public static final float DESIGN_WIDTH = 1920f;

    public static final int[] FOCUSED = {android.R.attr.state_focused};
    public static final int[] PRESSED = {android.R.attr.state_pressed};
    public static final int[] SELECTED = {android.R.attr.state_selected};
    public static final int[] EMPTY = {};

    /** Weights of the type scale. */
    public static final int W_REGULAR = Tokens.W_BODY, W_BODY_L = Tokens.W_BODY_L, W_CAPTION = Tokens.W_CAPTION,
            W_LABEL = Tokens.W_LABEL, W_BUTTON = 620, W_TITLE = Tokens.W_SETUP_TITLE, W_STATUS = 600;

    /** The palette on screen, cached once per process (a theme change restarts the wizard's views). */
    public static Palette P = LumenTheme.LIGHT;
    // the palette roles under the names the steps use
    public static int BG, BG_BOTTOM, SURFACE, SURFACE2, TEXT, TEXT_DIM, TEXT3, FOCUS, FOCUS_TEXT, ACCENT,
            ACCENT_STRONG, ERROR, OK, PANEL, PANEL_LINE, RING, RING_GAP, BUTTON_IDLE, LINE, FIELD_LINE;

    private static float sScale = 1f;
    private static int sDensityDpi;
    private static Context sApp;
    private static final SparseArray<Typeface> FACES = new SparseArray<>();
    private static final Map<Typeface, Integer> WEIGHT = new IdentityHashMap<>();
    private static Typeface sMedium, sRegular, sLight, sThin;

    public static void init(Context c) {
        sApp = c.getApplicationContext() != null ? c.getApplicationContext() : c;
        P = LumenTheme.palette(c);
        BG = P.bg;
        BG_BOTTOM = P.bgLow;
        SURFACE = P.surface;
        SURFACE2 = P.surface2;
        TEXT = P.text1;
        TEXT_DIM = P.text2;
        TEXT3 = P.text3;
        FOCUS = P.focus;
        FOCUS_TEXT = P.onFocus;
        ACCENT = P.accent;
        ACCENT_STRONG = P.accentOnBg;
        ERROR = P.error;
        OK = P.ok;
        PANEL = P.panel;
        PANEL_LINE = P.panelLine;
        RING = P.ring;
        RING_GAP = P.ringGap;
        BUTTON_IDLE = P.pill;
        LINE = Palette.alpha(P.text1, 0.14f);
        // the outline of an empty input field on the light field: at least 3:1 against every part of the field
        // (WCAG 1.4.11; the panel hairline is about 1.1:1, a white box on a white field)
        FIELD_LINE = Palette.alpha(P.text3, 0.8f);
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        sScale = scaleOf(m);
        sDensityDpi = m.densityDpi;
        FACES.clear();
        WEIGHT.clear();
        sRegular = marker(W_REGULAR);
        sMedium = marker(W_LABEL);
        sLight = marker(300);
        sThin = marker(200);
    }

    private static Typeface marker(int weight) {
        Typeface t = font(weight, 24);
        WEIGHT.put(t, weight);
        return t;
    }

    private static float scaleOf(DisplayMetrics m) {
        int w = Math.max(m.widthPixels, m.heightPixels);
        return w <= 0 ? 1f : w / DESIGN_WIDTH;
    }

    /**
     * The display's size or density is no longer the one {@link #init} saw (the UI resolution 1080p /
     * 2K / 4K changed while the process runs): every px size made since then is of the old scale.
     */
    public static boolean displayChanged(Context c) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        return m.densityDpi != sDensityDpi || Math.abs(scaleOf(m) - sScale) > 1e-4f;
    }

    public static int px(float design) { return Math.round(design * sScale); }

    public static float pxf(float design) { return design * sScale; }

    /** Inter at a weight and an optical size (design px, the font's 14..32 range); the system font without the asset. */
    public static Typeface font(int weight, float designSize) {
        int o = Math.max(14, Math.min(32, Math.round(designSize)));
        int key = weight * 100 + o;
        Typeface t = FACES.get(key);
        if (t == null) {
            try {
                if (sApp != null) {
                    t = new Typeface.Builder(sApp.getAssets(), "fonts/InterVariable.ttf")
                            .setFontVariationSettings("'wght' " + weight + ", 'opsz' " + o).build();
                }
            } catch (RuntimeException e) {
                t = null;
            }
            if (t == null) t = Typeface.create(Typeface.DEFAULT, weight, false);
            FACES.put(key, t);
        }
        return t;
    }

    public static Typeface medium() { return sMedium; }

    public static Typeface regular() { return sRegular; }

    public static Typeface light() { return sLight; }

    public static Typeface thin() { return sThin; }

    /** Every step and focus motion: cubic-bezier(0.2, 0, 0, 1) (Motion.EMPHASIZED). */
    public static PathInterpolator decel() { return new PathInterpolator(0.2f, 0f, 0f, 1f); }

    public static PathInterpolator standard() { return new PathInterpolator(0.2f, 0f, 0f, 1f); }

    /** The system "remove animations" switch: scales and slides become short fades. */
    public static boolean reduced(Context c) { return Motion.reduced(c); }

    public static boolean isRtl(View v) { return v.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL; }

    /** True when the view (or its parent, for duplicateParentState children) is drawn focused. */
    public static boolean focusedState(View v) {
        for (int s : v.getDrawableState()) if (s == android.R.attr.state_focused) return true;
        return false;
    }

    /** Scales a view on focus (the design notes/3.5): 160 ms in, 120 ms out; nothing with reduced motion. */
    public static void focusScale(View v, boolean focused, float scale) {
        if (reduced(v.getContext())) return;
        float s = focused ? scale : 1f;
        v.animate().cancel();
        v.animate().scaleX(s).scaleY(s).setDuration(focused ? Tokens.MS_FOCUS_IN : Tokens.MS_FOCUS_OUT)
                .setInterpolator(Motion.EMPHASIZED).start();
    }

    // ------------------------------------------------------------------ text

    public static ColorStateList focusText() {
        return new ColorStateList(new int[][]{FOCUSED, EMPTY}, new int[]{FOCUS_TEXT, TEXT});
    }

    /** Secondary text: text3 at rest, on-focus at 75 % on the focus fill. */
    public static ColorStateList focusTextDim() {
        return new ColorStateList(new int[][]{FOCUSED, EMPTY}, new int[]{Palette.alpha(FOCUS_TEXT, 0.75f), TEXT3});
    }

    /** Icons in a row: text2 at rest, on-focus when focused. */
    public static ColorStateList focusIcon() {
        return new ColorStateList(new int[][]{FOCUSED, EMPTY}, new int[]{FOCUS_TEXT, TEXT_DIM});
    }

    public static TextView text(Context c, float size, int color, int weight) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, pxf(size));
        t.setTextColor(color);
        t.setTypeface(font(weight, size));
        t.setIncludeFontPadding(false);
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return t;
    }

    /** A text in one of the {@link #regular()} / {@link #medium()} / {@link #light()} weights, at its optical size. */
    public static TextView text(Context c, float size, int color, Typeface tf) {
        Integer w = tf == null ? null : WEIGHT.get(tf);
        if (w != null) return text(c, size, color, w);
        TextView t = text(c, size, color, W_REGULAR);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    /**
     * Line heights of the type scale in the spec's CSS sense: the distance from one baseline to the
     * next is the text size times this. Android's spacing multiplier scales Inter's 1.21 em font box instead (1.45 gave
     * 1.76 em, the text ran 20 % taller than the spec), so {@link #lineHeight} sets the distance in px.
     */
    public static final float LH_TITLE = 1.08f, LH_BODY = 1.45f;

    /** Baseline to baseline = text size x {@code cssLineHeight}; call after the text size is final. */
    public static void lineHeight(TextView t, float cssLineHeight) {
        t.setLineHeight(Math.round(t.getTextSize() * cssLineHeight));
    }

    /** Step title: 56 px, weight 680, line height 1.08, tracking -1.2 px, up to 3 lines. */
    public static TextView title(Context c) {
        TextView t = text(c, Tokens.TYPE_SETUP_TITLE, TEXT, W_TITLE);
        t.setMaxLines(3);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setLetterSpacing(-1.2f / Tokens.TYPE_SETUP_TITLE);
        lineHeight(t, LH_TITLE);
        return t;
    }

    /**
     * A word (a Wi-Fi name) cut with an ellipsis to one title line of the left column, so a title such as "Password
     * for &lt;name&gt;" breaks before the name instead of inside it.
     */
    public static CharSequence fitTitleWord(CharSequence word, float widthDesign) {
        if (word == null) return null;
        android.text.TextPaint p = new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(pxf(Tokens.TYPE_SETUP_TITLE));
        p.setTypeface(font(W_TITLE, Tokens.TYPE_SETUP_TITLE));
        p.setLetterSpacing(-1.2f / Tokens.TYPE_SETUP_TITLE);
        return TextUtils.ellipsize(word, p, pxf(widthDesign), TextUtils.TruncateAt.END);
    }

    /** Step explanation: 26 px, weight 420, text2, line height 1.45. */
    public static TextView subtitle(Context c) {
        TextView t = text(c, Tokens.TYPE_BODY_L, TEXT_DIM, W_BODY_L);
        t.setMaxLines(5);
        t.setEllipsize(TextUtils.TruncateAt.END);
        lineHeight(t, LH_BODY);
        return t;
    }

    public static TextView body(Context c, CharSequence s) {
        return body(c, s, Tokens.TYPE_BODY_L);
    }

    /** Body text at a size of the scale (26 setup subtitles, 24 descriptions), text2, line height 1.45. */
    public static TextView body(Context c, CharSequence s, float size) {
        TextView t = text(c, size, TEXT_DIM, W_BODY_L);
        t.setText(s);
        lineHeight(t, LH_BODY);
        return t;
    }

    /** A short list header ("Wi-Fi networks nearby"): 22 px, weight 600, text3. */
    public static TextView header(Context c, CharSequence s) {
        TextView t = text(c, Tokens.TYPE_LABEL, TEXT3, W_LABEL);
        t.setText(s);
        t.setPaddingRelative(px(28), 0, px(28), px(12));
        return t;
    }

    /** A note with the info icon (left column): 22 px, weight 500, text3. */
    public static View note(Context c, CharSequence s) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        Icon i = new Icon(c, Icon.INFO).color(TEXT3);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(px(28), px(28));
        ip.topMargin = px(2);
        l.addView(i, ip);
        TextView t = text(c, Tokens.TYPE_LABEL, TEXT3, 500);
        t.setText(s);
        lineHeight(t, LH_BODY);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMarginStart(px(12));
        l.addView(t, tp);
        return l;
    }

    /** The separator line of a list (text1 at 14 %, 28 px in from both sides). */
    public static View separator(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, px(1)));
        lp.setMargins(px(28), px(12), px(28), px(12));
        v.setLayoutParams(lp);
        return v;
    }

    // ------------------------------------------------------------------ backgrounds

    private static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(pxf(radius));
        return d;
    }

    /** Focus fill with the focus shadow when focused, {@code idle} otherwise. */
    public static Drawable focusBg(int idle, float radius, int strokeIdle) {
        return new FocusFill(idle, strokeIdle, radius);
    }

    public static Drawable roundRect(int color, float radius) { return round(color, radius); }

    /** Opaque panel (sheets, dialogs): panel colour with its line. */
    public static Drawable cardOpaque(float radius) {
        GradientDrawable d = round(PANEL, radius);
        d.setStroke(Math.max(1, px(1)), PANEL_LINE);
        return d;
    }

    /** A tile on the field (surface, the light theme's hairline). */
    public static Drawable card(float radius) {
        GradientDrawable d = round(SURFACE, radius);
        if (!P.dark) d.setStroke(Math.max(1, px(1)), PANEL_LINE);
        return d;
    }

    // ------------------------------------------------------------------ layout helpers

    public static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    public static LinearLayout.LayoutParams lpTop(int w, int h, float topMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = px(topMargin);
        return p;
    }

    public static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setClipChildren(false);
        l.setClipToPadding(false);
        return l;
    }

    public static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setClipChildren(false);
        l.setClipToPadding(false);
        return l;
    }

    public static View space(Context c) { return new View(c); }
}
