// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.ui;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * In-window side panel over Home (context menu, weather, how-to panels): a dim scrim and a 560 px
 * ListPanel sliding in from the end edge in 280 ms. Pages stack; BACK pops, then closes.
 */
public class ContextPanel extends ViewGroup {
    public interface OnClosed {
        void onClosed();
    }

    private static final class Page {
        android.widget.EditText editor;
        CharSequence title, sub;
        List<ListPanel.Item> items;
        int focus;
        Object tag;
    }

    private final View mScrim;
    private final ListPanel mPanel;
    private final ArrayList<Page> mStack = new ArrayList<>();
    private OnClosed mOnClosed;
    private boolean mOpen;
    private int mOpens;
    private int mWidthDesign = 560;

    public void setWidthDesign(int w) {
        mWidthDesign = w;
        requestLayout();
    }

    public ContextPanel(Context c) {
        super(c);
        mScrim = new View(c);
        mScrim.setBackgroundColor(0x66000000);
        addView(mScrim);
        mPanel = new ListPanel(c);
        mPanel.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                float r = Theme.pxf(32);
                boolean rtl = Theme.rtl(v);
                o.setRoundRect(rtl ? (int) -r : 0, 0, rtl ? v.getWidth() : (int) (v.getWidth() + r), v.getHeight(), r);
            }
        });
        mPanel.setClipToOutline(true);
        mPanel.setElevation(Theme.pxf(24));
        addView(mPanel);
        setVisibility(GONE);
    }

    public boolean isOpen() {
        return mOpen;
    }

    public void setOnClosed(OnClosed c) {
        mOnClosed = c;
    }

    public void open(CharSequence title, CharSequence sub, List<ListPanel.Item> items) {
        open(title, sub, items, null);
    }

    /** Opens on a page named {@code page}, which {@link #showing} finds again to {@link #update} it. */
    public void open(CharSequence title, CharSequence sub, List<ListPanel.Item> items, Object page) {
        mStack.clear();
        push(title, sub, items);
        mStack.get(0).tag = page;
        if (!mOpen) {
            mOpen = true;
            mOpens++;
            setVisibility(VISIBLE);
            float dx = Theme.rtl(this) ? -Theme.pxf(mWidthDesign) : Theme.pxf(mWidthDesign);
            mPanel.setTranslationX(dx);
            mPanel.setAlpha(0);
            mScrim.setAlpha(0);
            mPanel.animate().translationX(0).alpha(1).setDuration(Theme.PANEL_MS).setInterpolator(Theme.DECEL).start();
            mScrim.animate().alpha(1).setDuration(Theme.PANEL_MS).start();
        }
    }

    public void push(CharSequence title, CharSequence sub, List<ListPanel.Item> items) {
        pushEditor(title, sub, null, items);
    }

    /** A page with a text field above its items (rename, city search). */
    public void pushEditor(CharSequence title, CharSequence sub, android.widget.EditText editor, List<ListPanel.Item> items) {
        if (!mStack.isEmpty()) mStack.get(mStack.size() - 1).focus = mPanel.focus();
        Page p = new Page();
        p.editor = editor;
        p.title = title;
        p.sub = sub;
        p.items = items;
        mStack.add(p);
        show(p, 0);
    }

    /** The focused item of the current page, or null (Files: the long OK menu). */
    public ListPanel.Item focusedItem() {
        int f = mPanel.focus();
        List<ListPanel.Item> l = mPanel.items();
        return f >= 0 && f < l.size() ? l.get(f) : null;
    }

    /** Redraws one item of the current page after its text changed (nothing if it is not shown). */
    public void refresh(ListPanel.Item it) {
        mPanel.refresh(it);
    }

    /** Pages on the stack (1 = only the first page). */
    public int depth() {
        return mStack.size();
    }

    /** Drops the top page like BACK, never closing the panel. */
    public void pop() {
        if (mStack.size() <= 1) return;
        mStack.remove(mStack.size() - 1);
        Page p = mStack.get(mStack.size() - 1);
        show(p, p.focus);
    }

    /** A pushed page named {@code page} (see {@link #showing}). */
    public void push(CharSequence title, CharSequence sub, List<ListPanel.Item> items, Object page) {
        push(title, sub, items);
        mStack.get(mStack.size() - 1).tag = page;
    }

    /** The page on top is the one named {@code page}. */
    public boolean showing(Object page) {
        return mOpen && !mStack.isEmpty() && page != null && page.equals(mStack.get(mStack.size() - 1).tag);
    }

    /** New values for the items of the page on top (a switch, a look): the focus stays on the same item. */
    public void update(List<ListPanel.Item> items) {
        if (mStack.isEmpty()) return;
        mStack.get(mStack.size() - 1).items = items;
        mPanel.update(items);
    }

    /** The theme changed: the scrim stays, the panel takes the new palette. */
    public void restyle() {
        mPanel.restyle();
    }

    private void show(Page p, int focus) {
        mPanel.setEditor(p.editor);
        if (p.editor != null && focus == 0) focus = -1;
        mPanel.setHeader(p.title, p.sub);
        mPanel.setItems(p.items, focus);
    }

    public void close() {
        if (!mOpen) return;
        mOpen = false;
        mStack.clear();
        mPanel.setEditor(null);
        float dx = Theme.rtl(this) ? -Theme.pxf(mWidthDesign) : Theme.pxf(mWidthDesign);
        mPanel.animate().translationX(dx).alpha(0).setDuration(200).setInterpolator(Theme.EMPHASIZED)
                .withEndAction(() -> {
                    if (!mOpen) setVisibility(GONE);
                }).start();
        mScrim.animate().alpha(0).setDuration(200).start();
        if (mOnClosed != null) mOnClosed.onClosed();
    }

    /** For {@code dumpsys activity} (test/ui): open or not, how often it opened, the page stack and the shown page. */
    public void dump(java.io.PrintWriter w) {
        w.println("lumen.panel open=" + mOpen + " opens=" + mOpens + " depth=" + mStack.size());
        if (mOpen) mPanel.dump(w);
    }

    /** Mouse or touch: the point lies on the side panel (else on the scrim, which closes like BACK). */
    public boolean inPanel(float x, float y) {
        return Hit.inside(mPanel, x, y);
    }

    /** Mouse or touch: the item of the side panel under the point takes the focus (ListPanel.focusAt). */
    public boolean focusAt(float x, float y) {
        return mPanel.focusAt(x, y);
    }

    /** Mouse or touch: the panel's text field is under the point. */
    public boolean editorAt(float x, float y) {
        return mOpen && mPanel.editorAt(x, y);
    }

    public boolean onKey(int keyCode, KeyEvent e) {
        if (!mOpen) return false;
        if (mPanel.onKey(keyCode, e)) return true;
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() != KeyEvent.ACTION_UP) return true;
            if (mStack.size() > 1) {
                mStack.remove(mStack.size() - 1);
                Page p = mStack.get(mStack.size() - 1);
                show(p, p.focus);
            } else {
                close();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (e.getAction() == KeyEvent.ACTION_UP) close();
            return true;
        }
        // modal: nothing reaches the page underneath, except typing into the panel's editor
        return !mPanel.editorFocused();
    }

    public void openEditor(CharSequence title, CharSequence sub, android.widget.EditText editor, List<ListPanel.Item> items) {
        open(title, sub, new java.util.ArrayList<>());
        mStack.clear();
        pushEditor(title, sub, editor, items);
    }

    @Override
    protected void onMeasure(int wms, int hms) {
        int w = MeasureSpec.getSize(wms), h = MeasureSpec.getSize(hms);
        mScrim.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        mPanel.measure(MeasureSpec.makeMeasureSpec(Theme.px(mWidthDesign), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l, h = b - t;
        mScrim.layout(0, 0, w, h);
        int pw = mPanel.getMeasuredWidth();
        if (Theme.rtl(this)) mPanel.layout(0, 0, pw, h);
        else mPanel.layout(w - pw, 0, w, h);
    }
}
