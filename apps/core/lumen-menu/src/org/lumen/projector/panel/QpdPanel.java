package org.lumen.projector.panel;

import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.lumen.projector.R;
import org.lumen.projector.ui.ChoiceRow;
import org.lumen.projector.ui.EqBandsRow;
import org.lumen.projector.ui.OverlayHost;
import org.lumen.projector.ui.Page;
import org.lumen.projector.ui.PageAccess;
import org.lumen.projector.ui.PagedPanel;
import org.lumen.projector.ui.SliderRow;
import org.lumen.projector.ui.Theme;
import org.lumen.ui.Palette;
import org.lumen.ui.ThemeFade;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * The quick panel window of layout D. The card (tiles, slider) sits top left; every page the controller pushes
 * (section pages, their sub-pages, confirmations) opens in a popup card beside the tile it belongs to. It is a
 * PagedPanel for the controller (push / pop / currentPage / refreshPage, DialogPanel.confirmPage), with its own page
 * stack: the root page is the card, pages above it are the popup. The rows themselves are the controller's, so
 * every action, rule and vendor call stays as it was; only where and how they are drawn changed (QpdRowFrame).
 *
 * All settings (the round gear button): the card gives way to the prototype's All settings screen, a rail of round
 * category buttons (the section pages), the list of the chosen page and a hint card for a row that has a stock hint
 * picture. On the stack that is [root, category page, pages pushed from it...]; moving on the rail swaps the category
 * page.
 *
 * Keys: the controller sees every key first (Boost, 3D, game gates). On the card the arrows follow the prototype's
 * focus map (QpdLayout.move, RIGHT on the last cell opens its popup); in a popup UP / DOWN move between rows, LEFT
 * closes it (unless the row steps with LEFT / RIGHT), BACK goes up one level. All settings: UP / DOWN on the rail
 * change the category, RIGHT or OK go into the list, LEFT or BACK in the list go back to the rail, LEFT or BACK on
 * the rail close All settings.
 */
final class QpdPanel extends PagedPanel {
    private static final String TAG = "Z9xPanel";
    private static final long OPEN_MS = 320, CLOSE_MS = 160;

    interface Host {
        boolean onKey(KeyEvent ev);
        void onClosed(QpdPanel p);
        /** The shared theme changed while this window shows: re-style card and popup in {@code p} (under the fade). */
        void onTheme(Palette p);
        /** Hint block for a popup page, or null (no picture for that item). */
        View tipFor(Page p);
        /** Hint block of a row in the All settings list (the popup hint of the same item), or null. */
        View tipForRow(View row);
        /** Tile key the popup of this section page opens beside. */
        String anchorFor(Page sectionPage);
        boolean hasPopup(String key);
        void openPopup(String key);
        /** The icon of a page's panel header (its tile's icon), 0 for none. */
        int iconFor(Page p);
        /** The value line under a page's panel title (its tile's value), or null. */
        CharSequence valueFor(Page p);
        /** BACK while the pencil editor shows: its own step back (true), false when it is not shown. */
        boolean onSideBack();
    }

    /** One All settings category: a section page with its rail label and icon. */
    static final class Cat {
        final String key;
        final CharSequence label;
        final int icon;
        final Page page;

        Cat(String key, CharSequence label, int icon, Page page) {
            this.key = key;
            this.label = label;
            this.icon = icon;
            this.page = page;
        }
    }

    private final Host host;
    private final QpdCard card;
    private final boolean bar;
    private final ArrayDeque<Page> stack = new ArrayDeque<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Context ctx;
    private FrameLayout root;
    /** Follows the shared theme live while the window shows (lumen-ui ThemeFade cross fade). */
    private ThemeFade fade;
    private Popup popup;
    private OutputSheet sheet;
    /** The pencil editor's sheet in the panel's place (showSide), or null. */
    private View side;
    private QpdAll all;
    private View accentRow;
    private QpdTile held;
    private final Paint accent = new Paint();
    // All settings
    private List<Cat> cats;
    private int cat = -1;
    private String allAnchor;
    private boolean allFocusList;
    private View allFocusRow;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            card.tick();
            main.postDelayed(this, 10_000);
        }
    };
    private final ViewTreeObserver.OnGlobalFocusChangeListener focusWatch = (o, n) -> onFocusMoved(n);

    QpdPanel(Page rootPage, QpdCard card, Host host, boolean bar) {
        super(rootPage);
        this.card = card;
        this.host = host;
        this.bar = bar;
        stack.push(rootPage);
        // focused row of a type QpdRowFrame does not draw (equalizer bands, extension rows): the shared rows draw a
        // white pill with dark text; this maps white to the accent and dark to white (one hardware layer, that row)
        float r = (59 - 255) / 255f, g = (120 - 255) / 255f, b = (231 - 255) / 255f;
        accent.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                r, 0, 0, 0, 255,
                0, g, 0, 0, 255,
                0, 0, b, 0, 255,
                0, 0, 0, 1, 0})));
    }

    boolean isBar() { return bar; }

    /** The menu sits on the right side (MenuLook, read when the view is built). */
    private boolean right;

    /** A child placed {@code design} px in from the menu's side: the left edge, or the right one (MenuLook). */
    private void side(FrameLayout.LayoutParams lp, float design) {
        lp.gravity = Gravity.TOP | (right ? Gravity.RIGHT : Gravity.LEFT);
        if (right) lp.rightMargin = px(design);
        else lp.leftMargin = px(design);
    }

    /** True when the menu sits on the right side (MenuLook): the panes, slides and hint card are mirrored. */
    boolean isRight() { return right; }

    /** {@code d} px towards the screen edge the menu sits on (a slide in or out starts there). */
    private float edge(float d) { return right ? d : -d; }

    /** A page level change slides in from the deeper side: right of the menu, left of a mirrored one. */
    private int deeper(int direction) { return right ? -direction : direction; }

    /**
     * The compact menu (MenuLook): the page is laid out larger by 1 / scale and drawn at the scale from the menu's side,
     * so the same rows take less room and more of them fit. Keys and focus are untouched.
     */
    static final class LookFrame extends FrameLayout {
        private final View inner;
        private final float scale;
        private final boolean right;

        LookFrame(Context c, View inner, float scale, boolean right) {
            super(c);
            this.inner = inner;
            this.scale = scale;
            this.right = right;
            setClipChildren(false);
            addView(inner);
        }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
            inner.measure(MeasureSpec.makeMeasureSpec(Math.round(w / scale), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Math.round(h / scale), MeasureSpec.EXACTLY));
            setMeasuredDimension(w, h);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int iw = inner.getMeasuredWidth(), ih = inner.getMeasuredHeight();
            int left = right ? (r - l) - iw : 0;
            inner.layout(left, 0, left + iw, ih);
            inner.setPivotX(right ? iw : 0);
            inner.setPivotY(0);
            inner.setScaleX(scale);
            inner.setScaleY(scale);
        }
    }

    /** True while All settings is shown (or about to be). */
    boolean isAllMode() { return cats != null && stack.size() > 1; }

    /** Key of the All settings category shown, or null. */
    String allCategory() { return isAllMode() && cat >= 0 ? cats.get(cat).key : null; }

    // ------------------------------------------------------------------ PagedPanel API (own stack)
    @Override public Page currentPage() { return stack.peek(); }

    @Override public int depth() { return stack.size(); }

    @Override
    public void push(Page p) {
        Page cur = stack.peek();
        if (cur != null && cur != p) {
            if (stack.size() > 1) PageAccess.setFocusIndex(cur, focusedIndex());
            PageAccess.hidden(cur);
        }
        stack.push(p);
        if (isAllMode()) allFocusList = true;
        if (root != null) render(1);
    }

    @Override
    public boolean pop() {
        if (stack.size() <= 1) return false;
        Page top = stack.pop();
        PageAccess.hidden(top);
        if (stack.size() <= 1) cats = null;              // leaving All settings
        else if (isAllMode()) allFocusList = true;
        if (root != null) render(-1);
        return true;
    }

    @Override
    public void refreshPage() { refreshPage(-1); }

    /** Repaints the shown page with the focus on row {@code focusIndex}, or where it is now (-1). */
    void refreshPage(int focusIndex) {
        if (root == null || stack.size() <= 1) return;
        PageAccess.setFocusIndex(stack.peek(), focusIndex >= 0 ? focusIndex : focusedIndex());
        render(0);
    }

    @Override
    protected boolean onBack() {
        if (side != null && host.onSideBack()) return true;
        if (stack.peek() instanceof SheetPage) return pop();
        if (isAllMode() && root != null) {
            if (all.inList(root.findFocus())) {
                if (stack.size() > 2) return pop();
                focusRail();
                return true;
            }
            closeAll();
            return true;
        }
        return pop();
    }

    @Override
    protected long autoHideMs() { return bar ? QpdLayout.BAR_HIDE_MS : DEFAULT_AUTO_HIDE_MS; }

    @Override
    protected WindowManager.LayoutParams onCreateLayoutParams(Context c) {
        int w = Math.min(c.getResources().getDisplayMetrics().widthPixels, Theme.px(c, QpdLayout.WINDOW_W));
        int side = MenuLookPrefs.right(c) ? Gravity.RIGHT : Gravity.LEFT;     // the user's side (MenuLook)
        return OverlayHost.params(true, w, ViewGroup.LayoutParams.MATCH_PARENT, side | Gravity.TOP);
    }

    // ------------------------------------------------------------------ All settings
    /**
     * Opens All settings on category {@code index} (the window may not exist yet: a direct open). Focus on the rail,
     * or on {@code focusRow} in that category's list when given (BACK from a screen the list opened).
     */
    void openAll(List<Cat> categories, int index, String anchorKey, View focusRow) {
        if (categories == null || categories.isEmpty()) return;
        while (stack.size() > 1) PageAccess.hidden(stack.pop());
        cats = new ArrayList<>(categories);
        cat = Math.max(0, Math.min(cats.size() - 1, index));
        allAnchor = anchorKey;
        if (anchorKey != null) card.remember(anchorKey);
        Page p = cats.get(cat).page;
        int fi = focusRow == null ? -1 : p.rows().indexOf(focusRow);
        allFocusList = fi >= 0;
        allFocusRow = fi >= 0 ? focusRow : null;
        PageAccess.setFocusIndex(p, fi);
        stack.push(p);
        if (root != null) render(1);
    }

    private void selectCategory(int index) {
        if (!isAllMode() || index == cat || index < 0 || index >= cats.size()) return;
        while (stack.size() > 2) PageAccess.hidden(stack.pop());
        PageAccess.hidden(stack.pop());
        cat = index;
        Page p = cats.get(cat).page;
        PageAccess.setFocusIndex(p, -1);
        stack.push(p);
        allFocusList = false;
        render(0);
    }

    private void closeAll() {
        while (stack.size() > 1) PageAccess.hidden(stack.pop());
        cats = null;
        render(-1);
    }

    private void focusRail() {
        allFocusList = false;
        if (all != null) all.focusRail(cat);
    }

    /** Rail OK / RIGHT: into the list (first row, or the row remembered for this page). */
    void enterList() {
        if (!isAllMode() || all == null) return;
        allFocusList = true;
        all.focusList(PageAccess.focusIndex(stack.peek()));
    }

    // ------------------------------------------------------------------ views
    private int px(float d) { return Theme.px(ctx, d); }

    @Override
    protected View onCreateView(Context c) {
        ctx = c;
        right = MenuLookPrefs.right(c);
        root = new FrameLayout(c);
        root.setClipChildren(false);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        fade = ThemeFade.follow(root, host::onTheme);
        if (!bar) {
            // scrim: the video darkens towards the panel (prototype .scrim, 0 % .5, 40 % .18, 60 % 0 of the screen)
            final float scrimW = Theme.pxf(c, QpdLayout.SCRIM_W);
            ShapeDrawable sd = new ShapeDrawable(new RectShape());
            sd.setShaderFactory(new ShapeDrawable.ShaderFactory() {
                @Override public Shader resize(int w, int h) {
                    float from = right ? w : 0, to = right ? w - scrimW : scrimW;   // darker towards the menu's side
                    return new LinearGradient(from, 0, to, 0, new int[]{0x80000000, 0x2E000000, 0x00000000},
                            new float[]{0f, 0.667f, 1f}, Shader.TileMode.CLAMP);
                }
            });
            root.setBackground(sd);
        }
        if (card.getParent() instanceof ViewGroup) ((ViewGroup) card.getParent()).removeView(card);
        card.setBar(bar);
        card.setVisibility(View.VISIBLE);
        card.setAlpha(1f);
        card.setTranslationX(0f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        side(lp, QpdLayout.CARD_X);
        lp.topMargin = px(QpdLayout.CARD_Y);
        root.addView(card, lp);
        popup = new Popup(c);
        popup.setVisibility(View.GONE);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        side(pp, QpdLayout.SIDE_X);
        pp.topMargin = px(QpdLayout.MARGIN);
        root.addView(popup, pp);
        if (!bar) {
            all = new QpdAll(c, this);
            all.setVisibility(View.GONE);
            FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            side(ap, QpdLayout.ALL_X);
            ap.topMargin = px(QpdLayout.ALL_Y);
            ap.bottomMargin = px(QpdLayout.ALL_Y);
            root.addView(all, ap);
        }
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(focusWatch);
        render(0);
        float scale = MenuLookPrefs.scale(c);
        return scale < 1f ? new LookFrame(c, root, scale, right) : root;
    }

    @Override
    protected void onShown(View v) {
        if (!isAllMode()) {
            card.setPivotX(right ? card.getWidth() : 0f);
            card.setAlpha(0f);
            card.setTranslationX(edge(Theme.pxf(ctx, 30)));
            card.animate().alpha(1f).translationX(0f).setDuration(OPEN_MS).setInterpolator(QpdStyle.ease()).start();
        }
        if (root.getBackground() != null) {
            root.getBackground().setAlpha(0);
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofInt(0, 255);
            a.setDuration(300);
            a.addUpdateListener(x -> { if (root.getBackground() != null) root.getBackground().setAlpha((int) x.getAnimatedValue()); });
            a.start();
        }
        main.removeCallbacks(tick);
        main.postDelayed(tick, 10_000);
    }

    @Override
    protected void animateOut(View v, Runnable end) {
        v.animate().cancel();
        v.animate().alpha(0f).translationX(edge(Theme.pxf(v.getContext(), 30))).setDuration(CLOSE_MS)
                .setInterpolator(new android.view.animation.PathInterpolator(0.4f, 0f, 1f, 1f))
                .withLayer().withEndAction(end).start();
    }

    @Override
    protected void onDismissed() {
        main.removeCallbacks(tick);
        if (root != null) root.getViewTreeObserver().removeOnGlobalFocusChangeListener(focusWatch);
        clearAccent();
        Page cur = stack.peek();
        PageAccess.hidden(cur);
        if (popup != null) popup.list.removeAllViews();
        hideSheet();
        if (all != null) all.clear();
        if (held != null) { held.setHeld(false); held = null; }
        if (card.getParent() == root) root.removeView(card);
        card.setVisibility(View.VISIBLE);
        card.setAlpha(1f);
        card.setTranslationX(0f);
        try { host.onClosed(this); } catch (Throwable t) { Log.w(TAG, "closed: " + t); }
    }

    // ------------------------------------------------------------------ keys
    @Override
    protected boolean onKeyEvent(KeyEvent ev) {
        try {
            if (host.onKey(ev)) return true;
        } catch (Throwable t) {
            Log.w(TAG, "key: " + t);
        }
        int dir = dirOf(ev.getKeyCode());
        if (dir < 0) return false;
        if (bar) return dir == QpdLayout.UP || dir == QpdLayout.DOWN;   // LEFT / RIGHT go to the slider
        boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
        int pane = QpdLayout.toward(dir, right);                   // between the panes a mirrored menu swaps LEFT / RIGHT
        if (stack.peek() instanceof SheetPage) {                   // UP / DOWN: its rows, LEFT: back
            if (pane == QpdLayout.LEFT && down && ev.getRepeatCount() == 0) pop();
            return dir != QpdLayout.UP && dir != QpdLayout.DOWN;
        }
        if (isAllMode() && all != null) return allKey(pane, down, ev.getRepeatCount());
        if (stack.size() > 1) {
            View f = popup.findFocus();
            if (dir == QpdLayout.LEFT || dir == QpdLayout.RIGHT) {
                if (stepsItself(f)) return false;
                if (pane == QpdLayout.LEFT && down && ev.getRepeatCount() == 0) pop();
                return true;
            }
            return false;                                      // UP / DOWN: rows of the popup
        }
        int[] cell = card.focusedCell();
        if (cell == null) {
            if (down) card.focusRemembered();
            return true;
        }
        List<List<String>> rows = card.rows();
        String key = rows.get(cell[0]).get(cell[1]);
        if (card.isSlider(key) && (dir == QpdLayout.LEFT || dir == QpdLayout.RIGHT)) return false;
        if (!down) return true;
        int[] to = QpdLayout.move(rows, cell[0], cell[1], dir, host::hasPopup, card::isSlider, right);
        if (to == null) return true;
        if (to[2] == QpdLayout.OPEN) {
            if (ev.getRepeatCount() == 0) host.openPopup(key);
            return true;
        }
        View v = card.cell(rows.get(to[0]).get(to[1]));
        if (v != null) v.requestFocus();
        return true;
    }

    private static boolean stepsItself(View f) {
        return f instanceof ChoiceRow || f instanceof SliderRow || f instanceof EqBandsRow;
    }

    private boolean allKey(int dir, boolean down, int repeat) {
        View f = root.findFocus();
        if (f instanceof EqBandsRow && ((EqBandsRow) f).isEditing()) return false;   // its own UP / DOWN while editing
        if (all.inList(f)) {
            if (dir == QpdLayout.LEFT || dir == QpdLayout.RIGHT) {
                if (stepsItself(f)) return false;
                if (dir == QpdLayout.LEFT && down && repeat == 0) {
                    if (stack.size() > 2) pop();
                    else focusRail();
                }
                return true;
            }
            if (down) all.moveInList(f, dir == QpdLayout.DOWN);
            return true;
        }
        if (!down) return true;
        switch (dir) {
            case QpdLayout.UP:
            case QpdLayout.DOWN: {
                int n = QpdLayout.railMove(cat, cats.size(), dir);
                if (n >= 0) {
                    selectCategory(n);
                    all.focusRail(cat);
                }
                return true;
            }
            case QpdLayout.RIGHT:
                if (repeat == 0) enterList();
                return true;
            case QpdLayout.LEFT:
                if (repeat == 0) closeAll();
                return true;
            default:
                return true;
        }
    }

    private static int dirOf(int k) {
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP: return QpdLayout.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return QpdLayout.DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return QpdLayout.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return QpdLayout.RIGHT;
            default: return -1;
        }
    }

    // ------------------------------------------------------------------ the pencil editor's sheet
    /** Puts {@code v} (the pencil editor's sheet) in the side panel's place; the card stays and keeps its focus map. */
    void showSide(View v) {
        if (root == null || v == null) return;
        if (side != null && side != v) hideSide();
        hidePopup();
        if (v.getParent() instanceof ViewGroup && v.getParent() != root) ((ViewGroup) v.getParent()).removeView(v);
        if (v.getParent() == null) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(QpdLayout.SIDE_W), px(QpdLayout.SIDE_H));
            side(lp, QpdLayout.SIDE_X);
            lp.topMargin = px(QpdLayout.MARGIN);
            root.addView(v, lp);
            v.setAlpha(0f);
            v.setTranslationX(edge(Theme.pxf(ctx, QpdLayout.POP_SHIFT)));
            v.animate().alpha(1f).translationX(0f).setDuration(QpdLayout.POP_GROW_MS).setInterpolator(QpdStyle.ease()).start();
        }
        side = v;
        card.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }

    void hideSide() {
        if (side == null) return;
        View v = side;
        side = null;
        v.animate().cancel();
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
    }

    boolean hasSide() { return side != null; }

    /** This window's theme follower (null before the window exists): the theme button switches through it. */
    ThemeFade fade() { return fade; }

    /** The theme changed while the panel shows: the card, the popup and their rows redraw in the new colours. */
    void retheme() {
        card.retheme();
        if (popup != null) popup.retheme();
        if (root != null) root.invalidate();
    }

    // ------------------------------------------------------------------ render
    private int focusedIndex() {
        LinearLayout l = isAllMode() && all != null ? all.list() : popup == null ? null : popup.list;
        if (l == null) return -1;
        View f = l.getFocusedChild();
        return f == null ? -1 : l.indexOfChild(f);
    }

    private void render(int direction) {
        Page top = stack.peek();
        if (top == null) return;
        if (top instanceof SheetPage && stack.size() > 1 && !bar) {
            renderSheet((SheetPage) top, direction);
            return;
        }
        hideSheet();
        if (isAllMode() && all != null) {
            renderAll(top, direction);
            return;
        }
        if (all != null && all.getVisibility() == View.VISIBLE) hideAll();
        if (stack.size() <= 1 || bar) {
            hidePopup();
            card.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            View focusTo = bar ? card.lamp() : null;
            if (focusTo != null) focusTo.post(() -> { if (focusTo.isAttachedToWindow()) focusTo.requestFocus(); });
            else {
                // before the window is attached requestFocus does nothing: try now and again once laid out
                if (card.isAttachedToWindow()) card.focusRemembered();
                card.post(() -> { if (stack.size() <= 1 && card.isAttachedToWindow()) card.focusRemembered(); });
            }
            // focus is back on the tile first, so letting go of its held look does not blink it
            if (held != null) { held.setHeld(false); held = null; }
            safeShown(top);
            return;
        }
        // popup level: the section page is the one right above the root
        Page section = null;
        int i = 0;
        for (java.util.Iterator<Page> it = stack.descendingIterator(); it.hasNext(); i++) {
            Page p = it.next();
            if (i == 1) { section = p; break; }
        }
        String anchorKey = host.anchorFor(section);
        QpdTile anchor = anchorKey == null ? null : card.tile(anchorKey);
        if (anchor != null) card.remember(anchorKey);
        if (held != anchor) {
            if (held != null) held.setHeld(false);
            held = anchor;
            if (held != null) held.setHeld(true);
        }
        card.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        boolean wasOpen = popup.getVisibility() == View.VISIBLE;
        popup.fill(top, host.tipFor(top));
        place(anchor);
        // a popup pushed before the window was laid out (direct opens: KEYSTONE key, restorer, BACK from zoom) would
        // stay at the top margin: place it again beside its tile once the card has its layout
        if (!card.isLaidOut() || (anchor != null && !anchor.isLaidOut())) {
            final QpdTile a = anchor;
            card.post(() -> { if (stack.size() > 1 && !isAllMode() && popup.getVisibility() == View.VISIBLE) place(a); });
        }
        // the page's shown hook first (it may enable / disable rows), then the focus, picked when it is applied
        safeShown(top);
        final int want = PageAccess.focusIndex(top);
        popup.post(() -> {
            if (!popup.isAttachedToWindow() || stack.peek() != top || isAllMode()) return;
            View t = popup.target(want);
            if (t != null) t.requestFocus();
        });
        if (!wasOpen) showPopup();
        else if (direction != 0) {
            float dx = Theme.pxf(ctx, 40) * deeper(direction);
            popup.body.setTranslationX(dx);
            popup.body.setAlpha(0f);
            popup.body.animate().translationX(0f).alpha(1f).setDuration(Theme.PAGE_SWITCH_MS)
                    .setInterpolator(QpdStyle.ease()).start();
        }
    }

    /** A sheet page (the sound output page): its own surface beside the card, the popup and All settings put away. */
    private void renderSheet(SheetPage top, int direction) {
        clearAccent();
        if (popup.getVisibility() == View.VISIBLE) {                // gone at once: BACK shows it again with its own entry
            popup.animate().cancel();
            popup.setVisibility(View.GONE);
        }
        if (all != null && all.getVisibility() == View.VISIBLE) all.setVisibility(View.INVISIBLE);
        card.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        OutputSheet v = top.sheet;
        boolean fresh = v != sheet || v.getParent() != root;
        if (fresh) {
            hideSheet();
            if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            side(lp, isAllMode() ? QpdLayout.ALL_X : QpdLayout.SIDE_X);
            lp.topMargin = px(QpdLayout.MARGIN);
            root.addView(v, lp);
            sheet = v;
            v.setAlpha(0f);
            v.setTranslationX(edge(Theme.pxf(ctx, QpdLayout.POP_SHIFT)) * (direction < 0 ? -1 : 1));
            v.animate().alpha(1f).translationX(0f).setDuration(QpdLayout.POP_GROW_MS).setInterpolator(QpdStyle.ease()).start();
        }
        safeShown(top);
        v.post(() -> { if (v.isAttachedToWindow() && stack.peek() == top && !v.hasFocus()) v.focusFirst(); });
    }

    private void hideSheet() {
        if (sheet == null) return;
        OutputSheet v = sheet;
        sheet = null;
        v.animate().cancel();
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
    }

    private void renderAll(Page top, int direction) {
        hidePopup();
        if (held != null) { held.setHeld(false); held = null; }
        card.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        boolean category = stack.size() == 2;
        all.setRail(cats, cat);
        boolean wasShown = all.getVisibility() == View.VISIBLE;
        all.fill(category ? cats.get(cat).label : top.getTitle(), top, wasShown && direction != 0 ? direction : 0);
        if (!wasShown) showAll();
        safeShown(top);
        View row = allFocusRow;
        allFocusRow = null;
        if (allFocusList) {
            int idx = row != null ? top.rows().indexOf(row) : PageAccess.focusIndex(top);
            all.focusList(idx);
        } else {
            all.focusRail(cat);
        }
    }

    private void showAll() {
        card.animate().cancel();
        card.animate().alpha(0f).translationX(edge(Theme.pxf(ctx, 30))).setDuration(QpdLayout.POP_FADE_MS)
                .setInterpolator(QpdStyle.ease()).withEndAction(() -> {
                    if (isAllMode()) card.setVisibility(View.INVISIBLE);
                }).start();
        all.animate().cancel();
        all.setVisibility(View.VISIBLE);
        all.setAlpha(0f);
        all.setTranslationX(edge(Theme.pxf(ctx, 30)));
        all.animate().alpha(1f).translationX(0f).setDuration(OPEN_MS).setInterpolator(QpdStyle.ease()).start();
    }

    private void hideAll() {
        all.animate().cancel();
        all.animate().alpha(0f).translationX(edge(Theme.pxf(ctx, 30))).setDuration(QpdLayout.POP_FADE_MS)
                .setInterpolator(QpdStyle.ease()).withEndAction(() -> {
                    if (!isAllMode()) {
                        all.setVisibility(View.GONE);
                        all.clear();
                    }
                }).start();
        if (allAnchor != null) card.remember(allAnchor);
        card.animate().cancel();
        card.setVisibility(View.VISIBLE);
        card.animate().alpha(1f).translationX(0f).setDuration(OPEN_MS).setInterpolator(QpdStyle.ease()).start();
    }

    private void safeShown(Page p) {
        try {
            PageAccess.shown(p);
        } catch (Throwable t) {
            Log.w(TAG, "page shown hook: " + t);
        }
    }

    private void place(QpdTile anchor) {
        int screenH = root.getHeight() > 0 ? root.getHeight() : ctx.getResources().getDisplayMetrics().heightPixels;
        int maxH = Math.min(px(QpdLayout.popupMaxH()), screenH - 2 * px(QpdLayout.MARGIN));
        popup.setMaxHeight(maxH);
        popup.measure(View.MeasureSpec.makeMeasureSpec(px(QpdLayout.SIDE_W), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST));
        int h = popup.getMeasuredHeight();
        int top = px(QpdLayout.MARGIN);                  // the full height side panel: always at the top margin
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) popup.getLayoutParams();
        if (lp.topMargin != top) {
            lp.topMargin = top;
            popup.setLayoutParams(lp);
        }
        // grows out of its tile: pivot on the edge next to the card, at the tile's centre
        float tileMid = anchor == null ? 0f : px(QpdLayout.CARD_Y) + anchor.getTop() + anchor.getHeight() / 2f;
        popup.setPivotX(right ? px(QpdLayout.SIDE_W) : 0f);
        popup.setPivotY(QpdLayout.popupPivotY(tileMid, top, h, anchor != null));
    }

    private void showPopup() {
        popup.animate().cancel();
        popup.setVisibility(View.VISIBLE);
        popup.setAlpha(0f);
        popup.setTranslationX(edge(Theme.pxf(ctx, QpdLayout.POP_SHIFT)));
        popup.setScaleX(QpdLayout.POP_SCALE_FROM);
        popup.setScaleY(QpdLayout.POP_SCALE_FROM);
        popup.animate().translationX(0f).scaleX(1f).scaleY(1f).setDuration(QpdLayout.POP_GROW_MS)
                .setInterpolator(QpdStyle.ease()).start();
        android.animation.ObjectAnimator fade = android.animation.ObjectAnimator.ofFloat(popup, View.ALPHA, 0f, 1f);
        fade.setDuration(QpdLayout.POP_FADE_MS);
        fade.setInterpolator(QpdStyle.ease());
        fade.start();
        popup.staggerRows();
    }

    private void hidePopup() {
        if (popup == null) return;
        clearAccent();
        if (popup.getVisibility() != View.VISIBLE) return;
        popup.animate().cancel();
        popup.animate().alpha(0f).translationX(edge(Theme.pxf(ctx, QpdLayout.POP_SHIFT))).setDuration(CLOSE_MS)
                .withEndAction(() -> {
                    if (stack.size() <= 1 || isAllMode()) {
                        popup.setVisibility(View.GONE);
                        popup.list.removeAllViews();
                    }
                }).start();
    }

    /** Rows of a new popup or list come in one after the other (prototype rowin: 260 ms, 22 ms apart, 8 px). */
    static void stagger(LinearLayout list, Context c, boolean right) {
        float dx = (right ? 1 : -1) * Theme.pxf(c, QpdLayout.ROW_SHIFT);
        int k = 0;
        for (int i = 0; i < list.getChildCount(); i++) {
            View r = list.getChildAt(i);
            if (r.getVisibility() != View.VISIBLE || r.getHeight() == 0 && r.getMeasuredHeight() == 0) continue;
            r.animate().cancel();
            r.setTranslationX(dx);
            r.setAlpha(0f);
            r.animate().translationX(0f).alpha(1f).setStartDelay(k++ * QpdLayout.ROW_STAGGER_MS)
                    .setDuration(QpdLayout.ROW_IN_MS).setInterpolator(QpdStyle.ease()).start();
        }
    }

    // ------------------------------------------------------------------ focus watch
    private void onFocusMoved(View now) {
        View row = null;
        if (now != null && now.getParent() instanceof QpdRowFrame
                && ((QpdRowFrame) now.getParent()).kind == QpdRowFrame.NATIVE) row = now;
        if (row != accentRow) {
            clearAccent();
            if (row instanceof org.lumen.projector.ui.Row) {
                row.setLayerType(View.LAYER_TYPE_HARDWARE, accent);
                accentRow = row;
            }
        }
        if (isAllMode() && all != null) {
            boolean inList = all.inList(now);
            all.setListFocused(inList);
            all.showTip(inList ? host.tipForRow(now) : null, inList && now instanceof org.lumen.projector.ui.Row
                    ? ((org.lumen.projector.ui.Row) now).getTitle() : null);
        }
    }

    private void clearAccent() {
        if (accentRow != null) {
            accentRow.setLayerType(View.LAYER_TYPE_NONE, null);
            accentRow = null;
        }
    }

    /** Frames of rows, one per row object and container, so a refresh keeps each row's focus and switch state. */
    static final class Frames {
        private final WeakHashMap<View, QpdRowFrame> map = new WeakHashMap<>();
        private final QpdRowFrame.Look look;

        Frames(QpdRowFrame.Look look) { this.look = look; }

        QpdRowFrame of(Context c, View row) {
            QpdRowFrame f = map.get(row);
            if (f == null) {
                f = new QpdRowFrame(c, row, look);
                map.put(row, f);
            } else {
                f.bind();
            }
            return f;
        }

        /**
         * Fills {@code list} with the frames of {@code rows}; a link row right after a radio list gets a divider, and so
         * does a line header (the stock line between two groups).
         */
        void fill(Context c, LinearLayout list, List<View> rows) {
            list.removeAllViews();
            int prev = -1;
            for (View v : rows) {
                QpdRowFrame f = of(c, v);
                if (f.getParent() instanceof ViewGroup) ((ViewGroup) f.getParent()).removeView(f);
                f.setDivider(prev == QpdRowFrame.RADIO && f.kind == QpdRowFrame.NAV || f.isLine());
                f.setTranslationX(0f);
                f.setAlpha(1f);
                list.addView(f, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                if (v.getVisibility() != View.GONE) prev = f.kind;
            }
        }
    }

    /** Focus target of a list of frames: the row at {@code idx} when it can take focus, else the first one. */
    static View target(LinearLayout list, int idx) {
        if (idx >= 0 && idx < list.getChildCount()) {
            View c = list.getChildAt(idx);
            if (c instanceof QpdRowFrame && ((QpdRowFrame) c).isFocusableRow()) return ((QpdRowFrame) c).row;
        }
        for (int k = 0; k < list.getChildCount(); k++) {
            View c = list.getChildAt(k);
            if (c instanceof QpdRowFrame && ((QpdRowFrame) c).isFocusableRow()) return ((QpdRowFrame) c).row;
        }
        return null;
    }

    /**
     * The side panel (2026-11-18, decision 1: every popup is the full height panel right of the card): optional
     * header (the tile's icon, the page title, its value), the hint, the page's rows in the prototype look
     * (QpdRowFrame) inside a scroll view, the key hints at the bottom. 600 x 1000 px at 1920 x 1080.
     */
    private final class Popup extends ViewGroup {
        final LinearLayout body;
        final QpdSide.Header head;
        final ScrollView scroll;
        final LinearLayout list;
        final QpdSide.Keys keys;
        final Frames frames = new Frames(QpdRowFrame.POPUP);
        final GradientDrawable bg = new GradientDrawable();
        View tip;
        int maxH = Integer.MAX_VALUE;

        Popup(Context c) {
            super(c);
            bg.setColor(QpdStyle.PANEL);
            bg.setCornerRadius(Theme.pxf(c, QpdLayout.SIDE_RADIUS));
            setBackground(bg);
            setElevation(Theme.pxf(c, 30));
            setPadding(Theme.px(c, QpdLayout.SIDE_PAD_H), Theme.px(c, QpdLayout.SIDE_PAD_TOP), Theme.px(c, QpdLayout.SIDE_PAD_H),
                    Theme.px(c, QpdLayout.SIDE_PAD_BOTTOM));
            setFocusable(false);
            setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
            body = new LinearLayout(c);
            body.setOrientation(LinearLayout.VERTICAL);
            head = new QpdSide.Header(c);
            list = new LinearLayout(c);
            list.setOrientation(LinearLayout.VERTICAL);
            scroll = new ScrollView(c);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scroll.setVerticalFadingEdgeEnabled(true);
            scroll.setFadingEdgeLength(Theme.px(c, 48));
            scroll.setClipChildren(false);
            scroll.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            keys = new QpdSide.Keys(c);
            keys.set(c.getText(R.string.lumen_qe_select), c.getText(R.string.lumen_qe_close_panel));
            body.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            FrameLayout clip = new FrameLayout(c);          // the scrolled rows never draw over the header or the hints
            clip.setClipChildren(true);
            clip.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            body.addView(clip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            body.addView(keys, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            addView(body);
        }

        void setMaxHeight(int h) { maxH = h; }

        void retheme() {
            bg.setColor(QpdStyle.PANEL);
            head.invalidate();
            keys.invalidate();
            for (int i = 0; i < list.getChildCount(); i++) list.getChildAt(i).invalidate();
        }

        void fill(Page page, View newTip) {
            if (tip != null && tip != newTip) body.removeView(tip);
            if (newTip != null && newTip.getParent() != body) {
                if (newTip.getParent() instanceof ViewGroup) ((ViewGroup) newTip.getParent()).removeView(newTip);
                body.addView(newTip, 1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));      // under the header (mock 08)
            }
            tip = newTip;
            bg.setColor(QpdStyle.PANEL);
            head.set(host.iconFor(page), page.getTitle(), host.valueFor(page), null);
            clearAccent();
            frames.fill(getContext(), list, page.rows());
            scroll.scrollTo(0, 0);
        }

        View target(int idx) { return QpdPanel.target(list, idx); }

        void staggerRows() { stagger(list, getContext(), right); }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = Theme.px(getContext(), QpdLayout.SIDE_W);
            int h = Math.min(maxH, Theme.px(getContext(), QpdLayout.SIDE_H));
            body.measure(MeasureSpec.makeMeasureSpec(w - getPaddingLeft() - getPaddingRight(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h - getPaddingTop() - getPaddingBottom(), MeasureSpec.EXACTLY));
            setMeasuredDimension(w, h);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            body.layout(getPaddingLeft(), getPaddingTop(), getPaddingLeft() + body.getMeasuredWidth(),
                    getPaddingTop() + body.getMeasuredHeight());
        }
    }
}
