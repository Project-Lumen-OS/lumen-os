package org.lumen.projector.panel;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;

import org.lumen.menu.quick.Density;
import org.lumen.menu.quick.EditResult;
import org.lumen.menu.quick.Presentation;
import org.lumen.menu.quick.QuickCatalog;
import org.lumen.menu.quick.QuickEditor;
import org.lumen.menu.quick.QuickItem;
import org.lumen.menu.quick.QuickLayout;
import org.lumen.menu.quick.QuickLayoutJson;
import org.lumen.projector.R;

import java.util.List;

/**
 * The pencil editor on screen (2026-11-18, mocks qm-editor 02 to 04): the user's card stays on the left as a
 * live preview, the sheet is on the right. Catalogue: the menu look, every item of this projector by section (В меню,
 * Добавить, Новое), Вернуть как было. OK on a card item: its inspector (looks, Переместить, Убрать из меню). Moving:
 * arrows move the item, OK puts it there, BACK puts it back. Every change goes through QuickEditor (refused when the
 * card would not fit, with a plain note); BACK in the catalogue is Готово: the menu is stored only when it changed.
 */
final class QuickEditMode {
    interface Host {
        /** Draws {@code l} on the card now (the preview). */
        void preview(QuickLayout l);
        QpdCard card();
        QpdPanel panel();
        CharSequence title(String id);
        int icon(String id);
        CharSequence groupTitle(String group);
        void toast(int res);
        /** The editor is closed: {@code store} true writes {@code value} (null deletes the stored menu). */
        void done(boolean store, QuickLayout value);
    }

    private static final int CATALOGUE = 0, INSPECTOR = 1, MOVE = 2;
    private static final String DENSITY = "density.", RESET = "reset", LOOK = "look.", MOVE_KEY = "move", REMOVE = "remove";

    private final Context app;
    private final Host host;
    private final QuickEditor ed;
    private final QuickLayout stored, start;
    private final QuickEditSheet sheet;
    private int mode = CATALOGUE;
    private String selected;
    private QuickLayout beforeMove;
    private boolean moveFromAdd;

    QuickEditMode(Context app, Host host, QuickCatalog cat, QuickLayout defaults, QuickLayout shown, QuickLayout stored) {
        this.app = app;
        this.host = host;
        this.ed = new QuickEditor(cat, defaults, shown);
        this.stored = stored;
        this.start = shown;
        this.sheet = new QuickEditSheet(app);
    }

    void start() {
        host.panel().showSide(sheet);
        host.preview(ed.layout());
        showCatalogue(null);
    }

    private CharSequence s(int res) { return app.getText(res); }

    private QpdCard card() { return host.card(); }

    // ------------------------------------------------------------------ keys
    private static int dirOf(int k) {
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP: return QpdLayout.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return QpdLayout.DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return QpdLayout.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return QpdLayout.RIGHT;
            default: return -1;
        }
    }

    private static boolean ok(int k) { return k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER; }

    /** Every key while the editor shows (BACK comes through {@link #onBack}); true = done here. */
    boolean onKey(KeyEvent ev) {
        int k = ev.getKeyCode(), dir = dirOf(k);
        boolean down = ev.getAction() == KeyEvent.ACTION_DOWN, up = ev.getAction() == KeyEvent.ACTION_UP && !ev.isCanceled();
        if (mode == MOVE) {
            if (dir >= 0) { if (down) move(dir); return true; }
            if (ok(k)) { if (up) drop(); return true; }
            return false;
        }
        boolean onCard = card().findFocus() != null;
        if (onCard) {
            if (ok(k)) {
                String key = card().focusedKey();
                if (up && key != null) select(key);
                return true;
            }
            if (dir >= 0) { if (down) cardMove(dir); return true; }
            return false;
        }
        if (dir >= 0) { if (down) sheetMove(dir); return true; }
        if (sheet.findFocus() == null && (ok(k) || dir >= 0)) { focusSheet(null); return true; }
        return false;                                      // OK: the focused pick runs itself
    }

    private void cardMove(int dir) {
        QpdCard c = card();
        int[] at = c.focusedCell();
        if (at == null) { c.focusRemembered(); return; }
        int[] to = QpdLayout.move(c.rows(), at[0], at[1], dir, k -> false, k -> false);
        if (to == null) {
            if (dir == QpdLayout.RIGHT) focusSheet(null);
            return;
        }
        View v = c.cell(c.rows().get(to[0]).get(to[1]));
        if (v != null) v.requestFocus();
    }

    private void sheetMove(int dir) {
        View f = sheet.findFocus();
        if (f == null) { focusSheet(null); return; }
        int fd = dir == QpdLayout.UP ? View.FOCUS_UP : dir == QpdLayout.DOWN ? View.FOCUS_DOWN
                : dir == QpdLayout.LEFT ? View.FOCUS_LEFT : View.FOCUS_RIGHT;
        View n = android.view.FocusFinder.getInstance().findNextFocus(sheet, f, fd);   // inside the sheet only
        if (n != null) { n.requestFocus(fd); return; }
        if (dir == QpdLayout.LEFT) {
            if (selected != null && card().cell(selected) != null) card().cell(selected).requestFocus();
            else card().focusRemembered();
        }
    }

    private void focusSheet(String key) {
        View v = key == null ? null : sheet.pick(key);
        if (v == null) v = sheet.first();
        if (v != null) v.requestFocus();
    }

    /** BACK: the move goes back, the inspector closes, the catalogue is Готово. */
    boolean onBack() {
        if (mode == MOVE) { cancelMove(); return true; }
        if (mode == INSPECTOR) {
            String was = selected;
            mode = CATALOGUE;
            card().setMark(null, 0);
            selected = null;
            showCatalogue(was);
            View v = was == null ? null : card().cell(was);
            if (v != null) v.requestFocus();
            return true;
        }
        finish();
        return true;
    }

    // ------------------------------------------------------------------ catalogue
    private void showCatalogue(String focusKey) {
        mode = CATALOGUE;
        sheet.clear();
        sheet.head.set(0, s(R.string.lumen_qe_edit), s(R.string.lumen_qe_subtitle), null);
        card().setHeading(s(R.string.lumen_qe_your_menu), s(R.string.lumen_qe_hint_select));
        sheet.label(sheet.top, s(R.string.lumen_qe_density), false);
        LinearLayout pair = sheet.pair(sheet.top);
        for (Density d : Density.values()) {
            boolean tiles = d == Density.TILES;
            QuickEditSheet.Pick p = new QuickEditSheet.Pick(app, QuickEditSheet.Pick.CARD, DENSITY + d.token,
                    tiles ? R.drawable.lumen_ic_tiles : R.drawable.lumen_ic_list, s(tiles ? R.string.lumen_qe_tiles : R.string.lumen_qe_list),
                    s(tiles ? R.string.lumen_qe_tiles_sub : R.string.lumen_qe_list_sub));
            p.compact = true;
            p.checked = ed.layout().density == d;
            p.onOk = () -> density(d);
            sheet.add(pair, p);
        }
        sheet.label(sheet.top, s(R.string.lumen_qe_show), false);
        for (String g : QuickCatalog.GROUPS) {
            List<QuickItem> items = ed.catalog().group(g);
            if (items.isEmpty()) continue;
            sheet.label(sheet.list, host.groupTitle(g), true);
            for (QuickItem it : items) {
                QuickEditSheet.Pick p = new QuickEditSheet.Pick(app, QuickEditSheet.Pick.ROW, it.id, host.icon(it.id), host.title(it.id), null);
                boolean in = ed.inMenu(it.id);
                p.badge = in ? QuickEditSheet.Pick.BADGE_IN : QuickEditSheet.Pick.BADGE_ADD;
                p.badgeText = s(in ? R.string.lumen_qe_in_menu : R.string.lumen_qe_add);
                if (QuickEditor.isNew(stored, it.id)) p.newText = s(R.string.lumen_qe_new);
                p.onOk = () -> { if (ed.inMenu(it.id)) select(it.id); else add(it.id); };
                sheet.add(sheet.list, p);
            }
        }
        QuickEditSheet.Pick reset = new QuickEditSheet.Pick(app, QuickEditSheet.Pick.PILL, RESET, R.drawable.lumen_ic_restore,
                s(R.string.lumen_qe_reset), null);
        reset.onOk = this::reset;
        sheet.add(sheet.bottom, reset);
        sheet.keys.set(s(R.string.lumen_qe_select), s(R.string.lumen_qe_done));
        sheet.bottom.addView(sheet.keys, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (focusKey != null) sheet.post(() -> focusSheet(focusKey));
    }

    private void density(Density d) {
        EditResult r = ed.setDensity(d);
        if (r == EditResult.FULL) { host.toast(d == Density.LIST ? R.string.lumen_qe_list_full : R.string.lumen_qe_full); return; }
        host.preview(ed.layout());
        showCatalogue(DENSITY + d.token);
    }

    private void reset() {
        ed.reset();
        host.preview(ed.layout());
        showCatalogue(RESET);
    }

    private void add(String id) {
        QuickLayout before = ed.layout();
        EditResult r = ed.add(id);
        if (r == EditResult.FULL) { host.toast(R.string.lumen_qe_full); return; }
        if (r != EditResult.DONE) return;
        host.preview(ed.layout());
        selected = id;
        beforeMove = before;
        moveFromAdd = true;
        startMove(before);
    }

    // ------------------------------------------------------------------ inspector
    private void select(String id) {
        if (ed.catalog().get(id) == null) return;
        selected = id;
        mode = INSPECTOR;
        card().setMark(id, QpdTile.MARK_SELECTED);
        showInspector(null);
    }

    private static int lookName(Presentation p) {
        switch (p) {
            case SLIDER: return R.string.lumen_qe_slider;
            case WIDE: return R.string.lumen_qe_wide;
            case SMALL: return R.string.lumen_qe_small;
            case ROUND: return R.string.lumen_qe_round;
            default: return R.string.lumen_qe_footer;
        }
    }

    private static int lookLine(Presentation p) {
        switch (p) {
            case SLIDER: return R.string.lumen_qe_slider_sub;
            case WIDE: return R.string.lumen_qe_wide_sub;
            case SMALL: return R.string.lumen_qe_small_sub;
            case ROUND: return R.string.lumen_qe_round_sub;
            default: return R.string.lumen_qe_footer_sub;
        }
    }

    private void showInspector(String focusKey) {
        mode = INSPECTOR;
        QuickItem it = ed.catalog().get(selected);
        Presentation now = ed.look(selected);
        sheet.clear();
        sheet.head.set(host.icon(selected), host.title(selected),
                now == null ? null : app.getString(R.string.lumen_qe_now, app.getString(lookName(now))), s(R.string.lumen_qe_edit));
        card().setHeading(s(R.string.lumen_qe_your_menu), s(R.string.lumen_qe_preview));
        sheet.label(sheet.top, s(R.string.lumen_qe_how), false);
        LinearLayout pair = null;
        int n = 0;
        for (Presentation p : it.allowed()) {
            if (n++ % 2 == 0) pair = sheet.pair(sheet.list);
            QuickEditSheet.Pick c = new QuickEditSheet.Pick(app, QuickEditSheet.Pick.CARD, LOOK + p.token, 0, s(lookName(p)), s(lookLine(p)));
            c.preview = p;
            c.checked = p == now;
            c.onOk = () -> look(p);
            sheet.add(pair, c);
        }
        sheet.endPair(pair);
        if (it.allows(Presentation.SLIDER)) sheet.note(sheet.list, s(R.string.lumen_qe_panel_note));
        sheet.label(sheet.list, s(R.string.lumen_qe_place), false);
        QuickEditSheet.Pick mv = new QuickEditSheet.Pick(app, QuickEditSheet.Pick.ROW, MOVE_KEY, R.drawable.lumen_ic_move, s(R.string.lumen_qe_move), null);
        mv.onOk = () -> { moveFromAdd = false; startMove(ed.layout()); };
        sheet.add(sheet.list, mv);
        if (it.pinned()) {
            sheet.note(sheet.list, s(R.string.lumen_qe_pinned));
        } else {
            QuickEditSheet.Pick rm = new QuickEditSheet.Pick(app, QuickEditSheet.Pick.ROW, REMOVE, R.drawable.lumen_ic_remove,
                    s(R.string.lumen_qe_remove), null);
            rm.onOk = this::remove;
            sheet.add(sheet.list, rm);
        }
        sheet.keys.set(s(R.string.lumen_qe_select), s(R.string.lumen_qe_back));
        sheet.bottom.addView(sheet.keys, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        String want = focusKey != null ? focusKey : now == null ? null : LOOK + now.token;
        sheet.post(() -> focusSheet(want));
    }

    private void look(Presentation p) {
        EditResult r = ed.setLook(selected, p);
        if (r == EditResult.FULL) { host.toast(R.string.lumen_qe_full); return; }
        if (r != EditResult.DONE) return;
        host.preview(ed.layout());
        card().setMark(selected, QpdTile.MARK_SELECTED);
        showInspector(LOOK + p.token);
    }

    private void remove() {
        String was = selected;
        if (ed.remove(selected) != EditResult.DONE) return;
        card().setMark(null, 0);
        selected = null;
        host.preview(ed.layout());
        showCatalogue(was);
    }

    // ------------------------------------------------------------------ move
    private void startMove(QuickLayout before) {
        beforeMove = before;
        mode = MOVE;
        sheet.clear();
        sheet.head.set(0, s(R.string.lumen_qe_moving), app.getString(R.string.lumen_qe_move_line, host.title(selected)),
                s(R.string.lumen_qe_edit));
        card().setHeading(s(R.string.lumen_qe_moving), app.getString(R.string.lumen_qe_move_new_place, host.title(selected)));
        sheet.legend(sheet.top, s(R.string.lumen_qe_move_keys), R.drawable.lumen_ic_key_left, R.drawable.lumen_ic_key_right,
                R.drawable.lumen_ic_key_up, R.drawable.lumen_ic_key_down);
        sheet.legend(sheet.top, s(R.string.lumen_qe_put), "OK");
        sheet.legend(sheet.top, s(R.string.lumen_qe_move_cancel), R.drawable.lumen_ic_arrow_back);
        sheet.note(sheet.top, s(R.string.lumen_qe_move_hint));
        markMoving();
    }

    private void markMoving() {
        card().setMark(selected, QpdTile.MARK_MOVING);
        View v = card().cell(selected);
        if (v != null) v.requestFocus();
        else card().post(() -> { View w = card().cell(selected); if (w != null) w.requestFocus(); });
    }

    private void move(int dir) {
        EditResult r = ed.move(selected, dir);
        if (r == EditResult.FULL) { host.toast(R.string.lumen_qe_full); return; }
        if (r != EditResult.DONE) return;
        host.preview(ed.layout());
        markMoving();
    }

    private void drop() {
        if (moveFromAdd) {
            String was = selected;
            card().setMark(null, 0);
            selected = null;
            showCatalogue(was);
            return;
        }
        card().setMark(selected, QpdTile.MARK_SELECTED);
        showInspector(MOVE_KEY);
    }

    private void cancelMove() {
        ed.restore(beforeMove);
        host.preview(ed.layout());
        if (moveFromAdd) {
            String was = selected;
            card().setMark(null, 0);
            selected = null;
            showCatalogue(was);
            return;
        }
        card().setMark(selected, QpdTile.MARK_SELECTED);
        showInspector(MOVE_KEY);
    }

    // ------------------------------------------------------------------ done
    /** The menu window closes while editing: a move goes back, the rest is kept as with Готово. */
    void close() {
        if (mode == MOVE) {
            ed.restore(beforeMove);
            mode = INSPECTOR;
        }
        finish();
    }

    private void finish() {
        host.panel().hideSide();
        card().setHeading(null, null);
        card().setMark(null, 0);
        if (ed.wasReset()) {
            host.done(stored != null, null);
            return;
        }
        if (!ed.layout().sameMenu(start)) {
            host.done(true, ed.toStore(stored));
            return;
        }
        if (stored != null && !stored.seen.containsAll(ed.catalog().ids())) {   // the Новое marks were seen
            host.done(true, QuickLayoutJson.toStore(stored, ed.layout(), ed.catalog()));
            return;
        }
        host.done(false, null);
    }
}
