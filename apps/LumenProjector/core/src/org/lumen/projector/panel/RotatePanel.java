// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lumen.menu.EdgeFrame;
import org.lumen.projector.Family;
import org.lumen.projector.Hal;
import org.lumen.projector.R;
import org.lumen.projector.Ui;
import org.lumen.projector.ak.AkOverlay;
import org.lumen.projector.ui.Notify;
import org.lumen.projector.ui.OverlayHost;
import org.lumen.projector.ui.Panel;
import org.lumen.projector.ui.Theme;

import java.util.Locale;

/**
 * "Picture rotation", the stock RotateWind (picture_screen_rotation): straightens a picture that sits slightly tilted
 * on the screen (a projector that does not stand level), by the keystone engine, without moving anything. A
 * full-screen grid (EdgeGrid) shows the picture's edge.
 *   RIGHT: 0.5 degrees clockwise, LEFT: counter-clockwise, OK: back to the angle the screen opened with, BACK: done.
 * The HAL steps are the family's (Family.rotateIo, RotateIo): the bracket on open and close, the {min, max, current,
 * start} read and the relative turn. The step is the stock default (user_manu_screen_rotation_step 0.5).
 * How keys become steps is family data (Family.ADJUST_COALESCE): one step at a time between two fresh reads, keys
 * dropped while a step runs (gmpf), or the keys queued and sent as few turns as possible, the target angle shown at
 * once and one read 1.5 s after the last turn (Play 6: its IGmpf2 138 is slow). "Reset keystone" also straightens
 * the picture again.
 */
public final class RotatePanel extends Panel {
    static final String TAG = Family.TAG_PREFIX + "Rotate";

    private static final float STEP = 0.5f;
    private static final long MIN_STEP_GAP_MS = 150;
    private static final long RESYNC_MS = 1_500;
    private static final long AUTO_HIDE_MS = 60_000;

    private static final int WAIT = 0, READY = 1, FAILED = 2;

    private static volatile RotatePanel sOpen;
    /** A rotation read was refused in this process: the row stays hidden. */
    private static volatile boolean sUnsupported;

    private final Context app;
    private final Runnable onBackExit;
    private volatile int state = WAIT;
    private volatile boolean inFlight, closed, backExit, active, moved;
    private volatile long lastStepAt;
    private volatile float min, max, cur, opened;
    /** ADJUST_COALESCE: main thread adds, HAL thread takes (under the lock): degrees still to send, positive = clockwise. */
    private final Object lock = new Object();
    private float pending;
    private boolean undo;
    private int gen;
    private TextView value, status;

    private RotatePanel(Context app, Runnable onBackExit) {
        this.app = app;
        this.onBackExit = onBackExit;
    }

    public static boolean isOpen() { return sOpen != null; }

    public static boolean isUnsupported() { return sUnsupported; }

    public static void markUnsupported(String why) {
        if (!sUnsupported) Log.w(TAG, "picture rotation hidden for this process: " + why);
        sUnsupported = true;
    }

    /** App start: the family's recovery of a card a dead process left open (Family.rotateInstall). */
    public static void install(Context ctx) {
        Family.rotateInstall(ctx.getApplicationContext());
    }

    /** Main thread. {@code onBackExit} (may be null) runs after a BACK exit. */
    public static void open(Context ctx, Runnable onBackExit) {
        Context app = ctx.getApplicationContext();
        if (!Family.adjustHalReady()) { refuse(app, "HAL not ready", R.string.rot_unavailable); return; }
        if (Hal.isKeystoneRunning() || AkOverlay.isActive()) { refuse(app, "auto keystone running", R.string.kst_reason_ak_busy); return; }
        if (Family.adjustOpen()) { Log.i(TAG, "another adjustment card is open"); return; }
        final RotatePanel p = new RotatePanel(app, onBackExit);
        if (!OverlayHost.get(app).show(p)) { refuse(app, "overlay window could not be added", R.string.rot_unavailable); return; }
        sOpen = p;
        if (!Hal.run((g, g2) -> p.enter(Family.rotateIo(app, g, g2)))) {
            p.fail("not queued");
            return;
        }
        if (Family.ADJUST_WATCHDOG_MS > 0) Ui.main().postDelayed(() -> {
            if (sOpen == p && p.state == WAIT && p.isShowing()) p.fail("enter did not run within " + Family.ADJUST_WATCHDOG_MS + " ms");
        }, Family.ADJUST_WATCHDOG_MS);
    }

    private static void refuse(Context app, String log, int res) {
        Log.w(TAG, "refused: " + log);
        Notify.show(app, res);
    }

    // =================================================================== HAL thread

    private void enter(RotateIo io) {
        if (closed) { Log.i(TAG, "enter skipped: the card is already closed"); return; }
        try {
            active = true;
            if (!io.open("rotate enter")) {
                active = false;
                ui(() -> fail("open not delivered"));
                return;
            }
            float[] r = io.rotation();
            min = r[0];
            max = r[1];
            cur = opened = r[2];
            Log.i(TAG, "rotation -> min " + r[0] + " max " + r[1] + " cur " + r[2] + " start " + r[3]);
            io.supported();
            state = READY;
            ui(this::render);
            if (closed) exitNow(io, "closed during enter");
        } catch (Throwable t) {
            Log.w(TAG, "enter failed: " + t);
            if (io.refused(t)) markUnsupported("rotation: " + t.getMessage());
            exitNow(io, "enter failed");
            ui(() -> fail("enter"));
        }
    }

    private void exitNow(RotateIo io, String who) {
        if (!active) return;
        active = false;
        if (!io.close(who, moved)) Log.w(TAG, who + ": close incomplete, left to the connect hook");
    }

    /** One step towards {@code clockwise}, or (undo) back to the opening angle, between two fresh reads. */
    private void step(RotateIo io, Boolean clockwise) {
        try {
            if (state != READY || closed) return;
            float now = io.rotation()[2];
            boolean cw;
            float deg;
            if (clockwise == null) {                           // undo: one relative move back
                float diff = opened - now;
                if (Math.abs(diff) < 0.05f) return;
                cw = diff > 0;
                deg = Math.min(Math.abs(diff), 10f);
            } else {
                cw = clockwise;
                deg = STEP;
                float target = cw ? now + STEP : now - STEP;
                if (target > max + 0.01f || target < min - 0.01f) {
                    cur = now;
                    ui(() -> { render(); say(R.string.rot_limit); });
                    return;
                }
            }
            boolean ok = io.rotate(cw, deg);
            cur = io.rotation()[2];
            moved = true;
            Log.i(TAG, "turn(" + (cw ? 5 : 6) + ", " + deg + ") -> " + ok + ", now " + cur);
            ui(() -> { render(); if (!ok) say(R.string.rot_refused); });
        } catch (Throwable t) {
            Log.w(TAG, "step failed: " + t);
            ui(() -> say(R.string.rot_refused));
        } finally {
            lastStepAt = SystemClock.uptimeMillis();
            inFlight = false;
        }
    }

    /** ADJUST_COALESCE: sends what the keys queued, as few turns as possible, then lets the main thread re-read later. */
    private void drain(RotateIo io) {
        try {
            while (state == READY && !closed) {
                float deg;
                synchronized (lock) {
                    if (undo) { pending = opened - cur; undo = false; }
                    deg = pending;
                    pending = 0;
                }
                if (Math.abs(deg) < 0.05f) break;
                boolean cw = deg > 0;
                float abs = Math.min(Math.abs(deg), 45f);               // the client's rotation step bound
                boolean ok = io.rotate(cw, abs);
                moved = true;
                if (ok) cur = Math.max(min, Math.min(max, cur + (cw ? abs : -abs)));
                Log.i(TAG, "turn(" + (cw ? 5 : 6) + ", " + abs + ") -> " + ok + ", now " + cur);
                ui(() -> { render(); if (!ok) say(R.string.rot_refused); });
            }
        } catch (Throwable t) {
            Log.w(TAG, "step failed: " + t);
            ui(() -> say(R.string.rot_refused));
        } finally {
            inFlight = false;
            final int g = ++gen;
            Ui.main().postDelayed(() -> resync(g), RESYNC_MS);
        }
    }

    /** Main thread, 1.5 s after the last turn: one read, unless more steps came meanwhile. */
    private void resync(int g) {
        if (g != gen || inFlight || closed || state != READY) return;
        Hal.run((gm, g2) -> {
            if (g != gen || inFlight || closed) return;
            try {
                cur = Family.rotateIo(app, gm, g2).rotation()[2];
                ui(this::render);
            } catch (Throwable t) {
                Log.d(TAG, "re-read: " + t);
            }
        });
    }

    // =================================================================== keys and window (main thread)

    @Override
    protected long autoHideMs() { return AUTO_HIDE_MS; }

    @Override
    protected boolean onKeyEvent(KeyEvent ev) {
        int code = ev.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
        boolean left = code == KeyEvent.KEYCODE_DPAD_LEFT, right = code == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (!ok && !left && !right) return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
        if (ev.getAction() != KeyEvent.ACTION_DOWN || ev.getRepeatCount() > 0 && ok) return true;
        if (!Family.ADJUST_COALESCE) {
            if (state != READY || inFlight || SystemClock.uptimeMillis() - lastStepAt < MIN_STEP_GAP_MS) return true;
            inFlight = true;
            final Boolean cw = ok ? null : (Boolean) right;
            if (!Hal.run((g, g2) -> step(Family.rotateIo(app, g, g2), cw))) inFlight = false;
            return true;
        }
        if (state != READY) return true;
        synchronized (lock) {
            if (ok) {
                undo = true;
                pending = 0;
            } else {
                float target = cur + pending + (right ? STEP : -STEP);
                if (target > max + 0.01f || target < min - 0.01f) {
                    say(R.string.rot_limit);
                    return true;
                }
                pending += right ? STEP : -STEP;
            }
        }
        showTarget();
        if (!inFlight) {
            inFlight = true;
            if (!Hal.run((g, g2) -> drain(Family.rotateIo(app, g, g2)))) inFlight = false;
        }
        return true;
    }

    /** ADJUST_COALESCE: the angle the queued steps lead to, shown at once (the projector catches up). */
    private void showTarget() {
        if (value == null) return;
        float t;
        synchronized (lock) { t = undo ? opened : cur + pending; }
        float a = Math.round(t * 10f) / 10f;
        value.setText(String.format(Locale.ROOT, "%+.1f°", a == 0f ? 0f : a));
        status.setText("");
    }

    @Override
    protected boolean onBack() {
        backExit = true;
        return false;
    }

    @Override
    protected void onDismissed() {
        closed = true;
        if (sOpen == this) sOpen = null;
        if (!Hal.run((g, g2) -> exitNow(Family.rotateIo(app, g, g2), "rotate close"))) {
            Log.w(TAG, "close not queued: left to the connect hook");
        }
        if (backExit && onBackExit != null) Ui.main().post(onBackExit);
    }

    private void fail(String why) {
        Log.w(TAG, "unavailable: " + why);
        state = FAILED;
        Notify.show(app, R.string.rot_unavailable);
        dismiss();
    }

    @Override
    protected WindowManager.LayoutParams onCreateLayoutParams(Context c) {
        return EdgeGrid.fullscreen(c, TAG);
    }

    @Override
    protected View onCreateView(Context c) {
        FrameLayout root = new FrameLayout(c);
        root.addView(new EdgeFrameView(c, EdgeFrame.FRAME), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout card = EdgeGrid.card(c);
        card.addView(EdgeGrid.text(c, c.getString(R.string.rot_title), 40, Theme.TEXT));
        value = EdgeGrid.text(c, c.getString(R.string.zoom_wait), 56, Theme.TEXT);
        card.addView(value);
        card.addView(EdgeGrid.text(c, c.getString(R.string.rot_hint), 28, Theme.TEXT_DIM));
        status = EdgeGrid.text(c, "", 28, Theme.TEXT_DIM);
        card.addView(status);
        root.addView(card, new FrameLayout.LayoutParams(Theme.px(c, 900), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        return root;
    }

    private void render() {
        if (value == null || state != READY) return;
        float a = Math.round(cur * 10f) / 10f;
        value.setText(String.format(Locale.ROOT, "%+.1f°", a == 0f ? 0f : a));
        status.setText("");
    }

    private void say(int res) {
        if (status != null) status.setText(app.getString(res));
    }

    private static void ui(Runnable r) { Ui.main().post(r); }
}
