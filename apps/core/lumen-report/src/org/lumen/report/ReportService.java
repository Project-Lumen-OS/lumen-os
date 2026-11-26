// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.report;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

/**
 * The background side of problem reports: SCAN looks for new incidents (after boot, after a DropBox entry of a
 * watched tag, and every 6 hours) and queues an automatic report, SEND empties the queue whenever a network is up
 * and comes back when the next queued report is due. The host app declares this service with
 * android.permission.BIND_JOB_SERVICE (README).
 */
public final class ReportService extends JobService {
    private static final String TAG = ReportConfig.TAG;
    /** 'LR' ids: job ids are per uid, and a host may share the system uid with other apps. */
    static final int JOB_SCAN = 0x4c520001, JOB_SEND = 0x4c520002, JOB_PERIODIC = 0x4c520003;
    private static final long PERIOD_MS = 6 * 3600_000L, FLEX_MS = 2 * 3600_000L, SCAN_WINDOW_MS = 10 * 60_000L;

    static void scheduleScan(Context c, long delayMs) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null || !ReportConfig.enabled()) return;
        ComponentName cn = new ComponentName(c, ReportService.class);
        js.schedule(new JobInfo.Builder(JOB_SCAN, cn).setMinimumLatency(delayMs)
                .setOverrideDeadline(delayMs + SCAN_WINDOW_MS).build());
        if (js.getPendingJob(JOB_PERIODIC) == null) {
            js.schedule(new JobInfo.Builder(JOB_PERIODIC, cn).setPeriodic(PERIOD_MS, FLEX_MS).setPersisted(true).build());
        }
    }

    static void scheduleSend(Context c, long delayMs) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null || !ReportConfig.enabled()) return;
        js.schedule(new JobInfo.Builder(JOB_SEND, new ComponentName(c, ReportService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(Math.max(0, delayMs))
                .setBackoffCriteria(60_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL).setPersisted(true).build());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        final Context app = getApplicationContext();
        final int id = params.getJobId();
        ReportBoot.listen(app);
        AliveWatch.start(app);
        new Thread(() -> {
            long next = -1;
            try {
                if (id == JOB_SEND) next = ReportSender.sendDue(app);
                else if (AutoReports.scan(app) || ReportSender.queued(app) > 0) next = 0;
            } catch (Throwable t) {
                Log.w(TAG, "job " + id + ": " + t);
            } finally {
                jobFinished(params, false);
            }
            if (next >= 0) scheduleSend(app, next);
        }, "lumen-report-job").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
