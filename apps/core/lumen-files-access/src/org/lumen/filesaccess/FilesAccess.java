// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
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
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Gives trusted apps their app ops (FilesAccessPolicy: all files access, drawing over other apps) and media read
 * permissions on install, on update and once per start of the projector app process. Runs in the persistent projector
 * app (platform signed or android.uid.system), which holds MANAGE_APP_OPS_MODES and GRANT_RUNTIME_PERMISSIONS.
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

    /** Grants each app op of FilesAccessPolicy.grants() to pkg when the policy says so. Never throws. */
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
            List<String> asked = pi.requestedPermissions != null ? Arrays.asList(pi.requestedPermissions) : new ArrayList<>();
            int uid = pi.applicationInfo.uid;
            AppOpsManager ops = app.getSystemService(AppOpsManager.class);
            SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            for (String[] g : FilesAccessPolicy.grants()) {
                String op = g[1], key = FilesAccessPolicy.handledKey(pkg, op);
                boolean requested = asked.contains(g[0]);
                boolean allowed = ops.unsafeCheckOpNoThrow(op, uid, pkg) == AppOpsManager.MODE_ALLOWED;
                long handled = prefs.getLong(key, -1);
                if (!FilesAccessPolicy.shouldGrant(pkg, signers, requested, allowed, pi.lastUpdateTime, handled)) {
                    if (FilesAccessPolicy.signedByPin(pkg, signers) && allowed) prefs.edit().putLong(key, pi.lastUpdateTime).apply();
                    continue;
                }
                boolean done = setAllowed(ops, op, uid, pkg);
                if (done) prefs.edit().putLong(key, pi.lastUpdateTime).apply();
                Log.i(TAG, pkg + ": " + op + (done ? " granted" : " refused by the system"));
            }
            PackageManager pm = app.getPackageManager();
            UserHandle user = Process.myUserHandle();
            for (String perm : FilesAccessPolicy.runtimeGrants()) {
                String key = FilesAccessPolicy.handledKey(pkg, perm);
                boolean requested = asked.contains(perm);
                boolean granted = pm.checkPermission(perm, pkg) == PackageManager.PERMISSION_GRANTED;
                boolean fixed = requested && (pm.getPermissionFlags(perm, pkg, user) & FIXED) != 0;
                long handled = prefs.getLong(key, -1);
                if (!FilesAccessPolicy.shouldGrantRuntime(pkg, signers, requested, granted, fixed, pi.lastUpdateTime, handled)) {
                    if (FilesAccessPolicy.signedByPin(pkg, signers) && granted) prefs.edit().putLong(key, pi.lastUpdateTime).apply();
                    continue;
                }
                boolean done = grant(pm, pkg, perm, user);
                if (done) prefs.edit().putLong(key, pi.lastUpdateTime).apply();
                Log.i(TAG, pkg + ": " + perm + (done ? " granted" : " refused by the system"));
            }
        } catch (RuntimeException e) {
            Log.w(TAG, pkg + ": " + e);
        }
    }

    /** A choice the grant must never override: the user's "don't ask again", a policy, the system. */
    private static final int FIXED = PackageManager.FLAG_PERMISSION_USER_FIXED | PackageManager.FLAG_PERMISSION_POLICY_FIXED
            | PackageManager.FLAG_PERMISSION_SYSTEM_FIXED;

    /** PackageManager.grantRuntimePermission (system API, GRANT_RUNTIME_PERMISSIONS), then read back. */
    private static boolean grant(PackageManager pm, String pkg, String perm, UserHandle user) {
        try {
            pm.grantRuntimePermission(pkg, perm, user);
            return pm.checkPermission(perm, pkg) == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException e) {
            Log.w(TAG, "grantRuntimePermission: " + e);
            return false;
        }
    }

    /** AppOpsManager.setUidMode(String, int, int), else setMode(String, int, String, int): system APIs, compiled against
     *  the framework header jar (apps/sdk/framework-full-headers.jar), allowed for the platform signed projector app. */
    private static boolean setAllowed(AppOpsManager ops, String op, int uid, String pkg) {
        try {
            ops.setUidMode(op, uid, AppOpsManager.MODE_ALLOWED);
            return ops.unsafeCheckOpNoThrow(op, uid, pkg) == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException e) {
            Log.w(TAG, "setUidMode: " + e);
        }
        try {
            ops.setMode(op, uid, pkg, AppOpsManager.MODE_ALLOWED);
            return ops.unsafeCheckOpNoThrow(op, uid, pkg) == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException e) {
            Log.w(TAG, "setMode: " + e);
            return false;
        }
    }
}
