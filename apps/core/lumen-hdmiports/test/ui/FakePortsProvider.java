// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hdmiports.fake;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.util.Log;

/**
 * A stand-in of org.lumen.tvinput's hdmi_state provider for emulator walks (test APK of test/ui/build_fake.sh, never
 * an image): three ports as on Slava's X50 (an eARC soundbar on HDMI 1, a CEC box on HDMI 2, HDMI 3 empty), the HDMI
 * options, and a change announcement on PORTS_URI for every FakePortsReceiver broadcast. Every call is logged
 * (tag FakePorts), so a walk can count who reads the ports and when.
 */
public class FakePortsProvider extends ContentProvider {
    static final String TAG = "FakePorts";
    static final Uri PORTS_URI = Uri.parse("content://org.lumen.tvinput.hdmistate/ports");
    static final String[] LABEL = {null, "HDMI 1 (ARC)", "HDMI 2", "HDMI 3"};
    static final String[] KIND = {null, "audio", "player", "other"};
    static final String[] STATE = {null, "on", "on", "disconnected"};
    static final String[] TITLE = {null, null, "Box R 4K Plus", null};
    static final Bundle PREFS = new Bundle();

    static {
        PREFS.putBoolean("auto_switch_on_plug", false);
        PREFS.putBoolean("return_home_on_unplug", true);
        PREFS.putInt("boot_source", 0);
    }

    @Override public boolean onCreate() { return true; }

    @Override public synchronized Bundle call(String method, String arg, Bundle extras) {
        Log.i(TAG, "call " + method + " " + arg + " uid=" + Binder.getCallingUid());
        Bundle b = new Bundle();
        switch (method) {
            case "hdmi_state":
                b.putIntArray("port_ids", new int[]{1, 2, 3});
                for (int p = 1; p <= 3; p++) {
                    Bundle q = new Bundle();
                    q.putString("input_id", "org.lumen.tvinput/.HdmiInputService/HW" + p);
                    q.putString("label", LABEL[p]);
                    q.putString("title", TITLE[p]);
                    q.putString("kind", KIND[p]);
                    q.putString("device_state", STATE[p]);
                    q.putInt("input_state", "disconnected".equals(STATE[p]) ? 2 : 0);
                    b.putBundle("port_" + p, q);
                }
                b.putIntArray("ports", new int[0]);
                return b;
            case "get_hdmi_prefs":
                return new Bundle(PREFS);
            case "set_hdmi_pref":
                Object v = extras == null ? null : extras.get("value");
                if (v instanceof Boolean) PREFS.putBoolean(arg, (Boolean) v);
                else if (v instanceof Integer) PREFS.putInt(arg, (Integer) v);
                Log.i(TAG, "pref " + arg + "=" + v);
                b.putBoolean("ok", v != null);
                return b;
            default:
                return null;
        }
    }

    static synchronized void set(int port, String state, String title) {
        if (state != null) STATE[port] = state;
        if (title != null) TITLE[port] = title.isEmpty() ? null : title;
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
