// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.power;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.projector.R;
import org.lumen.projector.ui.NavRow;
import org.lumen.projector.ui.Row;
import org.lumen.projector.ui.Theme;

/**
 * The sleep timer card of the in-process timer ({@link SleepTimer}): 15 min, 30 min, Custom (LEFT / RIGHT change it in
 * 5-minute steps, 15 when held; OK starts) and "Turn off timer" while a timer runs. One card for every window that
 * shows it: the power menu's Sleep timer tile ({@link PowerMenu}) and the card on its own (HbSleepTimerPanel, core
 * feature sleep-timer-card). Starting or stopping the timer closes the window and SleepTimer shows its Notify card.
 * Main thread.
 */
final class SleepTimerCard {
    /** The window the card sits in. */
    interface Host {
        /** {@code r} that runs only while the window lets OK act. */
        Runnable guarded(Runnable r);

        /** OK may act now (no curtain, the window has settled). */
        boolean canAct();

        void resetAutoHide();

        void dismiss();
    }

    /** The index of the first row in the card (title and hint come first): the window focuses it. */
    static final int FIRST_ROW = 2;

    private static final int[] PRESETS = {15, 30};                  // v6.2 requirements 2: 15, 30 or custom

    private SleepTimerCard() { }

    /** "15 min", "1 h", "1 h 30 min". */
    static String duration(Context c, int minutes) {
        if (minutes < 60) return c.getString(R.string.power_timer_min, minutes);
        int h = minutes / 60, m = minutes % 60;
        return m == 0 ? c.getString(R.string.power_timer_hours, h) : c.getString(R.string.power_timer_hours_min, h, m);
    }

    static LinearLayout build(Context c, Host host) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.PANEL_BG);
        bg.setCornerRadius(Theme.pxf(c, Theme.PANEL_RADIUS));
        bg.setStroke(Theme.px(c, Theme.STROKE), Theme.PANEL_STROKE);
        card.setBackground(bg);
        int ph = Theme.px(c, 24);
        card.setPadding(ph, Theme.px(c, 36), ph, Theme.px(c, 24));

        TextView title = new TextView(c);
        title.setText(R.string.lumen_m_sleep_timer);
        title.setTextColor(Theme.TEXT);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Theme.pxf(c, 38));
        title.setPadding(Theme.px(c, Theme.ROW_PAD_H), 0, Theme.px(c, Theme.ROW_PAD_H), 0);
        card.addView(title);

        TextView sub = new TextView(c);
        boolean running = SleepTimer.isRunning() && SleepTimer.minutesLeft() > 0;
        sub.setText(running ? c.getString(R.string.power_timer_running, SleepTimer.endClock(c))
                : c.getString(R.string.power_timer_hint));
        sub.setTextColor(Theme.TEXT_DIM);
        sub.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Theme.pxf(c, 24));
        sub.setPadding(Theme.px(c, Theme.ROW_PAD_H), Theme.px(c, 6), Theme.px(c, Theme.ROW_PAD_H), Theme.px(c, 18));
        card.addView(sub);

        for (int m : PRESETS) {
            final int min = m;
            addRow(card, new NavRow(c, duration(c, m), host.guarded(() -> start(c, host, min))).setChevron(false));
        }
        addRow(card, new CustomRow(c, host));
        if (running) {
            addRow(card, new NavRow(c, c.getString(R.string.power_timer_cancel), host.guarded(() -> {
                SleepTimer.cancel(c, true);
                host.dismiss();
            })).setChevron(false));
        }
        return card;
    }

    private static void addRow(LinearLayout card, Row r) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Theme.px(card.getContext(), Theme.ROW_GAP);
        card.addView(r, lp);
    }

    private static void start(Context c, Host host, int min) {
        SleepTimer.start(c, min);
        host.dismiss();
    }

    /** "Custom    ‹ 45 min ›": LEFT / RIGHT change the time, OK starts it. */
    private static final class CustomRow extends Row {
        private final Host host;
        private int minutes;
        private CharSequence text;

        CustomRow(Context c, Host host) {
            super(c, c.getString(R.string.power_timer_custom));
            this.host = host;
            minutes = SleepTimer.customMinutes(c);
            text = duration(c, minutes);
        }

        @Override protected float rightWidth() {
            float w = valuePaint.measureText(duration(getContext(), 225));      // widest: "3 h 45 min"
            return w + 2 * (chevronWidth(getContext()) + padH / 2);
        }

        @Override protected void drawRight(Canvas c, float right, float cy, boolean focused) {
            float cw = chevronWidth(getContext());
            drawChevronRight(c, right, cy, focused, minutes >= SleepTimer.CUSTOM_MAX);
            float textRight = right - cw - padH / 2;
            float w = drawValueRight(c, text, textRight, cy);
            drawChevronLeft(c, textRight - w - padH / 2 - cw, cy, focused, minutes <= SleepTimer.CUSTOM_MIN);
        }

        @Override protected boolean onStep(int dir, int repeatCount) {
            int step = repeatCount > 6 ? 15 : 5;
            int v = Math.max(SleepTimer.CUSTOM_MIN, Math.min(SleepTimer.CUSTOM_MAX, minutes + dir * step));
            if (repeatCount > 6) v = Math.round(v / 5f) * 5;
            if (v != minutes) {
                minutes = v;
                text = duration(getContext(), v);
                invalidate();
            }
            host.resetAutoHide();
            return true;                                                     // never leaves the row sideways
        }

        @Override protected boolean onActivate() {
            if (!host.canAct()) return true;
            SleepTimer.saveCustomMinutes(getContext(), minutes);
            start(getContext(), host, minutes);
            return true;
        }
    }
}
