// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu.walk;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import org.lumen.menu.FeatureId;
import org.lumen.projector.panel.PowerKeyPrefs;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.reflect.Method;

/**
 * Test harness of the power key walk (test/ui/power_walk.py), built only into a test APK by test/ui/build.sh
 * (WALK=PowerWalkActivity) from a model's real assembled app, never into the image. The SDK emulator has no Lumen
 * global keys, so a virtual HID remote sends PROG_RED (Android's own repeats and timing) and this activity hands each
 * of its events, as KEYCODE_STB_POWER with the same times, to the family's real power key entry: the X50 family's
 * X50Power.key, else Family.powerKey, the KeyReceiver's power entry (gmpf PowerKey, Play 6 PowerKey). {@code am start --es power_short TOKEN}
 * stores the setting through the menu's own ModuleScreens.set; {@code dumpsys activity} prints it as power.choice.
 * While the power menu has the focus a HID key reaches the menu, not this activity (on a projector the power key is a
 * global key and reaches the app in any window): {@code am broadcast -a org.lumen.menu.walk.PRESS --ei ms MS -p PKG}
 * presses the power key through the same entry, DOWN, Android's repeats (400 ms, then every 50 ms) and UP at MS.
 */
public class PowerWalkActivity extends Activity {
    private static final String TAG = "PowerWalk";
    static final String PRESS = "org.lumen.menu.walk.PRESS";
    private static boolean sPressReceiver;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        View v = new View(this);
        v.setBackgroundColor(0xFF305070);
        v.setFocusable(true);
        setContentView(v);
        v.requestFocus();
        apply(getIntent());
        if (!sPressReceiver) {
            sPressReceiver = true;
            getApplicationContext().registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    press(c.getApplicationContext(), i.getIntExtra("ms", 150));
                }
            }, new IntentFilter(PRESS), Context.RECEIVER_EXPORTED);
        }
    }

    /** One power key press held {@code ms} through the power key entry, with Android's repeat timing. */
    private static void press(Context app, int ms) {
        Handler h = new Handler(Looper.getMainLooper());
        long down = SystemClock.uptimeMillis();
        h.post(() -> entry(app, new KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_STB_POWER, 0)));
        int r = 1;
        for (long t = 400; t < ms; t += 50, r++) {
            final int rep = r;
            final long at = down + t;
            h.postAtTime(() -> entry(app, new KeyEvent(down, at, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_STB_POWER, rep)), at);
        }
        h.postAtTime(() -> entry(app, new KeyEvent(down, down + ms, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_STB_POWER, 0)),
                down + ms);
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        apply(i);
    }

    private void apply(Intent i) {
        String t = i == null ? null : i.getStringExtra("power_short");
        if (t == null) return;
        try {
            Class<?> ms = Class.forName("org.lumen.projector.panel.ModuleScreens");
            Method set = ms.getDeclaredMethod("set", Context.class, FeatureId.class, String.class);
            set.setAccessible(true);
            Log.i(TAG, "set " + t + ": " + set.invoke(null, getApplicationContext(), FeatureId.SETTINGS_POWER_KEY, t));
        } catch (Throwable e) {
            Log.e(TAG, "set " + t, e);
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_PROG_RED) return super.dispatchKeyEvent(e);
        KeyEvent p = new KeyEvent(e.getDownTime(), e.getEventTime(), e.getAction(), KeyEvent.KEYCODE_STB_POWER,
                e.getRepeatCount(), e.getMetaState(), e.getDeviceId(), e.getScanCode(), e.getFlags(), e.getSource());
        entry(getApplicationContext(), p);
        return true;
    }

    /** One event of the power key into the family's real entry (X50Power.key, else Family.powerKey). */
    private static void entry(Context app, KeyEvent p) {
        if (p.getRepeatCount() == 0) Log.i(TAG, "power " + (p.getAction() == KeyEvent.ACTION_DOWN ? "down" : "up")
                + " at " + (p.getEventTime() - p.getDownTime()) + " ms");
        try {
            Method key;
            try {
                key = Class.forName("org.lumen.x50.projector.X50Power").getDeclaredMethod("key", Context.class,
                        KeyEvent.class, boolean.class);
                key.setAccessible(true);
                key.invoke(null, app, p, false);
            } catch (ClassNotFoundException x50) {
                Intent g = new Intent(Intent.ACTION_GLOBAL_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT, p);
                key = Class.forName("org.lumen.projector.Family").getDeclaredMethod("powerKey", Context.class,
                        Intent.class, KeyEvent.class);
                key.setAccessible(true);
                key.invoke(null, app, g, p);
            }
        } catch (Throwable t) {
            Log.e(TAG, "power key entry", t);
        }
    }

    @Override public void dump(String prefix, FileDescriptor fd, PrintWriter w, String[] args) {
        w.println("power.choice=" + PowerKeyPrefs.choice(getApplicationContext()).token);
        try {                                                         // the lamp-only standby of the gmpf and Play 6 families
            w.println("power.standby=" + Class.forName("org.lumen.projector.Family").getMethod("standbyActive").invoke(null));
        } catch (Throwable t) {
            w.println("power.standby=false");
        }
    }
}
