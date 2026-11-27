// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

/**
 * The real power off and restart of every Lumen projector: Android's ShutdownThread with the system menu's reason
 * (sys.powerctl "shutdown,userrequested" / "reboot,userrequested"), the call of Android TV Settings' Shutdown
 * (TvSettings ShutdownConfirmFragment, pm.shutdown) and of the stock power menu's own real power off (待机 /
 * REQUEST_SHUTDOWN). The stock 关机 item injects the power key, which the XGIMI framework turns into STR (goToSleep
 * reason 100, getShutdownMode): the user wants Power off to really power off, so no family sends the power key for
 * it. Every family's power menu Power off and Restart and the X50 family's stock power off of a sleep (SleepRule) end
 * here. Blocks until the system goes down: call it on a worker thread. Needs REBOOT (platform signature).
 */
public final class Halt {
    static final String TAG = "LumenHalt";

    private Halt() {}

    /** Android's power off; {@code why} names the caller in the log. */
    public static void powerOff(Context c, String why) {
        Log.i(TAG, "power off: Android shutdown, userrequested (" + why + ")");
        c.getSystemService(PowerManager.class).shutdown(false, PowerManager.SHUTDOWN_USER_REQUESTED, false);
    }

    /** Android's restart; {@code why} names the caller in the log. */
    public static void restart(Context c, String why) {
        Log.i(TAG, "restart: Android reboot, userrequested (" + why + ")");
        c.getSystemService(PowerManager.class).reboot(PowerManager.SHUTDOWN_USER_REQUESTED);
    }
}
