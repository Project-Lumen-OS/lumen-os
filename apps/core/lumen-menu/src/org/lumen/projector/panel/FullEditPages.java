// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;

import org.lumen.menu.FeatureId;
import org.lumen.menu.quick.FullLayout;
import org.lumen.projector.R;
import org.lumen.projector.ui.NavRow;
import org.lumen.projector.ui.Page;
import org.lumen.projector.ui.TextRow;
import org.lumen.projector.ui.ToggleRow;

import java.util.List;

/**
 * The editor of All settings (Slava 2026-11-22), the last row of the General page: a page per category with a switch per
 * row (OK shows or hides it), its Change the order page (OK takes a row, up and down move it inside its group, OK or
 * BACK puts it down) and Reset to default; the last row opens Lumen Home's backup of the menus and Home. Built from the
 * menu's own row types, so focus, keys and looks are the menu's. The editor row itself is not a feature row, so it can
 * never be hidden: the way back always stays.
 */
final class FullEditPages {
    /** What the editor needs from the menu. */
    interface Host {
        List<String> categories();
        CharSequence category(String section);
        /** The section's groups of rows this projector shows, stock order. */
        List<List<FeatureId>> groups(String section);
        CharSequence title(String section, FeatureId f);
        FullLayout layout();
        void save(FullLayout l, String section);
        void push(Page p);
        /** Repaints the shown page with the focus on row {@code index}. */
        void refresh(int index);
        void openBackup();
    }

    private final Context app;
    private final Host host;
    /** The order page being edited, its section, and the row taken (null: none). */
    private Page orderPage;
    private String orderSection;
    private FeatureId taken;

    FullEditPages(Context app, Host host) {
        this.app = app;
        this.host = host;
    }

    Page top() {
        Page p = new Page(app.getString(R.string.lumen_fe_edit));
        for (String sec : host.categories()) {
            if (host.groups(sec).isEmpty()) continue;
            p.add(new NavRow(app, host.category(sec), () -> host.push(category(sec))));
        }
        p.add(new NavRow(app, app.getString(R.string.lumen_fe_backup), host::openBackup));
        return p;
    }

    private Page category(String sec) {
        Page p = new Page(host.category(sec));
        fillCategory(p, sec);
        return p;
    }

    private void fillCategory(Page p, String sec) {
        p.clear();
        p.add(new TextRow(app, app.getString(R.string.lumen_fe_show_hint)));
        FullLayout l = host.layout();
        for (List<FeatureId> g : host.groups(sec)) {
            for (FeatureId f : l.ordered(sec, g)) {
                // a switch per row, as stock Android TV shows on and off lists: the off state stays visible
                p.add(new ToggleRow(app, host.title(sec, f), (row, wanted) -> {
                    FullLayout next = host.layout().toggle(sec, f);
                    host.save(next, sec);
                    row.setChecked(next.shown(sec, f));
                })).setChecked(l.shown(sec, f));
            }
        }
        p.add(new NavRow(app, app.getString(R.string.lumen_fe_order), () -> host.push(order(sec))));
        NavRow[] reset = new NavRow[1];
        reset[0] = p.add(new NavRow(app, app.getString(R.string.lumen_qe_reset), () -> {
            host.save(host.layout().reset(sec), sec);
            fillCategory(p, sec);
            host.refresh(p.rows().size() - 1);
        }));
        reset[0].setChevron(false);
    }

    /** Rows in the order the page shows them, hidden ones too (a hidden row keeps its place for when it comes back). */
    private Page order(String sec) {
        Page p = new Page(app.getString(R.string.lumen_fe_order));
        orderPage = p;
        orderSection = sec;
        taken = null;
        fillOrder(p, sec);
        p.setOnHidden(() -> taken = null);
        return p;
    }

    private void fillOrder(Page p, String sec) {
        p.clear();
        p.add(new TextRow(app, app.getString(R.string.lumen_fe_move_hint)));
        FullLayout l = host.layout();
        for (List<FeatureId> g : host.groups(sec)) {
            for (FeatureId f : l.ordered(sec, g)) {
                NavRow r = p.add(new NavRow(app, host.title(sec, f)));
                r.setChevron(false);
                r.setTag(f);
                r.setOnClick(() -> {
                    taken = taken == f ? null : f;
                    marks(p);
                });
            }
        }
        marks(p);
    }

    private void marks(Page p) {
        for (View v : p.rows()) {
            if (v instanceof NavRow && v.getTag() instanceof FeatureId) {
                ((NavRow) v).setValue(v.getTag() == taken ? app.getString(R.string.lumen_qe_moving) : null);
            }
        }
    }

    /** Keys of the order page while a row is taken: up and down move it, BACK puts it down. True when used. */
    boolean onKey(KeyEvent ev, Page shown) {
        if (taken == null || shown != orderPage || orderPage == null) return false;
        int k = ev.getKeyCode();
        if (k != KeyEvent.KEYCODE_DPAD_UP && k != KeyEvent.KEYCODE_DPAD_DOWN && k != KeyEvent.KEYCODE_BACK) return false;
        if (ev.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (k == KeyEvent.KEYCODE_BACK) {
            taken = null;
            marks(orderPage);
            return true;
        }
        FullLayout next = host.layout().move(orderSection, host.groups(orderSection), taken, k == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
        if (next == null) return true;                        // the edge of its group: it stays
        host.save(next, orderSection);
        FeatureId f = taken;
        fillOrder(orderPage, orderSection);
        taken = f;
        marks(orderPage);
        int at = 0;
        for (int i = 0; i < orderPage.rows().size(); i++) if (orderPage.rows().get(i).getTag() == f) at = i;
        host.refresh(at);
        return true;
    }
}
