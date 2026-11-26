// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hdmiports.fake;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Changes one fake port and announces it as the HDMI app does:
 * {@code am broadcast -n org.lumen.hdmiports.fake/.FakePortsReceiver --ei port 3 --es state on [--es title NAME]}
 * (title "" drops the CEC name).
 */
public class FakePortsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        int port = i.getIntExtra("port", 0);
        if (port < 1 || port > 3) return;
        FakePortsProvider.set(port, i.getStringExtra("state"), i.getStringExtra("title"));
        Log.i(FakePortsProvider.TAG, "changed port " + port);
        c.getContentResolver().notifyChange(FakePortsProvider.PORTS_URI, null);
    }
}
