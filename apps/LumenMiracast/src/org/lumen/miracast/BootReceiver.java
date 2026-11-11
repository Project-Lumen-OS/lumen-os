// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Starts the receiver after boot (and after an update of this app) when it is enabled.
 * Also the control entry for other Lumen apps (quick panel, Home): action
 * org.lumen.miracast.action.SET_ENABLED with boolean extra "enabled", guarded by the signature
 * permission org.lumen.miracast.permission.CONTROL (only apps signed with the same platform key).
 */
public final class BootReceiver extends BroadcastReceiver {
    static final String ACTION_SET_ENABLED = "org.lumen.miracast.action.SET_ENABLED";
    static final String EXTRA_ENABLED = "enabled";

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (ACTION_SET_ENABLED.equals(a)) {
            boolean on = i.getBooleanExtra(EXTRA_ENABLED, Prefs.enabled(c));
            Prefs.get(c).edit().putBoolean(Prefs.ENABLED, on).commit();
            Log.i(MiracastService.TAG, "set enabled " + on + " by broadcast");
        }
        try {
            MiracastService.sync(c);
        } catch (RuntimeException e) {
            Log.w(MiracastService.TAG, "start from " + a + ": " + e);
        }
    }
}
