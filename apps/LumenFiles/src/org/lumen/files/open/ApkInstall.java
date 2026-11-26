// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.open;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import org.lumen.files.model.Kinds;

import java.io.File;

/**
 * An APK goes to the system package installer, which shows its confirm window (the Lumen look comes from
 * LumenPackageInstallerOverlay) like the stock file managers do. Files is a privileged app and says the file is not
 * from an unknown source, so the user sees one window: the app, its permissions, Install.
 */
public final class ApkInstall {
    public static final int REQUEST = 41;
    private static final String TAG = "LumenFiles";

    private ApkInstall() {}

    @SuppressWarnings("deprecation")
    public static Intent intent(Context c, Uri apk) {
        return new Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setDataAndType(apk, Kinds.APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                .putExtra(Intent.EXTRA_RETURN_RESULT, true)
                .putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, c.getPackageName());
    }

    public static boolean start(Activity a, File apk) {
        try {
            a.startActivityForResult(intent(a, LocalProvider.uri(apk)), REQUEST);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "installer: " + e);
            return false;
        }
    }

    /** Where an APK of a network folder is copied before the installer reads it. */
    public static File cache(Context c) {
        File d = new File(c.getCacheDir(), "apk");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }
}
