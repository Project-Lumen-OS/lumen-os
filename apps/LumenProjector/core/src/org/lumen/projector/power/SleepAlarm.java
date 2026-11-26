// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.power;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import org.lumen.projector.Family;

/**
 * The sleep timer of a family whose stock ends it with the power key (core feature sleep-alarm, the MT9681 family; the
 * other families run the in-process fade, power/SleepTimer). The expiry is the stock one: SystemUI
 * power/PowerUtils.shutDownTimer sends KEYCODE_POWER, so the projector goes to its normal standby exactly as on a power
 * key press (the family's SleepHost.press). An exact alarm (ELAPSED_REALTIME_WAKEUP), the state in device protected
 * prefs (it survives the process), cleared at boot and when it fires. Every switch to standby (the power key, the Home
 * Sleep tile, the power menu) cancels a pending timer: the app's persistent process calls cancelOnScreenOff on
 * ACTION_SCREEN_OFF, so the alarm cannot wake the projector from standby and press the power key again (that press
 * would turn it back on). The expiry itself presses only while the projector is on (SleepHost.mayPress).
 * The stock warnings ride on two more alarms of the same timer, at the family's offsets (SleepHost.warnAt / countdownAt)
 * and shown by the family (SleepHost.notice); OK on either stops the timer (stopFromNotice).
 */
public final class SleepAlarm extends BroadcastReceiver {
    private static final String TAG = Family.SLEEP_TAG;
    static final String ACTION = "org.lumen.projector.action.SLEEP_TIMER";
    static final String ACTION_WARN = "org.lumen.projector.action.SLEEP_TIMER_WARN";
    static final String ACTION_COUNTDOWN = "org.lumen.projector.action.SLEEP_TIMER_COUNTDOWN";
    private static final String PREFS = "sleep_timer";

    /** A new timer of {@code minutes} (0 or less: off); any pending one and its warnings are dropped first. */
    public static void set(Context c, int minutes) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = pending(c, ACTION);
        am.cancel(pi);
        am.cancel(pending(c, ACTION_WARN));
        am.cancel(pending(c, ACTION_COUNTDOWN));
        SharedPreferences.Editor e = prefs(c).edit();
        if (minutes <= 0) {
            e.putLong("at", 0).putInt("min", 0).apply();
            Log.i(TAG, "sleep timer off");
            return;
        }
        SleepHost host = Family.sleepHost();
        long now = SystemClock.elapsedRealtime();
        long at = now + minutes * 60_000L;
        am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi);
        long warn = host.warnAt(at, now);
        if (warn > 0) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, warn, pending(c, ACTION_WARN));
        long count = host.countdownAt(at, now);
        if (count > 0) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, count, pending(c, ACTION_COUNTDOWN));
        e.putLong("at", at).putInt("min", minutes).apply();
        Log.i(TAG, "sleep timer " + minutes + " min");
    }

    /** ACTION_SCREEN_OFF (the app's persistent process): the projector went to standby, a pending timer has nothing left to do. */
    public static void cancelOnScreenOff(Context c) {
        if (prefs(c).getLong("at", 0) <= 0) return;
        set(c, 0);
        Log.i(TAG, "sleep timer cancelled: standby (screen off)");
    }

    /** Boot: the alarms of the last boot are gone, so is the timer. */
    public static void clear(Context c) {
        prefs(c).edit().putLong("at", 0).putInt("min", 0).apply();
    }

    /** The minutes of the pending timer as chosen, 0 when none. */
    public static int chosenMinutes(Context c) {
        return minutesLeft(c) > 0 ? prefs(c).getInt("min", 0) : 0;
    }

    /** Minutes left (rounded up), 0 when no timer is pending. */
    public static long minutesLeft(Context c) {
        long at = prefs(c).getLong("at", 0);
        long left = at - SystemClock.elapsedRealtime();
        return at <= 0 || left <= 0 ? 0 : (left + 59_999) / 60_000;
    }

    private static SharedPreferences prefs(Context c) {
        return c.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static PendingIntent pending(Context c, String action) {
        return PendingIntent.getBroadcast(c, 0, new Intent(action).setClass(c, SleepAlarm.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** The time a pending timer ends (elapsedRealtime), 0 when none. */
    public static long endsAt(Context c) {
        return prefs(c).getLong("at", 0);
    }

    /** OK on a warning: the timer stops and says so (stock setTimePowerOff(0) and its clean_time_shutdown toast). */
    public static void stopFromNotice(Context c) {
        if (endsAt(c) <= 0) return;
        set(c, 0);
        Family.sleepHost().stopped(c);
    }

    @Override public void onReceive(Context c, Intent i) {
        SleepHost host = Family.sleepHost();
        if (!host.active()) return;
        String action = i.getAction();
        if (ACTION_WARN.equals(action) || ACTION_COUNTDOWN.equals(action)) {
            PowerManager pm = c.getSystemService(PowerManager.class);
            long at = endsAt(c);
            if (host.mayPress(at, pm != null && pm.isInteractive())) {
                host.notice(c, ACTION_WARN.equals(action), at);
            }
            return;
        }
        if (!ACTION.equals(action)) return;
        long at = prefs(c).getLong("at", 0);
        clear(c);
        PowerManager pm = c.getSystemService(PowerManager.class);
        boolean interactive = pm != null && pm.isInteractive();
        if (!host.mayPress(at, interactive)) {
            Log.i(TAG, "sleep timer: no power key (" + (at <= 0 ? "cancelled" : "already in standby") + ")");
            return;
        }
        host.press("sleep timer", goAsync());
    }
}
