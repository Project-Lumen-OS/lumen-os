// SPDX-License-Identifier: Apache-2.0
package org.lumen.stats;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/**
 * The statistics runs of an app without an updater: one 30 s to 15 min after boot and one a day, each with a network
 * (Ping.due keeps the once per start / once a day rule). The host calls schedule at BOOT_COMPLETED and declares this
 * service with android.permission.BIND_JOB_SERVICE.
 */
public final class StatsJob extends JobService {
    /** 'LS' ids: job ids are per uid, and a host may share the system uid with other apps. */
    static final int JOB_BOOT = 0x4c530001, JOB_DAILY = 0x4c530002;

    public static void schedule(Context c) {
        if (!Ping.configured()) return;
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) return;
        ComponentName cn = new ComponentName(c, StatsJob.class);
        js.schedule(new JobInfo.Builder(JOB_BOOT, cn).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(30 * 1000L).setOverrideDeadline(15 * 60 * 1000L).build());
        if (js.getPendingJob(JOB_DAILY) == null) {
            js.schedule(new JobInfo.Builder(JOB_DAILY, cn).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(24 * 3600 * 1000L, 6 * 3600 * 1000L).setPersisted(true).build());
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        StatsClient.maybeSend(getApplicationContext(), PrefsState.get(this), Device.release(), false);   // own thread
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) { return false; }
}
