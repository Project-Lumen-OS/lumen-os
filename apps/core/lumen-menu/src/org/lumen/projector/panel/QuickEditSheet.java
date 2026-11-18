package org.lumen.projector.panel;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.lumen.projector.R;
import org.lumen.projector.ui.Theme;

import java.util.List;

/**
 * The pencil editor's sheet in the side panel's place (mock qm-editor 02, 03, 04): a header, then the catalogue
 * (the menu look Плитки / Список, every item grouped by section with В меню / Добавить and the Новое mark, Вернуть как
 * было) or the inspector of one item (its looks with a preview, Переместить, Убрать из меню) or the move hint, and the
 * key hints at the bottom. Only views; QuickEditMode decides what it shows and what a press does.
 */
final class QuickEditSheet extends FrameLayout {
    final QpdSide.Header head;
    final LinearLayout top, list;
    final ScrollView scroll;
    final LinearLayout bottom;
    final QpdSide.Keys keys;
    private final GradientDrawable bg = new GradientDrawable();

    QuickEditSheet(Context c) {
        super(c);
        bg.setColor(QpdStyle.PANEL);
        bg.setCornerRadius(Theme.pxf(c, QpdLayout.SIDE_RADIUS));
        setBackground(bg);
        setElevation(Theme.pxf(c, 30));
        setClipChildren(false);
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);
        body.setPadding(Theme.px(c, QpdLayout.SIDE_PAD_H), Theme.px(c, QpdLayout.SIDE_PAD_TOP), Theme.px(c, QpdLayout.SIDE_PAD_H),
                Theme.px(c, QpdLayout.SIDE_PAD_BOTTOM));
        head = new QpdSide.Header(c);
        top = new LinearLayout(c);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setClipChildren(false);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setPadding(Theme.px(c, 16), Theme.px(c, 6), Theme.px(c, 16), Theme.px(c, 6));   // room for a focused row to grow
        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(Theme.px(c, 48));
        scroll.setClipToPadding(true);                      // rows never draw over the head or the bottom bar
        scroll.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bottom = new LinearLayout(c);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(android.view.Gravity.CENTER_VERTICAL);
        keys = new QpdSide.Keys(c);
        body.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        sl.leftMargin = -Theme.px(c, 16);
        sl.rightMargin = -Theme.px(c, 16);
        sl.bottomMargin = Theme.px(c, 12);
        FrameLayout clip = new FrameLayout(c);              // clips the scrolled rows to the list's own box
        clip.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        body.addView(clip, sl);
        body.addView(bottom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(body, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
    }

    void clear() {
        bg.setColor(QpdStyle.PANEL);
        top.removeAllViews();
        list.removeAllViews();
        bottom.removeAllViews();
        scroll.scrollTo(0, 0);
    }

    private int px(float d) { return Theme.px(getContext(), d); }

    /** A section label ("Вид меню", "Что показывать", "Место") or a group label of the catalogue (small). */
    View label(LinearLayout into, CharSequence t, boolean small) {
        Label l = new Label(getContext(), t, small);
        into.addView(l, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    /** A note with the info icon (what a look does, why an item stays). */
    void note(LinearLayout into, CharSequence t) {
        Label l = new Label(getContext(), t, true);
        l.info = true;
        into.addView(l, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Two cards side by side (the menu look, two looks of an item). */
    LinearLayout pair(LinearLayout into) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = px(16);
        into.addView(row, lp);
        return row;
    }

    /** Closes a pair holding one card: an empty half keeps the card half wide (the 2 x 2 grid of the mock). */
    void endPair(LinearLayout pair) {
        if (pair == null || pair.getChildCount() != 1) return;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
        lp.leftMargin = px(16);
        pair.addView(new View(getContext()), lp);
    }

    Pick add(LinearLayout into, Pick p) {
        boolean inRow = into.getOrientation() == LinearLayout.HORIZONTAL;
        LinearLayout.LayoutParams lp = inRow ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                : new LinearLayout.LayoutParams(p.style == Pick.PILL ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (inRow && into.getChildCount() > 0) lp.leftMargin = px(16);
        into.addView(p, lp);
        return p;
    }

    /** The first focusable pick of the sheet. */
    View first() {
        for (LinearLayout l : new LinearLayout[]{top, list, bottom}) {
            View v = firstIn(l);
            if (v != null) return v;
        }
        return null;
    }

    private static View firstIn(ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v instanceof Pick && v.isFocusable() && v.getVisibility() == VISIBLE) return v;
            if (v instanceof ViewGroup) {
                View w = firstIn((ViewGroup) v);
                if (w != null) return w;
            }
        }
        return null;
    }

    /** The pick showing {@code key}, or null. */
    Pick pick(String key) { return find(this, key); }

    private static Pick find(ViewGroup g, String key) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v instanceof Pick && key.equals(((Pick) v).key)) return (Pick) v;
            if (v instanceof ViewGroup) {
                Pick p = find((ViewGroup) v, key);
                if (p != null) return p;
            }
        }
        return null;
    }

    /** A line of key caps and what they do (the move sheet, mock 04): "‹ › ˄ ˅ Двигать", "OK Поставить сюда". */
    /** A key legend line: one key cap per icon of apps/core/lumen-icons (the remote's arrows, BACK), then the text. */
    void legend(LinearLayout into, CharSequence text, int... icons) {
        add(into, new Legend(getContext(), null, icons, text));
    }

    /** A key legend line with a word on its one cap (OK). */
    void legend(LinearLayout into, CharSequence text, String cap) {
        add(into, new Legend(getContext(), cap, new int[0], text));
    }

    private void add(LinearLayout into, Legend l) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = px(12);
        into.addView(l, lp);
    }

    static final class Legend extends View {
        private final String word;
        private final int[] icons;
        private final CharSequence text;
        private final Paint capP = new Paint(Paint.ANTI_ALIAS_FLAG), keyP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.text.TextPaint textP = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Legend(Context c, String word, int[] icons, CharSequence text) {
            super(c);
            this.word = word;
            this.icons = icons;
            this.text = text;
            keyP.setTextSize(Theme.pxf(c, 17));
            keyP.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textP.setTextSize(Theme.pxf(c, 19));
            textP.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            setFocusable(false);
        }

        @Override protected void onMeasure(int ws, int hs) {
            setMeasuredDimension(MeasureSpec.getSize(ws), Theme.px(getContext(), 44));
        }

        @Override protected void onDraw(Canvas c) {
            float h = getHeight(), cap = Theme.pxf(getContext(), 34), gap = Theme.pxf(getContext(), 8), x = 0;
            capP.setColor(QpdStyle.TILE_HI);
            keyP.setColor(QpdStyle.INK);
            textP.setColor(QpdStyle.INK);
            float radius = Theme.pxf(getContext(), 9);
            for (int icon : icons) {
                r.set(x, (h - cap) / 2f, x + cap, (h + cap) / 2f);
                c.drawRoundRect(r, radius, radius, capP);
                Drawable d = getContext().getDrawable(icon);
                if (d != null) {
                    d = d.mutate();
                    d.setTint(QpdStyle.INK);
                    int s = Math.round(Theme.pxf(getContext(), 26)), left = Math.round(x + (cap - s) / 2f), top = Math.round((h - s) / 2f);
                    d.setBounds(left, top, left + s, top + s);
                    d.draw(c);
                }
                x += cap + gap;
            }
            if (word != null) {
                float w = Math.max(cap, keyP.measureText(word) + Theme.pxf(getContext(), 18));
                r.set(x, (h - cap) / 2f, x + w, (h + cap) / 2f);
                c.drawRoundRect(r, radius, radius, capP);
                c.drawText(word, x + (w - keyP.measureText(word)) / 2f, h / 2f - (keyP.descent() + keyP.ascent()) / 2f, keyP);
                x += w + gap;
            }
            x += Theme.pxf(getContext(), 8);
            QpdStyle.drawFaded(c, text, textP, x, Math.max(1f, getWidth() - x), 0, (int) h, h / 2f - (textP.descent() + textP.ascent()) / 2f,
                    0f, Theme.pxf(getContext(), QpdLayout.FADE));
        }
    }

    /** A label line: section title (20 px, ink) or group / note (16 px, muted). */
    static final class Label extends View {
        private final TextPaint p;
        private final CharSequence t;
        private final boolean small;
        boolean info;
        private android.text.StaticLayout lay;

        Label(Context c, CharSequence t, boolean small) {
            super(c);
            this.t = t == null ? "" : t;
            this.small = small;
            p = QpdStyle.text(c, small ? 16 : 20, small ? 550 : 650, QpdStyle.INK);
            setFocusable(false);
        }

        private float px(float d) { return Theme.pxf(getContext(), d); }

        @Override
        protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            float indent = info ? px(30) : px(small ? 6 : 4);
            lay = android.text.StaticLayout.Builder.obtain(t, 0, t.length(), p, Math.max(1, Math.round(width - indent))).build();
            float above = small ? px(info ? 6 : 14) : px(22), below = small ? px(8) : px(12);
            setMeasuredDimension(width, Math.round(above + lay.getHeight() + below));
        }

        @Override
        protected void onDraw(Canvas c) {
            p.setColor(small ? QpdStyle.MUTE : QpdStyle.INK);
            float above = small ? px(info ? 6 : 14) : px(22);
            float indent = info ? px(30) : px(small ? 6 : 4);
            if (info) {
                Drawable d = getContext().getDrawable(R.drawable.lumen_ic_info);
                if (d != null) {
                    d = d.mutate();
                    d.setTint(QpdStyle.MUTE);
                    int s = Math.round(px(20));
                    d.setBounds(Math.round(px(2)), Math.round(above), Math.round(px(2)) + s, Math.round(above) + s);
                    d.draw(c);
                }
            }
            c.save();
            c.translate(indent, above);
            lay.draw(c);
            c.restore();
        }
    }

    /**
     * One focusable thing of the sheet: a catalogue or action ROW (icon in a circle, name, right badge), a CARD (a
     * look or the menu look: preview icon, name, line, check badge when chosen) or a PILL (Вернуть как было). Focus:
     * the accent colour and the stock scale. OK runs {@link #onOk}.
     */
    static final class Pick extends View {
        static final int ROW = 0, CARD = 1, PILL = 2;
        static final int BADGE_NONE = 0, BADGE_IN = 1, BADGE_ADD = 2;
        final int style;
        final String key;
        int icon;
        CharSequence title, sub, badgeText, newText;
        int badge;
        boolean checked, current;
        /** A card with its preview on the left (the menu look cards, 96 px), else on top (the look cards). */
        boolean compact;
        /** A look card's drawn preview: one of the Presentation values, null for an icon. */
        org.lumen.menu.quick.Presentation preview;
        Runnable onOk;
        private float focus;
        private ValueAnimator anim;
        private final Paint bgP = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tp, sp, bp, np;
        private final RectF r = new RectF();

        Pick(Context c, int style, String key, int icon, CharSequence title, CharSequence sub) {
            super(c);
            this.style = style;
            this.key = key;
            this.icon = icon;
            this.title = title == null ? "" : title;
            this.sub = sub;
            setFocusable(true);
            setFocusableInTouchMode(false);
            setDefaultFocusHighlightEnabled(false);
            setContentDescription(this.title);
            tp = QpdStyle.text(c, style == ROW ? 21 : style == PILL ? 18 : 20, 600, QpdStyle.INK);
            sp = QpdStyle.text(c, style == CARD ? 15 : 16, 450, QpdStyle.MUTE);
            bp = QpdStyle.text(c, 16, 500, QpdStyle.MUTE);
            np = QpdStyle.text(c, 13, 700, 0xFFFFFFFF);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(Theme.pxf(c, 3));
        }

        private float px(float d) { return Theme.pxf(getContext(), d); }

        @Override
        protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            if (style == PILL) {
                width = Math.round(px(22 + 24 + 10 + 22) + tp.measureText(title, 0, title.length()));
                setMeasuredDimension(width, Math.round(px(56)));
                return;
            }
            setMeasuredDimension(width, Math.round(px(style == ROW ? 64 : compact || icon == 0 && preview == null ? 96 : 168)));
        }

        @Override
        protected void onFocusChanged(boolean gain, int dir, Rect prev) {
            super.onFocusChanged(gain, dir, prev);
            if (anim != null) anim.cancel();
            if (!isAttachedToWindow() || !android.animation.ValueAnimator.areAnimatorsEnabled()) {
                focus = gain ? 1f : 0f;
                apply();
                return;
            }
            anim = ValueAnimator.ofFloat(focus, gain ? 1f : 0f);
            anim.setDuration(QpdLayout.ROW_FOCUS_MS);
            anim.setInterpolator(QpdStyle.ease());
            anim.addUpdateListener(a -> { focus = (float) a.getAnimatedValue(); apply(); });
            anim.start();
        }

        private void apply() {
            float s = 1f + (style == ROW ? 0.02f : 0.03f) * focus;
            setScaleX(s);
            setScaleY(s);
            invalidate();
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent ev) {
            if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) && onOk != null && !ev.isCanceled()) {
                onOk.run();
                return true;
            }
            return super.onKeyUp(keyCode, ev);
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent ev) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) return true;
            return super.onKeyDown(keyCode, ev);
        }

        /** The mock's small drawing of a look: a slider bar, a wide tile, a tile, a circle or a footer button. */
        private void drawPreview(Canvas c, int w, int ink) {
            float cx = w / 2f, cy = px(26 + 32);
            int box = QpdStyle.PANEL | 0xFF000000;
            dot.setColor(box);
            float dotR = px(8);
            switch (preview) {
                case SLIDER: {
                    r.set(cx - px(100), cy - px(22), cx + px(100), cy + px(22));
                    c.drawRoundRect(r, px(12), px(12), dot);
                    dot.setColor(ink);
                    c.drawCircle(cx - px(78), cy, dotR, dot);
                    r.set(cx - px(56), cy - px(3), cx + px(52), cy + px(3));
                    dot.setAlpha(110);
                    c.drawRoundRect(r, px(3), px(3), dot);
                    dot.setAlpha(255);
                    r.set(cx - px(56), cy - px(3), cx + px(18), cy + px(3));
                    c.drawRoundRect(r, px(3), px(3), dot);
                    c.drawCircle(cx + px(80), cy, px(5), dot);
                    break;
                }
                case WIDE:
                case FOOTER: {
                    r.set(cx - px(76), cy - px(30), cx + px(76), cy + px(30));
                    c.drawRoundRect(r, px(14), px(14), dot);
                    dot.setColor(ink);
                    c.drawCircle(cx - px(52), cy, dotR, dot);
                    dot.setAlpha(170);
                    r.set(cx - px(34), cy - px(10), cx + px(48), cy - px(4));
                    c.drawRoundRect(r, px(3), px(3), dot);
                    dot.setAlpha(110);
                    r.set(cx - px(34), cy + px(4), cx + px(20), cy + px(10));
                    c.drawRoundRect(r, px(3), px(3), dot);
                    dot.setAlpha(255);
                    break;
                }
                case SMALL: {
                    r.set(cx - px(32), cy - px(32), cx + px(32), cy + px(32));
                    c.drawRoundRect(r, px(14), px(14), dot);
                    dot.setColor(QpdStyle.ACCENT);
                    c.drawCircle(cx, cy, px(11), dot);
                    break;
                }
                default: {
                    c.drawCircle(cx, cy, px(30), dot);
                    dot.setColor(ink);
                    c.drawCircle(cx, cy, dotR, dot);
                    break;
                }
            }
        }

        private void drawIcon(Canvas c, int res, float cx, float cy, float size, int tint) {
            Drawable d = res == 0 ? null : getContext().getDrawable(res);
            if (d == null) return;
            d = d.mutate();
            d.setTint(tint);
            int s = Math.round(size);
            d.setBounds(Math.round(cx - s / 2f), Math.round(cy - s / 2f), Math.round(cx - s / 2f) + s, Math.round(cy - s / 2f) + s);
            d.draw(c);
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            boolean card = style == CARD;                   // cards: a ring and a lighter face on focus (mock 03), rows: blue
            float lit = card ? 0f : focus;
            int ink = QpdStyle.lerp(QpdStyle.INK, 0xFFFFFFFF, lit), mute = QpdStyle.lerp(QpdStyle.MUTE, QpdStyle.VALUE_FOCUSED, lit);
            float rad = style == PILL ? h / 2f : px(style == ROW ? 22 : 24);
            int base = style == ROW ? (QpdStyle.TILE & 0x00FFFFFF) : QpdStyle.TILE;
            bgP.setColor(card ? QpdStyle.lerp(base, QpdStyle.TILE_HI, focus) : QpdStyle.lerp(base, QpdStyle.ACCENT, focus));
            r.set(0, 0, w, h);
            c.drawRoundRect(r, rad, rad, bgP);
            if (card && focus > 0f) {
                ring.setColor(QpdStyle.lerp(0x00FFFFFF & QpdStyle.INK, QpdStyle.INK, focus));
                float in = ring.getStrokeWidth() / 2f;
                r.set(in, in, w - in, h - in);
                c.drawRoundRect(r, rad - in, rad - in, ring);
            }
            float focus = card ? 0f : this.focus;           // below: the face colours of a card do not follow focus
            tp.setColor(ink);
            sp.setColor(mute);
            if (style == PILL) {
                drawIcon(c, icon, px(22 + 12), h / 2f, px(24), ink);
                c.drawText(title, 0, title.length(), px(22 + 24 + 10), h / 2f - (tp.descent() + tp.ascent()) / 2f, tp);
                return;
            }
            if (style == ROW) {
                float d = px(44), cx = px(16) + d / 2f;
                dot.setColor(focus > 0.5f ? 0x33FFFFFF : QpdStyle.lerp(QpdStyle.TILE, 0x33FFFFFF, focus));
                c.drawCircle(cx, h / 2f, d / 2f, dot);
                drawIcon(c, icon, cx, h / 2f, px(24), ink);
                float x0 = px(16) + d + px(18);
                float right = w - px(18);
                if (badge != BADGE_NONE) {
                    int bc = badge == BADGE_IN ? QpdStyle.lerp(QpdStyle.SELECTED, 0xFFFFFFFF, focus) : mute;
                    bp.setColor(badge == BADGE_IN ? bc : ink);
                    CharSequence bt = badge == BADGE_IN || focus > 0.5f ? badgeText : null;
                    float bw = bt == null ? 0 : bp.measureText(bt, 0, bt.length());
                    if (bt != null) c.drawText(bt, 0, bt.length(), right - bw, h / 2f - (bp.descent() + bp.ascent()) / 2f, bp);
                    float is = px(22);
                    drawIcon(c, badge == BADGE_IN ? R.drawable.lumen_ic_check_circle : R.drawable.lumen_ic_add_circle,
                            right - bw - (bt == null ? 0 : px(8)) - is / 2f, h / 2f, is, badge == BADGE_IN ? bc : focus > 0.5f ? ink : mute);
                    right -= bw + (bt == null ? 0 : px(8)) + is + px(12);
                }
                if (newText != null) {
                    float nw = np.measureText(newText, 0, newText.length()) + px(16);
                    dot.setColor(QpdStyle.lerp(QpdStyle.ACCENT, 0x40FFFFFF, focus));
                    r.set(right - nw, h / 2f - px(13), right, h / 2f + px(13));
                    c.drawRoundRect(r, px(13), px(13), dot);
                    c.drawText(newText, 0, newText.length(), right - nw + px(8), h / 2f - (np.descent() + np.ascent()) / 2f, np);
                    right -= nw + px(10);
                }
                QpdStyle.drawFaded(c, title, tp, x0, Math.max(1f, right - x0), 0, h, h / 2f - (tp.descent() + tp.ascent()) / 2f,
                        0f, px(QpdLayout.FADE));
                return;
            }
            // CARD: preview icon on top, name and line at the bottom, a check badge when chosen
            float pad = px(18);
            if (compact) {
                pad = px(14);
                float pw = px(72), ph = px(52), py0 = (h - ph) / 2f;
                dot.setColor(focus > 0.5f ? 0x33FFFFFF : QpdStyle.lerp(QpdStyle.PANEL | 0xFF000000, 0x33FFFFFF, focus));
                r.set(pad, py0, pad + pw, py0 + ph);
                c.drawRoundRect(r, px(12), px(12), dot);
                drawIcon(c, icon, pad + pw / 2f, h / 2f, px(30), ink);
                float x0 = pad + pw + px(14), bw = Math.max(1f, w - x0 - pad);
                float tb = h / 2f - px(4);                    // the check is beside the name, the line below runs under it
                QpdStyle.drawFaded(c, title, tp, x0, Math.max(1f, bw - (checked ? px(30) : 0)), 0, h, tb, 0f, px(QpdLayout.FADE));
                if (sub != null) QpdStyle.drawFaded(c, sub, sp, x0, bw, 0, h, tb + px(24), 0f, px(QpdLayout.FADE));
                if (checked) {
                    float cr = px(14), cx = w - px(22), cy = px(22);
                    dot.setColor(focus > 0.5f ? 0xFFFFFFFF : QpdStyle.ACCENT);
                    c.drawCircle(cx, cy, cr, dot);
                    drawIcon(c, R.drawable.lumen_ic_check, cx, cy, px(20), focus > 0.5f ? QpdStyle.ACCENT : 0xFFFFFFFF);
                }
                return;
            }
            if (preview != null) drawPreview(c, w, ink);
            if (icon != 0 && preview == null) {
                float pw = px(120), ph = px(64), px0 = (w - pw) / 2f, py0 = px(26);
                dot.setColor(focus > 0.5f ? 0x33FFFFFF : QpdStyle.lerp(QpdStyle.PANEL | 0xFF000000, 0x33FFFFFF, focus));
                r.set(px0, py0, px0 + pw, py0 + ph);
                c.drawRoundRect(r, px(14), px(14), dot);
                drawIcon(c, icon, w / 2f, py0 + ph / 2f, px(36), ink);
            }
            float tb = h - pad - (sub == null ? 0 : px(22)) - tp.descent();
            QpdStyle.drawFaded(c, title, tp, pad, w - 2 * pad - px(30), 0, h, tb, 0f, px(QpdLayout.FADE));
            if (sub != null) QpdStyle.drawFaded(c, sub, sp, pad, w - 2 * pad, 0, h, tb + px(24), 0f, px(QpdLayout.FADE));
            if (checked) {
                float cr = px(14), cx = w - px(22), cy = px(22);
                dot.setColor(focus > 0.5f ? 0xFFFFFFFF : QpdStyle.ACCENT);
                c.drawCircle(cx, cy, cr, dot);
                drawIcon(c, R.drawable.lumen_ic_check, cx, cy, px(20), focus > 0.5f ? QpdStyle.ACCENT : 0xFFFFFFFF);
            }
            if (current && focus < 0.5f) {                  // the look shown on the card now (preview)
                ring.setColor(QpdStyle.lerp(QpdStyle.INK, 0x00FFFFFF, focus));
                float in = ring.getStrokeWidth() / 2f;
                r.set(in, in, w - in, h - in);
                c.drawRoundRect(r, rad - in, rad - in, ring);
            }
        }
    }
}
