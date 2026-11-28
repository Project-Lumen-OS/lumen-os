// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.power;

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

import org.lumen.projector.ui.OverlayHost;
import org.lumen.projector.ui.Panel;
import org.lumen.projector.ui.Theme;

/**
 * HB only (hb-t5): Lumen Home's "Sleep timer" tile opens the sleep timer card itself, not the power menu.
 *
 * On the Z9X the tile calls PowerMenu.show(ctx, true): the four power tiles (Sleep / Power off / Restart /
 * Sleep timer) with the timer tile focused, one OK more to reach the timer. The the device test of hb-t4
 * (2026-10-29) read that as "the sleep timer entry opens the power menu". PowerMenu and
 * PanelRequestReceiver are shared files (apps/common/projector/FILES) owned by the Z9X, so the HB gets this
 * panel instead, reached through home.HbPanelRequestReceiver. It is PowerMenu's timer card on its own (the one
 * {@link SleepTimerCard}: 15 min, 30 min, Custom, "Turn off timer" while a timer runs). BACK closes it. Starting
 * or stopping the timer closes it and SleepTimer shows its Notify card. Auto-hide 30 s.
 *
 * Safety as PowerMenu: no row acts under a curtain, and OK is ignored for {@link #OK_GUARD_MS} after the
 * panel opened (a press that began before it never acts). Main thread.
 */
public final class HbSleepTimerPanel extends Panel {
    private static final String TAG = "Z9xPowerMenu";
    /** OK / ENTER is ignored this long after the panel opened (PowerMenu.OK_GUARD_MS). */
    static final long OK_GUARD_MS = PowerMenu.OK_GUARD_MS;

    private static HbSleepTimerPanel sPanel;
    private long openedAt;

    public static void show(Context ctx) {
        if (WakeCurtain.isShowing()) {
            Log.i(TAG, "sleep timer not shown: a curtain is up");
            return;
        }
        if (sPanel == null) sPanel = new HbSleepTimerPanel();
        OverlayHost.get(ctx).show(sPanel);
    }

    private boolean canAct() {
        if (WakeCurtain.isShowing()) {
            Log.i(TAG, "OK ignored: a curtain is up");
            return false;
        }
        if (SystemClock.uptimeMillis() - openedAt < OK_GUARD_MS) {
            Log.i(TAG, "OK ignored: sleep timer not settled yet");
            return false;
        }
        return true;
    }

    private Runnable guarded(Runnable r) {
        return () -> { if (canAct()) r.run(); };
    }

    @Override protected long autoHideMs() { return 30_000; }

    @Override protected WindowManager.LayoutParams onCreateLayoutParams(Context ctx) {
        return OverlayHost.params(true, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
    }

    @Override protected boolean onKeyEvent(KeyEvent ev) {
        int code = ev.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        // the DOWN never reaches a row while not settled, so its UP cannot act either
        return ok && SystemClock.uptimeMillis() - openedAt < OK_GUARD_MS;
    }

    @Override protected View onCreateView(Context ctx) {
        openedAt = SystemClock.uptimeMillis();
        FrameLayout stage = new FrameLayout(ctx);
        stage.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x8C000000, 0xB3000000, 0xD9000000}));
        LinearLayout card = SleepTimerCard.build(ctx, cardHost);
        stage.addView(card, new FrameLayout.LayoutParams(Theme.px(ctx, 640),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        View first = card.getChildAt(SleepTimerCard.FIRST_ROW);
        if (first != null) first.requestFocus();
        return stage;
    }

    @Override protected void onShown(View root) {
        root.setAlpha(0f);
        root.setScaleX(1.04f);
        root.setScaleY(1.04f);
        root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240)
                .setInterpolator(Theme.panelInterpolator()).start();
    }

    @Override protected void animateOut(View root, Runnable end) {
        root.animate().cancel();
        root.animate().alpha(0f).setDuration(180).withEndAction(end).start();
    }

    /** The timer card acts through this panel's guards and closes it. */
    private final SleepTimerCard.Host cardHost = new SleepTimerCard.Host() {
        @Override public Runnable guarded(Runnable r) { return HbSleepTimerPanel.this.guarded(r); }

        @Override public boolean canAct() { return HbSleepTimerPanel.this.canAct(); }

        @Override public void resetAutoHide() { HbSleepTimerPanel.this.resetAutoHide(); }

        @Override public void dismiss() { HbSleepTimerPanel.this.dismiss(); }
    };
}
