package org.lumen.projector.panel;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.lumen.projector.ui.Page;
import org.lumen.projector.ui.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * All settings of layout D (prototype #all): a rail of round category buttons on the left (the quick panel's section
 * pages), the list of the chosen page in the middle (the controller's own rows, drawn by QpdRowFrame in the list
 * look) and, on the right, the hint card of the focused row when that item has a stock hint picture (picture mode,
 * rear projection on the Z9X). QpdPanel owns the page stack and the keys; this view only shows them.
 */
final class QpdAll extends ViewGroup {
    private static final String TAG = "Z9xPanel";

    private final QpdPanel panel;
    private final LinearLayout railCard;
    private final List<RailItem> items = new ArrayList<>();
    private final LinearLayout listCard;
    private final TextView title;
    private final ScrollView scroll;
    private final LinearLayout list;
    private final QpdPanel.Frames frames = new QpdPanel.Frames(QpdRowFrame.LIST);
    private final FrameLayout tipCard;
    private View tipShown;
    private int current = -1;
    private boolean listFocused;

    QpdAll(Context c, QpdPanel panel) {
        super(c);
        this.panel = panel;
        setClipChildren(false);
        setClipToPadding(false);
        railCard = new LinearLayout(c);
        railCard.setOrientation(LinearLayout.VERTICAL);
        card(railCard, QpdLayout.RAIL_PAD, QpdLayout.RAIL_PAD);
        addView(railCard);

        listCard = new LinearLayout(c);
        listCard.setOrientation(LinearLayout.VERTICAL);
        card(listCard, QpdLayout.LIST_PAD_H, QpdLayout.LIST_PAD_V);
        title = new TextView(c);
        title.setTypeface(QpdStyle.inter(c, 650, QpdLayout.LIST_TITLE));
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(QpdLayout.LIST_TITLE));
        title.setTextColor(QpdStyle.INK);
        title.setLetterSpacing(-0.3f / QpdLayout.LIST_TITLE);
        title.setIncludeFontPadding(false);
        title.setMaxLines(2);
        title.setPadding(0, Math.round(px(6)), 0, Math.round(px(10)));
        listCard.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(Math.round(px(48)));
        scroll.setClipChildren(false);
        scroll.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        // the rows' focus pill reaches the card's padding (prototype .r margin 0 -18)
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        sp.leftMargin = sp.rightMargin = -Math.round(px(QpdLayout.ROW_PAD));
        listCard.addView(scroll, sp);
        addView(listCard);

        tipCard = new FrameLayout(c);
        card(tipCard, QpdLayout.TIP_PAD, QpdLayout.TIP_PAD);
        tipCard.setVisibility(GONE);
        addView(tipCard);
    }

    private float px(float d) { return Theme.pxf(getContext(), d); }

    private void card(ViewGroup v, float padH, float padV) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(QpdStyle.PANEL);
        bg.setCornerRadius(px(QpdLayout.CARD_RADIUS));
        v.setBackground(bg);
        v.setElevation(px(30));
        v.setPadding(Math.round(padH), Math.round(padV), Math.round(padH), Math.round(padV));
        v.setClipToPadding(false);
    }

    // ------------------------------------------------------------------ content
    void setRail(List<QpdPanel.Cat> cats, int sel) {
        boolean same = cats.size() == items.size();
        for (int i = 0; same && i < cats.size(); i++) same = items.get(i).cat == cats.get(i);
        if (!same) {
            railCard.removeAllViews();
            items.clear();
            for (int i = 0; i < cats.size(); i++) {
                RailItem it = new RailItem(getContext(), cats.get(i), i);
                items.add(it);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.round(px(QpdLayout.ITEM_H)));
                lp.bottomMargin = Math.round(px(QpdLayout.ITEM_GAP));
                railCard.addView(it, lp);
            }
        }
        current = sel;
        for (RailItem it : items) it.setCurrent(it.index == sel && listFocused);
    }

    private Page shown;

    /**
     * Shows {@code page} in the list. A new page comes in (slide for a level change, staggered rows for a category);
     * the same page again (refreshPage) keeps its scroll position and does not animate.
     */
    void fill(CharSequence t, Page page, int direction) {
        boolean same = page == shown;
        shown = page;
        title.setText(t);
        int y = scroll.getScrollY();
        frames.fill(getContext(), list, page.rows());
        if (same && direction == 0) {
            scroll.scrollTo(0, y);
            return;
        }
        scroll.scrollTo(0, 0);
        showTip(null, null);
        if (direction != 0) {
            float dx = px(40) * direction;
            list.setTranslationX(dx);
            list.setAlpha(0f);
            list.animate().translationX(0f).alpha(1f).setDuration(Theme.PAGE_SWITCH_MS)
                    .setInterpolator(QpdStyle.ease()).start();
        } else {
            list.animate().cancel();
            list.setTranslationX(0f);
            list.setAlpha(1f);
            QpdPanel.stagger(list, getContext());
        }
    }

    void clear() {
        list.removeAllViews();
        shown = null;
        showTip(null, null);
    }

    LinearLayout list() { return list; }

    boolean inList(View v) {
        for (View p = v; p != null && p != this; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            if (p == list) return true;
        }
        return false;
    }

    void focusRail(int index) {
        if (index < 0 || index >= items.size()) return;
        final RailItem it = items.get(index);
        if (!it.requestFocus()) it.post(() -> { if (it.isAttachedToWindow()) it.requestFocus(); });
    }

    void focusList(int idx) {
        View t = QpdPanel.target(list, idx);
        if (t != null && t.requestFocus()) return;
        // not attached / laid out yet: pick the row again when the focus can be applied
        list.post(() -> {
            View r = QpdPanel.target(list, idx);
            if (r != null && r.isAttachedToWindow()) r.requestFocus();
        });
    }

    /** UP / DOWN in the list: the next row that takes focus, never out of the list (the rail is LEFT). */
    void moveInList(View focused, boolean down) {
        int at = -1;
        for (int i = 0; i < list.getChildCount(); i++) {
            View c = list.getChildAt(i);
            if (c instanceof QpdRowFrame && ((QpdRowFrame) c).row == focused) { at = i; break; }
        }
        if (at < 0) return;
        for (int i = at + (down ? 1 : -1); i >= 0 && i < list.getChildCount(); i += down ? 1 : -1) {
            View c = list.getChildAt(i);
            if (c instanceof QpdRowFrame && ((QpdRowFrame) c).isFocusableRow()) {
                ((QpdRowFrame) c).row.requestFocus();
                if (!down && i <= 1) scroll.smoothScrollTo(0, 0);    // headers above the first row stay in view
                return;
            }
        }
        if (!down) scroll.smoothScrollTo(0, 0);
    }

    void setListFocused(boolean on) {
        if (listFocused == on) return;
        listFocused = on;
        for (RailItem it : items) it.setCurrent(it.index == current && on);
    }

    /** Hint card of the focused row ({@code popupTip} = its popup hint, a QpdTip), or none. */
    void showTip(View popupTip, CharSequence rowTitle) {
        if (!(popupTip instanceof QpdTip)) {
            if (tipShown != null) {
                tipCard.animate().cancel();
                tipCard.setVisibility(GONE);
                tipCard.removeAllViews();
                tipShown = null;
            }
            return;
        }
        QpdTip src = (QpdTip) popupTip;
        if (tipShown instanceof QpdTip && ((QpdTip) tipShown).kind() == src.kind()) return;
        tipCard.removeAllViews();
        QpdTip t = new QpdTip(getContext(), src.kind(), src.text(), rowTitle);
        tipCard.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        tipShown = t;
        tipCard.setVisibility(VISIBLE);
        tipCard.setAlpha(0f);
        tipCard.setTranslationX(-px(14));
        tipCard.animate().alpha(1f).translationX(0f).setDuration(320).setInterpolator(QpdStyle.ease()).start();
    }

    // ------------------------------------------------------------------ layout (design px)
    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int h = MeasureSpec.getSize(hSpec);
        if (MeasureSpec.getMode(hSpec) == MeasureSpec.UNSPECIFIED || h == 0) {
            h = Math.round(px(QpdLayout.SCREEN_H - 2 * QpdLayout.ALL_Y));
        }
        int railW = Math.round(px(QpdLayout.RAIL_W)), listW = Math.round(px(QpdLayout.LIST_W)),
                tipW = Math.round(px(QpdLayout.TIP_W)), gap = Math.round(px(QpdLayout.ALL_GAP));
        railCard.measure(MeasureSpec.makeMeasureSpec(railW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        listCard.measure(MeasureSpec.makeMeasureSpec(listW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        tipCard.measure(MeasureSpec.makeMeasureSpec(tipW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST));
        setMeasuredDimension(railW + listW + tipW + 2 * gap, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int gap = Math.round(px(QpdLayout.ALL_GAP));
        int x = 0;
        railCard.layout(x, 0, x + railCard.getMeasuredWidth(), railCard.getMeasuredHeight());
        x += railCard.getMeasuredWidth() + gap;
        listCard.layout(x, 0, x + listCard.getMeasuredWidth(), listCard.getMeasuredHeight());
        x += listCard.getMeasuredWidth() + gap;
        tipCard.layout(x, 0, x + tipCard.getMeasuredWidth(), tipCard.getMeasuredHeight());
    }

    /**
     * One rail entry (prototype .it): a 96 px row with a 64 px round icon button and the category name; focus =
     * accent fill, the circle lightens and grows (1.06, spring), the icon plays its animation; the category whose
     * list has the focus keeps a faint fill.
     */
    private final class RailItem extends View {
        final QpdPanel.Cat cat;
        final int index;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final TextPaint label;
        private final QpdStyle.Icon icon;
        private StaticLayout layout;
        private int layoutW = -1;
        private float focus, grow;
        private boolean cur;
        private ValueAnimator focusAnim, growAnim;

        RailItem(Context c, QpdPanel.Cat cat, int index) {
            super(c);
            this.cat = cat;
            this.index = index;
            setFocusable(true);
            setFocusableInTouchMode(false);
            setDefaultFocusHighlightEnabled(false);
            setContentDescription(cat.label);
            label = QpdStyle.text(c, QpdLayout.ITEM_TEXT, 550, QpdStyle.INK);
            icon = new QpdStyle.Icon(this, cat.icon);
        }

        @Override
        protected boolean verifyDrawable(Drawable who) { return icon.is(who) || super.verifyDrawable(who); }

        void setCurrent(boolean on) {
            if (cur == on) return;
            cur = on;
            invalidate();
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent ev) {
            if (isOk(keyCode)) return true;
            return super.onKeyDown(keyCode, ev);
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent ev) {
            if (isOk(keyCode)) {
                if (!ev.isCanceled()) panel.enterList();
                return true;
            }
            return super.onKeyUp(keyCode, ev);
        }

        private boolean isOk(int k) {
            return k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
        }

        @Override
        protected void onFocusChanged(boolean gain, int direction, android.graphics.Rect prev) {
            super.onFocusChanged(gain, direction, prev);
            focusAnim = run(focusAnim, focus, gain ? 1f : 0f, 260, QpdStyle.ease(), v -> focus = v);
            growAnim = run(growAnim, grow, gain ? 1f : 0f, 300, gain ? QpdStyle.spring() : QpdStyle.ease(), v -> grow = v);
            if (gain) icon.start();
            else icon.rest();
        }

        private ValueAnimator run(ValueAnimator old, float from, float to, long ms, android.animation.TimeInterpolator in,
                                  java.util.function.Consumer<Float> set) {
            if (old != null) old.cancel();
            if (!isAttachedToWindow()) {
                set.accept(to);
                invalidate();
                return null;
            }
            ValueAnimator a = ValueAnimator.ofFloat(from, to);
            a.setDuration(ms);
            a.setInterpolator(in);
            a.addUpdateListener(x -> {
                set.accept((Float) x.getAnimatedValue());
                invalidate();
            });
            a.start();
            return a;
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            int base = cur ? QpdStyle.RAIL_CURRENT : (QpdStyle.RAIL_CURRENT & 0x00FFFFFF);
            fill.setColor(QpdStyle.lerp(base, QpdStyle.ACCENT, focus));
            if (Color.alpha(fill.getColor()) > 0) {
                r.set(0, 0, w, h);
                float rad = px(QpdLayout.ITEM_RADIUS);
                c.drawRoundRect(r, rad, rad, fill);
            }
            float pad = px(16), d = px(QpdLayout.ITEM_CIRCLE);
            float cx = pad + d / 2f, cy = h / 2f;
            float s = 1f + 0.06f * grow;
            fill.setColor(QpdStyle.lerp(QpdStyle.RAIL_CIRCLE, QpdStyle.RAIL_CIRCLE_FOCUSED, focus));
            c.drawCircle(cx, cy, d / 2f * s, fill);
            Drawable icon = this.icon.drawable();
            if (icon != null) {
                int half = Math.round(px(QpdLayout.ITEM_ICON) / 2f);
                icon.setBounds(0, 0, 2 * half, 2 * half);
                int save = c.save();
                c.translate(cx, cy);
                c.scale(s, s);
                c.translate(-half, -half);
                icon.draw(c);
                c.restoreToCount(save);
            }
            float x = pad + d + px(18);
            int avail = Math.max(1, Math.round(w - x - px(12)));
            if (layout == null || layoutW != avail) {
                CharSequence t = cat.label == null ? "" : cat.label;
                layout = StaticLayout.Builder.obtain(t, 0, t.length(), label, avail)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setMaxLines(2)
                        .setEllipsize(TextUtils.TruncateAt.END).setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
                        .setLineSpacing(0f, 1.05f).build();
                layoutW = avail;
            }
            label.setColor(QpdStyle.lerp(QpdStyle.INK, 0xFFFFFFFF, focus));
            c.save();
            c.translate(x, cy - layout.getHeight() / 2f);
            layout.draw(c);
            c.restore();
        }
    }

    private static final class Color {
        static int alpha(int c) { return c >>> 24; }
    }
}
