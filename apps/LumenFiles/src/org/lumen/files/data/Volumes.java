// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.data;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import org.lumen.files.R;
import org.lumen.files.model.LocalFs;
import org.lumen.files.model.Place;
import org.lumen.files.model.PlaceKind;
import org.lumen.files.model.Places;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The local drives (worker thread): the projector's storage and every mounted USB drive and SD card. */
public final class Volumes {
    /** A drive and the store that reads and writes it. */
    public static final class Volume {
        public final Place place;
        public final LocalFs fs;

        Volume(Place place, LocalFs fs) {
            this.place = place;
            this.fs = fs;
        }
    }

    private Volumes() {}

    public static List<Volume> list(Context c) {
        List<Place> places = new ArrayList<>();
        File in = Environment.getExternalStorageDirectory();
        if (in != null && in.isDirectory()) places.add(place(PlaceKind.INTERNAL, c.getString(R.string.files_internal), in));
        StorageManager sm = c.getSystemService(StorageManager.class);
        if (sm != null) {
            for (StorageVolume v : sm.getStorageVolumes()) {
                if (!v.isRemovable() || !Environment.MEDIA_MOUNTED.equals(v.getState())) continue;
                File root = v.getDirectory();
                if (root == null) continue;
                String d = v.getDescription(c);
                boolean sd = d != null && d.toUpperCase(Locale.ROOT).contains("SD");
                String label = d == null || d.isEmpty() ? root.getName() : d;
                places.add(place(sd ? PlaceKind.SD : PlaceKind.USB, label, root));
            }
        }
        Collator col = Collator.getInstance();
        col.setStrength(Collator.SECONDARY);
        List<Volume> out = new ArrayList<>();
        for (Place p : Places.order(places, col::compare)) out.add(new Volume(p, new LocalFs(new File(p.root))));
        return out;
    }

    private static Place place(PlaceKind k, String label, File root) {
        long total = -1, free = -1;
        try {
            StatFs s = new StatFs(root.getPath());
            total = s.getTotalBytes();
            free = s.getAvailableBytes();
        } catch (IllegalArgumentException ignored) {
        }
        return new Place(k, label, root.getAbsolutePath(), total, free);
    }

    /** Drives coming and going (system broadcasts, data scheme file). */
    public static IntentFilter changes() {
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_MEDIA_MOUNTED);
        f.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        f.addAction(Intent.ACTION_MEDIA_REMOVED);
        f.addAction(Intent.ACTION_MEDIA_EJECT);
        f.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        f.addDataScheme("file");
        return f;
    }
}
