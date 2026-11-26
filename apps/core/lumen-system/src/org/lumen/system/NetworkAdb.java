// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.system;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

/**
 * ADB over network, the switch of LineageOS TV's developer options: it sets the non-persistent service.adb.tcp.port
 * (5555 on, -1 off) and the image remembers the choice across restarts (overlay z9x_adb_net.rc). adbd listens only
 * with USB debugging on and still asks for the computer's key on screen.
 */
public final class NetworkAdb {
    static final String PORT_PROP = "service.adb.tcp.port", ON = "5555", OFF = "-1";
    private static final String TAG = "LumenSystem";

    private NetworkAdb() { }

    public static boolean on() { return ON.equals(Props.get(PORT_PROP)); }

    /** The switch works only with USB debugging on (as Android's developer options grey it out). */
    public static boolean usable(Context c) {
        return Settings.Global.getInt(c.getContentResolver(), Settings.Global.ADB_ENABLED, 0) != 0;
    }

    /** Sets the port, then reads it back. */
    public static boolean set(boolean on) {
        if (!Props.set(PORT_PROP, on ? ON : OFF)) Log.w(TAG, "network adb not set");
        return on() == on;
    }
}
