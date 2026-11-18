// SPDX-License-Identifier: Apache-2.0
package org.lumen.audio;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * BtA2dp: the A2DP devices the Bluetooth stack has CONNECTED (not only the active one AudioManager lists) and making one
 * active (A2dpPick). BluetoothAdapter.setActiveDevice / getActiveDevices are system APIs (BLUETOOTH_CONNECT +
 * BLUETOOTH_PRIVILEGED, held by every projector app: platform signed, system uid). The listeners hear connection and
 * active device changes, so the output choice applies again. Without the profile proxy (Bluetooth off) the lists are
 * empty and the choices behave as before. Log tag LumenBtA2dp.
 */
public final class BtA2dp {
    private static final String TAG = "LumenBtA2dp";
    private static volatile BluetoothA2dp sProxy;
    private static boolean sInstalled;
    private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private BtA2dp() {}

    /** Once per process (any thread); onChange runs on the main thread after a connection or active device change. */
    public static synchronized void install(Context ctx, Runnable onChange) {
        if (onChange != null) LISTENERS.addIfAbsent(onChange);
        if (sInstalled) return;
        sInstalled = true;
        Context app = ctx.getApplicationContext();
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a != null) a.getProfileProxy(app, new BluetoothProfile.ServiceListener() {
                @Override public void onServiceConnected(int profile, BluetoothProfile p) {
                    sProxy = (BluetoothA2dp) p;
                    changed();
                }
                @Override public void onServiceDisconnected(int profile) { sProxy = null; }
            }, BluetoothProfile.A2DP);
            IntentFilter f = new IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
            f.addAction(BluetoothA2dp.ACTION_ACTIVE_DEVICE_CHANGED);
            app.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) { changed(); }
            }, f, Context.RECEIVER_EXPORTED);
        } catch (Throwable t) {
            Log.w(TAG, "install: " + t);
        }
    }

    private static void changed() {
        for (Runnable r : LISTENERS) {
            try { r.run(); } catch (Throwable t) { Log.w(TAG, "listener: " + t); }
        }
    }

    /** Addresses of the connected A2DP devices (stack order), empty when unknown. */
    public static List<String> connected() {
        List<String> out = new ArrayList<>();
        try {
            BluetoothA2dp p = sProxy;
            if (p != null) for (BluetoothDevice d : p.getConnectedDevices()) out.add(d.getAddress());
        } catch (Throwable t) {
            Log.w(TAG, "connected: " + t);
        }
        return out;
    }

    /** Addresses of the active A2DP devices, empty when none or unknown. */
    public static List<String> active() {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null) return Collections.emptyList();
            List<String> out = new ArrayList<>();
            for (BluetoothDevice d : a.getActiveDevices(BluetoothProfile.A2DP)) if (d != null) out.add(d.getAddress());
            return out;
        } catch (Throwable t) {
            Log.w(TAG, "active: " + t);
            return Collections.emptyList();
        }
    }

    /** The device's name for an output row, null when unknown. */
    public static String name(String addr) {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null || addr == null || !BluetoothAdapter.checkBluetoothAddress(addr)) return null;
            BluetoothDevice d = a.getRemoteDevice(addr);
            String n = d.getAlias();
            return n != null && !n.isEmpty() ? n : d.getName();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * A normal connect of a paired device (the output menu's Connect on an offline row): BluetoothDevice.connect, the
     * system call Settings makes for a paired device (A2DP and every other profile the device allows). Pairing is never
     * touched. True when the stack took the request; the connection change then reaches the listeners.
     */
    public static boolean connect(String addr, String why) {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null || addr == null || !BluetoothAdapter.checkBluetoothAddress(addr)) return false;
            BluetoothDevice d = a.getRemoteDevice(addr);
            if (d.getBondState() != BluetoothDevice.BOND_BONDED) {
                Log.i(TAG, why + ": " + addr + " is not paired, no connect");
                return false;
            }
            int r = d.connect();
            Log.i(TAG, why + ": connect " + addr + " -> " + r);
            return r == android.bluetooth.BluetoothStatusCodes.SUCCESS;
        } catch (Throwable t) {
            Log.w(TAG, why + ": connect " + addr + ": " + t);
            return false;
        }
    }

    /** The output of a Bluetooth choice (A2dpPick.device over the connected devices), null when none is connected. */
    public static String pick(String stored) {
        return A2dpPick.device(stored, connected());
    }

    /** Makes the picked device the active A2DP device when it is not (A2dpPick.activate); true when it was asked. */
    public static boolean activateIfNeeded(String pick, String why) {
        if (!A2dpPick.activate(pick, active())) return false;
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            boolean ok = a != null && a.setActiveDevice(a.getRemoteDevice(pick), BluetoothAdapter.ACTIVE_DEVICE_AUDIO);
            Log.i(TAG, why + ": Bluetooth " + pick + " connected but not active, made active" + (ok ? "" : " (REFUSED)"));
            return ok;
        } catch (Throwable t) {
            Log.w(TAG, why + ": setActiveDevice " + pick + ": " + t);
            return false;
        }
    }
}
