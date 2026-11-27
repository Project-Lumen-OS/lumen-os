// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.updater;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONException;
import org.lumen.download.HttpError;
import org.lumen.download.ParallelDownload;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Foreground service (dataSync) for the two long jobs: download (parallel range connections, resumable,
 * sha256-checked: PackageDownload, ParallelDownload) and install (update_engine writes the other slot). Background
 * priority.
 * Stops itself when idle, so the app keeps 0 MB resident.
 */
public final class UpdateService extends Service implements Engine.Listener, ServiceHost {
    public static final String ACTION_DOWNLOAD = "org.lumen.updater.DOWNLOAD";
    public static final String ACTION_INSTALL = "org.lumen.updater.INSTALL";
    public static final String ACTION_PAUSE = "org.lumen.updater.PAUSE";
    public static final String ACTION_CANCEL = "org.lumen.updater.CANCEL";
    public static final String ACTION_REVERT = "org.lumen.updater.REVERT";
    public static final String ACTION_WATCH = "org.lumen.updater.WATCH";
    /** Test: preflight + payload metadata signature + snapshot space, never applies. */
    public static final String ACTION_VERIFY_ONLY = "org.lumen.updater.VERIFY_ONLY";
    /** extra: continue with the install after the download (the user pressed "Download and install") */
    public static final String EXTRA_THEN_INSTALL = "then_install";

    static final int NOTIF_PROGRESS = 1, NOTIF_RESULT = 2;

    private final JobRunner jobs = new JobRunner();
    private PowerManager.WakeLock wake;
    private long lastNotif;

    public static void start(Context c, String action, boolean thenInstall) {
        if (Ota.T.dsuHandover && Dsu.running()) {   // a DSU try on the stock system: never any update work
            Log.i(Ota.TAG, "DSU try: no update work (" + action + ")");
            return;
        }
        c.startForegroundService(new Intent(c, UpdateService.class).setAction(action)
                .putExtra(EXTRA_THEN_INSTALL, thenInstall));
    }

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        channel(this);
        InstallWay.current().listen(this, true);
    }

    @Override
    public void onDestroy() {
        InstallWay.current().listen(this, false);
        releaseWake();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent != null && intent.getAction() != null ? intent.getAction() : ACTION_WATCH;
        startForeground(NOTIF_PROGRESS, progressNotification(getString(R.string.inst_preparing), 0, true),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        Store st = Store.get(this);
        switch (a) {
            case ACTION_PAUSE:
                jobs.requestStop();
                if (st.state() == UpdateState.DOWNLOADING) st.setState(UpdateState.PAUSED);
                stopIfIdle();
                break;
            case ACTION_CANCEL:
                jobs.requestStop();
                if (st.state() == UpdateState.INSTALLING) InstallWay.current().cancel();
                runWorker(ServiceJob.OTHER, () -> {
                    // 2026-11-04 (M2): Cancel just as update_engine reached NEED_REBOOT: the cancel above failed
                    // quietly and the slot switch is staged; undo it, or the next restart installs it unasked
                    try {
                        if (InstallWay.current().status() == Ota.UE_NEED_REBOOT) {
                            Log.i(Ota.TAG, "cancel: update_engine already finished, reverting the staged slot");
                            InstallWay.current().revertReady();
                            st.clearPending();
                        }
                    } catch (Throwable t) {
                        Log.w(Ota.TAG, "cancel revert: " + t);
                    }
                    deleteFiles(this);
                    st.setState(UpdateState.AVAILABLE);
                    Ota.setBusy(this, false, "idle");
                    stopIfIdle();
                });
                break;
            case ACTION_REVERT:
                runWorker(ServiceJob.OTHER, () -> {
                    InstallWay.current().revertReady();
                    st.clearPending();
                    st.setState(UpdateState.DOWNLOADED);
                    cancelNotif(NOTIF_RESULT);
                    stopIfIdle();
                });
                break;
            case ACTION_DOWNLOAD: {
                if (Checker.dropStaleOffer(this, "download")) {
                    stopIfIdle();
                    break;
                }
                boolean then = intent.getBooleanExtra(EXTRA_THEN_INSTALL, false);
                ServiceJob kind = then ? ServiceJob.DOWNLOAD_INSTALL : ServiceJob.DOWNLOAD;
                if (repeat(kind)) break;
                runWorker(kind, () -> {
                    if (download() && then && newest()) install();
                    stopIfIdle();
                });
                break;
            }
            case ACTION_INSTALL:
                if (InstallWay.current().dropsInstall(this)) break;
                if (Checker.dropStaleOffer(this, "install")) {
                    stopIfIdle();
                    break;
                }
                runWorker(ServiceJob.INSTALL, () -> {
                    if (newest()) install();
                    stopIfIdle();
                });
                break;
            case ACTION_VERIFY_ONLY:
                runWorker(ServiceJob.OTHER, () -> {
                    verifyOnly();
                    stopIfIdle();
                });
                break;
            default: // WATCH: re-attach to an install that runs in update_engine
                // 2026-11-04 (M1): the activity sends WATCH at every onResume during an install. A worker that is
                // already following it must not be stopped (runWorker's join used to end its wait loop, and then
                // nothing ended the foreground service after READY)
                if (jobs.busy()) {
                    Log.i(Ota.TAG, "watch: a worker is already running, nothing to do");
                    break;
                }
                runWorker(ServiceJob.OTHER, this::watch);
        }
        return START_NOT_STICKY;
    }

    /**
     * Runs {@code r} on a new worker after the previous one ended (asked to stop, joined up to 10 s). 2026-11-04
     * (M3): the join runs on the new worker, not on the main thread (the activity shares this process: Pause or
     * Cancel froze the UI for up to 10 s).
     */
    private void runWorker(ServiceJob kind, Runnable r) {
        jobs.start(kind, () -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            r.run();
        }, e -> {
            Log.e(Ota.TAG, "worker", e);
            Store.get(this).setError("err_install_unknown", "");
            stopIfIdle();
        });
    }

    /** The request repeats the running job (a second "Install now" press while it installs): nothing to start. */
    @Override
    public boolean repeat(ServiceJob kind) {
        if (!jobs.repeats(kind)) return false;
        Log.i(Ota.TAG, kind + ": the running job already does it");
        return true;
    }

    /** Worker: follow an install the store says is running (InstallWay.watch), after settling a stale state. */
    private void watch() {
        BootReceiver.settleIfStale(this, "watch");
        InstallWay.current().watch(this, Store.get(this));
        stopIfIdle();
    }

    private void stopIfIdle() {
        if (!jobs.mayEnd()) return;                           // a newer job runs and ends the service itself
        UpdateState s = Store.get(this).state();
        if (s != UpdateState.DOWNLOADING && s != UpdateState.INSTALLING) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    // ------------------------------------------------------------------ files
    static File zipFile(Context c, UpdateManifest.Pkg p) { return new File(Ota.dir(c), p.fileName()); }
    static File metaFile(Context c, UpdateManifest.Pkg p) { return new File(Ota.dir(c), p.fileName() + ".metadata"); }

    /** The file update_engine reads: the download, or the zip on the USB stick. */
    static File payloadSource(Context c, Store st, UpdateManifest.Pkg p) {
        return st.fromUsb() ? new File(st.usbPath(), p.fileName()) : zipFile(c, p);
    }

    public static void deleteFiles(Context c) { Ota.deleteFiles(c); }

    private UpdateManifest.Pkg pkg() {
        try {
            return UpdateManifest.Pkg.parse(Store.get(this).packageJson());
        } catch (JSONException e) {
            return null;
        }
    }

    @Override
    public String version() {
        try {
            return new UpdateManifest(Store.get(this).manifestJson()).version;
        } catch (JSONException e) {
            return "?";
        }
    }

    // ------------------------------------------------------------------ download
    /**
     * The offer's package, verified; a newer build that replaces the offer meanwhile is downloaded instead and the old
     * files go (Newest.downloadNewest). @return true when the verified zip + metadata of the newest offer are ready
     */
    private boolean download() {
        return Newest.downloadNewest(Store.get(this), this::downloadOnce);
    }

    /**
     * Right before an install: a fresh check, a newer build is downloaded first (Newest.beforeInstall), so the install
     * is always of the newest verified build. @return true = install the saved offer
     */
    private boolean newest() {
        return Newest.beforeInstall(Store.get(this), () -> Checker.check(this), this::download);
    }

    /**
     * One package (its json): download and verify, stopped early once a check replaced the offer with a newer build.
     * DOWNLOADED is set by Newest.downloadNewest. @return true when the verified zip + metadata are ready
     */
    private boolean downloadOnce(String pkgJson) {
        Store st = Store.get(this);
        UpdateManifest.Pkg p;
        try {
            p = UpdateManifest.Pkg.parse(pkgJson);
        } catch (JSONException e) {
            st.setState(UpdateState.IDLE);
            return false;
        }
        String ver = version();
        File meta = metaFile(this, p);
        File src = payloadSource(this, st, p);
        InstallKind kind = Ota.T.install;
        if (st.state() == UpdateState.DOWNLOADED && src.isFile() && (!kind.metadataCompletesDownload || meta.isFile())) {
            return true;
        }
        st.setState(UpdateState.DOWNLOADING);
        Ota.setBusy(this, true, "downloading");
        try {
            if (st.fromUsb()) {
                if (kind.needsMetadata(p)) copyUsbMetadata(st, p, meta);
                if (!verified(src, p)) {
                    st.setError("err_hash", "");
                    return false;
                }
            } else {
                Preflight.Block b = Preflight.downloadSpace(this, p, ParallelDownload.have(src, p.size));
                if (b != null) {
                    st.setError(b.res, b.arg);
                    return false;
                }
                if (kind.needsMetadata(p) && !meta.isFile()) {
                    byte[] m = fetchWithRetry(p.metadataUrl);
                    if (m == null) return false;
                    if (!p.metadataSha256.isEmpty() && !Trust.hex(Trust.sha256().digest(m)).equals(p.metadataSha256)) {
                        if (Newest.deltaFailed(st, "metadata sha256", UpdateState.DOWNLOADING)) return false;
                        throw new IOException("metadata sha256 mismatch");
                    }
                    try (FileOutputStream o = new FileOutputStream(meta)) {
                        o.write(m);
                    }
                }
                String tag = new PackageDownload(downloader(), downloadHost(st, p, ver))
                        .fetch(p.url, src, p.size, p.sha256, kind.skipsCompleteFile);
                if (tag == null) return false;
                st.setDownload(p.size, p.size, tag, 0);
            }
            // the way's own check: metadata = payload prefix, or the package signature
            if (!InstallWay.current().verifyDownload(st, src, p, meta)) {
                Newest.deltaFailed(st, "payload check", UpdateState.DOWNLOADING);   // a full package keeps its error
                return false;
            }
            Log.i(Ota.TAG, "download verified: " + p.fileName());
            return true;
        } catch (PackageDownload.HashMismatch e) {
            if (Newest.deltaFailed(st, "sha256", UpdateState.DOWNLOADING)) return false;
            st.setError("err_hash", "");
            return false;
        } catch (IOException e) {
            if (gone(e) && Newest.deltaFailed(st, e.getMessage(), UpdateState.DOWNLOADING)) return false;
            Log.w(Ota.TAG, "download failed", e);
            if (!jobs.stopRequested()) st.setError(st.fromUsb() ? "usb_none" : "err_network", "");
            return false;
        } finally {
            if (jobs.stopRequested() && st.state() == UpdateState.DOWNLOADING) st.setState(UpdateState.PAUSED);
            Ota.setBusy(this, false, st.state().name().toLowerCase());
        }
    }

    /** The file is not on the server (404, 410): a delta that was taken down gives way to the full package. */
    static boolean gone(IOException e) {
        return e instanceof HttpError && (((HttpError) e).code == 404 || ((HttpError) e).code == 410);
    }

    /** The whole zip against the manifest's sha256: the same check for a download and a file on the USB stick. */
    private boolean verified(File f, UpdateManifest.Pkg p) throws IOException {
        notifyProgress(getString(R.string.dl_verifying), -1);
        String h = Trust.sha256(f, -1, jobs::stopRequested);
        if (h.equals(p.sha256)) return true;
        Log.w(Ota.TAG, "zip sha256 " + h + " != " + p.sha256);
        return false;
    }

    /** Parallel range connections over HTTPS, on background threads. */
    private static ParallelDownload downloader() {
        return new ParallelDownload(Http::open, r -> new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            r.run();
        }, "lumen-download"), ParallelDownload.Tuning.DEFAULT, m -> Log.i(Ota.TAG, m));
    }

    /**
     * The download's view of the service. A download that lasts (a slow line, a network gap, retries) checks the
     * manifests again (Newest.Recheck) and stops as soon as a newer build replaced its package (Newest.superseded).
     */
    private PackageDownload.Host downloadHost(Store st, UpdateManifest.Pkg p, String ver) {
        Newest.Recheck recheck = new Newest.Recheck(SystemClock.elapsedRealtime());
        return new PackageDownload.Host() {
            @Override
            public boolean online() { return UpdateService.this.online(); }

            @Override
            public boolean stopRequested() { return jobs.stopRequested() || Newest.superseded(p.json, st); }

            @Override
            public void sleep(long ms) {
                UpdateService.this.sleep(ms);
                recheck();
            }

            @Override
            public void offline(long have, long total) {
                recheck.offline();
                st.setDownload(have, total, st.dlEtag(), 0);
                notifyProgress(getString(R.string.dl_waiting_network), -1);
            }

            @Override
            public boolean progress(long bytes, long total, long speed) {
                st.setDownload(bytes, total, st.dlEtag(), speed);
                notifyProgress(getString(R.string.dl_title, ver), total > 0 ? (int) (bytes * 100 / total) : -1);
                recheck();
                return !stopRequested();
            }

            private void recheck() {
                if (recheck.due(SystemClock.elapsedRealtime(), UpdateService.this.online())) Checker.check(UpdateService.this);
            }

            @Override
            public boolean verified(File f) throws IOException { return UpdateService.this.verified(f, p); }
        };
    }

    private void copyUsbMetadata(Store st, UpdateManifest.Pkg p, File meta) throws IOException {
        String name = p.metadataUrl.substring(p.metadataUrl.lastIndexOf('/') + 1);
        File m = new File(st.usbPath(), name);
        if (!m.isFile() || m.length() > (8 << 20)) throw new IOException("no " + m);
        byte[] data = java.nio.file.Files.readAllBytes(m.toPath());
        try (FileOutputStream o = new FileOutputStream(meta)) {
            o.write(data);
        }
    }

    private byte[] fetchWithRetry(String url) throws IOException {
        for (int i = 0; ; i++) {
            if (jobs.stopRequested()) return null;
            try {
                return Http.fetch(url, 8 << 20);
            } catch (HttpError e) {
                if (e.code == 404 || e.code == 410) throw e;
            } catch (IOException e) {
                if (i > 20) throw e;
            }
            sleep(Math.min(60000, 5000L * (i + 1)));
        }
    }

    private boolean online() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        return cm != null && cm.getActiveNetwork() != null;
    }

    @Override
    public void sleep(long ms) {
        long end = SystemClock.elapsedRealtime() + ms;   // a monotonic clock: the network time may jump the wall clock
        while (!jobs.stopRequested() && SystemClock.elapsedRealtime() < end) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    // ------------------------------------------------------------------ install
    private void install() {
        Store st = Store.get(this);
        UpdateManifest.Pkg p = pkg();
        UpdateManifest m;
        try {
            m = new UpdateManifest(st.manifestJson());
        } catch (JSONException e) {
            st.setState(UpdateState.IDLE);
            return;
        }
        if (p == null) return;
        InstallWay.current().install(this, st, m, p);
    }

    /** Dry run of everything install() checks; logs the result, writes nothing to any slot. */
    private void verifyOnly() {
        Store st = Store.get(this);
        UpdateManifest.Pkg p = pkg();
        if (p == null || st.state() != UpdateState.DOWNLOADED) {
            Log.i(Ota.TAG, "verify-only: no downloaded update (state " + st.state() + ")");
            return;
        }
        Log.i(Ota.TAG, InstallWay.current().dryRun(this, st, p));
    }

    // ------------------------------------------------------------------ ServiceHost (InstallWay's view)
    @Override
    public Context context() { return this; }

    @Override
    public boolean stopRequested() { return jobs.stopRequested(); }

    @Override
    public void progress(String title, int pct) { notifyProgress(title, pct); }

    @Override
    public void complete(int code) { onComplete(code); }

    @Override
    public boolean busy() { return jobs.busy(); }

    @Override
    public void onStatus(int status, float percent) {
        Store st = Store.get(this);
        if (st.state() != UpdateState.INSTALLING) return;
        float f;
        switch (status) {
            case Ota.UE_DOWNLOADING: f = 0.90f * percent; break;
            case Ota.UE_VERIFYING: f = 0.90f + 0.05f * percent; break;
            case Ota.UE_FINALIZING: f = 0.95f + 0.05f * percent; break;
            default: return;
        }
        st.setProgress(f);
        notifyProgress(getString(R.string.inst_title, version()), (int) (f * 100));
    }

    @Override
    public void onComplete(int code) {
        Store st = Store.get(this);
        if (st.state() != UpdateState.INSTALLING) return;
        String ver = version();
        if (code == Ota.ERR_SUCCESS || code == Ota.ERR_UPDATED_BUT_NOT_ACTIVE) {
            st.setProgress(1f);
            st.setState(UpdateState.READY);
            Ota.setBusy(this, false, "ready");
            result(getString(R.string.n_ready_title), getString(R.string.ready_body, ver));
        } else if (code == Ota.ERR_USER_CANCELED) {
            st.clearPending();
            st.setState(UpdateState.DOWNLOADED);
            Ota.setBusy(this, false, "idle");
        } else if (DeltaRule.applyFailed(code)
                && Newest.deltaFailed(st, "update_engine code " + code, UpdateState.AVAILABLE)) {
            // the delta did not fit this system: the full package of the same build is the offer now
            st.clearPending();
            Ota.setBusy(this, false, "idle");
        } else {
            st.clearPending();
            switch (Outcome.installError(code)) {
                case SPACE: st.setError("err_space_device", String.valueOf(code)); break;
                case DOWNGRADE: st.setError("err_downgrade", String.valueOf(code)); break;
                case UNKNOWN: st.setError("err_install_unknown", ""); break;
                default: st.setError("err_install", String.valueOf(code));
            }
            Log.w(Ota.TAG, "install ended with update_engine code " + code);
            Ota.setBusy(this, false, "error");
        }
        releaseWake();
        cancelNotif(NOTIF_PROGRESS);
        stopIfIdle();                                         // finding 5: the foreground service ends here too
    }

    @Override
    public void acquireWake() {
        if (wake == null) {
            wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lumen:ota-install");
            wake.setReferenceCounted(false);
        }
        wake.acquire(40 * 60 * 1000L);
    }

    @Override
    public void releaseWake() {
        if (wake != null && wake.isHeld()) wake.release();
    }

    // ------------------------------------------------------------------ notifications
    static void channel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(Ota.NOTIF_CHANNEL, c.getString(R.string.ch_updates),
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(true);
        nm.createNotificationChannel(ch);
    }

    static PendingIntent openUi(Context c) {
        return PendingIntent.getActivity(c, 0, new Intent(c, UpdaterActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification progressNotification(String title, int pct, boolean indeterminate) {
        return new Notification.Builder(this, Ota.NOTIF_CHANNEL)
                .setSmallIcon(R.drawable.ic_update).setContentTitle(title)
                .setProgress(100, Math.max(0, pct), indeterminate || pct < 0)
                .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(openUi(this)).build();
    }

    private void notifyProgress(String title, int pct) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastNotif < 1000) return;
        lastNotif = now;
        getSystemService(NotificationManager.class).notify(NOTIF_PROGRESS, progressNotification(title, pct, false));
    }

    private void result(String title, String text) { notifyResult(this, title, text); }

    static void notifyResult(Context c, String title, String text) {
        channel(c);
        Notification n = new Notification.Builder(c, Ota.NOTIF_CHANNEL)
                .setSmallIcon(R.drawable.ic_update).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true).setContentIntent(openUi(c)).build();
        c.getSystemService(NotificationManager.class).notify(NOTIF_RESULT, n);
    }

    private void cancelNotif(int id) { getSystemService(NotificationManager.class).cancel(id); }
}
