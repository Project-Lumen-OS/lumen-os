// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home;

import android.content.Intent;

/**
 * Files is its own app now (org.lumen.files, apps/LumenFiles): Home's USB card opens it at that drive, the keyboard
 * hint of search opens its Apps view (the .apk files of the drives). Home keeps no file code.
 */
public final class FilesLink {
    public static final String PKG = "org.lumen.files";
    static final String ACTIVITY = "org.lumen.files.ui.FilesActivity";
    static final String EXTRA_PATH = "org.lumen.files.extra.PATH", EXTRA_CATEGORY = "org.lumen.files.extra.CATEGORY";
    /** The USB card's intent: this prefix, then the drive's folder. */
    public static final String FILES = "files:";

    private FilesLink() {}

    public static Intent open(String path) {
        Intent i = new Intent(Intent.ACTION_MAIN).setClassName(PKG, ACTIVITY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (path != null && !path.isEmpty()) i.putExtra(EXTRA_PATH, path);
        return i;
    }

    /** The .apk files of every drive (Install from USB). */
    public static Intent apps() {
        return open(null).putExtra(EXTRA_CATEGORY, "APK");
    }

    /** The drive folder of a USB card's intent, "" when it names none. */
    public static String path(String intent) {
        return intent != null && intent.startsWith(FILES) ? intent.substring(FILES.length()) : "";
    }
}
