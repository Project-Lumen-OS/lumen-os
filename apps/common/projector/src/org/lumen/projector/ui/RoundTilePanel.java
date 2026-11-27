// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.lumen.menu.PowerCountdown;

/**
 * The round power menu of every projector (Lumen OS 1.0, the stock-like look the owner keeps): {@link RoundTile}s in
 * one row, centred over a dimmed picture, full screen in the host that shows it (OverlayHost for the gmpf and Play 6
 * power menu, the menu's own PanelHost for the X50 family, so a device's gate holds for it). A subclass adds its tiles
 * and their actions; the stage, the animations and the safety live here once.
 *
 * Safety (a menu must never act unseen): no tile has focus until the enter animation has finished (the tiles are
 * blocked, a neutral holder takes the window focus), then focus goes to {@link #initialTile} or the first tile;
 * OK / ENTER is ignored for {@link #OK_GUARD_MS} after the menu opened and until focus is placed, D-pad keys are held
 * back until then too (a tile acts only on the UP of a DOWN it received itself); {@link #blockedReason} stops every
 * tile while it answers a reason.
 *
 * The 15 s power off of the stock menu (lumen-menu {@link PowerCountdown}): once the menu settled with the focus on
 * {@link #countdownTile} and no sleep timer running, that tile shows the seconds left under its label and
 * {@link #onCountdownEnd} runs one second after 0 if it still has the focus; only the focus leaving that tile stops it
 * for this show (as stock: UP, DOWN and the other keys that keep the focus there do not). The menu
 * closes {@link #AUTO_HIDE_MS} after the last key, as stock, so the count always ends first.
 *
 * Animations: the scrim fades in (200 ms), the tiles rise and settle with a 40 ms stagger (Theme.panelInterpolator,
 * 320 ms), the focused tile's disc grows by 8 % (160 ms). Main thread.
 */
public abstract class RoundTilePanel extends Panel {
    /** OK / ENTER is ignored this long after the menu opened. */
    public static final long OK_GUARD_MS = 400;
    /** Stock PowerDialog MSG_CANCEL_POWER_DIALOG: the menu closes 20 s after the last focus change or key. */
    public static final long AUTO_HIDE_MS = 20_000;
    /** Enter animation length (scrim 200 ms; tiles 60 + 40 * 2 + 320 ms) plus margin: focus fallback. */
    private static final long READY_FALLBACK_MS = 700;

    protected FrameLayout stage;
    protected LinearLayout tiles;
    /** uptimeMillis when the menu was built; OK is ignored for OK_GUARD_MS after it. */
    private long openedAt;
    /** Initial focus placed (enter animation finished). */
    private boolean focusReady;
    private View focusHolder;
    private final Runnable readyFallback = this::makeReady;
    private final PowerCountdown countdown = new PowerCountdown();
    /** The tile the running count shows on, null when none runs. */
    private RoundTile counting;
    private final Runnable countdownTick = this::countdownTick;

    /** The log tag of this menu. */
    protected abstract String tag();

    /** Adds this show's tiles in their order ({@link #addTile}). */
    protected abstract void onAddTiles(Context ctx);

    /** The tile that takes focus once the menu settled; null = the first one. */
    protected View initialTile() { return null; }

    /** Why no tile may act now (e.g. "a curtain is up"), null when they may. */
    protected String blockedReason() { return null; }

    /** The tile the 15 s power off runs on (Power off), null for none. */
    protected RoundTile countdownTile() { return null; }

    /** A sleep timer runs: no count (stock focuses the timer tile then). */
    protected boolean timerRunning() { return false; }

    /** The count ended with {@link #countdownTile} still focused: power off. */
    protected void onCountdownEnd() {}

    protected final RoundTile addTile(Context c, int icon, String label, Runnable onClick) {
        RoundTile t = new RoundTile(c, icon, label, onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.px(c, 230), Theme.px(c, 270));
        int m = Theme.px(c, 22);
        lp.setMargins(m, 0, m, 0);
        tiles.addView(t, lp);
        return t;
    }

    /** No action may run while blocked or before the menu is settled. Main thread. */
    protected final boolean canAct() {
        String why = blockedReason();
        if (why != null) {
            Log.i(tag(), "OK ignored: " + why);
            return false;
        }
        if (!focusReady || SystemClock.uptimeMillis() - openedAt < OK_GUARD_MS) {
            Log.i(tag(), "OK ignored: menu not settled yet");
            return false;
        }
        return true;
    }

    protected final Runnable guarded(Runnable r) {
        return () -> { if (canAct()) r.run(); };
    }

    private static boolean isOkKey(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private static boolean isDpadKey(int code) {
        return code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    @Override protected boolean onKeyEvent(KeyEvent ev) {
        int code = ev.getKeyCode();
        boolean settled = focusReady && SystemClock.uptimeMillis() - openedAt >= OK_GUARD_MS
                && blockedReason() == null;
        if (settled) return false;
        if (isOkKey(code)) {
            if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) {
                Log.i(tag(), "OK ignored: menu not settled yet");
            }
            return true;                    // the DOWN never reaches a tile, so its UP cannot act
        }
        return !focusReady && isDpadKey(code);
    }

    /** End of the enter animation (or the fallback): unblock the tiles, focus the initial one. */
    private void makeReady() {
        if (focusReady || tiles == null || stage == null || !isShowing()) return;
        stage.removeCallbacks(readyFallback);
        focusReady = true;
        tiles.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        View first = initialTile();
        if (first == null && tiles.getChildCount() > 0) first = tiles.getChildAt(0);
        if (first != null) first.requestFocus();
        if (focusHolder != null) focusHolder.setFocusable(false);
        RoundTile power = countdownTile();
        if (power != null && countdown.start(first == power, timerRunning())) {
            counting = power;
            power.setOnFocusChangeListener((v, has) -> {
                if (countdown.focusChanged(has)) stopCountdown("the focus left Power off");
            });
            showCount();
            stage.postDelayed(countdownTick, PowerCountdown.TICK_MS);
            Log.i(tag(), "power off in " + countdown.left() + " s unless the focus leaves Power off");
        }
    }

    /** One second of the 15 s power off. */
    private void countdownTick() {
        if (counting == null || !isShowing()) return;
        if (!countdown.tick()) {
            showCount();
            stage.postDelayed(countdownTick, PowerCountdown.TICK_MS);
            return;
        }
        RoundTile t = counting;
        counting = null;
        t.value = null;
        t.invalidate();
        String why = !t.hasFocus() ? "the focus left Power off" : blockedReason();
        if (why != null) {
            Log.i(tag(), "countdown end ignored: " + why);
            return;
        }
        Log.i(tag(), "countdown end: power off");
        onCountdownEnd();
    }

    private void showCount() {
        counting.value = counting.getContext().getString(org.lumen.projector.R.string.lumen_1_d_s, countdown.left());
        counting.invalidate();
    }

    /** No power off in this show (the focus left Power off, the menu closing, the power key again). */
    public final void stopCountdown(String why) {
        if (stage != null) stage.removeCallbacks(countdownTick);
        countdown.stop();
        if (counting == null) return;
        counting.value = null;
        counting.invalidate();
        counting = null;
        Log.i(tag(), "countdown stopped: " + why);
    }

    @Override protected long autoHideMs() { return AUTO_HIDE_MS; }

    @Override protected WindowManager.LayoutParams onCreateLayoutParams(Context ctx) {
        return OverlayHost.params(true, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
    }

    @Override protected View onCreateView(Context ctx) {
        focusReady = false;
        openedAt = SystemClock.uptimeMillis();
        stage = new FrameLayout(ctx);
        GradientDrawable scrim = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x8C000000, 0xB3000000, 0xD9000000});
        stage.setBackground(scrim);
        stage.setClipChildren(false);
        tiles = new LinearLayout(ctx);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        tiles.setClipChildren(false);
        tiles.setClipToPadding(false);
        onAddTiles(ctx);
        stage.addView(tiles, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        // Until the enter animation ends no tile may hold focus: the tiles are blocked and this
        // invisible holder takes the window's initial focus (non-touch mode auto-focuses the
        // first focusable view as soon as the window gets focus).
        tiles.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        focusHolder = new View(ctx);
        focusHolder.setFocusable(true);
        focusHolder.setDefaultFocusHighlightEnabled(false);
        stage.addView(focusHolder, 0, new FrameLayout.LayoutParams(1, 1));
        return stage;
    }

    @Override protected void onShown(View root) {
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(200).start();
        float dy = Theme.pxf(root.getContext(), 36);
        int n = tiles.getChildCount();
        if (focusHolder != null) focusHolder.requestFocus();
        for (int i = 0; i < n; i++) {
            View t = tiles.getChildAt(i);
            t.setAlpha(0f);
            t.setTranslationY(dy);
            android.view.ViewPropertyAnimator a = t.animate().alpha(1f).translationY(0f)
                    .setStartDelay(60 + 40L * i).setDuration(320).setInterpolator(Theme.panelInterpolator());
            if (i == n - 1) a.withEndAction(this::makeReady);      // fully visible: focus the initial tile
            a.start();
        }
        stage.removeCallbacks(readyFallback);
        stage.postDelayed(readyFallback, n == 0 ? 0 : READY_FALLBACK_MS);
        Log.i(tag(), "round power menu shown: " + n + " tiles");
    }

    @Override protected void animateOut(View root, Runnable end) {
        root.animate().cancel();
        root.animate().alpha(0f).setDuration(180).withEndAction(end).start();
    }

    @Override protected void onDismissed() {
        focusReady = false;
        stopCountdown("menu closed");
        if (stage != null) stage.removeCallbacks(readyFallback);
    }
}
