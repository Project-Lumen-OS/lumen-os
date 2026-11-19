// SPDX-License-Identifier: Apache-2.0
package org.lumen.signal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.lumen.remote.keys.KeyActions;

/**
 * {@link SignalPanel#ACTION} sent by this app to itself: the remote's signal action (lumen-remote KeyActions, also
 * the Shortcut key while the user gave it nothing else) and the round signal button of the menu. Not exported.
 */
public final class SignalReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (i == null || !SignalPanel.ACTION.equals(i.getAction())) return;
        final Context app = c.getApplicationContext();
        final boolean byKey = i.getBooleanExtra(KeyActions.EXTRA_BY_KEY, false);
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!SignalPanel.press(app, byKey)) Log.i(SignalPanel.TAG, "no signal panel on this model");
        });
    }
}
