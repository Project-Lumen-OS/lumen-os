// SPDX-License-Identifier: Apache-2.0
package org.lumen.report;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the sign of life of this start (Alive, files/report_alive) in the projector app's persistent process, once
 * per process: started by AliveStart (a provider, so at every process start), BOOT_COMPLETED and every ReportService
 * job. Its first step is the boot check (Alive.bootCheck: an unexpected stop of the start before leaves
 * files/report_stop for the next scan), then it feeds AliveState: a beat every AliveState.BEAT_MS while the screen is on, and
 * the broadcasts of every way out. ACTION_SHUTDOWN (ShutdownThread waits for it, every Lumen reboot and power off
 * goes through PowerManager) and SCREEN_OFF (the suspend waits for it) are written before onReceive returns.
 * STANDBY_CHANGED: the lamp-only standby of Z9X, HB and Play 6 (FLAG_RECEIVER_REGISTERED_ONLY, sent by this app).
 */
final class AliveWatch {
    private static final String TAG = ReportConfig.TAG;
    private static final long CHECK_WAIT_MS = 10_000L;
    static final String ACTION_STANDBY_CHANGED = "org.lumen.projector.action.STANDBY_CHANGED";
    static final String EXTRA_STANDBY = "standby";
    static final String PERMISSION_STANDBY = "org.lumen.projector.permission.STANDBY_STATE";
    private static final String MARKER = "report_alive", PENDING = "report_stop";
    private static final CountDownLatch CHECKED = new CountDownLatch(1);
    private static AliveWatch sWatch;

    private final Context app;
    private final Handler worker;
    private final File marker, pending;
    private final Runnable beat = () -> record(AliveState.BEAT);
    private String boot = "";
    private AliveState state;

    private AliveWatch(Context app) {
        this.app = app;
        HandlerThread t = new HandlerThread("lumen-report-alive");
        t.start();
        worker = new Handler(t.getLooper());
        marker = new File(app.getFilesDir(), MARKER);
        pending = new File(app.getFilesDir(), PENDING);
    }

    /** Once per process, any thread. Nothing at all on an image without a report server. */
    static void start(Context c) {
        AliveWatch w;
        synchronized (AliveWatch.class) {
            if (sWatch != null) return;
            if (!ReportConfig.enabled()) {
                CHECKED.countDown();
                return;
            }
            w = sWatch = new AliveWatch(c.getApplicationContext());
        }
        w.begin();
    }

    /** Waits (at most CHECK_WAIT_MS) until the boot check of this process ran. Worker threads only. */
    static void awaitCheck() {
        try {
            CHECKED.await(CHECK_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The unexpected stop the boot check left, or null; read and removed, so it is handled once. */
    static Incident takeStop(Context c) {
        return Alive.incident(AliveFile.take(new File(c.getApplicationContext().getFilesDir(), PENDING)), TimeZone.getDefault());
    }

    private void begin() {
        // the check is queued first: every broadcast below lands on the same thread after it
        worker.post(this::check);
        BroadcastReceiver r = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String e = event(i);
                if (e != null) record(e);
            }
        };
        IntentFilter sys = new IntentFilter();
        sys.addAction(Intent.ACTION_SHUTDOWN);
        sys.addAction(Intent.ACTION_REBOOT);
        sys.addAction(Intent.ACTION_SCREEN_OFF);
        sys.addAction(Intent.ACTION_SCREEN_ON);
        sys.addAction(Intent.ACTION_DREAMING_STARTED);
        sys.addAction(Intent.ACTION_DREAMING_STOPPED);
        try {
            // protected broadcasts of the system: only the system sends them (as the Z9X shutdown mark does)
            app.registerReceiver(r, sys, null, worker, Context.RECEIVER_EXPORTED);
            app.registerReceiver(r, new IntentFilter(ACTION_STANDBY_CHANGED), PERMISSION_STANDBY, worker,
                    Context.RECEIVER_NOT_EXPORTED);
        } catch (RuntimeException e) {
            Log.w(TAG, "alive receivers: " + e);
        }
    }

    private static String event(Intent i) {
        String a = i == null ? null : i.getAction();
        if (a == null) return null;
        switch (a) {
            case Intent.ACTION_SHUTDOWN: return AliveState.SHUTDOWN;
            case Intent.ACTION_REBOOT: return AliveState.REBOOT;
            case Intent.ACTION_SCREEN_OFF: return AliveState.SCREEN_OFF;
            case Intent.ACTION_SCREEN_ON: return AliveState.SCREEN_ON;
            case Intent.ACTION_DREAMING_STARTED: return AliveState.DREAM_ON;
            case Intent.ACTION_DREAMING_STOPPED: return AliveState.DREAM_OFF;
            case ACTION_STANDBY_CHANGED:
                return i.getBooleanExtra(EXTRA_STANDBY, false) ? AliveState.STANDBY_ON : AliveState.STANDBY_OFF;
            default: return null;
        }
    }

    /** Worker thread, first thing of this process. */
    private void check() {
        try {
            boot = bootId();
            long up = SystemClock.elapsedRealtime();
            Alive prev = Alive.parse(AliveFile.read(marker));
            String[] reasons = new String[Alive.REASON_PROPS.length];
            for (int i = 0; i < reasons.length; i++) reasons[i] = ReportConfig.prop(Alive.REASON_PROPS[i]);
            if (Alive.bootCheck(marker, pending, boot, up, System.currentTimeMillis(), reasons)) {
                Log.i(TAG, "the last start ended without a shutdown (last sign of life " + prev.time + ", " + prev.why
                        + "): one automatic report");
            }
            state = new AliveState(prev, boot, up);
        } catch (Throwable t) {
            Log.w(TAG, "alive check: " + t);
            state = new AliveState(null, boot, SystemClock.elapsedRealtime());
        } finally {
            CHECKED.countDown();
        }
        record(AliveState.START);
    }

    /** Worker thread: writes what the event changes and plans the next beat. */
    private void record(String event) {
        if (state == null) return;
        boolean interactive = true;
        try {
            PowerManager pm = app.getSystemService(PowerManager.class);
            if (pm != null) interactive = pm.isInteractive();
        } catch (RuntimeException e) {
            Log.w(TAG, "alive interactive: " + e);
        }
        Alive a = state.on(event, interactive, front(), boot, System.currentTimeMillis(), SystemClock.elapsedRealtime());
        if (a != null && !AliveFile.write(marker, a.line())) Log.w(TAG, "alive record not written (" + event + ")");
        worker.removeCallbacks(beat);
        if (AliveState.beats(interactive)) worker.postDelayed(beat, AliveState.BEAT_MS);
    }

    /** The activity in front (REAL_GET_TASKS, every projector app holds it), "" when unknown. */
    @SuppressWarnings("deprecation")
    private String front() {
        try {
            ActivityManager am = app.getSystemService(ActivityManager.class);
            List<ActivityManager.RunningTaskInfo> l = am == null ? null : am.getRunningTasks(1);
            ComponentName cn = l == null || l.isEmpty() ? null : l.get(0).topActivity;
            return cn == null ? "" : cn.flattenToString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** The kernel's id of this start; "" when unreadable (then Alive.sameBoot uses the time since the start). */
    private static String bootId() {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/sys/kernel/random/boot_id"))) {
            String s = r.readLine();
            return s == null ? "" : s.trim();
        } catch (Throwable t) {
            return "";
        }
    }
}
