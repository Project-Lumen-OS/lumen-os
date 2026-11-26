// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.setup;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

/**
 * Lumen OS 1.0 SetupBridge (setup/bridge_api.md): Lumen Setup (org.lumen.setup) and Lumen Home
 * ask the persistent projector app to do hardware work. This app stays the only one that talks to the
 * HAL and to Bluetooth bonding; every existing gate and whitelist stays where it is (the family's HAL gates and
 * call list, RemotePairing).
 *
 * <b>Transport</b>: authority {@value #AUTHORITY}, exported, permission {@value #PERMISSION}
 * (signature, defined here); only {@link #call} is used and it checks the permission itself (the
 * framework enforces a provider's permission only for query / insert / update / delete). The reply always
 * has {@code ok}; on failure {@code err} = not_ready | busy | feature_off | unsupported | denied.
 * The family's hardware methods are its subclass (hardware): SetupBridgeProvider for the gmpf families, posted to
 * their hal / kst threads, user actions on the main thread (as from a key) while the binder thread waits briefly;
 * X50SetupBridge of the X50 family's host. Log: one line per call, the subclass's tag,
 * {@code <caller> <method> -> <ok|err> <ms>} (the 1-2 Hz state polls only when they fail).
 *
 * <b>Methods</b>: ping, remote_state, hal_state, af_run, kst_auto, kst_fit, kst_manual, focus_manual,
 * toggles_get, toggle_set, set_device_name, set_launcher, feature. Deliberately NOT offered: any generic
 * HAL code / transact, calibration, factory or gyro calls, manual focus motor runs from another
 * process (manual focus stays inside ManualFocusActivity with its watchdog), Bluetooth bond / unbond.
 */
public abstract class SetupBridge extends ContentProvider {
    public static final String AUTHORITY = "org.lumen.projector.setupbridge";
    public static final String PERMISSION = "org.lumen.projector.permission.SETUP_BRIDGE";
    public static final int API_VERSION = 1;

    /** Features the first-run setup may ask about; the ALICE track flips "alice" later. */
    private static final String[][] FEATURES = {{"alice", "false"}};

    private final String tag;

    protected SetupBridge(String tag) {
        this.tag = tag;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context ctx = getContext();
        if (ctx == null) return null;
        if (ctx.checkCallingPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("needs " + PERMISSION);
        }
        final long t0 = SystemClock.uptimeMillis();
        String caller;
        try {
            caller = getCallingPackage();
        } catch (Throwable t) {
            caller = "?";
        }
        Bundle r;
        try {
            r = dispatch(ctx.getApplicationContext(), method == null ? "" : method, arg, extras == null ? new Bundle() : extras);
        } catch (Throwable t) {
            Log.w(tag, caller + " " + method + ": " + t);
            r = err("not_ready");
        }
        boolean ok = r.getBoolean("ok", false);
        boolean poll = "hal_state".equals(method) || "remote_state".equals(method) || "ping".equals(method);
        if (!poll || !ok) {
            Log.i(tag, caller + " " + method + " -> " + (ok ? "ok" : r.getString("err", "?")) + " "
                    + (SystemClock.uptimeMillis() - t0));
        }
        return r;
    }

    private Bundle dispatch(Context app, String method, String arg, Bundle x) throws Exception {
        Bundle r = hardware(app, method, arg, x);
        if (r != null) return r;
        switch (method) {
            case "ping": {
                Bundle b = ok();
                b.putInt("version", API_VERSION);
                try {
                    PackageInfo pi = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
                    b.putString("projectorVersion", pi.versionName);
                    b.putLong("projectorVersionCode", pi.getLongVersionCode());
                } catch (Throwable ignored) { }
                pingExtras(b);
                return b;
            }
            case "set_device_name":
                return setDeviceName(app, x.getString("name"));
            case "feature": {
                Bundle b = ok();
                boolean on = false;
                for (String[] f : FEATURES) if (f[0].equals(arg)) on = Boolean.parseBoolean(f[1]);
                b.putBoolean("enabled", on);
                return b;
            }
            default:
                return err("unsupported");
        }
    }

    /**
     * The family's methods (remote_state, hal_state, af_run, kst_auto, kst_fit, kst_manual, focus_manual, toggles_get,
     * toggle_set, set_launcher): the reply, or null for a method this family leaves to the shared part.
     */
    /** More fields of the ping reply (the family's device names). */
    protected void pingExtras(Bundle b) {}

    protected abstract Bundle hardware(Context app, String method, String arg, Bundle x) throws Exception;

    /**
     * Settings.Global device_name (read by Chromecast built-in and the framework; Z9xAirPlay 1.0 follows it
     * through a ContentObserver unless the user saved an AirPlay-only name) and the Bluetooth adapter name
     * (BLUETOOTH_CONNECT, held). 1..32 printable characters after trimming.
     */
    private Bundle setDeviceName(Context app, String raw) {
        if (raw == null) return err("denied");
        String n = raw.replaceAll("[\\p{Cntrl}]", "").trim().replaceAll("\\s+", " ");
        if (n.isEmpty() || n.length() > 32) return err("denied");
        try {
            Settings.Global.putString(app.getContentResolver(), Settings.Global.DEVICE_NAME, n);
        } catch (Throwable t) {
            Log.w(tag, "device_name: " + t);
            return err("not_ready");
        }
        try {
            BluetoothManager bm = app.getSystemService(BluetoothManager.class);
            BluetoothAdapter a = bm == null ? null : bm.getAdapter();
            if (a != null && a.isEnabled()) a.setName(n);
        } catch (Throwable t) {
            Log.w(tag, "bluetooth name: " + t);       // device_name is set; BT follows at its next start
        }
        Log.i(tag, "device name set (" + n.length() + " chars)");
        return ok();
    }

    // ------------------------------------------------------------------ replies

    protected static Bundle ok() {
        Bundle b = new Bundle();
        b.putBoolean("ok", true);
        return b;
    }

    protected static Bundle err(String e) {
        Bundle b = new Bundle();
        b.putBoolean("ok", false);
        b.putString("err", e);
        return b;
    }

    // ------------------------------------------------------------------ unused ContentProvider API
    // ------------------------------------------------------------------ unused ContentProvider API

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
