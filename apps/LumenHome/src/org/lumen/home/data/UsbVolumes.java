package org.lumen.home.data;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Mounted USB drives: name and size ("Kingston, 29 ГБ"), for the source menu and the calm screen's inputs. */
public final class UsbVolumes {
    public static final class Drive {
        public final String name;
        public final long bytes;

        Drive(String name, long bytes) {
            this.name = name;
            this.bytes = bytes;
        }
    }

    private UsbVolumes() {}

    public static List<Drive> list(Context c) {
        ArrayList<Drive> out = new ArrayList<>();
        try {
            StorageManager sm = c.getSystemService(StorageManager.class);
            if (sm == null) return out;
            for (StorageVolume v : sm.getStorageVolumes()) {
                if (!v.isRemovable() || !Environment.MEDIA_MOUNTED.equals(v.getState())) continue;
                File dir = v.getDirectory();
                long bytes = 0;
                if (dir != null) {
                    try {
                        bytes = new StatFs(dir.getPath()).getTotalBytes();
                    } catch (RuntimeException ignored) {
                    }
                }
                String n = v.getDescription(c);
                out.add(new Drive(n == null ? "" : n, bytes));
            }
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    /** Whole gigabytes as the drive maker counts them (1000^3), at least 1. */
    public static int gb(long bytes) {
        return (int) Math.max(1, Math.round(bytes / 1e9));
    }
}
