// SPDX-License-Identifier: Apache-2.0
package org.lumen.report;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.DropBoxManager;
import android.util.Log;

/**
 * Starts the background side: after boot a scan (1 minute later, so the start itself settles) and a send of what
 * waits in the queue; after a DropBox entry of a watched tag a scan 20 seconds later (the log around the crash is
 * then complete, and a burst of entries gives one scan). The host app declares it for BOOT_COMPLETED (README).
 * Android never hands DROPBOX_ENTRY_ADDED to a receiver of the manifest ("Background execution not allowed", seen
 * on X50 1.0.10-beta5), so listen() registers one in the running process: after boot and on every job of
 * ReportService, so a restarted process listens again. Every host app is persistent, the process stays.
 */
public final class ReportBoot extends BroadcastReceiver {
    static final long BOOT_SCAN_MS = 60_000L, BOOT_SEND_MS = 30_000L, INCIDENT_SCAN_MS = 20_000L;
    private static boolean sListening;

    @Override
    public void onReceive(Context c, Intent i) {
        if (i == null || !ReportConfig.enabled()) return;
        if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) {
            AliveWatch.start(c);
            listen(c);
            ReportService.scheduleScan(c, BOOT_SCAN_MS);
            ReportService.scheduleSend(c, BOOT_SEND_MS);
        } else if (DropBoxManager.ACTION_DROPBOX_ENTRY_ADDED.equals(i.getAction())
                && IncidentScan.kindOf(i.getStringExtra(DropBoxManager.EXTRA_TAG)) != null) {
            ReportService.scheduleScan(c, INCIDENT_SCAN_MS);
        }
    }

    /** Once per process: DropBox entries reach this process while it runs. Any thread. */
    static void listen(Context c) {
        synchronized (ReportBoot.class) {
            if (sListening) return;
            sListening = true;
        }
        try {
            c.getApplicationContext().registerReceiver(new ReportBoot(),
                    new IntentFilter(DropBoxManager.ACTION_DROPBOX_ENTRY_ADDED), Context.RECEIVER_NOT_EXPORTED);
        } catch (RuntimeException e) {
            Log.w(ReportConfig.TAG, "dropbox listener: " + e);
            synchronized (ReportBoot.class) {
                sListening = false;
            }
        }
    }
}
