// SPDX-License-Identifier: Apache-2.0
package org.lumen.updater;

import android.content.Context;
import android.util.Log;

import java.io.File;

/** Where the downloaded package is kept. */
public enum OtaDir {
    /** The app's device-protected no-backup folder "ota"; update_engine (root) reads the metadata file by path. */
    APP_DATA {
        @Override
        File get(Context c) {
            File d = new File(c.createDeviceProtectedStorageContext().getNoBackupFilesDir(), "ota");
            if (!d.isDirectory() && !d.mkdirs()) Log.w(Ota.TAG, "cannot create " + d);
            // update_engine (root) reads the small metadata file by path: keep the dir traversable
            d.setExecutable(true, false);
            d.getParentFile().setExecutable(true, false);
            return d;
        }
    },
    /** /cache/lumen_ota: the recovery reads the package from here (system:cache 0770, not encrypted). */
    CACHE {
        @Override
        File get(Context c) {
            File d = new File("/cache/lumen_ota");
            if (!d.isDirectory() && !d.mkdirs()) Log.w(Ota.TAG, "cannot create " + d);
            return d;
        }
    };

    abstract File get(Context c);
}
