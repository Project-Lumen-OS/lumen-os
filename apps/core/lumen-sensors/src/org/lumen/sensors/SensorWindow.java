// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.sensors;

import static org.lumen.signal.OverlayWindow.LABEL;
import static org.lumen.signal.OverlayWindow.ROW_H;
import static org.lumen.signal.OverlayWindow.SECTION;
import static org.lumen.signal.OverlayWindow.TITLE;
import static org.lumen.signal.OverlayWindow.VALUE;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.projector.R;
import org.lumen.signal.OverlayWindow;
import org.lumen.signal.PanelMode;
import org.lumen.signal.SignalLook;
import org.lumen.signal.SignalPanel;
import org.lumen.signal.SignalText;

/**
 * What the developer overlays of this module show in their overlay window (lumen-signal OverlayWindow: the window,
 * its keys and the one key rule are shared with the signal panel): the small sensor card top right, the full sensor
 * sheet on the right side (BACK closes or folds a sheet opened by holding OK, OK runs the sheet's action, up and down
 * scroll), or a text sheet (diagnostics). Same places, type and Lumen 2.0 colours as the signal panel.
 */
final class SensorWindow {
    private static final int CARD_TITLE = 17, CARD_LABEL = 15, CARD_VALUE = 17, CARD_ROW_H = 28, MONO = 15;

    private final Context c;
    private final OverlayWindow w;
    /** the sheet's OK action (diagnostics: read again), null = none */
    private Runnable ok;

    SensorWindow(Context c, Runnable back, Runnable collapse) {
        this.c = c;
        this.w = new OverlayWindow(c, "LumenSensors", 200, new OverlayWindow.Keys() {
            @Override public void back() { back.run(); }

            @Override public void ok() { if (ok != null) ok.run(); }

            @Override public void collapse() { if (collapse != null) collapse.run(); }
        });
    }

    void setOk(Runnable r) { ok = r; }

    boolean shown() { return w.shown(); }

    PanelMode mode() { return w.mode(); }

    /** BACK held on the small card: it takes the keys and {@code gone} runs on the press's release (lumen-signal). */
    void takeKeys(Runnable gone) { w.takeKeys(gone); }

    /** The full sensor sheet has the keys: OK is the sheet's (CardHold), else the focused window's. */
    boolean hasKeys() { return w.hasKeys(); }

    void hide() { w.hide(); }

    /**
     * The sensor card ({@code mode} COMPACT) or the full sensor sheet (DETAIL): the hold hint always on the card,
     * {@code held} a sheet opened by holding OK (BACK folds it back).
     */
    void show(PanelMode mode, SensorSheet s, boolean held) {
        w.show(mode, () -> {
            SensorSheet.Words t = SensorPanel.words(c);
            SignalLook look = w.look();
            if (mode != PanelMode.DETAIL) return card(s, look, t);
            return sheet(t.get("title"), rows(s, look), look, held ? null : t.get("hint_hide"),
                    held ? SignalPanel.text(c, SignalText.HINT_FOLD) : t.get("hint_close"));
        }, () -> show(mode, s, held));
    }

    /** A text sheet (diagnostics): {@code text} line by line in the full sheet. */
    void showText(String title, String text) {
        w.show(PanelMode.DETAIL, () -> {
            SignalLook look = w.look();
            LinearLayout list = new LinearLayout(c);
            list.setOrientation(LinearLayout.VERTICAL);
            for (String line : text.split("\n")) {
                TextView t = w.text(line, MONO, Typeface.MONOSPACE, look.ink);
                t.setPadding(0, w.d(3), 0, w.d(3));
                list.addView(t);
            }
            SensorSheet.Words words = SensorPanel.words(c);
            return sheet(title, list, look, words.get("hint_refresh"), words.get("hint_close"));
        }, () -> showText(title, text));
    }

    private View card(SensorSheet s, SignalLook look, SensorSheet.Words t) {
        LinearLayout box = w.column(look);
        box.setPadding(w.d(22), w.d(18), w.d(22), w.d(18));
        box.addView(w.iconLine(R.drawable.lumen_ic_info, t.get("title"), CARD_TITLE, w.medium(), look.mute, look.mute),
                w.margins(0, 0, 0, 10));
        for (SensorSheet.Row r : s.cardRows) box.addView(row(r, look, CARD_LABEL, CARD_VALUE, CARD_ROW_H, false));
        if (s.cardRows.isEmpty()) box.addView(w.text(t.get("none"), CARD_LABEL, w.regular(), look.mute));
        box.addView(w.cardHint(look), w.margins(0, 14, 0, 0));
        return box;
    }

    private View rows(SensorSheet s, SignalLook look) {
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        for (SensorSheet.Row r : s.rows) {
            if (r.section) {
                TextView t = w.text(r.label.toUpperCase(c.getResources().getConfiguration().getLocales().get(0)), SECTION,
                        w.bold(), look.mute);
                t.setLetterSpacing(0.08f);
                list.addView(t, w.margins(0, 14, 0, 4));
            } else {
                list.addView(row(r, look, LABEL, VALUE, ROW_H, true));
            }
        }
        if (s.rows.isEmpty()) list.addView(w.text(SensorPanel.words(c).get("none"), LABEL, w.regular(), look.mute));
        return list;
    }

    /** The full sheet: {@code okHint} next to the OK action's icon (null = no such hint), {@code backHint} BACK's. */
    private View sheet(String title, View content, SignalLook look, String okHint, String backHint) {
        LinearLayout box = w.column(look);
        box.setPadding(w.d(28), w.d(24), w.d(28), w.d(20));
        box.addView(w.text(title, TITLE, w.bold(), look.ink));
        box.addView(w.scroll(content), OverlayWindow.rest());
        box.addView(okHint == null ? w.hints(look, new int[] {R.drawable.lumen_ic_arrow_back}, new String[] {backHint})
                : w.hints(look, new int[] {ok == null ? R.drawable.lumen_ic_info : R.drawable.lumen_ic_refresh,
                        R.drawable.lumen_ic_arrow_back}, new String[] {okHint, backHint}), w.margins(0, 12, 0, 0));
        return box;
    }

    private View row(SensorSheet.Row r, SignalLook look, int label, int value, int h, boolean line) {
        LinearLayout l = w.line(h);
        l.addView(w.text(r.label, label, w.regular(), look.mute), new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        l.addView(w.text(r.value, value, w.medium(), look.ink));
        return line ? w.lined(l, look) : l;
    }
}
