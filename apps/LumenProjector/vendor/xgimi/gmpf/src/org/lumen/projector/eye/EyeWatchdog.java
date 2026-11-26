// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.eye;

import android.os.SystemClock;
import android.util.Log;

import org.lumen.projector.Hal;
import org.lumen.projector.Ui;
import org.lumen.projector.hal.GmpfClient;
import org.lumen.projector.hal.NuiNotReadyException;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The vendor UI watchdog of the eye protection mask on the gmpf families (Family.eyeWatchdog): while the opaque
 * mask is up, 115 uiHeartBeatSet arms the stock NativeUIWatch and 114 uiHeartBeatDo beats every second on the own
 * "z9x-eye" thread (Hal.runEye), so the vendor restores the light itself if this process dies. Main thread only.
 */
public final class EyeWatchdog {
    private static final String TAG = "Z9xEye";
    /** Stock watchdog name (GlobalConfig EyeProjectionService / SystemUI EyeProjectionDialog). */
    private static final String HB_NAME = "NativeUIWatch";
    /** Stock 1500; 3000 tolerates a busy z9x-hal thread (vendor ticks are 1 ms, so ~3 s+). */
    private static final int HB_PERIOD_MS = 3000;
    private static final long HB_PUMP_MS = 1000;
    private static volatile boolean sHbArmed;
    /** uptime when the beat in flight was queued (log only). */
    private static volatile long sBeatQueuedAt;
    private static boolean sLateLogged;
    /** 115(0) refused by the NUI gate: retries left / scheduled (main thread). */
    private static int sDisarmRetries;
    private static final int DISARM_RETRY_MAX = 30;
    /** One 114 beat in flight at most: a stuck HAL thread must not collect a queue of beats. */
    private static final AtomicBoolean sBeatInFlight = new AtomicBoolean();

    private EyeWatchdog() {}

    private static final Runnable PUMP = new Runnable() {
        @Override public void run() {
            if (!sHbArmed) return;
            if (sBeatInFlight.compareAndSet(false, true)) {
                // own "z9x-eye" thread; ifSkipped clears the flag when the task is dropped because
                // the HAL is not connected
                final long t0 = SystemClock.uptimeMillis();
                sBeatQueuedAt = t0;
                sLateLogged = false;
                boolean queued = Hal.runEye((g, g2) -> {
                    try {
                        if (sHbArmed) g.uiHeartBeatDo(HB_NAME);
                    } finally {
                        sBeatInFlight.set(false);
                        long dt = SystemClock.uptimeMillis() - t0;
                        if (dt > HB_PERIOD_MS / 2) {
                            Log.w(TAG, "114 beat completed " + dt + " ms after it was queued (period "
                                    + HB_PERIOD_MS + " ms)");
                        }
                    }
                }, () -> sBeatInFlight.set(false));
                if (!queued) sBeatInFlight.set(false);
            } else if (!sLateLogged && SystemClock.uptimeMillis() - sBeatQueuedAt > HB_PERIOD_MS / 2) {
                sLateLogged = true;
                Log.w(TAG, "114 beat still in flight " + (SystemClock.uptimeMillis() - sBeatQueuedAt)
                        + " ms (period " + HB_PERIOD_MS + " ms)");
            }
            Ui.main().postDelayed(this, HB_PUMP_MS);
        }
    };

    /** Main thread. Arms only with the fifo reader up (the timeout path goes through nuiCommand). */
    public static void arm(final boolean on) {
        if (on == sHbArmed) return;
        if (on && !GmpfClient.nuiReaderReady()) {
            Log.w(TAG, "no NUI fifo reader: vendor UI watchdog not armed");
            return;
        }
        sHbArmed = on;
        Ui.main().removeCallbacks(PUMP);
        Ui.main().removeCallbacks(DISARM_RETRY);
        sDisarmRetries = 0;
        send115(on);
        if (on) Ui.main().postDelayed(PUMP, HB_PUMP_MS);
    }

    /** 115 on the "z9x-eye" thread (after any queued beat). A refused disarm is retried. */
    private static void send115(final boolean on) {
        boolean queued = Hal.runEye((g, g2) -> {
            try {
                Log.i(TAG, "115 uiHeartBeatSet(" + HB_NAME + ", " + (on ? 1 : 0) + ", " + HB_PERIOD_MS
                        + ") -> " + g.uiHeartBeatSet(HB_NAME, on, HB_PERIOD_MS));
            } catch (NuiNotReadyException e) {
                Log.w(TAG, "115(" + (on ? 1 : 0) + ") not sent: " + e.getMessage());
                if (!on) Ui.main().post(EyeWatchdog::scheduleDisarmRetry);
            }
        }, null);
        if (!queued) Log.w(TAG, "115(" + (on ? 1 : 0) + "): HAL not available");
    }

    /** Main thread: the vendor watchdog may still be armed; retry 115(0) once the reader is back. */
    private static void scheduleDisarmRetry() {
        if (sHbArmed) return;                                   // re-armed meanwhile: its hide disarms
        if (++sDisarmRetries > DISARM_RETRY_MAX) {
            Log.w(TAG, "115(0) still refused after " + DISARM_RETRY_MAX + " s: giving up (the vendor "
                    + "watchdog restores the light on its own)");
            return;
        }
        Ui.main().removeCallbacks(DISARM_RETRY);
        Ui.main().postDelayed(DISARM_RETRY, 1000);
    }

    private static final Runnable DISARM_RETRY = () -> {
        try {
            if (sHbArmed) return;
            if (!GmpfClient.nuiReaderReady()) { scheduleDisarmRetry(); return; }
            send115(false);
        } catch (Throwable t) {
            Log.w(TAG, "115(0) retry: " + t);
        }
    };
}
