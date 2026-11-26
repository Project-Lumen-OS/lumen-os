// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hdmiports;

import android.content.Context;
import android.database.ContentObserver;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The reader of org.lumen.tvinput's HDMI port states and HDMI options (its hdmi_state provider, permission
 * org.lumen.tvinput.permission.HDMI_STATE). Every app that shows or uses a port's state reads it here.
 */
public final class HdmiPorts {
    private static final String TAG = "LumenHdmiPorts";
    public static final String AUTHORITY = "org.lumen.tvinput.hdmistate";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY);
    /** Announced by the HDMI app on every port change (ContentResolver.notifyChange). */
    public static final Uri PORTS_URI = Uri.parse("content://" + AUTHORITY + "/ports");

    /** The HDMI options of the HDMI app (get_hdmi_prefs / set_hdmi_pref). */
    public static final String AUTO_SWITCH = "auto_switch_on_plug", RETURN_HOME = "return_home_on_unplug",
            BOOT_SOURCE = "boot_source";
    /** {@link #BOOT_SOURCE}: the home screen, else an HDMI port number. */
    public static final int BOOT_HOME = 0;

    private HdmiPorts() {}

    /** Every HDMI port of the HDMI app, by port number; null when it does not answer. Worker thread (binder). */
    public static List<HdmiPort> read(Context c) {
        Bundle b = call(c, "hdmi_state", null, null);
        return b == null ? null : parse(b);
    }

    /** The ports of a hdmi_state bundle (only the ones with a state: an older HDMI app reports none). */
    public static List<HdmiPort> parse(Bundle b) {
        List<HdmiPort> out = new ArrayList<>();
        int[] ids = b.getIntArray("port_ids");
        if (ids == null) return out;
        for (int id : ids) {
            Bundle p = b.getBundle("port_" + id);
            if (p == null || p.getString("device_state") == null) continue;
            out.add(new HdmiPort(id, p.getString("input_id"), p.getString("child_input_id"), p.getString("label"),
                    trimmed(p.getString("title")), p.getString("kind"), HdmiPort.state(p.getString("device_state")),
                    p.getInt("input_state", TvInputManager.INPUT_STATE_DISCONNECTED), p.getInt("accent", 0)));
        }
        Collections.sort(out, (x, y) -> Integer.compare(x.port, y.port));
        return out;
    }

    /** Ports whose session holds the HDMI hardware now ("ports"), empty when none or unknown. Worker thread. */
    public static int[] open(Context c) {
        Bundle b = call(c, "hdmi_state", null, null);
        int[] p = b == null ? null : b.getIntArray("ports");
        return p != null ? p : new int[0];
    }

    /** The HDMI options ({@link #AUTO_SWITCH}, {@link #RETURN_HOME}: booleans, {@link #BOOT_SOURCE}: int), or null. */
    public static Bundle prefs(Context c) {
        return call(c, "get_hdmi_prefs", null, null);
    }

    /** Writes one HDMI option; true when the HDMI app took it. Worker thread. */
    public static boolean setPref(Context c, String key, boolean on) {
        Bundle v = new Bundle();
        v.putBoolean("value", on);
        return ok(call(c, "set_hdmi_pref", key, v));
    }

    public static boolean setBootSource(Context c, int port) {
        Bundle v = new Bundle();
        v.putInt("value", Math.max(BOOT_HOME, port));
        return ok(call(c, "set_hdmi_pref", BOOT_SOURCE, v));
    }

    private static boolean ok(Bundle r) {
        return r != null && r.getBoolean("ok", false);
    }

    private static Bundle call(Context c, String method, String arg, Bundle extras) {
        try {
            if (c.getPackageManager().resolveContentProvider(AUTHORITY, 0) == null) return null;
            return c.getContentResolver().call(URI, method, arg, extras);
        } catch (Throwable t) {
            Log.w(TAG, method + ": " + t);
            return null;
        }
    }

    private static String trimmed(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    /** A running watch of the port states; {@link #close} ends it. */
    public interface Watch {
        void close();
    }

    /**
     * Runs {@code changed} on {@code h} whenever a port may have changed: the HDMI app's announcement and the
     * framework's input callbacks (a port added, removed, its +5V state, a CEC device's input). Until close().
     */
    public static Watch watch(Context ctx, Handler h, Runnable changed) {
        final Context c = ctx.getApplicationContext();
        final ContentObserver obs = new ContentObserver(h) {
            @Override public void onChange(boolean self) { changed.run(); }
        };
        final TvInputManager.TvInputCallback cb = new TvInputManager.TvInputCallback() {
            @Override public void onInputStateChanged(String id, int state) { changed.run(); }
            @Override public void onInputAdded(String id) { changed.run(); }
            @Override public void onInputRemoved(String id) { changed.run(); }
            @Override public void onInputUpdated(String id) { changed.run(); }
        };
        final TvInputManager tim = c.getSystemService(TvInputManager.class);
        try {
            c.getContentResolver().registerContentObserver(PORTS_URI, false, obs);
        } catch (Throwable t) {
            Log.w(TAG, "ports observer: " + t);
        }
        try {
            if (tim != null) tim.registerCallback(cb, h);
        } catch (Throwable t) {
            Log.w(TAG, "input callback: " + t);
        }
        return () -> {
            try {
                c.getContentResolver().unregisterContentObserver(obs);
            } catch (Throwable ignored) {
            }
            try {
                if (tim != null) tim.unregisterCallback(cb);
            } catch (Throwable ignored) {
            }
        };
    }
}
