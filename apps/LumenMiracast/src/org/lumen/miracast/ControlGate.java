// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * Other Lumen apps find the receiver by its control provider (content://org.lumen.miracast): the projector's quick menu
 * builds its screen mirroring tile only when that provider resolves (lumen-menu QpdMirror.installed), and the projector
 * app tells Lumen Home to hide its tile without it. So on a unit without Wi-Fi Direct the provider is switched off, and
 * on again once Wi-Fi Direct is proven (core/P2pCapability.providerOn). PackageManager keeps the state over reboots, so
 * a unit decided once shows the right menu from the first second of the next boot.
 */
final class ControlGate {
    private ControlGate() {}

    /** on = true / false switches the provider, null leaves it as it is. */
    static void apply(Context c, Boolean on) {
        if (on == null) return;
        try {
            PackageManager pm = c.getPackageManager();
            ComponentName cn = new ComponentName(c, StateProvider.class);
            int now = pm.getComponentEnabledSetting(cn);
            boolean isOn = now != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    && now != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
            if (isOn == on) return;
            pm.setComponentEnabledSetting(cn, on ? PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            Log.i(MiracastService.TAG, "control provider " + (on ? "on" : "off") + ": screen mirroring tile "
                    + (on ? "shown" : "hidden"));
        } catch (RuntimeException e) {
            Log.w(MiracastService.TAG, "control provider switch: " + e);
        }
    }
}
