// SPDX-License-Identifier: Apache-2.0
package org.lumen.source;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.ui.LumenTheme;
import org.lumen.ui.Motion;
import org.lumen.ui.Palette;
import org.lumen.ui.Tokens;

/**
 * The small card of the Source key: one line at the top left, where the stock signal card and our HDMI viewer's
 * banner sit, in the Lumen 2.0 palette of the one theme switch (light or dark) and Inter. It slides in once; a new
 * press while it is up only changes the icon and text in place and restarts the timer, so quick presses never
 * replay the slide (the blink of the old right side list). One overlay window that never takes keys or touches,
 * as wide as the screen so a longer text never resizes the window. Main thread.
 */
final class SourceOsd {
    /** Design px at 1920 wide: the viewer banner's place (32 dp, 24 dp at 320 dpi), a Home chip's height. */
    static final int X = 64, Y = 48, H = 72, ICON_BOX = 48, ICON = 28, PAD_START = 12, PAD_END = 28, GAP = 16,
            TEXT_MAX = 960, NAME_WEIGHT = 600, LINE_WEIGHT = 450;
    static final int CARD_ALPHA = 0xF2;
    static final int MS_IN = 200, MS_OUT = 160;
    private static final String FONT = "fonts/InterVariable.ttf";

    private final Context c;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable hide = this::fadeOut;
    private FrameLayout root;
    private LinearLayout card;
    private ImageView icon;
    private TextView text;
    private Palette palette;
    private float px = 1f;
    private Typeface regular, medium;

    SourceOsd(Context c) {
        this.c = c;
        this.wm = c.getSystemService(WindowManager.class);
    }

    boolean shown() { return root != null; }

    /** Shows or updates the card for {@code ms}. {@code name} is drawn strong, the rest of {@code line} quieter. */
    void show(int iconRes, String line, String name, long ms) {
        Palette p = LumenTheme.palette(c);
        boolean fresh = root == null;
        if (fresh && !build(p)) return;
        if (!fresh && p != palette) paint(p);
        card.animate().cancel();
        icon.setImageResource(iconRes);
        text.setText(spans(line, name));
        if (fresh) {
            boolean still = Motion.reduced(c);
            card.setAlpha(still ? 1f : 0f);
            card.setTranslationX(still ? 0f : -d(Tokens.PANEL_SLIDE));
            if (!still) card.animate().alpha(1f).translationX(0f).setDuration(MS_IN).setInterpolator(Motion.ENTER).start();
        } else {
            card.setAlpha(1f);
            card.setTranslationX(0f);
        }
        main.removeCallbacks(hide);
        main.postDelayed(hide, ms);
    }

    /** The same card with a new line (a device name that arrived), when it is up. */
    void update(String line, String name) {
        if (root != null) text.setText(spans(line, name));
    }

    void hideNow() {
        main.removeCallbacks(hide);
        if (root == null) return;
        card.animate().cancel();
        try {
            wm.removeViewImmediate(root);
        } catch (Throwable t) {
            Log.w(SourceKey.TAG, "remove: " + t);
        }
        root = null;
    }

    private void fadeOut() {
        if (root == null) return;
        if (Motion.reduced(c)) { hideNow(); return; }
        card.animate().alpha(0f).setDuration(MS_OUT).setInterpolator(Motion.ENTER).withEndAction(this::hideNow).start();
    }

    private boolean build(Palette p) {
        px = Math.max(0.5f, c.getResources().getDisplayMetrics().widthPixels / 1920f);
        root = new FrameLayout(c);
        root.setClipChildren(false);
        card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPaddingRelative(d(PAD_START), 0, d(PAD_END), 0);
        icon = new ImageView(c);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = d((ICON_BOX - ICON) / 2);
        icon.setPadding(pad, pad, pad, pad);
        card.addView(icon, new LinearLayout.LayoutParams(d(ICON_BOX), d(ICON_BOX)));
        text = new TextView(c);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setMaxWidth(d(TEXT_MAX));
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, d(Tokens.TYPE_BODY_L));
        text.setTypeface(regular());
        text.setIncludeFontPadding(false);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.setMarginStart(d(GAP));
        card.addView(text, tl);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, d(H));
        cl.gravity = Gravity.TOP | Gravity.START;
        cl.setMarginStart(d(X));
        root.addView(card, cl);
        paint(p);
        try {
            wm.addView(root, params());
            return true;
        } catch (Throwable t) {
            Log.w(SourceKey.TAG, "add: " + t);
            root = null;
            return false;
        }
    }

    /** Colours of the palette: the panel colour a little see-through over video, a hairline, the accent icon tile. */
    private void paint(Palette p) {
        palette = p;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor((CARD_ALPHA << 24) | (p.panel & 0xFFFFFF));
        bg.setCornerRadius(d(H) / 2f);
        bg.setStroke(Math.max(1, d(1)), p.panelLine);
        card.setBackground(bg);
        GradientDrawable tile = new GradientDrawable();
        tile.setShape(GradientDrawable.OVAL);
        tile.setColor(p.accent);
        icon.setBackground(tile);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(p.dark ? p.onPfocus : 0xFFFFFFFF));
        text.setTextColor(p.ptext2);
    }

    private CharSequence spans(String line, String name) {
        SpannableStringBuilder b = new SpannableStringBuilder(line);
        int end = name != null && line.startsWith(name) ? name.length() : line.length();
        b.setSpan(new ForegroundColorSpan(palette.ptext1), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setSpan(new TypefaceSpan(medium()), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return b;
    }

    private WindowManager.LayoutParams params() {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.y = d(Y);
        p.windowAnimations = 0;
        p.setTitle("LumenSource");
        return p;
    }

    private int d(int designPx) { return Math.round(designPx * px); }

    private Typeface regular() { return regular != null ? regular : (regular = font(LINE_WEIGHT, Typeface.DEFAULT)); }

    private Typeface medium() { return medium != null ? medium : (medium = font(NAME_WEIGHT, Typeface.DEFAULT_BOLD)); }

    /** Inter (assets of lumen-menu, in every projector app) at a weight; the system font when the app has none. */
    private Typeface font(int weight, Typeface fallback) {
        try {
            Typeface t = new Typeface.Builder(c.getAssets(), FONT).setFontVariationSettings("'wght' " + weight).build();
            return t != null ? t : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }
}
