// SPDX-License-Identifier: Apache-2.0
package org.lumen.wallcolor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * The wall colour adaptation of a projector app (README.md): {@link #install} once with the app's WallColorHal, route
 * the vendor's focus events through {@link #onFocusEvent}, open the card from the menu row with {@link #open}. Screen
 * off / on and HOME (ACTION_CLOSE_SYSTEM_DIALOGS reason homekey) reach the flow here, as the stock SystemUI does.
 */
public final class WallColor {
    static final String TAG = "LumenWallColor";
    private static final String MODEL = "lumen_wallcolor/model.conf";
    private static final String REASON = "reason", HOME_KEY = "homekey";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile WallColorFlow sFlow;

    private WallColor() {}

    /** Once per process, from the app's onCreate. */
    public static synchronized void install(Context c, WallColorHal hal) {
        if (sFlow != null) return;
        Context app = c.getApplicationContext();
        WallColorModel model = model(app);
        if (model == null) {
            Log.i(TAG, "no " + MODEL + ": this model's stock has no wall colour adaptation");
            return;
        }
        WallColorHal onMain = (call, done) -> {
            Log.i(TAG, "call " + call);
            hal.call(call, ok -> {
                Log.i(TAG, call + " -> " + ok);
                MAIN.post(() -> done.done(ok));
            });
        };
        WallColorWindows windows = new WallColorWindows(app, model);
        WallColorFlow flow = new WallColorFlow(onMain, windows, MAIN::postDelayed);
        windows.bind(flow);
        sFlow = flow;
        BroadcastReceiver r = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) {
                String a = i.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(a)) flow.screenOff();
                else if (Intent.ACTION_SCREEN_ON.equals(a)) flow.screenOn();
                else if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(a) && HOME_KEY.equals(i.getStringExtra(REASON))) flow.home();
            }
        };
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        try {
            app.registerReceiver(r, f, Context.RECEIVER_EXPORTED);
        } catch (Throwable t) {
            Log.w(TAG, "screen receiver: " + t);
        }
        Log.i(TAG, "installed, model " + model.model + " type " + model.type + " ambient " + model.ambientLight);
    }

    /** True once the app installed the module (the menu row is shown only then). */
    public static boolean available(Context c) {
        return sFlow != null;
    }

    /** The menu row: the card with Start and Reset. */
    public static boolean open(Context c) {
        WallColorFlow f = sFlow;
        if (f == null) return false;
        MAIN.post(f::open);
        return true;
    }

    /**
     * A vendor focus event (any thread): true when it was a wall colour message (type 11001), which then goes to the
     * flow on the main thread. Other types are left to the caller.
     */
    public static boolean onFocusEvent(int type, String value) {
        if (type != WallColorEvent.TYPE) return false;
        WallColorEvent e = WallColorEvent.parse(value);
        Log.i(TAG, "event " + value + (e == null ? " (not read)" : ""));
        WallColorFlow f = sFlow;
        if (f != null && e != null) MAIN.post(() -> f.onEvent(e));
        return true;
    }

    private static WallColorModel model(Context app) {
        try (Reader in = new InputStreamReader(app.getAssets().open(MODEL), StandardCharsets.UTF_8)) {
            return WallColorModel.read(in);
        } catch (java.io.FileNotFoundException none) {
            return null;
        } catch (Throwable t) {
            Log.w(TAG, MODEL + ": " + t);
            return WallColorModel.DEFAULT;
        }
    }
}
