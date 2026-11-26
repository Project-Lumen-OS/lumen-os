// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.data;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import org.lumen.home.App;
import org.lumen.home.model.MemoryRules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Free up memory (Customize Home): closes the background processes of apps the user installed, the way Android does
 * when it needs the memory ({@link ActivityManager#killBackgroundProcesses}, never a force stop), and says how much
 * memory came back. Which apps: {@link MemoryRules}. Runs on Home's data lane, never on the main thread.
 */
public final class MemoryCleaner {
    /** The time Android takes to hand the memory of closed processes back before the second reading. */
    static final long SETTLE_MS = 1500;

    private MemoryCleaner() {}

    public static final class Result {
        public final long freedMb;
        public final int closed;

        Result(long freedMb, int closed) {
            this.freedMb = freedMb;
            this.closed = closed;
        }
    }

    public static Result run(Context c) {
        ActivityManager am = c.getSystemService(ActivityManager.class);
        PackageManager pm = c.getPackageManager();
        long before = avail(am);
        List<MemoryRules.Proc> procs = new ArrayList<>();
        List<ActivityManager.RunningAppProcessInfo> run = am.getRunningAppProcesses();
        if (run != null) {
            for (ActivityManager.RunningAppProcessInfo p : run) {
                if (p.pkgList == null) continue;
                procs.add(new MemoryRules.Proc(Arrays.asList(p.pkgList), p.importance, system(pm, p.pkgList), 0));
            }
        }
        List<String> victims = MemoryRules.victims(procs, keep(c));
        for (String pkg : victims) {
            try {
                am.killBackgroundProcesses(pkg);
            } catch (Throwable t) {
                Log.w(App.TAG, "free memory: " + pkg + ": " + t);
            }
        }
        if (!victims.isEmpty()) SystemClock.sleep(SETTLE_MS);
        long freed = MemoryRules.freedMb(before, avail(am));
        Log.i(App.TAG, "free memory: closed " + victims + ", freed " + freed + " MB");
        return new Result(freed, victims.size());
    }

    private static long avail(ActivityManager am) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem;
    }

    /** A process counts as system when any of its packages is (a shared uid process of the system image). */
    private static boolean system(PackageManager pm, String[] pkgs) {
        for (String p : pkgs) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(p, 0);
                if ((ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) return true;
            } catch (PackageManager.NameNotFoundException e) {
                return true;                                  // not ours to judge: leave it
            }
        }
        return false;
    }

    /**
     * Never closed, whatever Android counts them as (the protected set of the Recents screen's Close all, projector
     * app mem/ProtectedSet): Home and the other HOME and assistant holders, the keyboards, the screensaver, the
     * accessibility services, notification listeners, the speech services, and every app with a VPN service (a VPN
     * such as ByeDPI must never drop because memory was freed).
     */
    private static Set<String> keep(Context c) {
        Set<String> k = new HashSet<>();
        k.add(c.getPackageName());
        addComponents(k, setting(c, Settings.Secure.DEFAULT_INPUT_METHOD));
        addComponents(k, setting(c, Settings.Secure.ENABLED_INPUT_METHODS));
        addComponents(k, setting(c, "screensaver_components").replace(',', ':'));
        addComponents(k, setting(c, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES));
        addComponents(k, setting(c, "enabled_notification_listeners"));
        addComponents(k, setting(c, "voice_recognition_service"));
        addComponents(k, setting(c, "voice_interaction_service"));
        try {
            android.app.role.RoleManager rm = c.getSystemService(android.app.role.RoleManager.class);
            k.addAll(rm.getRoleHolders(android.app.role.RoleManager.ROLE_HOME));
            k.addAll(rm.getRoleHolders(android.app.role.RoleManager.ROLE_ASSISTANT));
        } catch (Throwable t) {
            Log.w(App.TAG, "free memory: roles: " + t);
        }
        try {
            for (android.content.pm.ResolveInfo ri : c.getPackageManager().queryIntentServices(
                    new android.content.Intent(android.net.VpnService.SERVICE_INTERFACE), 0)) {
                if (ri.serviceInfo != null) k.add(ri.serviceInfo.packageName);
            }
        } catch (Throwable t) {
            Log.w(App.TAG, "free memory: vpn apps: " + t);
        }
        return k;
    }

    private static String setting(Context c, String key) {
        try {
            String v = Settings.Secure.getString(c.getContentResolver(), key);
            return v == null ? "" : v;
        } catch (Throwable t) {
            return "";
        }
    }

    /** Packages of a ':' separated component list (pkg/.Cls:pkg2/.Cls). */
    private static void addComponents(Set<String> k, String v) {
        if (v == null) return;
        for (String part : v.split(":")) {
            int s = part.indexOf('/');
            String p = (s > 0 ? part.substring(0, s) : part).trim();
            if (!p.isEmpty()) k.add(p);
        }
    }
}
