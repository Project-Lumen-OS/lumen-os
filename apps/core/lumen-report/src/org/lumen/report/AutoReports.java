// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.report;

import android.content.Context;
import android.os.DropBoxManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Automatic reports: looks at the DropBox entries written since the last look (IncidentScan), at the boot reason of
 * this start and at an unexpected stop of the last start (AliveWatch), and queues ONE report for the incidents
 * AutoPolicy lets through. The first look after an install only remembers the time: old history is never sent. An
 * unexpected stop goes in also when the clock went back (a power cut can reset it). Worker thread (ReportService).
 */
final class AutoReports {
    private static final String TAG = ReportConfig.TAG;
    /** SYSTEM_LAST_KMSG ends with the panic: read all of it (the entry itself is capped by DropBox). */
    private static final int KMSG_CHARS = 256 * 1024;
    private static final int HEADER_CHARS = 4096;
    private static final int SCAN_PER_TAG = 200;

    private static final ReportCollector.Progress SILENT = new ReportCollector.Progress() {
        @Override public void onStep(int step, int total) {}
        @Override public boolean isCanceled() { return false; }
        @Override public void setProcess(Process p) {}
    };

    private AutoReports() {}

    /** True when a report was queued. */
    static boolean scan(Context c) {
        AliveWatch.start(c);
        AliveWatch.awaitCheck();
        long now = System.currentTimeMillis();
        long since = ReportPrefs.scanSince(c);
        int boot = bootCount(c);
        // taken on every scan, also while not allowed: turning the switch on later never sends it
        Incident stop = AliveWatch.takeStop(c);
        boolean allowed = AutoPolicy.allowed(ReportConfig.enabled(), ReportPrefs.autoOn(c), ReportPrefs.setupDone(c));
        List<Incident> found = AutoPolicy.dropBoxScan(allowed, since, now) ? find(c, since, now, boot) : new ArrayList<>();
        if (AutoPolicy.withStop(allowed, stop)) found.add(stop);
        // also while not allowed: turning the switch on later never sends what happened before
        ReportPrefs.setScanSince(c, now);
        ReportPrefs.setBootHandled(c, boot);
        List<Incident> pick = AutoPolicy.pick(found, ReportPrefs.autoLog(c), now);
        if (pick.isEmpty()) return false;
        File dir = new File(c.getCacheDir(), "report_auto");
        try {
            deleteDir(dir);
            if (!dir.mkdirs()) throw new java.io.IOException("no dir " + dir);
            ReportKind kind = pick.get(0).kind;
            File zip = new ReportCollector(c, dir, SILENT, kind, pick).collect();
            ReportSender.enqueue(c, zip, kind);
            ReportPrefs.setAutoLog(c, AutoPolicy.record(ReportPrefs.autoLog(c), pick, now));
            Log.i(TAG, "automatic " + kind.wire + " report queued for " + pick);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "automatic report: " + t);
            return false;
        } finally {
            deleteDir(dir);
        }
    }

    private static List<Incident> find(Context c, long since, long now, int boot) {
        List<Incident> out = new ArrayList<>();
        DropBoxManager db = c.getSystemService(DropBoxManager.class);
        if (db != null) {
            for (String tag : IncidentScan.tags()) {
                try {
                    long t = since;
                    for (int n = 0; n < SCAN_PER_TAG; n++) {
                        DropBoxManager.Entry e = db.getNextEntry(tag, t);
                        if (e == null) break;
                        try {
                            t = e.getTimeMillis();
                            String text = e.getText("SYSTEM_LAST_KMSG".equals(tag) ? KMSG_CHARS : HEADER_CHARS);
                            Incident i = IncidentScan.of(tag, t, text, since, now);
                            if (i != null) out.add(i);
                        } finally {
                            e.close();
                        }
                    }
                } catch (RuntimeException e) {
                    Log.w(TAG, "dropbox " + tag + ": " + e);           // SecurityException without READ_LOGS
                }
            }
        }
        if (boot != ReportPrefs.bootHandled(c)) {
            Incident b = IncidentScan.bootReason(ReportConfig.prop("sys.boot.reason"), now - SystemClock.elapsedRealtime());
            if (b != null) out.add(b);
        }
        return out;
    }

    private static int bootCount(Context c) {
        try {
            return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        } catch (Throwable t) {
            return -1;
        }
    }

    static void deleteDir(File d) {
        if (d == null) return;
        File[] fs = d.listFiles();
        if (fs != null) for (File f : fs) if (!f.delete()) Log.w(TAG, "cannot delete " + f);
        d.delete();
    }
}
