// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.power;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.menu.FeatureId;
import org.lumen.menu.PowerItem;
import org.lumen.projector.Family;
import org.lumen.projector.R;
import org.lumen.projector.panel.MenuController;
import org.lumen.projector.ui.NavRow;
import org.lumen.projector.ui.OverlayHost;
import org.lumen.projector.ui.RoundTile;
import org.lumen.projector.ui.RoundTilePanel;
import org.lumen.projector.ui.Row;
import org.lumen.projector.ui.Theme;

/**
 * MODULE "power" (v6.2): the power menu of the gmpf and Play 6 families (a short POWER press by default, the hold with
 * the setting "Sleep"; lumen-menu PowerKeyChoice). The items and their order are the shared lumen-menu PowerItem list
 * (the X50 family's panel.PowerMenu shows the same): Turn off screen (the menu's lamp.off, LampOffHal) / Sleep (lamp
 * off, then STR, the same as the power key's sleep) / Power off (Android's real power off, Halt) / Restart / Sleep
 * timer, round tiles centred over a dimmed picture. LEFT / RIGHT move, OK acts, BACK closes; closes 20 s after the last
 * key (stock).
 *
 * Stock (com.xgimi.shutdown PowerDialog, the same code on every family): the focus starts on Power off (on the timer
 * while one runs), Power off counts 15 s down and powers off unless the focus leaves it (ui.RoundTilePanel, lumen-menu
 * PowerCountdown); the power key again while the menu is open powers off at once ({@link #powerOffNow}).
 *
 * v6.2b safety (a menu must never act unseen):
 *  - never shown while the wake / sleep curtain is up ({@link #show} refuses; PowerKey lifts the
 *    curtain and waits for a new press), and no tile or row acts while a curtain is up;
 *  - no tile has focus until the enter animation has finished (the tiles are blocked, a neutral
 *    holder takes the window focus); then focus goes to Power off (or the timer, see above);
 *  - OK / ENTER is ignored for {@link #OK_GUARD_MS} after the menu opened and until focus is
 *    placed; an OK press that began in that time (or before the menu) never acts, because a tile
 *    acts only on the UP of a DOWN it received itself. D-pad keys are held back until then too.
 *
 * Sleep timer opens a small centred card (same panel window): 15 min, 30 min, Custom
 * (LEFT / RIGHT change it in 5-minute steps, 15 when held; OK starts) and "Turn off timer" while a
 * timer runs. BACK returns to the tiles. Starting or stopping the timer closes the menu and shows
 * the Notify card.
 *
 * The round tiles, their stage, animations, the 15 s power off and the safety below are the shared ui.RoundTilePanel /
 * ui.RoundTile; this class adds the tiles, the curtain block and the timer card. Framework widgets only (no AndroidX),
 * own line icons (no XGIMI assets). Main thread.
 */
public final class PowerMenu extends RoundTilePanel {
    private static PowerMenu sMenu;

    private View timerCard;
    private RoundTile timerTile, powerOffTile;
    private boolean timerPage;
    /** The app's context of the last show (the power key again powers off through it). */
    private Context shownIn;

    private static final String TAG = "Z9xPowerMenu";
    /** OK / ENTER is ignored this long after the menu opened (RoundTilePanel). */
    static final long OK_GUARD_MS = RoundTilePanel.OK_GUARD_MS;

    public static void show(Context ctx) {
        show(ctx, false);
    }

    /** focusTimer: initial focus on the Sleep timer tile (Lumen Home's "Sleep timer" shortcut). */
    public static void show(Context ctx, boolean focusTimer) {
        if (WakeCurtain.isShowing()) {
            Log.i(TAG, "not shown: a curtain is up");
            return;
        }
        if (sMenu == null) sMenu = new PowerMenu();
        sMenu.focusTimerFirst = focusTimer;
        sMenu.shownIn = ctx.getApplicationContext();
        OverlayHost.get(ctx).show(sMenu);
    }

    /** The menu is on screen (a short press of the power key then powers off, lumen-menu PowerKeyChoice). */
    public static boolean isOpen() {
        return sMenu != null && sMenu.isShowing();
    }

    /** The power key again while the menu is open (stock PowerUI.dispatchAction): power off now; false when not open. */
    public static boolean powerOffNow(String why) {
        PowerMenu m = sMenu;
        if (m == null || !m.isShowing()) return false;
        Log.i(TAG, why + ": power off");
        m.stopCountdown(why);
        m.dismiss();
        Family.powerOff(m.shownIn);
        return true;
    }

    /** One-shot: makeReady focuses the timer tile instead of the first one. */
    private boolean focusTimerFirst;

    @Override protected String tag() { return TAG; }

    /** No tile acts under a curtain. */
    @Override protected String blockedReason() {
        return WakeCurtain.isShowing() ? "a curtain is up" : null;
    }

    /** Power off, as stock; the timer tile while a timer runs or once after Home's "Sleep timer" shortcut. */
    @Override protected View initialTile() {
        View t = focusTimerFirst || timerRunning() ? timerTile : powerOffTile;
        focusTimerFirst = false;
        return t;
    }

    @Override protected RoundTile countdownTile() { return powerOffTile; }

    @Override protected boolean timerRunning() { return SleepTimer.isRunning() && SleepTimer.minutesLeft() > 0; }

    @Override protected void onCountdownEnd() {
        Context c = stage.getContext();
        dismiss();
        Family.powerOff(c);
    }

    /** "15 min", "1 h", "1 h 30 min". */
    static String duration(Context c, int minutes) {
        if (minutes < 60) return c.getString(R.string.power_timer_min, minutes);
        int h = minutes / 60, m = minutes % 60;
        return m == 0 ? c.getString(R.string.power_timer_hours, h) : c.getString(R.string.power_timer_hours_min, h, m);
    }

    @Override protected long autoHideMs() { return timerPage ? 30_000 : super.autoHideMs(); }

    @Override protected View onCreateView(Context ctx) {
        timerPage = false;
        timerCard = null;
        return super.onCreateView(ctx);
    }

    @Override protected void onAddTiles(Context ctx) {
        // the shared item list: Turn off screen where the profile has lamp.off (every gmpf and Play 6 model), the timer
        // card on every one of them
        for (PowerItem item : PowerItem.offered(f -> f == FeatureId.SETTINGS_SLEEP_TIMER
                || MenuController.profile(ctx).has(f))) {
            switch (item) {
                case SCREEN_OFF:
                    addTile(ctx, RoundTile.ICON_SCREEN_OFF, ctx.getString(R.string.lumen_m_lamp_off),
                            guarded(() -> { dismiss(); LampOffHal.get(ctx).run(r -> { }); }));
                    break;
                case SLEEP:
                    addTile(ctx, RoundTile.ICON_SLEEP, ctx.getString(R.string.power_menu_sleep),
                            guarded(() -> Family.powerSleepWithFade(ctx, "power menu sleep")));
                    break;
                case POWER_OFF:
                    powerOffTile = addTile(ctx, RoundTile.ICON_POWER, ctx.getString(R.string.power_menu_off),
                            guarded(() -> Family.powerOff(ctx)));
                    break;
                case RESTART:
                    addTile(ctx, RoundTile.ICON_RESTART, ctx.getString(R.string.power_menu_restart),
                            guarded(() -> Family.powerRestart(ctx)));
                    break;
                default:
                    timerTile = addTile(ctx, RoundTile.ICON_TIMER, ctx.getString(R.string.power_menu_timer),
                            guarded(() -> showTimer(ctx)));
            }
        }
        updateTimerTile(ctx);
    }

    @Override protected boolean onBack() {
        if (timerPage) {
            showMain(stage.getContext());
            return true;
        }
        return false;
    }

    @Override protected void onDismissed() {
        timerPage = false;
        super.onDismissed();
    }

    // ------------------------------------------------------------------ pages

    private void updateTimerTile(Context c) {
        int left = SleepTimer.minutesLeft();
        timerTile.value = left > 0 ? c.getString(R.string.power_timer_left, duration(c, left)) : null;
        timerTile.invalidate();
    }

    private void showMain(Context c) {
        timerPage = false;
        resetAutoHide();
        final View card = timerCard;
        timerCard = null;
        if (card != null) {
            if (card instanceof ViewGroup) {
                ((ViewGroup) card).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            }
            card.animate().cancel();
            card.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(140)
                    .withEndAction(() -> stage.removeView(card)).start();
        }
        updateTimerTile(c);
        tiles.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        tiles.setVisibility(View.VISIBLE);
        tiles.setAlpha(0f);
        tiles.setScaleX(1.04f);
        tiles.setScaleY(1.04f);
        tiles.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(60).setDuration(240)
                .setInterpolator(Theme.panelInterpolator()).start();
        timerTile.requestFocus();
    }

    private void showTimer(Context c) {
        timerPage = true;
        resetAutoHide();
        timerCard = buildTimerCard(c);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Theme.px(c, 640),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        stage.addView(timerCard, lp);
        tiles.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        tiles.animate().cancel();
        tiles.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setStartDelay(0).setDuration(140)
                .withEndAction(() -> { if (timerPage) tiles.setVisibility(View.INVISIBLE); }).start();
        timerCard.setAlpha(0f);
        timerCard.setScaleX(1.04f);
        timerCard.setScaleY(1.04f);
        timerCard.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(60).setDuration(240)
                .setInterpolator(Theme.panelInterpolator()).start();
        View first = ((ViewGroup) timerCard).getChildAt(2);
        if (first != null) first.requestFocus();
    }

    private View buildTimerCard(Context c) {
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
        title.setText(R.string.power_menu_timer);
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

        int[] presets = {15, 30};                 // v6.2 requirements 2: 15, 30 or custom
        for (int m : presets) {
            final int min = m;
            addRow(card, new NavRow(c, duration(c, m), guarded(() -> start(c, min))).setChevron(false));
        }
        addRow(card, new CustomRow(c));
        if (running) {
            addRow(card, new NavRow(c, c.getString(R.string.power_timer_cancel), guarded(() -> {
                SleepTimer.cancel(c, true);
                dismiss();
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

    private void start(Context c, int min) {
        SleepTimer.start(c, min);
        dismiss();
    }

    // ------------------------------------------------------------------ custom row

    /** "Custom    ‹ 45 min ›": LEFT / RIGHT change the time, OK starts it. */
    private final class CustomRow extends Row {
        private int minutes;
        private CharSequence text;

        CustomRow(Context c) {
            super(c, c.getString(R.string.power_timer_custom));
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
            resetAutoHide();
            return true;                                                     // never leaves the row sideways
        }

        @Override protected boolean onActivate() {
            if (!canAct()) return true;
            SleepTimer.saveCustomMinutes(getContext(), minutes);
            start(getContext(), minutes);
            return true;
        }
    }
}
