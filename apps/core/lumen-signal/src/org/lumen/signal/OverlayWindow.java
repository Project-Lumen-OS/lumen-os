// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.signal;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
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
import org.lumen.remote.keys.KeyHook;
import org.lumen.ui.LumenTheme;
import org.lumen.ui.ThemeFade;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * The one window of every developer overlay (the signal panel, the sensor card, the diagnostics sheet), a
 * TYPE_APPLICATION_OVERLAY window like the projector menus: the small card top right that never takes a key, or the
 * full sheet on the right side, grown out of the card and folded back into it (PanelMotion). The callers only build
 * what the window shows from the parts here (type, icons, rows, the hint line).
 *
 * <p>One key rule for all of them (2026-11-23: with the sensor card on screen OK on the gear of the quick menu
 * did nothing): an overlay takes no key and no focus while a Lumen panel is in front (KeyHook.appHasKeys: a menu
 * panel shown, a screen of the app resumed, a window of the app with the focus). Then the sheet's window is not
 * focusable, so the menu has the keys even where it sits below the overlay (the activity host of the X50 family), and
 * the sheet takes the keys back when the panel goes. While it has them: BACK closes or folds it, OK is its own action
 * (only a press that went down here), up and down scroll, the arrows stay with it. Main thread.
 */
public final class OverlayWindow {
    /** The full sheet's keys. */
    public interface Keys {
        void back();

        /** A short OK in the sheet (its own action). */
        void ok();

        /** OK held in the sheet while the key filter was not running (CardHold folds it otherwise). */
        void collapse();
    }

    private static final String FONT = "fonts/InterVariable.ttf";
    public static final int RADIUS = 24, HINT = 15, TITLE = 26, LABEL = 17, VALUE = 18, SMALL = 15, SECTION = 14,
            ROW_H = 32;
    private static final int SCROLL_STEP = 120;
    /** Every overlay on screen: a Lumen panel coming or going changes whether they may take the keys. */
    private static final Set<OverlayWindow> LIVE = Collections.newSetFromMap(new WeakHashMap<>());

    static {
        KeyHook.watch(OverlayWindow::panelsChanged);
    }

    private final Context c;
    private final WindowManager wm;
    private final String title;
    private final int cardHeight;
    private final Keys keys;
    private FrameLayout root;
    private ScrollView scroll;
    private PanelMode shownMode = PanelMode.HIDDEN;
    /** The draw of what is on screen, run again for a theme change, after a grow or a fold. */
    private Runnable shownAgain;
    /** the window's own OK: its DOWN came here (not the press that opened the sheet) */
    private boolean okDown;
    /** a grow or fold runs: a redraw waits for its end ({@link #later}) */
    private boolean moving, folding;
    private Runnable later;
    /** the small card's last size (px), where the sheet grows from and folds into */
    private int cardW, cardH;
    private float px = 1f;
    private Typeface regular, medium, bold;

    /** {@code title}: the window title (dumpsys window); {@code cardHeight}: design px of a card never measured. */
    public OverlayWindow(Context c, String title, int cardHeight, Keys keys) {
        this.c = c;
        this.wm = c.getSystemService(WindowManager.class);
        this.title = title;
        this.cardHeight = cardHeight;
        this.keys = keys;
    }

    public boolean shown() { return root != null && root.getParent() != null; }

    public PanelMode mode() { return shown() ? shownMode : PanelMode.HIDDEN; }

    /** The sheet has the keys: its own window has the input focus and no Lumen panel is in front (CardHold). */
    public boolean hasKeys() { return shown() && takesKeys() && root.hasWindowFocus(); }

    /** The one rule: only the full sheet ever takes keys, never while a Lumen panel is in front, never folding. */
    private boolean takesKeys() { return shownMode == PanelMode.DETAIL && !folding && !KeyHook.appHasKeys(); }

    /** BACK held on the small card: it takes the keys and {@code gone} runs on the press's release (CardKeys). */
    public void takeKeys(Runnable gone) {
        if (!shownMode.card()) gone.run();
        else CardKeys.take(wm, root, gone);
    }

    public void hide() {
        moving = folding = false;
        later = null;
        if (root != null && root.getParent() != null) {
            try {
                wm.removeViewImmediate(root);
            } catch (Throwable t) {
                Log.w(SignalPanel.TAG, title + " remove: " + t);
            }
        }
        LIVE.remove(this);
        root = null;
        scroll = null;
        shownMode = PanelMode.HIDDEN;
    }

    /**
     * Shows what {@code build} makes in the look of {@code mode} (a new window when the card becomes the sheet or
     * back); {@code again} draws the same again (a theme change, a draw that waited for a grow or a fold).
     */
    public void show(PanelMode mode, Supplier<View> build, Runnable again) {
        if (!mode.visible()) { hide(); return; }
        if (moving) {
            later = again;
            return;
        }
        boolean detail = mode == PanelMode.DETAIL;
        if (root != null && shownMode == PanelMode.DETAIL && !detail && PanelMotion.on() && root.getChildCount() > 0) {
            fold(again);
            return;
        }
        boolean grow = false;
        if (root == null || (shownMode == PanelMode.DETAIL) != detail) {
            if (root != null && shownMode.card() && detail && root.getHeight() > 0) {
                cardW = root.getWidth();
                cardH = root.getHeight();
                grow = PanelMotion.on();
            }
            hide();
            if (!add(mode)) return;
        }
        shownMode = mode;
        shownAgain = again;
        int y = scroll == null ? 0 : scroll.getScrollY();
        root.removeAllViews();
        View box = build.get();
        root.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (detail && scroll != null && y > 0) scroll.post(() -> { if (scroll != null) scroll.scrollTo(0, y); });
        if (grow) {
            moving = true;
            final FrameLayout r = root;
            PanelMotion.grow(box, cardRect(), RADIUS * px, () -> { if (root == r) moved(); });
        }
    }

    private boolean add(PanelMode mode) {
        boolean detail = mode == PanelMode.DETAIL;
        shownMode = mode;
        px = Math.max(0.5f, c.getResources().getDisplayMetrics().widthPixels / (float) SignalPlace.SCREEN_W);
        root = new FrameLayout(c);
        root.setClipChildren(false);
        final FrameLayout r = root;
        ThemeFade.follow(r, p -> {
            if (root == r && shownAgain != null && shownMode.visible()) shownAgain.run();
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
            Log.w(SignalPanel.TAG, title + " add: " + t);
            root = null;
            shownMode = PanelMode.HIDDEN;
            return false;
        }
        LIVE.add(this);
        if (takesKeys()) root.requestFocus();
        okDown = false;
        return true;
    }

    /** A Lumen panel came or went (KeyHook): every sheet on screen gives up or takes back the keys. */
    private static void panelsChanged() {
        for (OverlayWindow w : LIVE.toArray(new OverlayWindow[0])) w.refreshKeys();
    }

    private void refreshKeys() {
        if (!shown() || shownMode != PanelMode.DETAIL || folding) return;
        try {
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) root.getLayoutParams();
            int flags = focusFlags(p.flags, takesKeys());
            if (flags == p.flags) return;
            p.flags = flags;
            wm.updateViewLayout(root, p);
            okDown = false;
            if (takesKeys()) root.requestFocus();
        } catch (Throwable t) {
            Log.w(SignalPanel.TAG, title + " keys: " + t);
        }
    }

    private static int focusFlags(int flags, boolean takes) {
        return takes ? flags & ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                : flags | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    }

    /** The sheet folds into the card's place, the keys go back at once; {@code then} swaps the windows. */
    private void fold(Runnable then) {
        moving = folding = true;
        later = then;
        try {
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) root.getLayoutParams();
            p.flags = focusFlags(p.flags, false);
            wm.updateViewLayout(root, p);
        } catch (Throwable t) {
            Log.w(SignalPanel.TAG, title + " fold: " + t);
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

    /** The small card's place in the sheet's box (the card and the sheet share the right edge). */
    private Rect cardRect() {
        int w = cardW > 0 ? cardW : d(SignalPlace.CARD_W), h = cardH > 0 ? cardH : d(cardHeight);
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
            if (a == OkHold.Act.SHEET_OK) keys.ok();
            else if (a == OkHold.Act.COLLAPSE) keys.collapse();
            return true;
        }
        if ((code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) && scroll != null
                && e.getAction() == KeyEvent.ACTION_DOWN) {
            scroll.smoothScrollBy(0, d(code == KeyEvent.KEYCODE_DPAD_UP ? -SCROLL_STEP : SCROLL_STEP));
        }
        // arrows stay with the sheet while it has the keys: nothing reaches the film or the console
        return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private WindowManager.LayoutParams params(boolean detail) {
        int w = d(detail ? SignalPlace.SHEET_W : SignalPlace.CARD_W);
        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        if (!detail) flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        else flags = focusFlags(flags, takesKeys());
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(w,
                detail ? SignalPlace.sheetHeight(c.getResources().getDisplayMetrics().heightPixels, px)
                        : ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.END;
        p.x = d(detail ? SignalPlace.SHEET_RIGHT : SignalPlace.CARD_RIGHT);
        p.y = d(detail ? SignalPlace.SHEET_TOP : SignalPlace.CARD_TOP);
        p.setTitle(title);
        return p;
    }

    // ------------------------------------------------------------------ parts the callers build with

    /** The look of the current theme (the shared Lumen 2.0 palette). */
    public SignalLook look() { return SignalLook.of(LumenTheme.palette(c)); }

    /** The sheet's scrolling list (up and down scroll it), {@code content} inside. */
    public ScrollView scroll(View content) {
        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(false);
        scroll.setDefaultFocusHighlightEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(content);
        return scroll;
    }

    /** A card or sheet box: vertical, the card colour, round corners. */
    public LinearLayout column(SignalLook look) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(round(look.card, RADIUS));
        return box;
    }

    /** Always on the small card (2026-11-21): hold OK for the full sheet, hold BACK to hide the card. */
    public View cardHint(SignalLook look) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.addView(text(SignalPanel.text(c, SignalText.HINT_HOLD), HINT, regular(), look.mute), margins(0, 0, 10, 0));
        l.addView(hints(look, new int[] {R.drawable.lumen_ic_radio_on, R.drawable.lumen_ic_arrow_back},
                new String[] {SignalPanel.text(c, SignalText.HINT_MORE), SignalPanel.text(c, SignalText.HINT_GONE)}));
        return l;
    }

    /** Remote keys as icons, each with its words. */
    public View hints(SignalLook look, int[] icons, String[] texts) {
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

    /** An icon, then {@code s} filling the rest of the line. */
    public LinearLayout iconLine(int icon, String s, int size, Typeface tf, int ink, int iconInk) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.addView(icon(icon, size + 4, iconInk));
        TextView t = text(s, size, tf, ink);
        l.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ((LinearLayout.LayoutParams) t.getLayoutParams()).setMarginStart(d(10));
        return l;
    }

    /** {@code row} with a hairline under it (a row of the full sheet). */
    public View lined(View row, SignalLook look) {
        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(row);
        View line = new View(c);
        line.setBackgroundColor(look.line);
        wrap.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, d(1))));
        return wrap;
    }

    /** A horizontal line of {@code minHeight} design px, children centred. */
    public LinearLayout line(int minHeight) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setMinimumHeight(d(minHeight));
        return l;
    }

    public ImageView icon(int res, int size, int tint) {
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

    public TextView text(String s, int size, Typeface tf, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * px);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setFontFeatureSettings("tnum");
        return t;
    }

    public GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius * px);
        return g;
    }

    public LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(d(l), d(t), d(r), d(b));
        return p;
    }

    /** Full width (a row whose weight or alignment needs the box's width), top and bottom margins. */
    public LinearLayout.LayoutParams fill(int t, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, d(t), 0, d(b));
        return p;
    }

    /** The rest of a column's height (the sheet's list). */
    public static LinearLayout.LayoutParams rest() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
    }

    /** Design px at 1920x1080 on this screen. */
    public int d(int designPx) { return Math.round(designPx * px); }

    public Typeface regular() { return regular != null ? regular : (regular = font(450, Typeface.DEFAULT)); }

    public Typeface medium() { return medium != null ? medium : (medium = font(540, Typeface.DEFAULT)); }

    public Typeface bold() { return bold != null ? bold : (bold = font(620, Typeface.DEFAULT_BOLD)); }

    /** Inter (the menu's font, assets of lumen-menu) at a weight; the system font when the app has none. */
    private Typeface font(int weight, Typeface fallback) {
        try {
            Typeface t = new Typeface.Builder(c.getAssets(), FONT).setFontVariationSettings("'wght' " + weight).build();
            return t != null ? t : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }
}
