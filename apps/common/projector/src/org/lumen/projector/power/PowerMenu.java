// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.power;

import android.content.Context;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.lumen.menu.FeatureId;
import org.lumen.menu.PowerItem;
import org.lumen.projector.Family;
import org.lumen.projector.R;
import org.lumen.projector.panel.MenuController;
import org.lumen.projector.panel.PowerTiles;
import org.lumen.projector.ui.OverlayHost;
import org.lumen.projector.ui.RoundTile;
import org.lumen.projector.ui.RoundTilePanel;
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
            RoundTile t = addTile(ctx, PowerTiles.icon(item), ctx.getString(PowerTiles.label(item)), action(ctx, item));
            if (item == PowerItem.POWER_OFF) powerOffTile = t;
            if (item == PowerItem.SLEEP_TIMER) timerTile = t;
        }
        updateTimerTile(ctx);
    }

    /** What each tile does (the tile, its icon and label: PowerTiles, the same in the menu host's power menu). */
    private Runnable action(Context ctx, PowerItem item) {
        switch (item) {
            case SCREEN_OFF:
                return guarded(() -> { dismiss(); LampOffHal.get(ctx).run(r -> { }); });
            case SLEEP:
                return guarded(() -> Family.powerSleepWithFade(ctx, "power menu sleep"));
            case POWER_OFF:
                return guarded(() -> Family.powerOff(ctx));
            case RESTART:
                return guarded(() -> Family.powerRestart(ctx));
            default:
                return guarded(() -> showTimer(ctx));
        }
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
        timerTile.value = left > 0 ? c.getString(R.string.power_timer_left, SleepTimerCard.duration(c, left)) : null;
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
        timerCard = SleepTimerCard.build(c, cardHost);
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
        View first = ((ViewGroup) timerCard).getChildAt(SleepTimerCard.FIRST_ROW);
        if (first != null) first.requestFocus();
    }

    /** The timer card acts through the tiles' guards and closes the menu. */
    private final SleepTimerCard.Host cardHost = new SleepTimerCard.Host() {
        @Override public Runnable guarded(Runnable r) { return PowerMenu.this.guarded(r); }

        @Override public boolean canAct() { return PowerMenu.this.canAct(); }

        @Override public void resetAutoHide() { PowerMenu.this.resetAutoHide(); }

        @Override public void dismiss() { PowerMenu.this.dismiss(); }
    };
}
