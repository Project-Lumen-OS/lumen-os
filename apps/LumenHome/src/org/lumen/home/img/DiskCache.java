// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.img;

import android.graphics.Bitmap;
import android.util.Log;

import org.lumen.home.App;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * cacheDir/img: downscaled "master" copies of every piece of art (JPEG q88; PNG for icons with alpha and,
 * since 1.0.1, for the app banners and tiles, whose logos and names JPEG would soften).
 * LRU by modification time, trimmed to its budget once per process start (64 MB at 1080p, 114 MB at 2K
 * and 4K, whose masters are up to 2560x1440: UiScale.diskBytes). The system may clear it any time; that
 * only costs a re-download.
 */
final class DiskCache {
    private static final long TOUCH_AFTER_MS = 24L * 3600_000L;
    private final long mMax;
    /** Credential protected cache folder: null until the user is unlocked (Home starts before, model/BootGate). */
    private volatile File mDir;

    DiskCache(long maxBytes) {
        mMax = maxBytes;
    }

    /** The folder can be used from now on (the app's cache dir once the user is unlocked). */
    void open(File cacheDir) {
        File d = new File(cacheDir, "img");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        mDir = d;
    }

    boolean isOpen() {
        return mDir != null;
    }

    private File file(File dir, String key, boolean png) {
        return new File(dir, hash(key) + (png ? ".png" : ".jpg"));
    }

    /** Returns the cached file or null (also before the unlock); refreshes its LRU time at most once a day. */
    File get(String key, boolean png) {
        File dir = mDir;
        if (dir == null) return null;
        File f = file(dir, key, png);
        if (!f.isFile() || f.length() == 0) return null;
        long now = System.currentTimeMillis();
        if (now - f.lastModified() > TOUCH_AFTER_MS) {
            //noinspection ResultOfMethodCallIgnored
            f.setLastModified(now);
        }
        return f;
    }

    /** Keeps {@code sw} on disk; null when it could not (also before the unlock: nothing is written then). */
    File put(String key, Bitmap sw, boolean png) {
        File dir = mDir;
        if (dir == null) return null;
        File f = file(dir, key, png);
        File tmp = new File(dir, f.getName() + ".tmp" + Thread.currentThread().getId());
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            sw.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, png ? 100 : 88, out);
        } catch (Throwable t) {
            Log.w(App.TAG, "disk cache write: " + t);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return null;
        }
        if (!tmp.renameTo(f)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return null;
        }
        return f;
    }

    void trim() {
        File dir = mDir;
        File[] fs = dir == null ? null : dir.listFiles();
        if (fs == null) return;
        long total = 0;
        for (File f : fs) {
            if (f.getName().contains(".tmp")) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
                continue;
            }
            total += f.length();
        }
        if (total <= mMax) return;
        Arrays.sort(fs, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        int n = 0;
        for (File f : fs) {
            if (total <= mMax * 3 / 4) break;
            long len = f.length();
            if (f.delete()) {
                total -= len;
                n++;
            }
        }
        Log.i(App.TAG, "img cache trimmed " + n + " files, now " + (total >> 20) + " MB");
    }

    static String hash(String key) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(40);
            for (int i = 0; i < 16; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(key.hashCode());
        }
    }
}
