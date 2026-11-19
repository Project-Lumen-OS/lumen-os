// SPDX-License-Identifier: Apache-2.0
package org.lumen.parental;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The child lock as Lumen Home uses it: the state in Home's device protected preferences, which apps Home shows, which
 * launches ask the password, the power on password and child mode, the time limit watch. One process (Home), so a
 * plain synchronized load and save.
 */
public final class Parental {
    static final String TAG = "LumenParental";
    private static final String PREFS = "lumen_parental", K_STATE = "state", K_BOOT = "boot_mark", K_UNLOCKED = "boot_unlocked";
    /** Keys of Home's input cards (HDMI ports and other TV inputs) next to package names. */
    public static final String INPUT_PREFIX = "input:";

    private Parental() {}

    private static SharedPreferences prefs(Context c) {
        return c.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized ParentalState load(Context c) {
        return ParentalState.decode(prefs(c).getString(K_STATE, null));
    }

    public static synchronized void save(Context c, ParentalState s) {
        prefs(c).edit().putString(K_STATE, s.encode()).apply();
    }

    public static boolean visible(Context c, String key) { return ParentalRules.visible(load(c), key); }

    /**
     * Before Home opens {@code key}: false = open it now; true = the password screen is up and opens {@code then}
     * after the right password.
     */
    public static boolean guard(Context c, String key, Intent then) {
        if (!ParentalRules.needsPin(load(c), key)) return false;
        PinActivity.verifyThen(c, then);
        return true;
    }

    /** Home in front: once per power on child mode and the power on password; the time watch while controls act. */
    public static void onHomeResume(Activity a) {
        ParentalState s = load(a);
        long mark = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        long seen = prefs(a).getLong(K_BOOT, 0);
        if (Math.abs(mark - seen) > 60_000L) {
            prefs(a).edit().putLong(K_BOOT, mark).putBoolean(K_UNLOCKED, false).apply();
            ParentalRules.boot(s);
            save(a, s);
            Log.i(TAG, "power on: lock " + s.lockOn + ", child mode " + s.childMode + ", password " + s.bootPin);
        }
        if (s.lockOn && s.bootPin && !prefs(a).getBoolean(K_UNLOCKED, false)) {
            PinActivity.boot(a);
            return;
        }
        if (ParentalRules.controlled(s)) UsageService.start(a);
    }

    static void bootUnlocked(Context c) { prefs(c).edit().putBoolean(K_UNLOCKED, true).apply(); }

    static int today() { return Integer.parseInt(new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date())); }
}
