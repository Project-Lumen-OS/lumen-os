// SPDX-License-Identifier: Apache-2.0
package org.lumen.parental;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

/**
 * The stock time watch (ChildModeTimingService): one tick a minute while the screen is on and the controls act, the
 * break or day screen when a limit is reached, stops itself when the lock goes off.
 */
public final class UsageService extends Service {
    private static final long TICK_MS = 60_000L;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::tick;

    static void start(Context c) {
        try {
            c.startService(new Intent(c, UsageService.class));
        } catch (RuntimeException e) {
            Log.w(Parental.TAG, "time watch not started: " + e);
        }
    }

    @Override public int onStartCommand(Intent i, int flags, int id) {
        h.removeCallbacks(tick);
        h.postDelayed(tick, TICK_MS);
        return START_STICKY;
    }

    private void tick() {
        ParentalState s = Parental.load(this);
        if (!ParentalRules.controlled(s)) {
            stopSelf();
            return;
        }
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm == null || pm.isInteractive()) {
            LimitState st = ParentalRules.tick(s, Parental.today(), System.currentTimeMillis());
            Parental.save(this, s);
            if (st != LimitState.FREE) RestActivity.show(this, st);
        }
        h.postDelayed(tick, TICK_MS);
    }

    @Override public void onDestroy() {
        h.removeCallbacks(tick);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
