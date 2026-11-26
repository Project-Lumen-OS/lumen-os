// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserManager;
import android.util.Log;

import org.lumen.home.img.ImageLoader;
import org.lumen.home.model.BootGate;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Lumen Home process (org.lumen.home, priv-app, NOT persistent). Application.onCreate does nothing
 * expensive (cold start budget): the io thread, the image loader and the repository are
 * created lazily on first use.
 *
 * Crash safety: every uncaught exception is appended to files/crash (device protected) before the default
 * handler kills us; 3 crashes within 60 s switch HomeActivity to the plain safe-mode apps list, so a
 * crash loop in HOME can never brick the projector UI.
 */
public final class App extends Application {

    /** Home may start before the user is unlocked (direct boot, model/BootGate): the credential protected steps wait for it. */
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(base);
        UserManager um = base.getSystemService(UserManager.class);
        mGate = new BootGate(um == null || um.isUserUnlocked(), (step, t) -> Log.w(TAG, "after unlock, " + step + ": " + t));
    }

    public static final String TAG = "Z9xHome";
    private static final long CRASH_WINDOW_MS = 60_000;
    private static final int CRASH_LIMIT = 3;

    private static App sApp;
    private BootGate mGate;
    private Context mDe;
    private Handler mMain;
    private HandlerThread mIoThread;
    private Handler mIo;
    private Prefs mPrefs;
    private ImageLoader mImages;

    public static App get() {
        return sApp;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sApp = this;
        mMain = new Handler(Looper.getMainLooper());
        mDe = createDeviceProtectedStorageContext();
        if (!mGate.unlocked()) waitForUnlock();
        final Thread.UncaughtExceptionHandler def = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                recordCrash(e);
            } catch (Throwable ignored) {
            }
            if (def != null) def.uncaughtException(t, e);
            else Process.killProcess(Process.myPid());
        });
    }

    public Handler main() {
        return mMain;
    }

    /** Before the unlock only the device protected storage is there (model/BootGate); main thread steps. */
    public BootGate gate() {
        return mGate;
    }

    /** ACTION_USER_UNLOCKED; checked again after registering, an unlock in between is not missed. */
    private void waitForUnlock() {
        Log.i(TAG, "boot: user locked, Home starts on device protected storage");
        final long t0 = SystemClock.uptimeMillis();
        BroadcastReceiver rx = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                unlocked(this, t0);
            }
        };
        registerReceiver(rx, new IntentFilter(Intent.ACTION_USER_UNLOCKED), Context.RECEIVER_EXPORTED);
        UserManager um = getSystemService(UserManager.class);
        if (um == null || um.isUserUnlocked()) mMain.post(() -> unlocked(rx, t0));
    }

    private void unlocked(BroadcastReceiver rx, long t0) {
        if (mGate.unlocked()) return;
        try {
            unregisterReceiver(rx);
        } catch (Throwable ignored) {
        }
        Log.i(TAG, "boot: user unlocked +" + (SystemClock.uptimeMillis() - t0) + "ms");
        mGate.unlock();
    }

    public synchronized Handler io() {
        if (mIo == null) {
            mIoThread = new HandlerThread("z9x-home-io", Process.THREAD_PRIORITY_BACKGROUND
                    + Process.THREAD_PRIORITY_MORE_FAVORABLE);
            mIoThread.start();
            mIo = new Handler(mIoThread.getLooper());
        }
        return mIo;
    }

    private org.lumen.home.model.Lanes mLanes;
    private org.lumen.home.data.SnapshotFile mSnapshot;

    /**
     * The screen's data lane and the network lane (model/Lanes): the data of the screen never waits for a server. The
     * screen's lane runs at the default priority (the user is waiting for it), the network one in the background.
     */
    public synchronized org.lumen.home.model.Lanes lanes() {
        if (mLanes == null) {
            mLanes = new org.lumen.home.model.Lanes((lane, body) -> {
                final int pri = org.lumen.home.model.Lanes.LOAD.equals(lane) ? Process.THREAD_PRIORITY_DEFAULT
                        : Process.THREAD_PRIORITY_BACKGROUND;
                Thread t = new Thread(() -> {
                    Process.setThreadPriority(pri);
                    body.run();
                }, "z9x-home-" + lane);
                t.setDaemon(true);
                return t;
            });
        }
        return mLanes;
    }

    /** The last shown data on disk (data/SnapshotFile); lane threads only. */
    public synchronized org.lumen.home.data.SnapshotFile snapshot() {
        if (mSnapshot == null) mSnapshot = new org.lumen.home.data.SnapshotFile(this);
        return mSnapshot;
    }

    private volatile Runnable mHomeChanged;

    /** The visible Home (null when stopped): told when data behind it changed (TMDB art, an install). */
    public void setHomeListener(Runnable r) {
        mHomeChanged = r;
    }

    public void homeChanged() {
        Runnable r = mHomeChanged;
        if (r != null) main().post(r);
    }

    private org.lumen.home.data.LayoutStore mLayout;

    /** Home's layout (Settings.Secure, readable before the unlock; the old settings it starts from only after it). */
    public synchronized org.lumen.home.data.LayoutStore layout() {
        if (mLayout == null) mLayout = new org.lumen.home.data.LayoutStore(this, () -> gate().unlocked() ? prefs() : null);
        return mLayout;
    }

    /** Credential protected: never before the unlock ({@link #gate}). */
    public synchronized Prefs prefs() {
        if (mPrefs == null) mPrefs = new Prefs(this);
        return mPrefs;
    }

    public synchronized ImageLoader images() {
        if (mImages == null) mImages = new ImageLoader(this);
        return mImages;
    }

    /** TMDB key art for Continue watching, or null when this build carries no TMDB key (tmdb/TmdbArt). */
    public synchronized org.lumen.home.tmdb.TmdbArt tmdb() {
        if (!mTmdbChecked) {
            mTmdbChecked = true;
            mTmdb = org.lumen.home.tmdb.TmdbArt.create(this);
        }
        return mTmdb;
    }

    private org.lumen.home.tmdb.TmdbArt mTmdb;
    private boolean mTmdbChecked;

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        ImageLoader il;
        synchronized (this) {
            il = mImages;
        }
        if (il != null) il.trim(level);
    }

    // ------------------------------------------------------------------ crash counter

    /** Device protected, so the crash counter also guards a Home started before the unlock. */
    private File crashFile() {
        return new File(mDe.getFilesDir(), "crash");
    }

    private void recordCrash(Throwable e) throws Exception {
        Log.e(TAG, "crash recorded", e);
        try (FileOutputStream out = new FileOutputStream(crashFile(), true)) {
            out.write((System.currentTimeMillis() + "\n").getBytes(StandardCharsets.US_ASCII));
        }
    }

    /** True if HOME crashed CRASH_LIMIT times within the last minute. */
    public boolean isCrashLooping() {
        File f = crashFile();
        if (!f.exists()) return false;
        try {
            long now = System.currentTimeMillis();
            int n = 0;
            for (String s : new String(Files.readAllBytes(f.toPath()), StandardCharsets.US_ASCII).split("\n")) {
                if (s.isEmpty()) continue;
                long t = Long.parseLong(s.trim());
                if (now - t >= 0 && now - t < CRASH_WINDOW_MS) n++;
            }
            return n >= CRASH_LIMIT;
        } catch (Throwable t) {
            return false;
        }
    }

    public void clearCrashes() {
        //noinspection ResultOfMethodCallIgnored
        crashFile().delete();
    }
}
