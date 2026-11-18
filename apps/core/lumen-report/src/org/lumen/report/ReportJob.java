// SPDX-License-Identifier: Apache-2.0
package org.lumen.report;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The one manual report, process-wide, so ReportActivity can be left and reopened while it runs and still shows the
 * result. A run starts ONLY from ReportActivity's "Send report" / "Try again" buttons, and a canceled run never
 * uploads (its own cancel flag is checked between steps, per 1024 log lines and per 16 KB sent). When the server
 * cannot be reached the zip goes into the queue (state QUEUED) and ReportService sends it later; its code then shows
 * on the consent screen. State is read and changed on the main thread; each run collects and uploads on its own
 * worker thread in its own directory (cacheDir/report/run&lt;n&gt;), which ends with the run.
 */
final class ReportJob {
    private static final String TAG = ReportConfig.TAG;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Listener {
        void onReportChanged();
    }

    /** One press of "Send report" / "Try again". */
    private static final class Run {
        final int gen;
        final File dir;
        volatile boolean canceled;
        volatile Process process;
        volatile ReportUploader uploader;
        HandlerThread worker;

        Run(int gen, File dir) {
            this.gen = gen;
            this.dir = dir;
        }
    }

    private static ReportJob sJob;

    private final List<Listener> listeners = new ArrayList<>();
    private ReportState state = ReportState.IDLE;
    private int step, steps = ReportCollector.STEPS;
    private long sent, total;
    private String code;
    private ReportFailure failure;
    /** The zip of a refused upload: "Try again" sends it again instead of collecting anew. */
    private File zip;
    private Run current;
    private int generation;

    /** Main thread. */
    static ReportJob get() {
        if (sJob == null) sJob = new ReportJob();
        return sJob;
    }

    ReportState state() { return state; }
    int step() { return step; }
    int steps() { return steps; }
    long sent() { return sent; }
    long total() { return total; }
    String code() { return code; }
    ReportFailure failure() { return failure; }
    boolean busy() { return state == ReportState.COLLECTING || state == ReportState.UPLOADING; }

    void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }

    void removeListener(Listener l) { listeners.remove(l); }

    /** "Send report" / "Try again": the user's explicit go. Main thread. */
    void start(Context ctx) {
        if (busy()) return;
        final Context app = ctx.getApplicationContext();
        final List<String> urls = ReportConfig.urls();
        if (urls.isEmpty()) {
            fail(ReportFailure.REJECTED);
            return;
        }
        final File retry = zip != null && zip.isFile() ? zip : null;
        final int gen = ++generation;
        final Run r = new Run(gen, retry != null ? retry.getParentFile() : new File(root(app), "run" + gen));
        current = r;
        failure = null;
        code = null;
        sent = total = 0;
        step = 0;
        state = retry != null ? ReportState.UPLOADING : ReportState.COLLECTING;
        notifyListeners();
        r.worker = new HandlerThread("lumen-report");
        r.worker.start();
        new Handler(r.worker.getLooper()).post(() -> run(app, urls, r, retry));
    }

    /** Back / Cancel while busy. Main thread. The run stops at its next check and deletes its files. */
    void cancel() {
        if (!busy() || current == null) return;
        Run r = current;
        current = null;
        r.canceled = true;
        Process p = r.process;
        if (p != null) p.destroy();
        ReportUploader u = r.uploader;
        if (u != null) u.abort();
        zip = null;
        state = ReportState.IDLE;
        notifyListeners();
    }

    /** The user left a finished screen (Close / Back): next time starts at the consent screen. */
    void reset() {
        if (busy()) return;
        File z = zip;
        zip = null;
        if (z != null) AutoReports.deleteDir(z.getParentFile());   // a refused run's zip kept for "Try again"
        state = ReportState.IDLE;
        code = null;
        failure = null;
        notifyListeners();
    }

    // ------------------------------------------------------------------ worker thread
    private static File root(Context app) {
        return new File(app.getCacheDir(), "report");
    }

    private void run(Context app, List<String> urls, Run r, File retry) {
        File z = retry;
        try {
            if (z == null) {
                // leftovers of earlier runs (a canceled run still stopping only loses its own files)
                File[] old = root(app).listFiles();
                if (old != null) for (File d : old) if (!d.equals(r.dir)) AutoReports.deleteDir(d);
                AutoReports.deleteDir(r.dir);
                if (!r.dir.mkdirs()) throw new IOException("no dir " + r.dir);
                ReportCollector col = new ReportCollector(app, r.dir, new ReportCollector.Progress() {
                    @Override public void onStep(int s, int n) { post(r, () -> { step = s; steps = n; notifyListeners(); }); }
                    @Override public boolean isCanceled() { return r.canceled; }
                    @Override public void setProcess(Process p) { r.process = p; }
                }, ReportKind.MANUAL, null);
                z = col.collect();
            }
            final File done = z;
            post(r, () -> { zip = done; state = ReportState.UPLOADING; total = done.length(); notifyListeners(); });
            if (r.canceled) throw new CanceledException();
            if (!online(app)) throw new ReportUploader.UploadException(ReportFailure.NO_NETWORK, "offline");
            final File up = z;
            final long[] lastPost = {0};
            String c = ReportUploader.uploadAny(urls, u -> {
                ReportUploader one = new ReportUploader();
                r.uploader = one;
                if (r.canceled) throw new CanceledException();     // cancel() may have missed the uploader
                return one.upload(u, up, ReportPrefs.installId(app), ReportConfig.prop("ro.lumen.version"),
                        ReportSender.model(), ReportKind.MANUAL, (s, t) -> {
                            long now = SystemClock.uptimeMillis();
                            if (s == t || now - lastPost[0] > 250) {
                                lastPost[0] = now;
                                post(r, () -> { sent = s; total = t; notifyListeners(); });
                            }
                            return !r.canceled;
                        });
            });
            ReportPrefs.setLast(app, ReportKind.MANUAL, c, System.currentTimeMillis());
            Log.i(TAG, "report sent: " + c + " (" + z.length() + " bytes)");
            AutoReports.deleteDir(r.dir);
            post(r, () -> {
                code = c;
                zip = null;
                state = ReportState.DONE;
                current = null;
                notifyListeners();
            });
        } catch (Throwable t) {
            if (r.canceled) {
                Log.i(TAG, "report canceled");
                AutoReports.deleteDir(r.dir);
                return;
            }
            final ReportFailure f = t instanceof ReportUploader.UploadException
                    ? ((ReportUploader.UploadException) t).failure
                    : (z == null ? ReportFailure.COLLECT : ReportFailure.NETWORK);
            Log.w(TAG, "report failed (" + f + "): " + t);
            if (z != null && f.retry() && queue(app, z)) {
                AutoReports.deleteDir(r.dir);
                post(r, () -> {
                    zip = null;
                    current = null;
                    state = ReportState.QUEUED;
                    notifyListeners();
                });
                return;
            }
            final File keep = f == ReportFailure.COLLECT ? null : z;
            if (keep == null) {
                AutoReports.deleteDir(r.dir);
            } else {
                File[] fs = r.dir.listFiles();                 // section files are no longer needed
                if (fs != null) for (File x : fs) if (!x.equals(keep)) x.delete();
            }
            post(r, () -> {
                zip = keep;
                current = null;
                fail(f);
            });
        } finally {
            r.uploader = null;
            r.process = null;
            r.worker.quitSafely();                           // the thread ends after this task
        }
    }

    /** The zip into the queue and a send job for when a network is up. */
    private static boolean queue(Context app, File z) {
        try {
            ReportSender.enqueue(app, z, ReportKind.MANUAL);
            ReportService.scheduleSend(app, 0);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "queue: " + t);
            return false;
        }
    }

    private static boolean online(Context app) {
        try {
            ConnectivityManager cm = app.getSystemService(ConnectivityManager.class);
            Network n = cm == null ? null : cm.getActiveNetwork();
            NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
            return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Throwable t) {
            return true;                                 // let the upload itself find out
        }
    }

    // ------------------------------------------------------------------ main thread helpers
    /** Runs {@code u} on the main thread if {@code r} is still the current run (not canceled, not replaced). */
    private void post(Run r, Runnable u) {
        MAIN.post(() -> {
            if (r == current) u.run();
        });
    }

    private void fail(ReportFailure f) {
        failure = f;
        state = ReportState.FAILED;
        notifyListeners();
    }

    private void notifyListeners() {
        for (Listener l : new ArrayList<>(listeners)) {
            try {
                l.onReportChanged();
            } catch (Throwable t) {
                Log.w(TAG, "listener: " + t);
            }
        }
    }
}
