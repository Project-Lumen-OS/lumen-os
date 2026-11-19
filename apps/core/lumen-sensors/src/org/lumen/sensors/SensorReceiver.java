// SPDX-License-Identifier: Apache-2.0
package org.lumen.sensors;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/** {@link SensorPanel#ACTION} sent by this app to itself: the remote action of Remote buttons. Not exported. */
public final class SensorReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (i == null || !SensorPanel.ACTION.equals(i.getAction())) return;
        final Context app = c.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!SensorPanel.press(app)) Log.i(SensorPanel.TAG, "no sensor card in this app");
        });
    }
}
