// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.signal;

import static org.lumen.signal.OverlayWindow.HINT;
import static org.lumen.signal.OverlayWindow.LABEL;
import static org.lumen.signal.OverlayWindow.RADIUS;
import static org.lumen.signal.OverlayWindow.ROW_H;
import static org.lumen.signal.OverlayWindow.SECTION;
import static org.lumen.signal.OverlayWindow.SMALL;
import static org.lumen.signal.OverlayWindow.TITLE;
import static org.lumen.signal.OverlayWindow.VALUE;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.projector.R;

/**
 * What the signal panel shows in its overlay window (OverlayWindow: the window, its keys and the one key rule are
 * shared with the sensor card and the diagnostics sheet): the small card top right, or the full panel on the right
 * side (BACK closes or folds a panel opened by holding OK, OK switches the option row, up and down scroll a long
 * list). Places are SignalPlace's (title safe, below an app's header), sizes are design px at 1920x1080 scaled to the
 * screen, colours the shared Lumen 2.0 palette (SignalLook).
 */
final class SignalWindow {
    /** Type of the small card (design px): small and calm, 2026-11-18. */
    private static final int CARD_PORT = 17, CARD_BIG = 30, CHIP = 16, CHIP_H = 30, CARD_LINE = 17, CARD_LABEL = 15,
            CARD_ROW_H = 28;

    private final Context c;
    private final OverlayWindow w;

    SignalWindow(Context c, OverlayWindow.Keys keys) {
        this.c = c;
        this.w = new OverlayWindow(c, "LumenSignal", 220, keys);
    }

    boolean shown() { return w.shown(); }

    /** BACK held on the small card: it takes the keys and {@code gone} runs on the press's release (CardKeys). */
    void takeKeys(Runnable gone) { w.takeKeys(gone); }

    /** The full panel has the keys: OK is the panel's (CardHold), else the focused window's. */
    boolean hasKeys() { return w.hasKeys(); }

    void hide() { w.hide(); }

    /**
     * Draws {@code s} in the look of {@code mode} (a new window when the look changes): {@code pinned} the pin mark on
     * the small card (the option keeps it on screen), {@code held} a full panel opened by holding OK (BACK folds it
     * back).
     */
    void show(PanelMode mode, SignalSheet s, boolean popupOn, boolean pinned, boolean held) {
        w.show(mode, () -> {
            SignalLook look = w.look();
            return mode == PanelMode.DETAIL ? sheet(s, look, popupOn, held) : card(s, look, pinned);
        }, () -> show(mode, s, popupOn, pinned, held));
    }

    // ------------------------------------------------------------------ the small card (design A1 / A3, smaller)

    private View card(SignalSheet s, SignalLook look, boolean pinned) {
        LinearLayout box = w.column(look);
        box.setPadding(w.d(22), w.d(18), w.d(22), w.d(18));
        LinearLayout top = w.iconLine(R.drawable.lumen_ic_hdmi, s.port, CARD_PORT, w.medium(), look.mute, look.mute);
        if (pinned) top.addView(w.icon(R.drawable.lumen_ic_pin, CARD_PORT + 4, look.mute));   // kept on screen
        box.addView(top);
        if (s.big != null) {
            TextView big = w.text(s.big, CARD_BIG, w.bold(), look.ink);
            big.setSingleLine(true);
            box.addView(big, w.margins(0, 10, 0, 12));
        }
        for (SignalSheet.Row r : s.cardRows) box.addView(cardRow(r, look), w.fill(0, 0));
        if (s.judder == Judder.JUDDER) box.addView(status(s, look), w.fill(12, 0));
        box.addView(w.cardHint(look), w.margins(0, 14, 0, 0));
        return box;
    }

    // ------------------------------------------------------------------ the full panel (design A2 / A4, smaller)

    private View sheet(SignalSheet s, SignalLook look, boolean popupOn, boolean held) {
        SignalText.Words t = SignalPanel.words(c);
        LinearLayout box = w.column(look);
        box.setPadding(w.d(28), w.d(24), w.d(28), w.d(20));
        box.addView(w.text(t.get(SignalText.TITLE), TITLE, w.bold(), look.ink));
        box.addView(w.text(s.port, LABEL, w.regular(), look.mute), w.margins(0, 2, 0, 0));
        if (!s.chips.isEmpty()) box.addView(chips(s, look), w.fill(12, 0));
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        for (SignalSheet.Row r : s.rows) list.addView(r.section ? section(r, look) : row(r, look),
                r.section ? w.fill(14, 2) : w.fill(0, 0));
        box.addView(w.scroll(list), OverlayWindow.rest());
        box.addView(popupRow(t, look, popupOn), w.fill(12, 10));
        box.addView(held ? w.hints(look, new int[] {R.drawable.lumen_ic_arrow_back}, new String[] {t.get(SignalText.HINT_FOLD)})
                : w.hints(look, new int[] {R.drawable.lumen_ic_info, R.drawable.lumen_ic_arrow_back},
                        new String[] {t.get(SignalText.HINT_HIDE), t.get(SignalText.HINT_CLOSE)}));
        return box;
    }

    private View section(SignalSheet.Row r, SignalLook look) {
        int icon = SignalText.SEC_INPUT.equals(r.key) ? R.drawable.lumen_ic_input
                : SignalText.SEC_SOUND.equals(r.key) ? R.drawable.lumen_ic_volume : R.drawable.lumen_ic_keystone;
        LinearLayout l = w.iconLine(icon, r.label.toUpperCase(c.getResources().getConfiguration().getLocales().get(0)),
                SECTION, w.bold(), look.mute, look.mute);
        ((TextView) l.getChildAt(1)).setLetterSpacing(0.08f);
        return l;
    }

    private View row(SignalSheet.Row r, SignalLook look) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setMinimumHeight(w.d(ROW_H));
        l.setPadding(0, w.d(3), 0, w.d(3));
        l.addView(w.text(r.label, LABEL, w.regular(), look.mute), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (r.small != null) l.addView(w.text(r.small, SMALL, w.regular(), look.mute), w.margins(8, 0, 8, 0));
        l.addView(w.text(r.value, VALUE, w.medium(), look.tone(r.tone)));
        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(l);
        View line = new View(c);
        line.setBackgroundColor(look.line);
        wrap.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, w.d(1))));
        return wrap;
    }

    /** One line of the small card: the label left, quiet; the value right (the setting small before it). */
    private View cardRow(SignalSheet.Row r, SignalLook look) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setMinimumHeight(w.d(CARD_ROW_H));
        l.addView(w.text(r.label, CARD_LABEL, w.regular(), look.mute), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (r.small != null) l.addView(w.text(r.small, CARD_LABEL, w.regular(), look.mute), w.margins(8, 0, 10, 0));
        l.addView(w.text(r.value, CARD_LINE, w.medium(), look.tone(r.tone)));
        return l;
    }

    private View popupRow(SignalText.Words t, SignalLook look, boolean on) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(w.d(14), w.d(8), w.d(10), w.d(8));
        l.setBackground(w.round(look.chip, 14));
        l.addView(w.text(t.get(SignalText.POPUP_ROW), LABEL, w.medium(), look.ink),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView key = w.text("OK", SMALL, w.bold(), look.ink);
        key.setGravity(Gravity.CENTER);
        key.setBackground(w.round(look.key, 15));
        key.setPadding(w.d(9), 0, w.d(9), 0);
        l.addView(key, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, w.d(30)));
        ImageView sw = w.icon(on ? R.drawable.lumen_ic_toggle_on : R.drawable.lumen_ic_toggle_off, 36, on ? look.accent : look.mute);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w.d(36), w.d(36));
        lp.setMarginStart(w.d(10));
        l.addView(sw, lp);
        return l;
    }

    /** The badges, wrapped onto a second line when they do not fit (never cut). */
    private View chips(SignalSheet s, SignalLook look) {
        Flow l = new Flow(c, w.d(6));
        for (SignalSheet.Chip ch : s.chips) {
            TextView t = w.text(ch.text, CHIP, w.medium(), ch.accent ? look.onAccent : look.chipInk);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setPadding(w.d(12), 0, w.d(12), 0);
            t.setBackground(w.round(ch.accent ? look.accent : look.chip, CHIP_H / 2));
            l.addView(t, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, w.d(CHIP_H)));
        }
        return l;
    }

    private View status(SignalSheet s, SignalLook look) {
        boolean ok = s.judder == Judder.SMOOTH;
        LinearLayout l = w.iconLine(ok ? R.drawable.lumen_ic_check_circle : R.drawable.lumen_ic_warning, s.verdict, CARD_LINE,
                w.medium(), look.ink, ok ? look.ok : look.warn);
        l.setGravity(ok ? Gravity.CENTER_VERTICAL : Gravity.TOP);
        l.setPadding(w.d(12), w.d(9), w.d(12), w.d(9));
        l.setBackground(w.round(ok ? look.okBg : look.warnBg, 12));
        return l;
    }

    /** Children left to right, a new line when the next one does not fit; {@code gap} between them and the lines. */
    private static final class Flow extends ViewGroup {
        private final int gap;

        Flow(Context c, int gap) {
            super(c);
            this.gap = gap;
        }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            int max = MeasureSpec.getSize(wSpec), x = 0, y = 0, line = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                ch.measure(MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(ch.getLayoutParams().height, MeasureSpec.EXACTLY));
                if (x > 0 && x + ch.getMeasuredWidth() > max) {
                    x = 0;
                    y += line + gap;
                    line = 0;
                }
                x += ch.getMeasuredWidth() + gap;
                line = Math.max(line, ch.getMeasuredHeight());
            }
            setMeasuredDimension(max, y + line);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int max = r - l, x = 0, y = 0, line = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                int w = ch.getMeasuredWidth(), h = ch.getMeasuredHeight();
                if (x > 0 && x + w > max) {
                    x = 0;
                    y += line + gap;
                    line = 0;
                }
                ch.layout(x, y, x + w, y + h);
                x += w + gap;
                line = Math.max(line, h);
            }
        }
    }
}
