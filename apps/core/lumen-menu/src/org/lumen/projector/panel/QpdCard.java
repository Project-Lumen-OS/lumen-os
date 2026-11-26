// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;

import org.lumen.menu.quick.Density;
import org.lumen.menu.quick.LineKind;
import org.lumen.menu.quick.Placement;
import org.lumen.menu.quick.QuickCatalog;
import org.lumen.menu.quick.QuickLayout;
import org.lumen.menu.quick.QuickPacker;
import org.lumen.projector.ui.Theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The card of layout D: clock and date top left, the round buttons top right, then the lines of the user's quick menu
 * as QuickPacker packed them (tile rows, sliders, list rows) and the footer buttons. With nothing stored that is the
 * card MenuSpec gives (QuickDefaults): the tile rows, the brightness slider and the iris slider under it. One backing
 * card under all of it (tiles floating over video looked bad), in the light or the dark colours of the theme.
 * In bar mode (remote brightness key) only the brightness slider is shown and the card is the slim bar. In the pencil
 * editor the clock gives way to the editor's heading and the chosen item wears a ring.
 */
final class QpdCard extends ViewGroup {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Clock clock;
    private final Map<String, QpdTile> tiles = new LinkedHashMap<>();
    /** The slider tiles by key (feature id), top to bottom; the light level is {@link QpdLayout#SLIDER}. */
    private final Map<String, QpdSliderRow> sliders = new LinkedHashMap<>();
    /** Where each child goes, design px from the card's top left (computed from the packed card). */
    private final Map<View, Rect> slots = new HashMap<>();
    private QuickPacker.Card packed = QuickPacker.pack(new QuickLayout(QuickLayout.VERSION, Density.TILES,
            Collections.<Placement>emptyList(), Collections.<String>emptyList()));
    private List<List<String>> rows = new ArrayList<>();
    private boolean bar;
    private String last, marked;

    QpdCard(Context c) {
        super(c);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Theme.pxf(c, 3));
        clock = new Clock(c);
        addView(clock);
        setElevation(Theme.pxf(c, 30));
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Theme.pxf(v.getContext(), QpdLayout.CARD_RADIUS));
                o.setAlpha(0.55f);
            }
        });
    }

    /**
     * Shows the packed quick menu {@code card}; {@code views} holds the QpdTile or QpdSliderRow of each of its items (an
     * item without a view is left out). The focus map rows follow the packing: the circles, each line, the footer.
     */
    void setContent(QuickPacker.Card card, Map<String, View> views) {
        for (int i = getChildCount() - 1; i >= 0; i--) if (getChildAt(i) != clock) removeViewAt(i);
        tiles.clear();
        sliders.clear();
        slots.clear();
        packed = card;
        rows = new ArrayList<>();
        for (List<String> row : card.keyRows()) {
            List<String> r = new ArrayList<>();
            for (String id : row) {
                View v = views.get(id);
                if (v == null) continue;
                if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
                addView(v);
                if (v instanceof QpdSliderRow) sliders.put(id, (QpdSliderRow) v);
                else if (v instanceof QpdTile) tiles.put(id, (QpdTile) v);
                final String key = id;
                v.setOnFocusChangeListener((x, has) -> {
                    if (!has) return;
                    last = key;
                    QpdTile t = tiles.get(key);
                    clock.setLine(t != null && t.kind == QpdTile.ROUND ? t.title() : null);
                });
                r.add(id);
            }
            if (!r.isEmpty() || rows.isEmpty()) rows.add(r);       // row 0 = the circles, even when there are none
        }
        if (marked != null && cell(marked) == null) marked = null;
        requestLayout();
        invalidate();
    }

    /** True for the key of a slider tile (it steps itself on LEFT / RIGHT). */
    boolean isSlider(String key) { return sliders.containsKey(key); }

    /** The slider tiles, top to bottom. */
    java.util.Collection<QpdSliderRow> sliders() { return sliders.values(); }

    /** The tiles (every look), in card order. */
    java.util.Collection<QpdTile> tiles() { return tiles.values(); }

    List<List<String>> rows() { return rows; }

    QpdTile tile(String key) { return tiles.get(key); }

    /** The brightness slider, or null. */
    QpdSliderRow lamp() { return sliders.get(QpdLayout.SLIDER); }

    View cell(String key) {
        QpdSliderRow s = sliders.get(key);
        return s != null ? s : tiles.get(key);
    }

    /** {row, col} of the focused cell, or null. */
    int[] focusedCell() {
        View f = findFocus();
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            for (int c = 0; c < row.size(); c++) if (cell(row.get(c)) == f && f != null) return new int[]{r, c};
        }
        return null;
    }

    /** Key of the focused cell, or null. */
    String focusedKey() {
        int[] at = focusedCell();
        return at == null ? null : rows.get(at[0]).get(at[1]);
    }

    String lastKey() { return last; }

    void remember(String key) { if (key != null && cell(key) != null) last = key; }

    /** The cell the focus comes back to: the remembered one, else the first wide tile, else the first cell. */
    private View home() {
        View v = last == null ? null : cell(last);
        if (v != null && v.getVisibility() == VISIBLE) return v;
        for (QpdTile t : tiles.values()) if (t.kind == QpdTile.WIDE && t.getVisibility() == VISIBLE) return t;
        for (int r = 1; r < rows.size(); r++) if (!rows.get(r).isEmpty()) return cell(rows.get(r).get(0));
        return rows.isEmpty() || rows.get(0).isEmpty() ? null : cell(rows.get(0).get(0));
    }

    /** Focus the remembered cell (or the first wide tile). */
    boolean focusRemembered() {
        View v = home();
        return v != null && v.requestFocus();
    }

    /**
     * The window's first focus (and any focus coming into the card) lands on the remembered cell, not on the first
     * focusable (the eye button), as the old TileGrid did.
     */
    @Override
    protected boolean onRequestFocusInDescendants(int direction, android.graphics.Rect previouslyFocusedRect) {
        View v = home();
        if (v != null && v.isFocusable() && v.requestFocus()) return true;
        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
    }

    /** The pencil editor: its heading in place of the clock (null: the clock again). */
    void setHeading(CharSequence title, CharSequence sub) {
        clock.setHeading(title, sub);
    }

    /** The pencil editor's mark (QpdTile.MARK_*) on the item {@code key}, null: none. */
    void setMark(String key, int mark) {
        if (marked != null) {
            QpdTile t = tiles.get(marked);
            if (t != null) t.setMark(0);
        }
        marked = mark == 0 ? null : key;
        markKind = mark;
        QpdTile t = key == null ? null : tiles.get(key);
        if (t != null) t.setMark(mark);
        invalidate();
    }

    private int markKind;

    void setBar(boolean on) {
        bar = on;
        clock.setVisibility(on ? GONE : VISIBLE);
        for (QpdTile t : tiles.values()) t.setVisibility(on ? GONE : VISIBLE);
        QpdSliderRow lamp = lamp();
        for (QpdSliderRow s : sliders.values()) if (s != lamp) s.setVisibility(on ? GONE : VISIBLE);
        if (lamp != null) lamp.setBarMode(on);
        requestLayout();
        invalidate();
    }

    boolean isBar() { return bar; }

    void settle() {
        for (QpdTile t : tiles.values()) t.settle();
        for (QpdSliderRow s : sliders.values()) s.settle();
    }

    void tick() { clock.invalidate(); }

    /**
     * A tile's name changed (the theme button after a press): the line under the clock shows the focused round
     * button's name, so it changes with it (X50 live audit 2026-11-24: the line kept the old mode while the button showed the new one).
     */
    void titleChanged(QpdTile t) {
        if (t.kind == QpdTile.ROUND && t.hasFocus()) clock.setLine(t.title());
    }

    /** The theme changed: every part redraws in the new colours. */
    void retheme() {
        invalidate();
        clock.invalidate();
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).invalidate();
    }

    // ------------------------------------------------------------------ layout (design px)
    private int px(float d) { return Theme.px(getContext(), d); }

    /** The slots of the packed card in design px: circles right in the top row, lines, footer. */
    private void place() {
        slots.clear();
        int pad = QpdLayout.CARD_PAD, inner = QpdLayout.CARD_W - 2 * pad;
        int bx = QpdLayout.CARD_W - pad - 8, by = pad + QuickPacker.HEADER - 22 - 4 - QpdLayout.ROUND;
        List<Placement> round = packed.round;
        for (int i = round.size() - 1; i >= 0; i--) {
            View v = cell(round.get(i).id);
            if (v == null) continue;
            slots.put(v, new Rect(bx - QpdLayout.ROUND, by, bx, by + QpdLayout.ROUND));
            bx -= QpdLayout.ROUND + QpdLayout.ROUND_GAP;
        }
        for (QuickPacker.Line l : packed.lines) {
            int x = pad;
            for (Placement p : l.cells) {
                View v = cell(p.id);
                int w;
                if (l.kind != LineKind.TILES) w = inner;
                else if (p.as == org.lumen.menu.quick.Presentation.WIDE) w = QpdLayout.WIDE_W;
                else w = l.four() ? QuickPacker.TILE4 : QpdLayout.SMALL_W;
                if (v != null) slots.put(v, new Rect(x, l.top, x + w, l.top + l.height));
                x += w + QpdLayout.GAP;
            }
        }
        if (!packed.footer.isEmpty()) {
            int n = 0;
            boolean pencil = false;
            for (Placement p : packed.footer) {
                if (QuickCatalog.EDIT.equals(p.id)) pencil = true;
                else n++;
            }
            int foot = QuickPacker.FOOT, gap = 12;
            int right = pad + inner - (pencil ? foot + (n > 0 ? gap : 0) : 0);
            int each = n == 0 ? 0 : (right - pad - (n - 1) * gap) / n;
            int x = pad;
            for (Placement p : packed.footer) {
                View v = cell(p.id);
                if (v == null) continue;
                if (QuickCatalog.EDIT.equals(p.id)) {
                    slots.put(v, new Rect(pad + inner - foot, packed.footerTop, pad + inner, packed.footerTop + foot));
                } else {
                    slots.put(v, new Rect(x, packed.footerTop, x + each, packed.footerTop + foot));
                    x += each + gap;
                }
            }
        }
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = px(QpdLayout.CARD_W);
        QpdSliderRow lamp = lamp();
        if (bar) {
            if (lamp != null) lamp.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(px(QpdLayout.SLIDER_H), MeasureSpec.EXACTLY));
            setMeasuredDimension(w, px(QpdLayout.SLIDER_H));
            return;
        }
        place();
        clock.measure(MeasureSpec.makeMeasureSpec(px(QpdLayout.clockWidth(packed.round.size())), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(px(94), MeasureSpec.EXACTLY));
        for (Map.Entry<View, Rect> e : slots.entrySet()) {
            Rect r = e.getValue();
            e.getKey().measure(MeasureSpec.makeMeasureSpec(px(r.width()), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(px(r.height()), MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(w, px(packed.height));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (bar) {
            QpdSliderRow lamp = lamp();
            if (lamp != null) lamp.layout(0, 0, lamp.getMeasuredWidth(), lamp.getMeasuredHeight());
            return;
        }
        int pad = px(QpdLayout.CARD_PAD);
        clock.layout(pad + px(8), pad + px(8), pad + px(8) + clock.getMeasuredWidth(), pad + px(8) + clock.getMeasuredHeight());
        for (Map.Entry<View, Rect> e : slots.entrySet()) {
            Rect s = e.getValue();
            View v = e.getKey();
            int x = px(s.left), y = px(s.top);
            v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        bg.setColor(QpdStyle.PANEL);
        rect.set(0, 0, getWidth(), getHeight());
        float rad = Theme.pxf(getContext(), QpdLayout.CARD_RADIUS);
        c.drawRoundRect(rect, rad, rad, bg);
    }

    @Override
    protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
        QpdSliderRow s = marked == null ? null : sliders.get(marked);
        if (s == null || s.getVisibility() != VISIBLE) return;      // a slider's ring (tiles draw their own)
        ring.setColor(markKind == QpdTile.MARK_MOVING ? 0xFFFFFFFF : QpdStyle.SELECTED);
        float in = ring.getStrokeWidth() / 2f, rad = Theme.pxf(getContext(), QpdLayout.TILE_RADIUS);
        rect.set(s.getLeft() + in, s.getTop() + in, s.getRight() - in, s.getBottom() - in);
        c.drawRoundRect(rect, rad, rad, ring);
    }

    /**
     * Clock + date (Inter Light, tabular figures), updated by QpdPanel every 10 s while shown. A focused circle's name
     * takes the date line (the pencil: "Изменить меню"); the pencil editor puts its heading in place of both.
     */
    static final class Clock extends View {
        private final TextPaint time, date, line, head, sub;
        private CharSequence focusLine, heading, subline;

        Clock(Context c) {
            super(c);
            time = QpdStyle.text(c, 64, 300, QpdStyle.INK);
            time.setLetterSpacing(-1.5f / 64f);
            time.setFontFeatureSettings("'tnum', 'cv11', 'ss03'");
            date = QpdStyle.text(c, 19, 450, QpdStyle.MUTE);
            date.setLetterSpacing(0.1f / 19f);
            line = QpdStyle.text(c, 19, 600, QpdStyle.INK);
            head = QpdStyle.text(c, 34, 650, QpdStyle.INK);
            head.setLetterSpacing(-0.4f / 34f);
            sub = QpdStyle.text(c, 19, 450, QpdStyle.MUTE);
        }

        void setLine(CharSequence t) {
            focusLine = t;
            invalidate();
        }

        void setHeading(CharSequence t, CharSequence s) {
            heading = t;
            subline = s;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            Context ctx = getContext();
            if (heading != null) {
                head.setColor(QpdStyle.INK);
                sub.setColor(QpdStyle.MUTE);
                float hb = Theme.pxf(ctx, 12) - head.ascent();
                QpdStyle.drawFaded(c, heading, head, 0, getWidth(), 0, getHeight(), hb, 0f, Theme.pxf(ctx, QpdLayout.FADE));
                if (subline != null) {
                    float sb = hb + head.descent() + Theme.pxf(ctx, 10) - sub.ascent();
                    QpdStyle.drawFaded(c, subline, sub, 0, getWidth(), 0, getHeight(), sb, 0f, Theme.pxf(ctx, QpdLayout.FADE));
                }
                return;
            }
            time.setColor(QpdStyle.INK);
            date.setColor(QpdStyle.MUTE);
            line.setColor(QpdStyle.INK);
            Date now = new Date();
            String t = DateFormat.getTimeFormat(ctx).format(now);
            Locale loc = ctx.getResources().getConfiguration().getLocales().get(0);
            String d = new java.text.SimpleDateFormat(DateFormat.getBestDateTimePattern(loc, "EEEEdMMMM"), loc).format(now);
            if (!d.isEmpty()) d = d.substring(0, 1).toUpperCase(loc) + d.substring(1);
            // CSS line box of the prototype clock (64 px, line-height .95): baseline = half-leading + ascent
            float asc = -time.ascent(), desc = time.descent();
            float clockBase = (0.95f * time.getTextSize() - (asc + desc)) / 2f + asc;
            float fit = QpdLayout.clockFit(time.measureText(t), getWidth());
            c.save();
            c.scale(fit, fit, 0, clockBase);
            c.drawText(t, 0, clockBase, time);
            c.restore();
            float dateBase = asc * 0.95f + desc * 0.4f + Theme.pxf(ctx, 10) - date.ascent();
            CharSequence under = focusLine != null ? focusLine : d;
            TextPaint p = focusLine != null ? line : date;
            float base = Math.min(getHeight() - p.descent(), dateBase);
            QpdStyle.drawFaded(c, under, p, 0, getWidth(), 0, getHeight(), base, 0f, Theme.pxf(ctx, QpdLayout.FADE));
        }
    }
}
