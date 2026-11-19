// SPDX-License-Identifier: Apache-2.0
package org.lumen.sensors;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.lumen.projector.R;
import org.lumen.signal.CardHold;
import org.lumen.signal.OkHold;
import org.lumen.signal.PanelMode;
import org.lumen.signal.PanelMotion;
import org.lumen.signal.SignalLook;
import org.lumen.signal.SignalPanel;
import org.lumen.signal.SignalPlace;
import org.lumen.signal.SignalText;
import org.lumen.ui.LumenTheme;

/**
 * The overlay window of the developer overlays (TYPE_APPLICATION_OVERLAY, as the signal panel): the small sensor card
 * top left that never takes a key, or the full sheet on the right side that takes the keys while open (BACK closes or
 * folds a sheet opened by holding OK, OK runs the sheet's action, up and down scroll). The sheet grows and folds like
 * the signal panel's (PanelMotion). Same places, type and Lumen 2.0 colours as the signal panel
 * (SignalPlace, SignalLook), the card mirrored to the left so it never covers the signal card's corner.
 */
final class SensorWindow {
    private static final String FONT = "fonts/InterVariable.ttf";
    private static final int TITLE = 26, CARD_TITLE = 17, LABEL = 17, VALUE = 18, CARD_LABEL = 15, CARD_VALUE = 17,
            SECTION = 14, ROW_H = 32, CARD_ROW_H = 28, HINT = 15, MONO = 15, RADIUS = 24, SCROLL_STEP = 120;

    private final Context c;
    private final WindowManager wm;
    private final Runnable back;
    private Runnable ok, collapse;
    /** the window's own OK: its DOWN came here (not the press that opened the sheet) */
    private boolean okDown;
    /** a grow or fold runs: a redraw waits for its end */
    private boolean moving;
    private Runnable later;
    private int cardW, cardH;
    private FrameLayout root;
    private ScrollView scroll;
    private PanelMode shownMode = PanelMode.HIDDEN;
    private float px = 1f;
    private Typeface regular, medium, bold;

    SensorWindow(Context c, Runnable back) {
        this.c = c;
        this.wm = c.getSystemService(WindowManager.class);
        this.back = back;
    }

    void setOk(Runnable r) { ok = r; }

    /** OK held in the sheet while the key filter was not running (CardHold folds it otherwise). */
    void setCollapse(Runnable r) { collapse = r; }

    boolean shown() { return root != null && root.getParent() != null; }

    PanelMode mode() { return shown() ? shownMode : PanelMode.HIDDEN; }

    void hide() {
        moving = false;
        later = null;
        if (root != null && root.getParent() != null) {
            try {
                wm.removeViewImmediate(root);
            } catch (Throwable t) {
                Log.w(SensorPanel.TAG, "remove: " + t);
            }
        }
        root = null;
        scroll = null;
        shownMode = PanelMode.HIDDEN;
    }

    /**
     * The sensor card ({@code mode} COMPACT) or the full sensor sheet (DETAIL): {@code hint} the hold hint on the card,
     * {@code held} a sheet opened by holding OK (BACK folds it back).
     */
    void show(PanelMode mode, SensorSheet s, boolean hint, boolean held) {
        if (!mode.visible()) { hide(); return; }
        if (moving) {
            later = () -> show(mode, s, hint, held);
            return;
        }
        SensorSheet.Words w = SensorPanel.words(c);
        SignalLook look = SignalLook.of(LumenTheme.palette(c));
        boolean detail = mode == PanelMode.DETAIL;
        if (root != null && shownMode == PanelMode.DETAIL && !detail && PanelMotion.on() && root.getChildCount() > 0) {
            fold(() -> show(mode, s, hint, held));
            return;
        }
        boolean grow = false;
        if (root != null && shownMode != PanelMode.DETAIL && detail && root.getHeight() > 0) {
            cardW = root.getWidth();
            cardH = root.getHeight();
            grow = PanelMotion.on();
        }
        if (!open(detail)) return;
        int y = scroll == null ? 0 : scroll.getScrollY();
        root.removeAllViews();
        View box = detail ? sheet(w.get("title"), rows(s, look), look,
                held ? null : w.get("hint_hide"), held ? SignalPanel.text(c, SignalText.HINT_FOLD) : w.get("hint_close"))
                : card(s, look, w, hint);
        root.addView(box, fill());
        if (detail && scroll != null && y > 0) scroll.post(() -> { if (scroll != null) scroll.scrollTo(0, y); });
        if (grow) {
            moving = true;
            final FrameLayout r = root;
            PanelMotion.grow(box, cardRect(), RADIUS * px, () -> { if (root == r) moved(); });
        }
    }

    /** The sheet folds into a card sized box at its top right, the keys go back to the film at once, then the card. */
    private void fold(Runnable then) {
        moving = true;
        later = then;
        try {
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) root.getLayoutParams();
            p.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            wm.updateViewLayout(root, p);
        } catch (Throwable t) {
            Log.w(SensorPanel.TAG, "fold: " + t);
        }
        final FrameLayout r = root;
        PanelMotion.fold(root.getChildAt(0), cardRect(), RADIUS * px, () -> {
            if (root != r) return;
            Runnable next = later;
            hide();
            if (next != null) next.run();
        });
    }

    private void moved() {
        moving = false;
        Runnable next = later;
        later = null;
        if (next != null) next.run();
    }

    private android.graphics.Rect cardRect() {
        int w = cardW > 0 ? cardW : d(SignalPlace.CARD_W), h = cardH > 0 ? cardH : d(200);
        return PanelMotion.cardIn(d(SignalPlace.SHEET_W), w, h, d(SignalPlace.CARD_TOP - SignalPlace.SHEET_TOP));
    }

    /** A text sheet (diagnostics): {@code text} line by line in the full sheet. */
    void showText(String title, String text) {
        SignalLook look = SignalLook.of(LumenTheme.palette(c));
        if (!open(true)) return;
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        for (String line : text.split("\n")) {
            TextView t = text(line, MONO, Typeface.MONOSPACE, look.ink);
            t.setPadding(0, d(3), 0, d(3));
            list.addView(t);
        }
        int y = scroll == null ? 0 : scroll.getScrollY();
        root.removeAllViews();
        root.addView(sheet(title, list, look, SensorPanel.words(c).get("hint_refresh"), SensorPanel.words(c).get("hint_close")),
                fill());
        if (scroll != null && y > 0) scroll.post(() -> { if (scroll != null) scroll.scrollTo(0, y); });
    }

    /** The window in the look of {@code detail}: a new one when the look changes. False when it cannot be added. */
    private boolean open(boolean detail) {
        PanelMode mode = detail ? PanelMode.DETAIL : PanelMode.COMPACT;
        if (root == null || shownMode != mode) {
            hide();
            px = Math.max(0.5f, c.getResources().getDisplayMetrics().widthPixels / (float) SignalPlace.SCREEN_W);
            root = new FrameLayout(c);
            root.setClipChildren(false);
            if (detail) {
                root.setFocusable(true);
                root.setFocusableInTouchMode(true);
                root.setDefaultFocusHighlightEnabled(false);
                root.setOnKeyListener(this::onKey);
            }
            try {
                wm.addView(root, params(detail));
            } catch (Throwable t) {
                Log.w(SensorPanel.TAG, "add: " + t);
                root = null;
                return false;
            }
            if (detail) root.requestFocus();
            okDown = false;
        }
        shownMode = mode;
        return true;
    }

    private boolean onKey(View v, int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP) back.run();
            return true;
        }
        if (CardHold.ok(code)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) okDown = true;
                return true;
            }
            OkHold.Act a = OkHold.windowUp(okDown, e.isCanceled(), e.getEventTime() - e.getDownTime());
            okDown = false;
            if (a == OkHold.Act.SHEET_OK && ok != null) ok.run();
            else if (a == OkHold.Act.COLLAPSE && collapse != null) collapse.run();
            return true;
        }
        if ((code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) && scroll != null
                && e.getAction() == KeyEvent.ACTION_DOWN) {
            scroll.smoothScrollBy(0, d(code == KeyEvent.KEYCODE_DPAD_UP ? -SCROLL_STEP : SCROLL_STEP));
        }
        return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private WindowManager.LayoutParams params(boolean detail) {
        int w = d(detail ? SignalPlace.SHEET_W : SignalPlace.CARD_W);
        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        if (!detail) flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(w,
                detail ? SignalPlace.sheetHeight(c.getResources().getDisplayMetrics().heightPixels, px)
                        : ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | (detail ? Gravity.END : Gravity.START);
        p.x = d(detail ? SignalPlace.SHEET_RIGHT : SignalPlace.CARD_RIGHT);
        p.y = d(detail ? SignalPlace.SHEET_TOP : SignalPlace.CARD_TOP);
        p.setTitle("LumenSensors");
        return p;
    }

    private View card(SensorSheet s, SignalLook look, SensorSheet.Words w, boolean hint) {
        LinearLayout box = column(look);
        box.setPadding(d(22), d(18), d(22), d(18));
        box.addView(iconLine(R.drawable.lumen_ic_info, w.get("title"), CARD_TITLE, medium(), look.mute), margins(0, 0, 0, 10));
        for (SensorSheet.Row r : s.cardRows) box.addView(row(r, look, CARD_LABEL, CARD_VALUE, CARD_ROW_H, false));
        if (s.cardRows.isEmpty()) box.addView(text(w.get("none"), CARD_LABEL, regular(), look.mute));
        if (hint) box.addView(text(SignalPanel.text(c, SignalText.HINT_HOLD), HINT, regular(), look.mute), margins(0, 14, 0, 0));
        return box;
    }

    private View rows(SensorSheet s, SignalLook look) {
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        for (SensorSheet.Row r : s.rows) {
            if (r.section) {
                TextView t = text(r.label.toUpperCase(c.getResources().getConfiguration().getLocales().get(0)), SECTION,
                        bold(), look.mute);
                t.setLetterSpacing(0.08f);
                list.addView(t, margins(0, 14, 0, 4));
            } else {
                list.addView(row(r, look, LABEL, VALUE, ROW_H, true));
            }
        }
        if (s.rows.isEmpty()) list.addView(text(SensorPanel.words(c).get("none"), LABEL, regular(), look.mute));
        return list;
    }

    /** The full sheet: {@code okHint} next to the OK action's icon (null = no such hint), {@code backHint} BACK's. */
    private View sheet(String title, View content, SignalLook look, String okHint, String backHint) {
        LinearLayout box = column(look);
        box.setPadding(d(28), d(24), d(28), d(20));
        box.addView(text(title, TITLE, bold(), look.ink));
        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(false);
        scroll.setDefaultFocusHighlightEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(content);
        box.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        box.addView(okHint == null ? hints(look, new int[] {R.drawable.lumen_ic_arrow_back}, new String[] {backHint})
                : hints(look, new int[] {ok == null ? R.drawable.lumen_ic_info : R.drawable.lumen_ic_refresh,
                        R.drawable.lumen_ic_arrow_back}, new String[] {okHint, backHint}), margins(0, 12, 0, 0));
        return box;
    }

    private View row(SensorSheet.Row r, SignalLook look, int label, int value, int h, boolean line) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setMinimumHeight(d(h));
        l.addView(text(r.label, label, regular(), look.mute), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        l.addView(text(r.value, value, medium(), look.ink));
        if (!line) return l;
        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(l);
        View sep = new View(c);
        sep.setBackgroundColor(look.line);
        wrap.addView(sep, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, d(1))));
        return wrap;
    }

    private LinearLayout column(SignalLook look) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(round(look.card, RADIUS));
        return box;
    }

    private View hints(SignalLook look, int[] icons, String[] texts) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < icons.length; i++) {
            FrameLayout key = new FrameLayout(c);
            key.setBackground(round(look.key, 14));
            key.addView(icon(icons[i], 18, look.ink), new FrameLayout.LayoutParams(d(18), d(18), Gravity.CENTER));
            l.addView(key, new LinearLayout.LayoutParams(d(28), d(28)));
            l.addView(text(texts[i], HINT, regular(), look.mute), margins(9, 0, i + 1 < icons.length ? 16 : 0, 0));
        }
        return l;
    }

    private LinearLayout iconLine(int icon, String s, int size, Typeface tf, int ink) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.addView(icon(icon, size + 4, ink));
        TextView t = text(s, size, tf, ink);
        l.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ((LinearLayout.LayoutParams) t.getLayoutParams()).setMarginStart(d(10));
        return l;
    }

    private ImageView icon(int res, int size, int tint) {
        ImageView v = new ImageView(c);
        Drawable dr = c.getDrawable(res);
        if (dr != null) {
            dr = dr.mutate();
            dr.setTint(tint);
        }
        v.setImageDrawable(dr);
        v.setLayoutParams(new LinearLayout.LayoutParams(d(size), d(size)));
        return v;
    }

    private TextView text(String s, int size, Typeface tf, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * px);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setFontFeatureSettings("tnum");
        return t;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius * px);
        return g;
    }

    private static FrameLayout.LayoutParams fill() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(d(l), d(t), d(r), d(b));
        return p;
    }

    private int d(int designPx) { return Math.round(designPx * px); }

    private Typeface regular() { return regular != null ? regular : (regular = font(450, Typeface.DEFAULT)); }

    private Typeface medium() { return medium != null ? medium : (medium = font(540, Typeface.DEFAULT)); }

    private Typeface bold() { return bold != null ? bold : (bold = font(620, Typeface.DEFAULT_BOLD)); }

    private Typeface font(int weight, Typeface fallback) {
        try {
            Typeface t = new Typeface.Builder(c.getAssets(), FONT).setFontVariationSettings("'wght' " + weight).build();
            return t != null ? t : fallback;
        } catch (Throwable e) {
            return fallback;
        }
    }
}
