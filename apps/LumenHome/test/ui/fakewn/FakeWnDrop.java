// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.fakewn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.util.Log;

/**
 * Drops one of our Watch Next rows: am broadcast -n org.lumen.home.fakewn/.FakeWnDrop --el id N (no window, Home stays
 * resumed). On a projector Home holds ACCESS_ALL_EPG_DATA and clears the row's browsable flag on Delete; the emulator's
 * test build lacks that privileged permission, so the walk drops the row this way. Either way TvProvider tells Home
 * that Watch Next changed.
 */
public class FakeWnDrop extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        long id = i.getLongExtra("id", -1);
        int n = id < 0 ? 0 : c.getContentResolver().delete(TvContract.buildWatchNextProgramUri(id), null, null);
        Log.i("fakewn", "dropped id=" + id + " rows=" + n);
    }
}
