// SPDX-License-Identifier: Apache-2.0
package org.lumen.filesaccess;

import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Gives trusted user apps all files access (FilesAccessPolicy) on install, on update and once per start of the
 * projector app process. Runs in the persistent projector app (platform signed or android.uid.system), which holds
 * MANAGE_APP_OPS_MODES.
 */
public final class FilesAccess {
    private FilesAccess() {}

    private static final String TAG = "LumenFilesAccess";
    private static final String PREFS = "lumen_files_access";
    private static boolean listening;

    /** Starts the package listener and checks every trusted package once, off the main thread. */
    public static synchronized void start(Context context) {
        final Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        if (!listening) {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_PACKAGE_ADDED);
            f.addAction(Intent.ACTION_PACKAGE_REPLACED);
            f.addDataScheme("package");
            try {
                app.registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context c, Intent i) {
                        Uri data = i.getData();
                        final String pkg = data != null ? data.getSchemeSpecificPart() : null;
                        if (pkg == null || FilesAccessPolicy.pin(pkg) == null) return;
                        final PendingResult r = goAsync();
                        new Thread(() -> {
                            try { check(app, pkg); } finally { r.finish(); }
                        }, "lumen-files-access").start();
                    }
                }, f, Context.RECEIVER_NOT_EXPORTED);
                listening = true;
            } catch (RuntimeException e) {
                Log.w(TAG, "no package listener: " + e);
            }
        }
        new Thread(() -> {
            for (String pkg : FilesAccessPolicy.packages()) check(app, pkg);
        }, "lumen-files-access").start();
    }

    /** Grants all files access to pkg when the policy says so. Never throws. */
    static void check(Context app, String pkg) {
        try {
            PackageInfo pi;
            try {
                pi = app.getPackageManager().getPackageInfo(pkg,
                    PackageManager.GET_SIGNING_CERTIFICATES | PackageManager.GET_PERMISSIONS);
            } catch (PackageManager.NameNotFoundException e) {
                return;
            }
            // the current signers only (after a key rotation: the newest key), never the history
            List<String> signers = new ArrayList<>();
            if (pi.signingInfo != null) {
                for (Signature s : pi.signingInfo.getApkContentsSigners()) signers.add(FilesAccessPolicy.sha256(s.toByteArray()));
            }
            boolean requested = pi.requestedPermissions != null
                && Arrays.asList(pi.requestedPermissions).contains(FilesAccessPolicy.PERMISSION);
            int uid = pi.applicationInfo.uid;
            AppOpsManager ops = app.getSystemService(AppOpsManager.class);
            boolean allowed = ops.unsafeCheckOpNoThrow(FilesAccessPolicy.OP, uid, pkg) == AppOpsManager.MODE_ALLOWED;
            SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long handled = prefs.getLong(pkg, -1);
            if (!FilesAccessPolicy.shouldGrant(pkg, signers, requested, allowed, pi.lastUpdateTime, handled)) {
                if (FilesAccessPolicy.signedByPin(pkg, signers) && allowed) prefs.edit().putLong(pkg, pi.lastUpdateTime).apply();
                return;
            }
            boolean done = setAllowed(ops, uid, pkg);
            if (done) prefs.edit().putLong(pkg, pi.lastUpdateTime).apply();
            Log.i(TAG, pkg + ": all files access " + (done ? "granted" : "refused by the system"));
        } catch (RuntimeException e) {
            Log.w(TAG, pkg + ": " + e);
        }
    }

    /** AppOpsManager.setUidMode(String, int, int), else setMode(String, int, String, int): system APIs, compiled against
     *  the framework header jar (apps/sdk/framework-full-headers.jar), allowed for the platform signed projector app. */
    private static boolean setAllowed(AppOpsManager ops, int uid, String pkg) {
        try {
            ops.setUidMode(FilesAccessPolicy.OP, uid, AppOpsManager.MODE_ALLOWED);
            return ops.unsafeCheckOpNoThrow(FilesAccessPolicy.OP, uid, pkg) == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException e) {
            Log.w(TAG, "setUidMode: " + e);
        }
        try {
            ops.setMode(FilesAccessPolicy.OP, uid, pkg, AppOpsManager.MODE_ALLOWED);
            return ops.unsafeCheckOpNoThrow(FilesAccessPolicy.OP, uid, pkg) == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException e) {
            Log.w(TAG, "setMode: " + e);
            return false;
        }
    }
}
