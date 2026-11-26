// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.signal;

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
import org.lumen.ui.LumenTheme;
import org.lumen.ui.ThemeFade;

/**
 * The panel's overlay window (TYPE_APPLICATION_OVERLAY, like the projector menus): the small card top right that
 * never takes a key, or the full panel on the right side that takes the keys while open (BACK closes or folds a panel
 * opened by holding OK, OK switches the option row, up and down scroll a long list). The full panel grows out of the
 * card and folds back into it (PanelMotion). Places are SignalPlace's (title safe, below an app's header),
 * sizes are design px at 1920x1080 scaled to the screen, colours the shared Lumen 2.0 palette (SignalLook).
 */
final class SignalWindow {
    /** Keys of the full panel, main thread. */
    interface Keys {
        void back();

        void switchPopup();

        /** OK held in the full panel while the key filter was not running (CardHold folds it otherwise). */
        void collapse();
    }

    private static final String FONT = "fonts/InterVariable.ttf";
    /** Type of the small card and of the full panel (design px): small and calm, 2026-11-18. */
    private static final int CARD_PORT = 17, CARD_BIG = 30, CHIP = 16, CHIP_H = 30, CARD_LINE = 17, CARD_LABEL = 15,
            CARD_ROW_H = 28, HINT = 15,
            TITLE = 26, LABEL = 17, VALUE = 18, SMALL = 15, SECTION = 14, ROW_H = 32, RADIUS = 24;
    private static final int SCROLL_STEP = 120;

    private final Context c;
    private final WindowManager wm;
    private final Keys keys;
    private FrameLayout root;
    private ScrollView scroll;
    private PanelMode shownMode = PanelMode.HIDDEN;
    /** What is drawn now, drawn again in the new colours when the theme changes while the window shows. */
    private SignalSheet shownSheet;
    private boolean shownPopup, shownHint, shownHeld;
    /** the window's own OK: its DOWN came here (not the press that opened the panel) */
    private boolean okDown;
    /** a grow or fold runs: a redraw waits for its end ({@link #later}) */
    private boolean moving;
    private Runnable later;
    /** the small card's last size (px), where the full panel grows from and folds into */
    private int cardW, cardH;
    private float px = 1f;
    private Typeface regular, medium, bold;

    SignalWindow(Context c, Keys keys) {
        this.c = c;
        this.wm = c.getSystemService(WindowManager.class);
        this.keys = keys;
    }

    boolean shown() { return root != null && root.getParent() != null; }

    void hide() {
        moving = false;
        later = null;
        if (root != null && root.getParent() != null) {
            try {
                wm.removeViewImmediate(root);
            } catch (Throwable t) {
                Log.w(SignalPanel.TAG, "remove: " + t);
            }
        }
        root = null;
        scroll = null;
        shownMode = PanelMode.HIDDEN;
    }

    /**
     * Draws {@code s} in the look of {@code mode} (a new window when the look changes): {@code hint} the hold hint on
     * the small card, {@code held} a full panel opened by holding OK (BACK folds it back).
     */
    void show(PanelMode mode, SignalSheet s, boolean popupOn, boolean hint, boolean held) {
        if (!mode.visible()) { hide(); return; }
        if (moving) {
            later = () -> show(mode, s, popupOn, hint, held);
            return;
        }
        SignalLook look = SignalLook.of(LumenTheme.palette(c));
        boolean detail = mode == PanelMode.DETAIL;
        if (root != null && shownMode == PanelMode.DETAIL && mode.card() && PanelMotion.on() && root.getChildCount() > 0) {
            fold(() -> show(mode, s, popupOn, hint, held));
            return;
        }
        boolean grow = false;
        if (root == null || shownMode.card() != mode.card()) {
            if (root != null && shownMode.card() && root.getHeight() > 0) {
                cardW = root.getWidth();
                cardH = root.getHeight();
                grow = detail && PanelMotion.on();
            }
            hide();
            px = Math.max(0.5f, c.getResources().getDisplayMetrics().widthPixels / (float) SignalPlace.SCREEN_W);
            root = new FrameLayout(c);
            root.setClipChildren(false);
            final FrameLayout r = root;
            ThemeFade.follow(r, p -> {
                if (root == r && shownSheet != null && shownMode.visible()) {
                    show(shownMode, shownSheet, shownPopup, shownHint, shownHeld);
                }
            });
            if (detail) {
                root.setFocusable(true);
                root.setFocusableInTouchMode(true);
                root.setDefaultFocusHighlightEnabled(false);
                root.setOnKeyListener(this::onKey);
            }
            try {
                wm.addView(root, params(detail));
            } catch (Throwable t) {
                Log.w(SignalPanel.TAG, "add: " + t);
                root = null;
                return;
            }
            if (detail) root.requestFocus();
            okDown = false;
        }
        shownMode = mode;
        shownSheet = s;
        shownPopup = popupOn;
        shownHint = hint;
        shownHeld = held;
        int y = scroll == null ? 0 : scroll.getScrollY();
        root.removeAllViews();
        View box = detail ? sheet(s, look, popupOn, held) : card(s, look, hint);
        root.addView(box,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (detail && scroll != null && y > 0) scroll.post(() -> { if (scroll != null) scroll.scrollTo(0, y); });
        if (grow) {
            moving = true;
            final FrameLayout r = root;
            PanelMotion.grow(box, cardRect(), RADIUS * px, () -> { if (root == r) moved(); });
        }
    }

    /** The full panel folds into the card's place, the keys go back to the film at once; {@code then} swaps windows. */
    private void fold(Runnable then) {
        moving = true;
        later = then;
        try {
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) root.getLayoutParams();
            p.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            wm.updateViewLayout(root, p);
        } catch (Throwable t) {
            Log.w(SignalPanel.TAG, "fold: " + t);
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

    /** The small card's place in the full panel's box (the card and the panel share the right edge). */
    private android.graphics.Rect cardRect() {
        int w = cardW > 0 ? cardW : d(SignalPlace.CARD_W), h = cardH > 0 ? cardH : d(220);
        return PanelMotion.cardIn(d(SignalPlace.SHEET_W), w, h, d(SignalPlace.CARD_TOP - SignalPlace.SHEET_TOP));
    }

    private boolean onKey(View v, int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP) keys.back();
            return true;
        }
        if (CardHold.ok(code)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) okDown = true;
                return true;
            }
            OkHold.Act a = OkHold.windowUp(okDown, e.isCanceled(), e.getEventTime() - e.getDownTime());
            okDown = false;
            if (a == OkHold.Act.SHEET_OK) keys.switchPopup();
            else if (a == OkHold.Act.COLLAPSE) keys.collapse();
            return true;
        }
        if ((code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) && scroll != null
                && e.getAction() == KeyEvent.ACTION_DOWN) {
            scroll.smoothScrollBy(0, d(code == KeyEvent.KEYCODE_DPAD_UP ? -SCROLL_STEP : SCROLL_STEP));
        }
        // arrows stay with the panel while it is open: nothing reaches the film or the console
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
        p.gravity = Gravity.TOP | Gravity.END;
        p.x = d(detail ? SignalPlace.SHEET_RIGHT : SignalPlace.CARD_RIGHT);
        p.y = d(detail ? SignalPlace.SHEET_TOP : SignalPlace.CARD_TOP);
        p.setTitle("LumenSignal");
        return p;
    }

    // ------------------------------------------------------------------ the small card (design A1 / A3, smaller)

    private View card(SignalSheet s, SignalLook look, boolean hint) {
        LinearLayout box = column(look);
        box.setPadding(d(22), d(18), d(22), d(18));
        box.addView(iconLine(R.drawable.lumen_ic_hdmi, s.port, CARD_PORT, medium(), look.mute, look.mute));
        if (s.big != null) {
            TextView big = text(s.big, CARD_BIG, bold(), look.ink);
            big.setSingleLine(true);
            box.addView(big, margins(0, 10, 0, 12));
        }
        for (SignalSheet.Row r : s.cardRows) box.addView(cardRow(r, look), fill(0, 0));
        if (s.judder == Judder.JUDDER) box.addView(status(s, look), fill(12, 0));
        if (hint) box.addView(text(SignalPanel.words(c).get(SignalText.HINT_HOLD), HINT, regular(), look.mute),
                margins(0, 14, 0, 0));
        return box;
    }

    // ------------------------------------------------------------------ the full panel (design A2 / A4, smaller)

    private View sheet(SignalSheet s, SignalLook look, boolean popupOn, boolean held) {
        SignalText.Words w = SignalPanel.words(c);
        LinearLayout box = column(look);
        box.setPadding(d(28), d(24), d(28), d(20));
        box.addView(text(w.get(SignalText.TITLE), TITLE, bold(), look.ink));
        box.addView(text(s.port, LABEL, regular(), look.mute), margins(0, 2, 0, 0));
        if (!s.chips.isEmpty()) box.addView(chips(s, look), fill(12, 0));
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        for (SignalSheet.Row r : s.rows) list.addView(r.section ? section(r, look) : row(r, look),
                r.section ? fill(14, 2) : fill(0, 0));
        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(false);
        scroll.setDefaultFocusHighlightEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(list);
        box.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        box.addView(popupRow(w, look, popupOn), fill(12, 10));
        box.addView(held ? hints(look, new int[] {R.drawable.lumen_ic_arrow_back}, new String[] {w.get(SignalText.HINT_FOLD)})
                : hints(look, new int[] {R.drawable.lumen_ic_info, R.drawable.lumen_ic_arrow_back},
                        new String[] {w.get(SignalText.HINT_HIDE), w.get(SignalText.HINT_CLOSE)}));
        return box;
    }

    private View section(SignalSheet.Row r, SignalLook look) {
        int icon = SignalText.SEC_INPUT.equals(r.key) ? R.drawable.lumen_ic_input
                : SignalText.SEC_SOUND.equals(r.key) ? R.drawable.lumen_ic_volume : R.drawable.lumen_ic_keystone;
        LinearLayout l = iconLine(icon, r.label.toUpperCase(c.getResources().getConfiguration().getLocales().get(0)),
                SECTION, bold(), look.mute, look.mute);
        ((TextView) l.getChildAt(1)).setLetterSpacing(0.08f);
        return l;
    }

    private View row(SignalSheet.Row r, SignalLook look) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setMinimumHeight(d(ROW_H));
        l.setPadding(0, d(3), 0, d(3));
        l.addView(text(r.label, LABEL, regular(), look.mute), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (r.small != null) l.addView(text(r.small, SMALL, regular(), look.mute), margins(8, 0, 8, 0));
        l.addView(text(r.value, VALUE, medium(), look.tone(r.tone)));
        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(l);
        View line = new View(c);
        line.setBackgroundColor(look.line);
        wrap.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, d(1))));
        return wrap;
    }

    /** One line of the small card: the label left, quiet; the value right (the setting small before it). */
    private View cardRow(SignalSheet.Row r, SignalLook look) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setMinimumHeight(d(CARD_ROW_H));
        l.addView(text(r.label, CARD_LABEL, regular(), look.mute), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (r.small != null) l.addView(text(r.small, CARD_LABEL, regular(), look.mute), margins(8, 0, 10, 0));
        l.addView(text(r.value, CARD_LINE, medium(), look.tone(r.tone)));
        return l;
    }

    private View popupRow(SignalText.Words w, SignalLook look, boolean on) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(d(14), d(8), d(10), d(8));
        l.setBackground(round(look.chip, 14));
        l.addView(text(w.get(SignalText.POPUP_ROW), LABEL, medium(), look.ink),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView key = text("OK", SMALL, bold(), look.ink);
        key.setGravity(Gravity.CENTER);
        key.setBackground(round(look.key, 15));
        key.setPadding(d(9), 0, d(9), 0);
        l.addView(key, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, d(30)));
        ImageView sw = icon(on ? R.drawable.lumen_ic_toggle_on : R.drawable.lumen_ic_toggle_off, 36, on ? look.accent : look.mute);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(d(36), d(36));
        lp.setMarginStart(d(10));
        l.addView(sw, lp);
        return l;
    }

    // ------------------------------------------------------------------ parts

    private LinearLayout column(SignalLook look) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(round(look.card, RADIUS));
        return box;
    }

    /** The badges, wrapped onto a second line when they do not fit (never cut). */
    private View chips(SignalSheet s, SignalLook look) {
        Flow l = new Flow(c, d(6));
        for (SignalSheet.Chip ch : s.chips) {
            TextView t = text(ch.text, CHIP, medium(), ch.accent ? look.onAccent : look.chipInk);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setPadding(d(12), 0, d(12), 0);
            t.setBackground(round(ch.accent ? look.accent : look.chip, CHIP_H / 2));
            l.addView(t, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, d(CHIP_H)));
        }
        return l;
    }

    private View status(SignalSheet s, SignalLook look) {
        boolean ok = s.judder == Judder.SMOOTH;
        LinearLayout l = iconLine(ok ? R.drawable.lumen_ic_check_circle : R.drawable.lumen_ic_warning, s.verdict, CARD_LINE,
                medium(), look.ink, ok ? look.ok : look.warn);
        l.setGravity(ok ? Gravity.CENTER_VERTICAL : Gravity.TOP);
        l.setPadding(d(12), d(9), d(12), d(9));
        l.setBackground(round(ok ? look.okBg : look.warnBg, 12));
        return l;
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

    private LinearLayout iconLine(int icon, String s, int size, Typeface tf, int ink, int iconInk) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.addView(icon(icon, size + 4, iconInk));
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

    private LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(d(l), d(t), d(r), d(b));
        return p;
    }

    /** Full width (a row whose weight or alignment needs the card's width), top and bottom margins. */
    private LinearLayout.LayoutParams fill(int t, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, d(t), 0, d(b));
        return p;
    }

    private int d(int designPx) { return Math.round(designPx * px); }

    private Typeface regular() { return regular != null ? regular : (regular = font(450, Typeface.DEFAULT)); }

    private Typeface medium() { return medium != null ? medium : (medium = font(540, Typeface.DEFAULT)); }

    private Typeface bold() { return bold != null ? bold : (bold = font(620, Typeface.DEFAULT_BOLD)); }

    /** Inter (the menu's font, assets of lumen-menu) at a weight; the system font when the app has none. */
    private Typeface font(int weight, Typeface fallback) {
        try {
            Typeface t = new Typeface.Builder(c.getAssets(), FONT).setFontVariationSettings("'wght' " + weight).build();
            return t != null ? t : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** Children left to right, a new line when the next one does not fit; {@code gap} between them and the lines. */
    private static final class Flow extends ViewGroup {
        private final int gap;

        Flow(Context c, int gap) {
            super(c);
            this.gap = gap;
        }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            int max = MeasureSpec.getSize(wSpec), x = 0, y = 0, line = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                ch.measure(MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(ch.getLayoutParams().height, MeasureSpec.EXACTLY));
                if (x > 0 && x + ch.getMeasuredWidth() > max) {
                    x = 0;
                    y += line + gap;
                    line = 0;
                }
                x += ch.getMeasuredWidth() + gap;
                line = Math.max(line, ch.getMeasuredHeight());
            }
            setMeasuredDimension(max, y + line);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int max = r - l, x = 0, y = 0, line = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                int w = ch.getMeasuredWidth(), h = ch.getMeasuredHeight();
                if (x > 0 && x + w > max) {
                    x = 0;
                    y += line + gap;
                    line = 0;
                }
                ch.layout(x, y, x + w, y + h);
                x += w + gap;
                line = Math.max(line, h);
            }
        }
    }
}
