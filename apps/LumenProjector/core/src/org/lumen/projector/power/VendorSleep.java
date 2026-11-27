// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.power;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import org.lumen.projector.Family;
import org.lumen.projector.Hal;

/**
 * MODULE "power": the vendor step of the stock STR sleep on the families of the one projector core (Z9X, Z9X HB,
 * RS 20), the rule in {@link StrPrepare}; the family adapter gives the calls (Family.strVendor, null = none). Sent once
 * per Android sleep, in the stock order where Lumen starts the sleep: before PowerManager.goToSleep and so before the
 * framework's CEC standby, ARC termination and {@code <Standby>} (the stock prepare runs inside goToSleep;
 * {@link #beforeSleep} through org.lumen.hdmi.SleepSource), when STR would be allowed and no lamp-off of ours runs.
 * Otherwise (a sleep Lumen did not start, a blocker that cleared later) when the kernel is let suspend
 * (StandbyController.onSleepGate "STR allowed": SCREEN_OFF, a process restart while asleep, a blocker that cleared)
 * and our own lamp-off has finished (PowerPolicy.onSleepLampOff); never while an STR blocker keeps the CPU awake or a
 * restart for an update follows (stock sends it only when its shutdown mode is STR). A partial wake lock (at most
 * {@link #HOLD_MS}) holds the suspend until the vendor answered. Nothing is sent at the wake, as stock: after an STR
 * the power HAL's wakeUp resumes the vendor, a wake before the suspend gets the light back through LampControl's
 * no-STR path.
 */
final class VendorSleep {
    private static final String TAG = "Z9xPower";
    static final long HOLD_MS = 10_000L;
    /** {@link #beforeSleep} waits at most this long for the vendor's answer (stock: a synchronous call in goToSleep). */
    static final long BEFORE_WAIT_MS = 2_000L;

    private static final StrPrepare.Once ONCE = new StrPrepare.Once();
    private static PowerManager.WakeLock sHold;

    private VendorSleep() {}

    /** Main thread: the kernel may suspend now (strAllowed: no STR blocker); sends the prepare if this sleep has none. */
    static void maybeSend(Context app, String why, boolean strAllowed) {
        if (app == null) return;
        PowerManager pm = app.getSystemService(PowerManager.class);
        boolean asleep;
        try { asleep = pm != null && !pm.isInteractive(); } catch (Throwable t) { asleep = false; }
        if (!ONCE.take(asleep, strAllowed, LampControl.isOffPending())) return;
        hold(pm);
        Hal.runDelayed((g, g2) -> {
            try {
                send(Family.strVendor(g), why);
            } finally {
                release();
            }
        }, 0, () -> {
            ONCE.failed();
            release();
            Log.w(TAG, "sleep: vendor goToSleep(0, 0) not sent, HAL not connected (" + why + ")");
        });
    }

    /**
     * Before a Lumen sleep (SleepSource.beforeSleep; any thread but the HAL thread): the prepare now, in the stock
     * order, when STR would be allowed (strAllowed: StandbyController.strWouldBeAllowed) and no lamp-off of ours runs.
     * Waits for the vendor's answer, at most {@link #BEFORE_WAIT_MS}. A HAL that is not connected leaves it to the
     * SCREEN_OFF path.
     */
    static void beforeSleep(Context app, boolean strAllowed) {
        if (app == null) return;
        if (!ONCE.takeBefore(strAllowed, LampControl.isOffPending())) return;
        PowerManager pm = app.getSystemService(PowerManager.class);
        hold(pm);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        Hal.runDelayed((g, g2) -> {
            try {
                send(Family.strVendor(g), "before the sleep");
            } finally {
                release();
                done.countDown();
            }
        }, 0, () -> {
            ONCE.failed();
            release();
            done.countDown();
            Log.w(TAG, "sleep: vendor goToSleep(0, 0) not sent before the sleep, HAL not connected");
        });
        try {
            if (!done.await(BEFORE_WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "sleep: the vendor did not answer in " + BEFORE_WAIT_MS + " ms, the sleep goes on");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Main thread, SCREEN_ON: the next sleep sends its own prepare. */
    static void onWake() {
        ONCE.woke();
    }

    /** HAL thread; v null: this family's HAL has no such step, nothing is sent. */
    private static void send(StrPrepare.Vendor v, String why) {
        if (v == null) {
            Log.i(TAG, "sleep: no vendor sleep step on this HAL, nothing sent (" + why + ")");
            return;
        }
        try {
            StrPrepare.prepare(v);
            Log.i(TAG, "sleep: vendor goToSleep(0, 0) sent (the stock prepare, " + why + ")");
        } catch (Throwable t) {
            ONCE.failed();
            Log.w(TAG, "sleep: vendor goToSleep(0, 0) failed (" + why + "): " + t);
        }
    }

    private static synchronized void hold(PowerManager pm) {
        try {
            if (sHold == null && pm != null) {
                sHold = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lumen:strprepare");
                sHold.setReferenceCounted(false);
            }
            if (sHold != null) sHold.acquire(HOLD_MS);
        } catch (Throwable t) {
            Log.w(TAG, "strprepare wakelock: " + t);
        }
    }

    private static synchronized void release() {
        try { if (sHold != null && sHold.isHeld()) sHold.release(); } catch (Throwable t) { Log.w(TAG, "strprepare release: " + t); }
    }
}
