// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.data;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.SystemProperties;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.Settings;
import android.util.AtomicFile;
import android.util.Log;

import org.lumen.home.App;
import org.lumen.home.R;
import org.lumen.home.model.BackupFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings backup on disk (Customize Home, Backup): the file {@link BackupFile#NAME} in the root of a USB drive or
 * in Downloads (Downloads goes with a reinstall, a USB drive stays). Writes and reads only the Settings.Secure values of
 * {@link BackupFile#KEYS} of the current user; Home holds WRITE_SECURE_SETTINGS and all files access as a platform
 * signed priv-app. The projector menus read their values again when they open, Home through its observer.
 */
public final class BackupPlaces {
    private BackupPlaces() {}

    /** Where a backup can go: a USB drive (its label) or Downloads. */
    public static final class Place {
        public final File dir;
        public final String label;
        public final boolean usb;

        Place(File dir, String label, boolean usb) {
            this.dir = dir;
            this.label = label;
            this.usb = usb;
        }

        public File file() {
            return new File(dir, BackupFile.NAME);
        }
    }

    /** A backup found in a place, read and checked. */
    public static final class Found {
        public final Place place;
        public final BackupFile.Backup backup;
        public final long modified;

        Found(Place place, BackupFile.Backup backup, long modified) {
            this.place = place;
            this.backup = backup;
            this.modified = modified;
        }
    }

    /** Mounted USB drives first (they survive a reinstall), then Downloads. */
    public static List<Place> places(Context c) {
        List<Place> out = new ArrayList<>();
        try {
            StorageManager sm = c.getSystemService(StorageManager.class);
            for (StorageVolume v : sm.getStorageVolumes()) {
                if (!v.isRemovable() || !Environment.MEDIA_MOUNTED.equals(v.getState()) || v.getDirectory() == null) continue;
                String name = v.getDescription(c);
                out.add(new Place(v.getDirectory(), name == null || name.isEmpty() ? c.getString(R.string.bk_usb) : name, true));
            }
        } catch (Throwable t) {
            Log.w(App.TAG, "backup: volumes: " + t);
        }
        out.add(new Place(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                c.getString(R.string.bk_downloads), false));
        return out;
    }

    /** Backups that can be read, newest first. */
    public static List<Found> found(Context c) {
        List<Found> out = new ArrayList<>();
        for (Place p : places(c)) {
            File f = p.file();
            if (!f.isFile() || f.length() > BackupFile.MAX_BYTES) continue;
            BackupFile.Backup b = BackupFile.read(readText(f));
            if (b != null) out.add(new Found(p, b, f.lastModified()));
        }
        out.sort((a, b) -> Long.compare(b.modified, a.modified));
        return out;
    }

    /** Saves the current values into {@code p}; false when the file could not be written. */
    public static boolean save(Context c, Place p) {
        Map<String, String> values = new HashMap<>();
        for (String k : BackupFile.KEYS) values.put(k, secure(c, k));
        String text = BackupFile.write(values, SystemProperties.get("ro.boot.xgimi.modelname", Build.MODEL),
                System.currentTimeMillis());
        AtomicFile af = new AtomicFile(p.file());
        FileOutputStream o = null;
        try {
            if (!p.dir.isDirectory() && !p.dir.mkdirs()) return false;
            o = af.startWrite();
            o.write(text.getBytes(StandardCharsets.UTF_8));
            af.finishWrite(o);
            return true;
        } catch (Exception e) {
            if (o != null) af.failWrite(o);
            Log.w(App.TAG, "backup: save " + p.dir + ": " + e);
            return false;
        }
    }

    /** Writes the backup's parts (a null part = default: the value goes); the number of values written. */
    public static int restore(Context c, BackupFile.Backup b) {
        int n = 0;
        for (Map.Entry<String, String> e : b.parts.entrySet()) if (put(c, e.getKey(), e.getValue())) n++;
        Log.i(App.TAG, "backup: restored " + n + " of " + b.parts.size() + " parts made " + b.made + " on " + b.model);
        return n;
    }

    /** Every menu and Home back to how it came: the values of {@link BackupFile#KEYS} go. */
    public static void reset(Context c) {
        for (String k : BackupFile.KEYS) put(c, k, null);
    }

    private static String secure(Context c, String k) {
        try {
            return Settings.Secure.getString(c.getContentResolver(), k);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean put(Context c, String k, String v) {
        if (!BackupFile.KEYS.contains(k)) return false;
        try {
            return Settings.Secure.putString(c.getContentResolver(), k, v);
        } catch (Throwable t) {
            Log.w(App.TAG, "backup: " + k + ": " + t);
            return false;
        }
    }

    private static String readText(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), BackupFile.MAX_BYTES)];
            int off = 0, n;
            while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            return new String(b, 0, off, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
