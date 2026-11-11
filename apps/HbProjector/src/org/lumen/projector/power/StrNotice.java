package org.lumen.projector.power;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;

import org.lumen.projector.R;
import org.lumen.projector.ui.Notify;
import org.lumen.projector.Ui;

/**
 * HB beta6: tell the user WHY the projector will not sleep fully (real STR) while a {@link StrGate} blocker
 * holds: in lamp-only standby the SoC stays on and the fans keep running, which looks like a fault. Shown as a
 * small notice (Notify, no vendor call):
 * <ul>
 * <li>when the power menu opens (POWER held past PowerKey.LONG_PRESS_MS, the menu's Sleep would not sleep fully);</li>
 * <li>once per connection when a USB host appears (USB_STATE), the most common blocker (a computer on the cable).</li>
 * </ul>
 * Never inside a standby (the lamp is off, nobody sees it) and never with the screen off.
 */
public final class StrNotice {
    private static final String TAG = "Z9xStrNotice";
    /** After the long press is recognised, let the menu come up first. */
    static final long MENU_DELAY_MS = 300;

    private static boolean sUsbShown;
    private static long sMenuNotedFor = -1;

    private StrNotice() {}

    static int textRes(StrNoticeRules.Kind k) {
        switch (k) {
            case USB: return R.string.hb_str_blocked_usb;
            case UPDATE: return R.string.hb_str_blocked_update;
            case UPDATE_CHECK: return R.string.hb_str_blocked_update_check;
            default: return R.string.hb_str_blocked_off;
        }
    }

    /** From App.startModules (main thread). */
    public static void install(Context app) {
        try {
            app.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) { onUsbState(c.getApplicationContext()); }
            }, StrGate.usbStateFilter(), Context.RECEIVER_EXPORTED);
        } catch (Throwable t) {
            Log.w(TAG, "install: " + t);
        }
    }

    private static void onUsbState(Context app) {
        StrNoticeRules.Kind k = StrNoticeRules.kind(StrGate.blocker(app));
        boolean due = StrNoticeRules.usbNoticeDue(k, sUsbShown);
        sUsbShown = k == StrNoticeRules.Kind.USB;
        if (due) show(app, k, "usb connected");
    }

    /** From KeyReceiver for every global key, before PowerUi (main thread). */
    public static void onGlobalKey(Context ctx, KeyEvent ev) {
        if (!StrNoticeRules.powerMenuPress(ev.getKeyCode(), ev.getAction(), ev.getRepeatCount(),
                ev.getEventTime() - ev.getDownTime(), PowerKey.LONG_PRESS_MS, ev.getDownTime(), sMenuNotedFor)) return;
        sMenuNotedFor = ev.getDownTime();
        final Context app = ctx.getApplicationContext();
        Ui.main().postDelayed(() -> {
            StrNoticeRules.Kind k = StrNoticeRules.kind(StrGate.blocker(app));
            if (k != null) show(app, k, "power menu");
        }, MENU_DELAY_MS);
    }

    private static void show(Context app, StrNoticeRules.Kind k, String why) {
        try {
            if (StandbyController.isActive()) return;                      // lamp off: nobody sees it
            PowerManager pm = app.getSystemService(PowerManager.class);
            if (pm != null && !pm.isInteractive()) return;
            Log.i(TAG, why + ": " + k);
            Notify.show(app, app.getString(R.string.hb_str_blocked_title), app.getString(textRes(k)));
        } catch (Throwable t) {
            Log.w(TAG, "show: " + t);
        }
    }
}
