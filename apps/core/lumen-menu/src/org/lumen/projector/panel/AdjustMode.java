// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.KeyEvent;
import android.view.View;

import org.lumen.projector.ui.ChoiceRow;
import org.lumen.projector.ui.Row;
import org.lumen.projector.ui.SliderRow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The stock picture adjustment of every model: OK on a picture setting hides the big menu (the picture is seen at full
 * brightness, no scrim) and a small three row strip at the bottom centre adjusts it ({@link AdjustStrip}). UP / DOWN
 * reach the page's other settings, LEFT / RIGHT change the value through the row itself (so the menu's own locks,
 * notes and commits stay one path), OK or BACK bring the menu back on the row last changed.
 */
final class AdjustMode {
    /** The menu side this mode needs: its window and the rows of the page shown. */
    interface Menu {
        /** Shows {@code strip} in place of the menu in the menu's own window, which keeps the keys; null: the menu back. */
        void showStrip(AdjustStrip strip);
        /** The rows of the page shown now, in order. */
        List<View> pageRows();
    }

    private static final long SLIDE_MS = 160;

    private final Context app;
    private final Menu menu;
    private final Predicate<View> adjustable;
    private AdjustStrip strip;
    private View row;
    /**
     * The rows a strip opened from a quick menu tile walks (its page of the full menu), null: the page shown. The focus
     * stays on the tile all the while (the rows are not in the window).
     */
    private List<View> walk;
    private long okDown = -1;
    private ValueAnimator slide;

    AdjustMode(Context app, Menu menu, Predicate<View> adjustable) {
        this.app = app;
        this.menu = menu;
        this.adjustable = adjustable;
    }

    boolean active() { return strip != null; }

    /** The row being adjusted now, null outside the mode. */
    View row() { return row; }

    /**
     * Keys of the menu window, before the rows. Outside the mode: the release of OK on an adjustable row starts it.
     * In it: UP / DOWN move between the page's adjustable rows, OK / BACK leave, every other key goes on to the row
     * (true = consumed here).
     */
    boolean onKey(KeyEvent ev, View focused) {
        int k = ev.getKeyCode();
        boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
        boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (!active()) {
            if (!ok || !adjustable.test(focused)) return false;
            if (down) {
                if (ev.getRepeatCount() == 0) okDown = ev.getDownTime();
                return true;
            }
            boolean ours = okDown == ev.getDownTime() && !ev.isCanceled();
            okDown = -1;
            if (ours) start(focused);
            return true;
        }
        if (ok || k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE) {
            if (!down && !ev.isCanceled()) stop();
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (down) move(k == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
            return true;
        }
        strip.post(this::sync);                              // LEFT / RIGHT change the row: redraw after it
        if (walk == null) return false;
        boolean step = k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (step) row.dispatchKeyEvent(ev);                  // the focus is on the tile: the row gets its step itself
        return step;
    }

    /**
     * A slider tile of the quick menu (2026-11-24): the same strip on {@code r}, walking {@code rows} (the page
     * of the full menu that has it); the focus stays on the tile.
     */
    void startOn(View r, List<View> rows) {
        if (active() || r == null) return;
        walk = rows;
        start(r);
    }

    /** Enters the mode on {@code r}: the menu hides, the strip shows at the bottom centre. */
    void start(View r) {
        if (active() || r == null) return;
        strip = new AdjustStrip(app);
        row = r;
        menu.showStrip(strip);
        sync();
    }

    /** Leaves the mode: the strip goes, the menu comes back with the focus on the last row adjusted. */
    void stop() {
        if (!active()) return;
        if (slide != null) slide.cancel();
        strip = null;
        okDown = -1;
        menu.showStrip(null);
        View to = walk == null ? row : null;                 // a tile's strip: the focus never left the tile
        walk = null;
        row = null;
        if (to != null) to.requestFocus();
    }

    /** Redraws the strip from the rows (a value read back, a step taken). */
    void sync() {
        if (!active()) return;
        List<View> list = rows();
        int i = list.indexOf(row);
        if (i < 0) {                                          // the row closed or vanished under a new signal
            stop();
            return;
        }
        strip.show(i > 0 ? item(list.get(i - 1)) : null, item(row), i + 1 < list.size() ? item(list.get(i + 1)) : null);
    }

    /** UP (-1) / DOWN (1): the next adjustable row of the page, the rows slide in from that side. */
    private void move(int dir) {
        List<View> list = rows();
        int i = list.indexOf(row) + dir;
        if (i < 0 || i >= list.size()) return;
        row = list.get(i);
        if (walk == null) row.requestFocus();
        sync();
        if (slide != null) slide.cancel();
        slide = ValueAnimator.ofFloat(dir, 0f).setDuration(SLIDE_MS);
        slide.addUpdateListener(a -> { if (strip != null) strip.setShift((float) a.getAnimatedValue()); });
        slide.start();
    }

    /** The page's rows the strip walks: adjustable, shown, open now. */
    private List<View> rows() {
        List<View> out = new ArrayList<>();
        for (View v : walk != null ? walk : menu.pageRows()) {
            boolean can = walk != null ? adjustableKind(v) : adjustable.test(v);
            if (v == row || v.getVisibility() == View.VISIBLE && can && ((Row) v).isRowEnabled()) out.add(v);
        }
        return out;
    }

    /** One row as the strip draws it. */
    static AdjustStrip.Item item(View v) {
        if (v instanceof SliderRow) {
            SliderRow s = (SliderRow) v;
            return AdjustStrip.Item.slider(s.getTitle(), s.getMin(), s.getMax(), s.getValue(), s.isKnown(), s.valueText(), s.isRowEnabled());
        }
        ChoiceRow c = (ChoiceRow) v;
        return AdjustStrip.Item.choice(c.getTitle(), c.getSelectedText(), c.isRowEnabled());
    }

    /** A row the strip can adjust: a slider, or a choice that steps its options in place (no list of its own). */
    static boolean adjustableKind(View v) {
        return v instanceof SliderRow || v instanceof ChoiceRow && !((ChoiceRow) v).opensOnCenter();
    }
}
