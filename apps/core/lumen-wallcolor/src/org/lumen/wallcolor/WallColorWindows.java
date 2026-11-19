// SPDX-License-Identifier: Apache-2.0
package org.lumen.wallcolor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.projector.R;
import org.lumen.ui.LumenTheme;
import org.lumen.ui.Palette;
import org.lumen.ui.TvFocus;

/**
 * The screens of the wall colour flow as overlay windows (TYPE_APPLICATION_OVERLAY, like the projector menus): the
 * menu card on the left, the full screen pattern, the result bar at the bottom, the save question in the middle.
 * Sizes are design px of a 1920 x 1080 frame. Keys go to the flow; nothing here talks to the HAL.
 */
final class WallColorWindows implements WallColorView {
    private static final long FADE_MS = 500;
    private final Context c;
    private final WallColorModel model;
    private final WindowManager wm;
    private WallColorFlow flow;
    private View intro, result, ask;
    private PatternView pattern;

    WallColorWindows(Context c, WallColorModel model) {
        this.c = c;
        this.model = model;
        this.wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
    }

    void bind(WallColorFlow f) {
        flow = f;
    }

    // ------------------------------------------------------------------ WallColorView

    @Override public void closeSystemDialogs() {
        try {
            c.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS).putExtra("reason", "LumenWallColor"));
        } catch (Throwable t) {
            Log.w(WallColor.TAG, "close dialogs: " + t);
        }
    }

    @Override public void showIntro() {
        if (intro != null) return;
        Palette p = LumenTheme.palette(c);
        FrameLayout root = keys(new FrameLayout(c), () -> flow.back());
        root.setBackgroundColor(Palette.alpha(p.scrim, 0.72f));
        LinearLayout card = column(p);
        card.setPadding(px(56), px(56), px(56), px(48));
        card.addView(text(R.string.lumen_wallcolor_title, 40, true, p.ptext1));
        PatternView pic = new PatternView(c, true, model.type, "");
        GradientDrawable frame = new GradientDrawable();
        frame.setCornerRadius(px(16));
        pic.setClipToOutline(true);
        pic.setBackground(frame);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(608), px(342));
        lp.topMargin = px(32);
        card.addView(pic, lp);
        TextView desc = text(model.ambientLight ? R.string.lumen_wallcolor_desc_ambient : R.string.lumen_wallcolor_desc,
                26, false, p.ptext2);
        desc.setLineSpacing(0, 1.3f);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(px(608), ViewGroup.LayoutParams.WRAP_CONTENT);
        dp.topMargin = px(28);
        card.addView(desc, dp);
        Button start = button(R.string.lumen_wallcolor_start, () -> flow.start());
        Button reset = button(R.string.lumen_wallcolor_reset, () -> flow.reset());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(px(608), px(72));
        bp.topMargin = px(40);
        card.addView(start, bp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(px(608), px(72));
        rp.topMargin = px(16);
        card.addView(reset, rp);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(px(720), ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START | Gravity.CENTER_VERTICAL);
        cp.setMargins(px(48), px(48), 0, px(48));
        root.addView(card, cp);
        intro = root;
        add(root, true, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER, 0);
        start.requestFocus();
    }

    @Override public void hideIntro() {
        intro = remove(intro);
    }

    @Override public void showPattern(float alpha) {
        if (pattern != null) remove(pattern);
        PatternView v = new PatternView(c, false, model.type, c.getString(R.string.lumen_wallcolor_running));
        v.setFocusable(true);
        v.setOnKeyListener((view, code, e) -> code != KeyEvent.KEYCODE_HOME && code != KeyEvent.KEYCODE_POWER);
        v.setLoopAlpha(alpha);
        pattern = v;
        add(v, true, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER, 0);
        v.requestFocus();
        v.start();
    }

    @Override public void patternAlpha(float alpha) {
        if (pattern != null) pattern.setLoopAlpha(alpha);
    }

    @Override public void closePattern(boolean fade) {
        PatternView v = pattern;
        pattern = null;
        if (v == null) return;
        if (!fade) {
            remove(v);
            return;
        }
        v.animate().alpha(0f).setDuration(FADE_MS).setListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                remove(v);
            }
        });
    }

    @Override public void showResult() {
        if (result != null) return;
        Palette p = LumenTheme.palette(c);
        LinearLayout bar = keys(new LinearLayout(c), () -> flow.backOnResult());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(px(18), px(18), px(18), px(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Palette.alpha(p.panel, 0.94f));
        bg.setCornerRadius(px(86));
        bg.setStroke(1, p.panelLine);
        bar.setBackground(bg);
        Button matched = choice(R.string.lumen_wallcolor_matched, p, () -> flow.focusMatched());
        Button original = choice(R.string.lumen_wallcolor_original, p, () -> flow.focusOriginal());
        bar.addView(matched, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(76)));
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(76));
        op.leftMargin = px(12);
        bar.addView(original, op);
        result = bar;
        add(bar, true, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, px(32));
        matched.requestFocus();
    }

    @Override public void hideResult() {
        result = remove(result);
    }

    @Override public void showSaveAsk() {
        if (ask != null) return;
        Palette p = LumenTheme.palette(c);
        FrameLayout root = keys(new FrameLayout(c), () -> flow.backOnSaveAsk());
        root.setBackgroundColor(Palette.alpha(p.scrim, 0.5f));
        LinearLayout card = column(p);
        card.setPadding(px(56), px(48), px(56), px(48));
        card.addView(text(R.string.lumen_wallcolor_save_title, 36, true, p.ptext1));
        TextView desc = text(R.string.lumen_wallcolor_save_desc, 26, false, p.ptext2);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(px(640), ViewGroup.LayoutParams.WRAP_CONTENT);
        dp.topMargin = px(20);
        card.addView(desc, dp);
        Button keep = button(R.string.lumen_wallcolor_matched, () -> flow.save(true));
        Button orig = button(R.string.lumen_wallcolor_original, () -> flow.save(false));
        LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(px(640), px(72));
        kp.topMargin = px(36);
        card.addView(keep, kp);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(px(640), px(72));
        op.topMargin = px(16);
        card.addView(orig, op);
        root.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        ask = root;
        add(root, true, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER, 0);
        keep.requestFocus();
    }

    @Override public void hideSaveAsk() {
        ask = remove(ask);
        if (result != null) result.requestFocus();
    }

    @Override public void toast(WallColorMessage m) {
        TvFocus.toast(c, text(m), true);
    }

    /** The text of each message (stock toasts of WallColorCore.core, NOT_STARTED is Lumen's). */
    static int text(WallColorMessage m) {
        switch (m) {
            case CAMERA: return R.string.lumen_wallcolor_fail_camera;
            case START_FAILED: return R.string.lumen_wallcolor_fail_start;
            case DISTANCE: return R.string.lumen_wallcolor_fail_distance;
            case LIGHT_CHANGED: return R.string.lumen_wallcolor_fail_light;
            case CUSTOM_COLOUR: return R.string.lumen_wallcolor_fail_custom;
            case PERFORMANCE: return R.string.lumen_wallcolor_fail_performance;
            case AUTO_BRIGHTNESS: return R.string.lumen_wallcolor_fail_auto_brightness;
            case MODE_3D: return R.string.lumen_wallcolor_fail_3d;
            case BIG_SCREEN: return R.string.lumen_wallcolor_fail_big_screen;
            case NOT_STARTED: return R.string.lumen_wallcolor_fail_not_started;
            default: return R.string.lumen_wallcolor_fail_range;
        }
    }

    // ------------------------------------------------------------------ helpers

    private int px(float design) {
        return Math.round(design * c.getResources().getDisplayMetrics().widthPixels / WallColorPattern.W);
    }

    private LinearLayout column(Palette p) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(p.panel);
        bg.setCornerRadius(px(28));
        bg.setStroke(1, p.panelLine);
        l.setBackground(bg);
        return l;
    }

    private TextView text(int res, float size, boolean bold, int color) {
        TextView t = new TextView(c);
        t.setText(res);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }

    private Button button(int res, Runnable click) {
        Button b = new Button(c);
        b.setText(res);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(26));
        TvFocus.button(b);
        b.setOnClickListener(v -> click.run());
        return b;
    }

    /** A choice of the result bar: focusing it applies it (stock OverWindowV2.createItem), OK closes the bar. */
    private Button choice(int res, Palette p, Runnable focused) {
        Button b = new Button(c);
        b.setText(res);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(26));
        b.setPadding(px(40), 0, px(40), 0);
        b.setTextColor(p.ptext2);
        b.setBackground(null);
        b.setOnFocusChangeListener((v, has) -> {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(px(38));
            g.setColor(has ? p.pfocus : 0);
            v.setBackground(g);
            ((Button) v).setTextColor(has ? p.onPfocus : p.ptext2);
            if (has) focused.run();
        });
        b.setOnClickListener(v -> flow.choose());
        return b;
    }

    /** BACK on this window runs {@code r} (BackFrame reads it from the content's tag). */
    private static <V extends View> V keys(V v, Runnable r) {
        v.setTag(R.id.lumen_wallcolor_back, r);
        return v;
    }

    private void add(View v, boolean focusable, int w, int h, int gravity, int y) {
        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        if (!focusable) flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT);
        lp.gravity = gravity;
        lp.y = y;
        lp.setTitle("LumenWallColor");
        BackFrame host = new BackFrame(c, v);
        try {
            wm.addView(host, lp);
        } catch (Throwable t) {
            Log.w(WallColor.TAG, "window: " + t);
        }
    }

    private <V extends View> V remove(V v) {
        if (v == null) return null;
        View host = (View) v.getParent();
        try {
            if (host != null && host.isAttachedToWindow()) wm.removeViewImmediate(host);
        } catch (Throwable t) {
            Log.w(WallColor.TAG, "remove: " + t);
        }
        return null;
    }

    /** The window root: BACK goes to the runnable the content carries (R.id.lumen_wallcolor_back). */
    private static final class BackFrame extends FrameLayout {
        private final View content;

        BackFrame(Context c, View content) {
            super(c);
            this.content = content;
            addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        @Override public boolean dispatchKeyEvent(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                Object r = content.getTag(R.id.lumen_wallcolor_back);
                if (e.getAction() == KeyEvent.ACTION_UP && r instanceof Runnable) ((Runnable) r).run();
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
    }
}
