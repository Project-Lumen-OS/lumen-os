package org.lumen.home.data;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.util.Log;

import org.lumen.home.App;
import org.lumen.home.model.HomeSnapshot;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;

/**
 * The file of {@link HomeSnapshot}: the last shown data, read before Home's first frame and written after each change.
 * It lives in the device protected storage (readable before the user is unlocked, so a later direct boot Home can use
 * it too). Lane threads only (Lanes.LOAD), never the main thread.
 */
public final class SnapshotFile {
    private static final String NAME = "home_snapshot.bin";

    private final AtomicFile mFile;
    private final long mStamp;
    private final Context mApp;
    private byte[] mSaved;

    public SnapshotFile(Context c) {
        mApp = c.getApplicationContext();
        Context de = mApp.createDeviceProtectedStorageContext();
        mFile = new AtomicFile(new File(de.getFilesDir(), NAME));
        long stamp = 0;
        try {
            PackageInfo pi = mApp.getPackageManager().getPackageInfo(mApp.getPackageName(), 0);
            stamp = pi.lastUpdateTime ^ (pi.getLongVersionCode() << 40);
        } catch (Throwable ignored) {
        }
        mStamp = stamp;
    }

    /** The UI locale resources really use (per-app locales included): texts inside the snapshot are in it. */
    private String locale() {
        return mApp.getResources().getConfiguration().getLocales().toLanguageTags();
    }

    /** The last data of this APK build and locale, or null. */
    public HomeData read() {
        long t0 = SystemClock.uptimeMillis();
        if (!mFile.getBaseFile().exists()) return null;
        try {
            byte[] b = mFile.readFully();
            HomeData d = HomeSnapshot.decode(b, mStamp, locale());
            if (d != null) mSaved = b;
            Log.i(App.TAG, "snapshot " + (d == null ? "stale or broken" : "read") + " bytes=" + b.length + " ms="
                    + (SystemClock.uptimeMillis() - t0));
            return d;
        } catch (Throwable t) {
            Log.w(App.TAG, "snapshot read: " + t);
            return null;
        }
    }

    /** Keeps {@code d} for the next start; the same bytes as last time are not written again. */
    public void write(HomeData d) {
        FileOutputStream fo = null;
        try {
            byte[] b = HomeSnapshot.encode(d, mStamp, locale());
            if (Arrays.equals(b, mSaved)) return;
            fo = mFile.startWrite();
            fo.write(b);
            mFile.finishWrite(fo);
            mSaved = b;
        } catch (Throwable t) {
            Log.w(App.TAG, "snapshot write: " + t);
            if (fo != null) mFile.failWrite(fo);
        }
    }
}
