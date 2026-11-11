package org.lumen.projector.panel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;

import org.lumen.projector.ui.Theme;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The card of layout D: clock and date top left, the round buttons top right, the tile rows, the brightness slider.
 * One dark backing card under all of it (tiles floating over video looked bad). Positions are fixed design
 * px of the prototype; the rows come from {@link QpdLayout#rows}, built from the tiles this projector has.
 * In bar mode (remote brightness key) only the slider is shown and the card is the slim bar.
 */
final class QpdCard extends ViewGroup {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Clock clock;
    private final Map<String, QpdTile> tiles = new LinkedHashMap<>();
    private final List<String> header = new ArrayList<>(), wides = new ArrayList<>(), smalls = new ArrayList<>();
    private QpdLampRow lamp;
    private List<List<String>> rows = new ArrayList<>();
    private boolean bar;
    private String last;

    QpdCard(Context c) {
        super(c);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        bg.setColor(QpdStyle.PANEL);
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

    void addTile(QpdTile t) {
        tiles.put(t.key, t);
        addView(t);
        if (t.kind == QpdTile.ROUND) header.add(t.key);
        else if (t.kind == QpdTile.WIDE) wides.add(t.key);
        else smalls.add(t.key);
        t.setOnFocusChangeListener((v, has) -> { if (has) last = ((QpdTile) v).key; });
        rebuildRows();
    }

    void setLamp(QpdLampRow l) {
        if (lamp != null) removeView(lamp);
        lamp = l;
        if (l.getParent() instanceof ViewGroup) ((ViewGroup) l.getParent()).removeView(l);
        addView(l);
        l.setOnFocusChangeListener((v, has) -> { if (has) last = QpdLayout.SLIDER; });
        rebuildRows();
    }

    private void rebuildRows() {
        rows = QpdLayout.rows(header, wides, smalls, lamp != null);
        requestLayout();
    }

    List<List<String>> rows() { return rows; }

    QpdTile tile(String key) { return tiles.get(key); }

    QpdLampRow lamp() { return lamp; }

    View cell(String key) {
        if (QpdLayout.SLIDER.equals(key)) return lamp;
        return tiles.get(key);
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

    String lastKey() { return last; }

    void remember(String key) { if (key != null && cell(key) != null) last = key; }

    /** Focus the remembered cell (or the first wide tile). */
    boolean focusRemembered() {
        View v = last == null ? null : cell(last);
        if (v == null || v.getVisibility() != VISIBLE) v = wides.isEmpty() ? null : tiles.get(wides.get(0));
        return v != null && v.requestFocus();
    }

    /**
     * The window's first focus (and any focus coming into the card) lands on the remembered cell, not on the first
     * focusable (the eye button), as the old TileGrid did.
     */
    @Override
    protected boolean onRequestFocusInDescendants(int direction, android.graphics.Rect previouslyFocusedRect) {
        View v = last == null ? null : cell(last);
        if (v == null || v.getVisibility() != VISIBLE) v = wides.isEmpty() ? null : tiles.get(wides.get(0));
        if (v != null && v.isFocusable() && v.requestFocus()) return true;
        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
    }

    void setBar(boolean on) {
        bar = on;
        clock.setVisibility(on ? GONE : VISIBLE);
        for (QpdTile t : tiles.values()) t.setVisibility(on ? GONE : VISIBLE);
        if (lamp != null) lamp.setBarMode(on);
        requestLayout();
        invalidate();
    }

    boolean isBar() { return bar; }

    void settle() {
        for (QpdTile t : tiles.values()) t.settle();
        if (lamp != null) lamp.settle();
    }

    void tick() { clock.invalidate(); }

    // ------------------------------------------------------------------ layout (design px)
    private int px(float d) { return Theme.px(getContext(), d); }

    private int headerH() { return px(8 + 94 + 22); }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = px(QpdLayout.CARD_W);
        if (bar) {
            if (lamp != null) lamp.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(px(QpdLayout.SLIDER_H), MeasureSpec.EXACTLY));
            setMeasuredDimension(w, px(QpdLayout.SLIDER_H));
            return;
        }
        int pad = px(QpdLayout.CARD_PAD);
        clock.measure(MeasureSpec.makeMeasureSpec(px(QpdLayout.CARD_W - 2 * QpdLayout.CARD_PAD - 2 * QpdLayout.ROUND
                - QpdLayout.ROUND_GAP - 16), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(px(94), MeasureSpec.EXACTLY));
        for (QpdTile t : tiles.values()) {
            int tw = px(t.kind == QpdTile.WIDE ? QpdLayout.WIDE_W : t.kind == QpdTile.SMALL ? QpdLayout.SMALL_W : QpdLayout.ROUND);
            int th = px(t.kind == QpdTile.ROUND ? QpdLayout.ROUND : QpdLayout.TILE_H);
            t.measure(MeasureSpec.makeMeasureSpec(tw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(th, MeasureSpec.EXACTLY));
        }
        int gridRows = Math.max(0, rows.size() - 1 - (lamp != null ? 1 : 0));
        int h = pad + headerH() + gridRows * px(QpdLayout.TILE_H) + Math.max(0, gridRows - 1) * px(QpdLayout.GAP);
        if (lamp != null) {
            lamp.measure(MeasureSpec.makeMeasureSpec(w - 2 * pad, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(px(QpdLayout.SLIDER_H), MeasureSpec.EXACTLY));
            // the prototype's slider sits in a 120 px grid row (6 px above it, 18 px free below)
            h += px(QpdLayout.GAP + Math.max(QpdLayout.TILE_H, QpdLayout.SLIDER_TOP + QpdLayout.SLIDER_H));
        }
        h += pad;
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (bar) {
            if (lamp != null) lamp.layout(0, 0, lamp.getMeasuredWidth(), lamp.getMeasuredHeight());
            return;
        }
        int pad = px(QpdLayout.CARD_PAD);
        int w = r - l;
        clock.layout(pad + px(8), pad + px(8), pad + px(8) + clock.getMeasuredWidth(), pad + px(8) + clock.getMeasuredHeight());
        // round buttons: right aligned, bottom of the header area minus 4 px (prototype align-items:flex-end)
        int bx = w - pad - px(8);
        int by = pad + headerH() - px(22) - px(4) - px(QpdLayout.ROUND);
        for (int i = header.size() - 1; i >= 0; i--) {
            QpdTile tl = tiles.get(header.get(i));
            int s = tl.getMeasuredWidth();
            tl.layout(bx - s, by, bx, by + s);
            bx -= s + px(QpdLayout.ROUND_GAP);
        }
        int y = pad + headerH();
        int[] colX = {0, px(QpdLayout.WIDE_W + QpdLayout.GAP),
                px(QpdLayout.WIDE_W + QpdLayout.GAP + QpdLayout.SMALL_W + QpdLayout.GAP)};
        for (int ri = 1; ri < rows.size(); ri++) {
            List<String> row = rows.get(ri);
            if (row.size() == 1 && QpdLayout.SLIDER.equals(row.get(0))) {
                y += px(QpdLayout.SLIDER_TOP);
                lamp.layout(pad, y, pad + lamp.getMeasuredWidth(), y + lamp.getMeasuredHeight());
                continue;
            }
            boolean hasWide = !row.isEmpty() && tiles.get(row.get(0)).kind == QpdTile.WIDE;
            for (int ci = 0; ci < row.size(); ci++) {
                QpdTile tl = tiles.get(row.get(ci));
                int x = pad + (hasWide ? colX[ci] : ci * px(QpdLayout.SMALL_W + QpdLayout.GAP));
                tl.layout(x, y, x + tl.getMeasuredWidth(), y + tl.getMeasuredHeight());
            }
            y += px(QpdLayout.TILE_H + QpdLayout.GAP);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        rect.set(0, 0, getWidth(), getHeight());
        float rad = Theme.pxf(getContext(), QpdLayout.CARD_RADIUS);
        c.drawRoundRect(rect, rad, rad, bg);
    }

    /** Clock + date (Inter Light, tabular figures), updated by QpdPanel every 10 s while shown. */
    static final class Clock extends View {
        private final TextPaint time, date;

        Clock(Context c) {
            super(c);
            time = QpdStyle.text(c, 64, 300, QpdStyle.INK);
            time.setLetterSpacing(-1.5f / 64f);
            time.setFontFeatureSettings("'tnum', 'cv11', 'ss03'");
            date = QpdStyle.text(c, 19, 450, QpdStyle.MUTE);
            date.setLetterSpacing(0.1f / 19f);
        }

        @Override
        protected void onDraw(Canvas c) {
            Date now = new Date();
            Context ctx = getContext();
            String t = DateFormat.getTimeFormat(ctx).format(now);
            Locale loc = ctx.getResources().getConfiguration().getLocales().get(0);
            String d = new java.text.SimpleDateFormat(DateFormat.getBestDateTimePattern(loc, "EEEEdMMMM"), loc).format(now);
            if (!d.isEmpty()) d = d.substring(0, 1).toUpperCase(loc) + d.substring(1);
            // CSS line box of the prototype clock (64 px, line-height .95): baseline = half-leading + ascent
            float asc = -time.ascent(), desc = time.descent();
            float clockBase = (0.95f * time.getTextSize() - (asc + desc)) / 2f + asc;
            c.drawText(t, 0, clockBase, time);
            float dateBase = asc * 0.95f + desc * 0.4f + Theme.pxf(ctx, 10) - date.ascent();
            c.drawText(d, 0, Math.min(getHeight() - date.descent(), dateBase), date);
        }
    }
}
