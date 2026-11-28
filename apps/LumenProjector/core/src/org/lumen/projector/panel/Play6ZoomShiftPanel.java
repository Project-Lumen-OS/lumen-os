// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.menu.OkPress;
import org.lumen.menu.EdgeFrame;
import org.lumen.projector.Hal;
import org.lumen.projector.R;
import org.lumen.projector.Ui;
import org.lumen.projector.ak.AkOverlay;
import org.lumen.projector.ui.Notify;
import org.lumen.projector.ui.OverlayHost;
import org.lumen.projector.ui.Panel;
import org.lumen.projector.ui.Theme;

/**
 * Play 6 "Zoom and shift" (stock Settings > Picture correction > zoom_and_displacement, the
 * ZoomDisplacementWind of the non-GMPF2 platform, stock analysis B 4.1): the picture is made smaller and moved inside
 * the projected area by the keystone engine (digital only: G0058 zoom.ini ZoomControlEn = 0, no zoom motor), so
 * it can be fitted to the screen frame without moving the projector. Ideas from Z9xProjector
 * panel/ZoomShiftPanel.java (Lumen OS 1.0.4); the Play 6 calls and numbers are the stock Play 6 ones.
 *
 * A full-screen grid with a white edge shows the picture's edge.
 *   Size mode (first): RIGHT / UP bigger, LEFT / DOWN smaller (and VOLUME UP / DOWN, stock's keys, if they ever
 *   reach the window; Android TV keeps them). OK switches to Position: the arrows move the picture. BACK: done.
 * Sequence (Play6AdjustSession for the bracket): IGmpf2 125(1) -> 586 / 573(2,false) -> 121 zoomStart(0) -> 117
 * and 116 read, the raw replies logged. The digital range is used only when it parses as expected
 * (min < max, at most 200 steps, the 116 digital step inside it); otherwise the card offers shift only, and
 * with no usable reply at all it closes with "not available" and the quick panel row is hidden for this process
 * ({@link #isUnsupported}). A size step: a fresh 116 read, the neighbour (+1 smaller, -1 bigger, stock
 * setScreenResolutions 4 / 0) inside the range, 118, 116 again; the optical step must not move (there is no
 * optical zoom on the G0058; a moved value closes the card). A shift step: 141(dir, true), and 141(dir, false)
 * only when the check answered 0 (stock onKey). Close: 120 zoomFinish(0), 573(2,true), 125(0); no 158.
 * Lumen OS 1.0.6, stock parity (2026-10-24: "step 15, then it jumped to 3 and stuck there"; stock
 * release_838_newsettings ZoomDisplacementWind, non-optical, non-stepless path, re-read from the v1.7.116 dump):
 *  - steps as stock: one step bigger is 118 to the 116 step - 1 (setScreenResolutions 0), smaller +1 (4); a move
 *    is 141 (1 up, 2 down, 3 left, 4 right) after its check. Stock's keys (arrows always move, VOLUME sizes) need
 *    the vendor's key intercept service, so the keys stay as in 1.0.5 (see the key handler).
 *  - after a move that the vendor took, stock waits 200 ms and runs initZoom(false) again: 121 zoomStart(0) once more
 *    and a fresh 117 / 116. The vendor re-bases the zoom on the moved picture (its own log: Digital 0, PreStep 0)
 *    and the range shrinks near an edge; without the re-read the card kept -19..1 and the step jumped and stuck.
 *  - a size step outside the range is not sent, and a step the vendor refuses (118 answers -1) says so.
 *  Not ported, on purpose: vendor.mstar.osd.manualkeystone (no reader in the G0058 vendor), 158
 *  generateLogoByTrapezoid, KeyStoneRepository setImageShift / setZoomMode (analytics only).
 * Log tag P6ZoomShift.
 */
public final class Play6ZoomShiftPanel extends Panel {
    static final String TAG = "P6ZoomShift";

    private static final long MIN_STEP_GAP_MS = 150;
    private static final long AUTO_HIDE_MS = 60_000;
    private static final long ENTER_WATCHDOG_MS = 6_000;
    private static final int WAIT = 0, READY = 1, FAILED = 2;

    private static volatile Play6ZoomShiftPanel sOpen;
    /** The 117 / 116 replies did not parse in this process: the row stays hidden (rule, no disabled rows). */
    private static volatile boolean sUnsupported;

    private final Context app;
    private final Runnable onBackExit;
    private volatile int state = WAIT;
    private volatile boolean inFlight, closed, backExit, shiftMode, active;
    private volatile long lastStepAt;
    /** Digital range and position (HAL thread writes); optical position at open (must not change). */
    private volatile int lo, hi, cur, optical;
    /**
     * Steps smaller than the full picture: 116 - min of 117 at open and after each move (fresh session), then counted
     * from the steps the vendor took.
     */
    private volatile int level;
    /** Positions shown ("N of M"): the 117 span at open. */
    private volatile int span;
    private volatile boolean digital;
    private TextView mode, value, hint, status;
    private EdgeFrameView frame;
    private final OkPress okPress = new OkPress();
    /** Stock's side menu: one focused Reset button (MENU shows it). */
    private LinearLayout menu;
    /** Bumped by every move the vendor took: the 200 ms re-read (stock initZoom) runs only for the last one. */
    private volatile int moveGen;
    /** This image hands the volume keys to the card on request (lineage patch 0004, ro.lumen.volkeys=1): stock keys. */
    private final boolean stockKeys = android.os.SystemProperties.getBoolean("ro.lumen.volkeys", false);
    private static final String PROP_VOLKEYS = "sys.lumen.volkeys_until";
    private static final long VOLKEYS_LEASE_MS = 60_000, VOLKEYS_RENEW_MS = 20_000;
    private final Runnable renewVolumeKeys = new Runnable() {
        @Override public void run() {
            if (closed) return;
            leaseVolumeKeys(true);
            Ui.main().postDelayed(this, VOLKEYS_RENEW_MS);
        }
    };
    private static final long REINIT_MS = 200;
    /** Stock waits 500 ms after the reset before initZoom. */
    private static final long RESET_SETTLE_MS = 500;

    private Play6ZoomShiftPanel(Context app, Runnable onBackExit) {
        this.app = app;
        this.onBackExit = onBackExit;
    }

    static boolean isOpen() { return sOpen != null; }


    /** The zoom replies were found unusable in this process (row hidden). */
    static boolean isUnsupported() { return sUnsupported; }

    static void markUnsupported(String why) {
        if (!sUnsupported) Log.w(TAG, "zoom and shift hidden for this process: " + why);
        sUnsupported = true;
    }

    /** Main thread. {@code onBackExit} (may be null) runs after a BACK exit, e.g. to show the quick panel again. */
    static void open(Context ctx, Runnable onBackExit) {
        final Context app = ctx.getApplicationContext();
        if (!Hal.isReady() || !Hal.hasGmpf2()) { refuse(app, "HAL not ready or IGmpf2 guard closed", R.string.zs_unavailable); return; }
        if (Hal.isKeystoneRunning() || AkOverlay.isActive()) { refuse(app, "auto keystone running", R.string.kst_reason_ak_busy); return; }
        if (Play6AdjustSession.anyOpen()) { Log.i(TAG, "another adjustment card is open"); return; }
        final Play6ZoomShiftPanel p = new Play6ZoomShiftPanel(app, onBackExit);
        if (!OverlayHost.get(app).show(p)) { refuse(app, "overlay window could not be added", R.string.zs_unavailable); return; }
        sOpen = p;
        p.renewVolumeKeys.run();                                     // stock registerInterceptKey(24 / 25)
        if (!Hal.run((g, g2) -> p.enter(Play6PanelIo.zoom(app, g, g2)))) {
            p.fail("not queued");
            return;
        }
        Ui.main().postDelayed(() -> {
            if (sOpen == p && p.state == WAIT && p.isShowing()) p.fail("enter did not run within " + ENTER_WATCHDOG_MS + " ms");
        }, ENTER_WATCHDOG_MS);
    }

    private static void refuse(Context app, String log, int res) {
        Log.w(TAG, "refused: " + log);
        Notify.show(app, res);
    }

    // =================================================================== HAL thread
    private void enter(ZoomIo io) {
        if (closed) { Log.i(TAG, "enter skipped: the card is already closed"); return; }
        try {
            if (!io.pushPullOn("zoom enter")) {
                ui(() -> fail("IGmpf2 125(1) not delivered"));
                return;
            }
            active = true;
            io.pauseRealtime("zoom enter");
            io.zoomStart("zoom enter");
            int[] range = io.zoomStepRange();                        // 117
            int[] step = io.zoomStep();                              // 116
            Log.i(TAG, "117 raw: ret " + range[0] + " optical " + range[1] + ".." + range[2] + " digital " + range[3]
                    + ".." + range[4] + "; 116 raw: ret " + step[0] + " optical " + step[1] + " digital " + step[2]);
            optical = step[1];
            int a = range[3], b = range[4];
            lo = Math.min(a, b);
            hi = Math.max(a, b);
            cur = step[2];
            level = cur - lo;
            span = hi - lo + 1;
            digital = hi > lo && hi - lo <= ZoomIo.STEP_MAX && lo >= ZoomIo.STEP_MIN
                    && hi <= ZoomIo.STEP_MAX && cur >= lo && cur <= hi;
            if (!digital) {
                Log.w(TAG, "digital zoom range not usable (" + lo + ".." + hi + ", now " + cur + "): shift only");
                shiftMode = true;
            }
            state = READY;
            ui(this::render);
            if (closed) exitNow(io, "closed during enter");
        } catch (Throwable t) {
            Log.w(TAG, "enter failed: " + t);
            if (io.replyRefused(t)) markUnsupported("117 / 116 reply: " + t.getMessage());
            exitNow(io, "enter failed");
            ui(() -> fail("enter"));
        }
    }

    private void exitNow(ZoomIo io, String who) {
        if (!active) return;
        active = false;
        if (!io.close(who)) Log.w(TAG, who + ": close incomplete, left to the connect hook");
    }

    private void sizeStep(ZoomIo io, boolean smaller) {
        try {
            if (state != READY || closed || !digital) return;
            int[] now = io.zoomStep();                               // fresh 116
            if (now[1] != optical) throw new IllegalStateException("optical step moved: " + optical + " -> " + now[1]);
            int target = smaller ? now[2] + 1 : now[2] - 1;
            // stock setScreenResolutions(0 / 4): 118 to the fresh 116 -1 / +1 with no range check of its own; the
            // vendor decides. The 117 range is not a limit: after a move the vendor re-bases (116 0, 117 0..N) and
            // still takes a step bigger (2026-10-24: "20 of 35, moved, then 35 of 35 and no bigger").
            if (target < ZoomIo.STEP_MIN || target > ZoomIo.STEP_MAX) {
                ui(() -> say(smaller ? R.string.zoom_limit_small : R.string.zoom_limit_big));
                return;
            }
            int r = io.setDigitalZoomStep(target);                   // 118
            int[] after = io.zoomStep();                             // 116 read-back
            cur = after[2];
            if (after[1] != optical) throw new IllegalStateException("optical step moved: " + optical + " -> " + after[1]);
            final boolean took = r == 0 && after[2] == target;
            if (took) level += smaller ? 1 : -1;
            Log.i(TAG, "118(" + target + ") -> " + r + ", now digital " + after[2] + " optical " + after[1]
                    + (took ? ", size level " + level : ", refused"));
            ui(() -> { render(); if (!took) say(smaller ? R.string.zoom_limit_small : R.string.zoom_limit_big); });
        } catch (Throwable t) {
            Log.w(TAG, "size step failed: " + t);
            ui(() -> fail("size step"));
        } finally {
            lastStepAt = SystemClock.uptimeMillis();
            inFlight = false;
        }
    }

    private void shiftStep(ZoomIo io, ShiftMove dir) {
        try {
            if (state != READY || closed) return;
            int check = io.shift(dir, true);                         // 141(dir, true)
            int r = check == 0 ? io.shift(dir, false) : check;       // 141(dir, false)
            Log.i(TAG, "141(" + dir + ") check " + check + " -> " + r);
            ui(() -> { if (check != 0) say(R.string.zs_edge); else if (status != null) status.setText(""); });
            if (check == 0 && r != -1) {
                // stock: zoomJob = launchGlobalIO(200 ms) { initZoom(false) } after every move it made
                final int gen = ++moveGen;
                Ui.main().postDelayed(() -> {
                    if (gen != moveGen || closed) return;
                    Hal.run((gm, g2) -> reinit(Play6PanelIo.zoom(app, gm, g2), gen));
                }, REINIT_MS);
            }
        } catch (Throwable t) {
            Log.w(TAG, "shift step failed: " + t);
            ui(() -> say(R.string.zs_refused));
        } finally {
            lastStepAt = SystemClock.uptimeMillis();
            inFlight = false;
        }
    }

    /**
     * HAL thread, 200 ms after the last move: a fresh zoom session (120 then 121, Play6AdjustSession.zoomRestart) and
     * a fresh 117 / 116, so the size level comes from the vendor again. Stock runs initZoom(false) here (121 alone,
     * which the vendor refuses inside a session); with it the range kept its start values while 116 went back to 0.
     */
    private void reinit(ZoomIo io, int gen) {
        if (gen != moveGen || closed || state != READY || !active) return;
        try {
            io.zoomRestart("zoom after move");
            int[] range = io.zoomStepRange();                        // 117
            int[] step = io.zoomStep();                              // 116
            int a = range[3], b = range[4];
            int nlo = Math.min(a, b), nhi = Math.max(a, b);
            boolean ok = nhi > nlo && nhi - nlo <= ZoomIo.STEP_MAX && nlo >= ZoomIo.STEP_MIN
                    && nhi <= ZoomIo.STEP_MAX && step[2] >= nlo && step[2] <= nhi;
            Log.i(TAG, "after move: 117 digital " + a + ".." + b + ", 116 digital " + step[2] + (ok ? "" : " (not usable)"));
            if (ok) {
                lo = nlo;
                hi = nhi;
                cur = step[2];
                level = cur - lo;                                    // a move keeps the size: logged if it differs
                Log.i(TAG, "after move: size level " + level);
            }
            ui(this::render);
        } catch (Throwable t) {
            Log.w(TAG, "re-read after move: " + t);
        }
    }

    /**
     * HAL thread. The side menu's Reset (2026-10-24: back to the full picture in the centre, as stock). Stock
     * ZoomDisplacementWind.onClick(reset_bt_id) runs KeyStoneManager.resetKeyStone (setIChipReset, then
     * setLowConfigureKeyStoneValue(50); no stepless zoom, optical zoom or tilt-shift on the G0058) and 500 ms later
     * initZoom. Here the Keystone page's reset (Play6Gmpf.resetKeystone: 207, 130, 129 when it answers 6; 181 stays
     * banned, Note A), 500 ms, then a fresh zoom session for the new range.
     */
    private void resetAll(ZoomIo io) {
        try {
            if (state != READY || closed || !active) return;
            Log.i(TAG, "reset: 207(0), 130 -> " + io.resetKeystone());
            SystemClock.sleep(RESET_SETTLE_MS);
            io.zoomRestart("zoom reset");
            int[] range = io.zoomStepRange();                        // 117
            int[] step = io.zoomStep();                              // 116
            int a = range[3], b = range[4];
            int nlo = Math.min(a, b), nhi = Math.max(a, b);
            boolean ok = nhi > nlo && nhi - nlo <= ZoomIo.STEP_MAX && nlo >= ZoomIo.STEP_MIN
                    && nhi <= ZoomIo.STEP_MAX && step[2] >= nlo && step[2] <= nhi;
            Log.i(TAG, "after reset: 117 digital " + a + ".." + b + ", 116 digital " + step[2] + (ok ? "" : " (not usable)"));
            if (ok) {
                lo = nlo;
                hi = nhi;
                cur = step[2];
                level = cur - lo;
                span = hi - lo + 1;
                digital = true;
            }
            ui(() -> { render(); say(R.string.zs_reset_done); });
        } catch (Throwable t) {
            Log.w(TAG, "reset failed: " + t);
            ui(() -> say(R.string.zs_refused));
        } finally {
            lastStepAt = SystemClock.uptimeMillis();
            inFlight = false;
        }
    }

    /** Asks PhoneWindowManager for the volume keys until now + 60 s ({@code on}), or gives them back. */
    private void leaseVolumeKeys(boolean on) {
        if (!stockKeys) return;
        try {
            android.os.SystemProperties.set(PROP_VOLKEYS,
                    on ? Long.toString(SystemClock.uptimeMillis() + VOLKEYS_LEASE_MS) : "0");
        } catch (Throwable t) {
            Log.w(TAG, "volume keys " + (on ? "lease" : "release") + ": " + t);
        }
    }

    // =================================================================== keys and window (main thread)
    @Override
    protected long autoHideMs() { return AUTO_HIDE_MS; }

    /**
     * The remote's gear key while the card is open works as stock's MENU (2026-10-24: "menu throws me into the
     * quick panel instead of the reset"): the shared tool rule (org.lumen.menu.KeyTools) hands a short press to
     * onKeyEvent as KEYCODE_MENU.
     */
    @Override
    protected boolean takesMenuKey() { return true; }

    @Override
    protected boolean onKeyEvent(KeyEvent ev) {
        int code = ev.getKeyCode();
        // stock ZoomDisplacementWind: MENU (key up) shows / hides the side menu (KeyStoneLeftMenu, on the G0058 only its
        // Reset button); BACK hides it first; its Reset runs the reset and hides it.
        if (code == KeyEvent.KEYCODE_MENU) {
            if (ev.getAction() == KeyEvent.ACTION_UP && state == READY) showMenu(menu.getVisibility() != View.VISIBLE);
            return true;
        }
        if (menu != null && menu.getVisibility() == View.VISIBLE) {
            boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
            if (ok && ev.getAction() == KeyEvent.ACTION_UP && state == READY && !inFlight) {
                showMenu(false);
                moveGen++;                                           // no re-read of an older move after the reset
                inFlight = true;
                if (!Hal.run((g, g2) -> resetAll(Play6PanelIo.zoom(app, g, g2)))) inFlight = false;
            }
            return code != KeyEvent.KEYCODE_BACK && code != KeyEvent.KEYCODE_ESCAPE;
        }
        if (OkPress.isOk(code)) {
            // OK: the test chart on / off; hold OK: Size / Position (not with the stock keys: the arrows always move)
            int press = okPress.feed(ev.getAction(), ev.getRepeatCount(), ev.getDownTime(), ev.getEventTime(), ev.isCanceled());
            if (press == OkPress.SHORT && frame != null) frame.toggleChart();
            else if (press == OkPress.HOLD && !stockKeys && state == READY && digital) {
                shiftMode = !shiftMode;
                render();
            }
            return true;
        }
        // Stock's keys are VOLUME UP / DOWN for the size, but Android TV's window manager never hands the volume keys
        // to an app (PhoneWindowManager, TV routing; stock had the vendor's IGimiCommon.registerInterceptKey). They still
        // work here if they ever arrive; the arrows do the size in Size mode, hold OK switches Size / Position.
        boolean up = code == KeyEvent.KEYCODE_DPAD_UP, down = code == KeyEvent.KEYCODE_DPAD_DOWN;
        boolean left = code == KeyEvent.KEYCODE_DPAD_LEFT || code == 149, right = code == KeyEvent.KEYCODE_DPAD_RIGHT || code == 153;
        boolean arrowsMove = stockKeys || shiftMode;                 // stock: the arrows always move
        boolean bigger = code == KeyEvent.KEYCODE_VOLUME_UP || code == 150 || !arrowsMove && (up || right);
        boolean smaller = code == KeyEvent.KEYCODE_VOLUME_DOWN || code == 148 || !arrowsMove && (down || left);
        ShiftMove dir = !arrowsMove ? null : up ? ShiftMove.UP : down ? ShiftMove.DOWN
                : left ? ShiftMove.LEFT : right ? ShiftMove.RIGHT : null;
        if (ev.getAction() == KeyEvent.ACTION_DOWN) leaseVolumeKeys(true);
        if (!bigger && !smaller && dir == null) return up || down || left || right;
        if (ev.getAction() != KeyEvent.ACTION_DOWN || state != READY || inFlight
                || SystemClock.uptimeMillis() - lastStepAt < MIN_STEP_GAP_MS) return true;
        boolean queued;
        if (bigger || smaller) {
            if (!digital) return true;
            final boolean sm = smaller;
            inFlight = true;
            queued = Hal.run((g, g2) -> sizeStep(Play6PanelIo.zoom(app, g, g2), sm));
        } else {
            final ShiftMove d = dir;
            inFlight = true;
            queued = Hal.run((g, g2) -> shiftStep(Play6PanelIo.zoom(app, g, g2), d));
        }
        if (!queued) inFlight = false;
        return true;
    }

    @Override
    protected boolean onBack() {
        if (menu != null && menu.getVisibility() == View.VISIBLE) { showMenu(false); return true; }
        backExit = true;
        return false;
    }

    private void showMenu(boolean show) {
        if (menu == null) return;
        menu.setVisibility(show ? View.VISIBLE : View.GONE);
        Log.i(TAG, "menu " + (show ? "shown" : "hidden"));
    }

    @Override
    protected void onDismissed() {
        closed = true;
        Ui.main().removeCallbacks(renewVolumeKeys);
        leaseVolumeKeys(false);                                      // stock unregisterInterceptKey(25 / 24)
        if (sOpen == this) sOpen = null;
        if (!Hal.run((g, g2) -> exitNow(Play6PanelIo.zoom(app, g, g2), "zoom close"))) Log.w(TAG, "close not queued: left to the connect hook");
        if (backExit && onBackExit != null) Ui.main().post(onBackExit);
    }

    private void fail(String why) {
        Log.w(TAG, "unavailable: " + why);
        state = FAILED;
        Notify.show(app, R.string.zs_unavailable);
        dismiss();
    }

    @Override
    protected WindowManager.LayoutParams onCreateLayoutParams(Context c) {
        return EdgeGrid.fullscreen(c, TAG);
    }

    @Override
    protected View onCreateView(Context c) {
        FrameLayout root = new FrameLayout(c);
        frame = new EdgeFrameView(c, EdgeFrame.FRAME).withChart();
        root.addView(frame, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout card = EdgeGrid.card(c);
        card.addView(EdgeGrid.text(c, c.getString(R.string.zs_title), 40, Theme.TEXT));
        mode = EdgeGrid.text(c, "", 34, Theme.TEXT);
        card.addView(mode);
        value = EdgeGrid.text(c, c.getString(R.string.zoom_wait), 52, Theme.TEXT);
        card.addView(value);
        hint = EdgeGrid.text(c, "", 28, Theme.TEXT_DIM);
        card.addView(hint);
        card.addView(EdgeGrid.text(c, c.getString(R.string.lumen_focus_chart_hint), 28, Theme.TEXT_DIM));
        status = EdgeGrid.text(c, "", 28, Theme.TEXT_DIM);
        card.addView(status);
        root.addView(card, new FrameLayout.LayoutParams(Theme.px(c, 900), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        menu = EdgeGrid.card(c);
        TextView reset = EdgeGrid.text(c, c.getString(R.string.zs_menu_reset), 34, Theme.PANEL_BG);
        GradientDrawable focus = new GradientDrawable();
        focus.setColor(Theme.TEXT);
        focus.setCornerRadius(Theme.px(c, 40));
        reset.setBackground(focus);
        int ph = Theme.px(c, 40), pv = Theme.px(c, 14);
        reset.setPadding(ph, pv, ph, pv);
        menu.addView(reset);
        menu.setVisibility(View.GONE);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL);
        mlp.leftMargin = Theme.px(c, 96);
        root.addView(menu, mlp);
        return root;
    }

    /** Size N of M: M = the full picture, 1 = the smallest (the card's own level, see {@link #level}). */
    private void render() {
        if (value == null || state != READY) return;
        if (!digital) shiftMode = true;
        if (stockKeys) {                                             // stock: size and the keys, no modes
            mode.setVisibility(View.GONE);
            value.setText(app.getString(R.string.zoom_position, shownPosition(), span));
            value.setVisibility(digital ? View.VISIBLE : View.GONE);
            hint.setText(app.getString(digital ? R.string.zs_hint_stock : R.string.zs_hint_shift_only));
            status.setText("");
            return;
        }
        mode.setText(app.getString(shiftMode ? R.string.zs_mode_shift : R.string.zs_mode_size));
        value.setText(app.getString(R.string.zoom_position, shownPosition(), span));
        value.setVisibility(shiftMode ? View.GONE : View.VISIBLE);
        hint.setText(app.getString(shiftMode ? (digital ? R.string.zs_hint_shift : R.string.zs_hint_shift_only)
                : R.string.zs_hint_size));
        status.setText("");
    }

    /** M = the full picture, 1 = the smallest the vendor offered at open (clamped: the vendor may go further). */
    private int shownPosition() {
        return Math.max(1, Math.min(span, span - level));
    }

    private void say(int res) {
        if (status != null) status.setText(app.getString(res));
    }

    private static void ui(Runnable r) { Ui.main().post(r); }
}
