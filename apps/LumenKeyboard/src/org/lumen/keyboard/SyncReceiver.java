// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.keyboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Boot, update and system language change: {@link ImeLanguages#sync} even while the keyboard's own service is
 * not running (it also syncs when the system starts it).
 */
public final class SyncReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        ImeLanguages.sync(c);
    }
}
