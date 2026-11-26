// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.system;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.TetheringManager;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.security.SecureRandom;
import java.util.Arrays;

/**
 * The stock Wireless Hotspot on Android's own hotspot (SoftAp through TetheringManager), one copy for every projector:
 * whether the rows are there, the switch, the name and the password (WPA2 as stock), the first configuration kept as
 * the reset target, and the stop and start again when a running hotspot gets a new name or password (stock
 * NetWorkApManager.setWifiApConfig). Every call catches what Android throws: a refused call is a failure, never a crash.
 * The decisions are HotspotRules. Needs TETHER_PRIVILEGED, NETWORK_SETTINGS, OVERRIDE_WIFI_CONFIG, ACCESS_WIFI_STATE.
 */
public final class Hotspot {
    private static final String TAG = "LumenHotspot";
    /** Sent to the own app when the name or password changed, so an open menu reads the rows again. */
    public static final String CHANGED = "org.lumen.system.HOTSPOT_CHANGED";
    private static final String PREFS = "lumen_hotspot", FIRST_NAME = "first_name", FIRST_PASSWORD = "first_password";
    /** How long a restart waits for the hotspot to stop before it starts it anyway. */
    static final long RESTART_WAIT_MS = 8000;

    /** The end of a switch or a restart. */
    public interface Done {
        /** The hotspot runs under {@code name}. */
        void started(String name);

        void stopped();

        void failed();
    }

    private Hotspot() {}

    private static WifiManager wifi(Context c) { return c.getSystemService(WifiManager.class); }

    private static TetheringManager tethering(Context c) { return c.getSystemService(TetheringManager.class); }

    /** The filter of the moments the rows change: Android's hotspot state and a saved name or password. */
    public static IntentFilter changes() {
        IntentFilter f = new IntentFilter(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        f.addAction(CHANGED);
        return f;
    }

    /**
     * True when the hotspot rows are there: Android offers a tethered Wi-Fi hotspot and tethers the vendor AP
     * interface {@code iface} (the model's data).
     */
    public static boolean supported(Context c, String iface) {
        try {
            WifiManager w = wifi(c);
            TetheringManager t = tethering(c);
            return w != null && t != null && w.isPortableHotspotSupported() && t.isTetheringSupported()
                    && HotspotRules.tetherable(t.getTetherableWifiRegexs(), iface);
        } catch (RuntimeException e) {
            Log.w(TAG, "supported: " + e);
            return false;
        }
    }

    /** True while the hotspot runs or starts. */
    public static boolean on(Context c) {
        try {
            WifiManager w = wifi(c);
            return w != null && HotspotRules.on(w.getWifiApState());
        } catch (RuntimeException e) {
            Log.w(TAG, "state: " + e);
            return false;
        }
    }

    /** {name, password} of the stored configuration, null when Android gives none. */
    public static String[] config(Context c) {
        try {
            SoftApConfiguration s = wifi(c).getSoftApConfiguration();
            return s == null ? null : new String[]{s.getSsid(), s.getPassphrase()};
        } catch (RuntimeException e) {
            Log.w(TAG, "config: " + e);
            return null;
        }
    }

    /** The name of the Wi-Fi network the projector is on, null when none. */
    public static String wifiName(Context c) {
        try {
            WifiInfo i = wifi(c).getConnectionInfo();
            return HotspotRules.ssid(i == null ? null : i.getSSID());
        } catch (RuntimeException e) {
            Log.w(TAG, "wifi name: " + e);
            return null;
        }
    }

    /**
     * The reset target, made once and kept: the configuration found first, where Android's made-up name becomes
     * {@code modelName} ("Lumen Z9X") with a new password, written to Android at that moment. Null when Android gives
     * no configuration.
     */
    public static String[] first(Context c, String modelName) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String n = p.getString(FIRST_NAME, null), pw = p.getString(FIRST_PASSWORD, null);
        if (n != null && pw != null) return new String[]{n, pw};
        String[] cur = config(c);
        if (cur == null) return null;
        String[] f = HotspotRules.first(cur[0], cur[1], modelName, new SecureRandom());
        if (!Arrays.equals(f, cur) && !write(c, f[0], f[1])) return null;
        p.edit().putString(FIRST_NAME, f[0]).putString(FIRST_PASSWORD, f[1]).apply();
        return f;
    }

    /**
     * Saves a name and password that HotspotRules accepts; a running hotspot stops and starts again with them (stock).
     * False when refused (nothing changed then).
     */
    public static boolean save(Context c, String name, String password, Done d) {
        if (HotspotRules.name(name) != null || HotspotRules.password(password) != null) return false;
        boolean running = on(c);
        if (!write(c, name, password)) return false;
        c.sendBroadcast(new Intent(CHANGED).setPackage(c.getPackageName()));
        if (running) restart(c, d);
        return true;
    }

    /** Back to the first configuration ({@link #first}), as stock Restore default. */
    public static boolean reset(Context c, String modelName, Done d) {
        String[] f = first(c, modelName);
        return f != null && save(c, f[0], f[1], d);
    }

    private static boolean write(Context c, String name, String password) {
        try {
            WifiManager w = wifi(c);
            SoftApConfiguration cur = w.getSoftApConfiguration();
            SoftApConfiguration.Builder b = cur == null ? new SoftApConfiguration.Builder() : new SoftApConfiguration.Builder(cur);
            return w.setSoftApConfiguration(b.setSsid(name)
                    .setPassphrase(password, SoftApConfiguration.SECURITY_TYPE_WPA2_PSK).build());
        } catch (RuntimeException e) {
            Log.w(TAG, "save: " + e);
            return false;
        }
    }

    /** Starts the hotspot (stock startTethering WIFI); {@code d} hears how it ended. */
    public static void start(Context c, Done d) {
        Context app = c.getApplicationContext();
        try {
            TetheringManager.TetheringRequest r = new TetheringManager.TetheringRequest.Builder(TetheringManager.TETHERING_WIFI)
                    .setShouldShowEntitlementUi(false).build();
            tethering(app).startTethering(r, app.getMainExecutor(), new TetheringManager.StartTetheringCallback() {
                @Override public void onTetheringStarted() {
                    String[] cfg = config(app);
                    d.started(cfg == null || cfg[0] == null ? "" : cfg[0]);
                }

                @Override public void onTetheringFailed(int error) {
                    Log.w(TAG, "start failed, tether error " + error);
                    d.failed();
                }
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "start: " + e);
            d.failed();
        }
    }

    /** Stops the hotspot (stock stopTethering WIFI). */
    public static void stop(Context c, Done d) {
        try {
            tethering(c).stopTethering(TetheringManager.TETHERING_WIFI);
            d.stopped();
        } catch (RuntimeException e) {
            Log.w(TAG, "stop: " + e);
            d.failed();
        }
    }

    /** Stop, wait until Android reports the hotspot stopped (at most RESTART_WAIT_MS), start. */
    private static void restart(Context c, Done d) {
        Context app = c.getApplicationContext();
        Handler h = new Handler(Looper.getMainLooper());
        BroadcastReceiver r = new BroadcastReceiver() {
            private boolean done;

            @Override public void onReceive(Context x, Intent i) {
                if (i != null && !HotspotRules.stopped(i.getIntExtra(WifiManager.EXTRA_WIFI_AP_STATE, -1))) return;
                if (done) return;
                done = true;
                h.removeCallbacksAndMessages(null);
                try {
                    app.unregisterReceiver(this);
                } catch (RuntimeException e) {
                    Log.w(TAG, "restart: " + e);
                }
                start(app, d);
            }
        };
        app.registerReceiver(r, new IntentFilter(WifiManager.WIFI_AP_STATE_CHANGED_ACTION), Context.RECEIVER_NOT_EXPORTED);
        h.postDelayed(() -> r.onReceive(app, null), RESTART_WAIT_MS);
        try {
            tethering(app).stopTethering(TetheringManager.TETHERING_WIFI);
        } catch (RuntimeException e) {
            Log.w(TAG, "restart stop: " + e);
            r.onReceive(app, null);
        }
    }
}
