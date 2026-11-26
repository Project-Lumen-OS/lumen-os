// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu.walk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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
 */
public class PowerWalkActivity extends Activity {
    private static final String TAG = "PowerWalk";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        View v = new View(this);
        v.setBackgroundColor(0xFF305070);
        v.setFocusable(true);
        setContentView(v);
        v.requestFocus();
        apply(getIntent());
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
        if (p.getRepeatCount() == 0) Log.i(TAG, "power " + (p.getAction() == KeyEvent.ACTION_DOWN ? "down" : "up")
                + " at " + (p.getEventTime() - p.getDownTime()) + " ms");
        try {
            Context app = getApplicationContext();
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
        return true;
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
