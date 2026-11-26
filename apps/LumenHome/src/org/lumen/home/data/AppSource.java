// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.data;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import org.lumen.home.App;
import org.lumen.home.model.AppPick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Installed apps: packages with a MAIN+LEANBACK_LAUNCHER activity, plus MAIN+LAUNCHER for packages
 * without a leanback entry, so phone-only apps (LocalSend, ByeDPI) stay reachable. One entry per package, on the
 * activity the system launches ({@link AppPick}), label from that activity. Runs on the io thread.
 */
public final class AppSource {
    private AppSource() {}

    public static final String PKG_YOUTUBE = "com.google.android.youtube.tv";
    public static final String PKG_KINOPOISK = "ru.kinopoisk.tv";
    public static final String PKG_PLAY = "com.android.vending";

    public static final class Entry {
        public ComponentName cn;
        public String label;
        public boolean leanback;
        public boolean system;
        public long updated;
        public long installed;
    }

    /** The apps (one per package) and every launcher activity they were picked from. */
    public static final class Apps {
        public final List<Entry> entries = new ArrayList<>();
        public final List<AppPick.Activity> resolved = new ArrayList<>();
    }

    public static Apps load(Context c) {
        PackageManager pm = c.getPackageManager();
        Apps out = new Apps();
        Map<String, ResolveInfo> infos = new HashMap<>();
        HashSet<String> lbPkgs = new HashSet<>();
        query(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, Collections.emptySet(), infos, out.resolved);
        for (AppPick.Activity a : out.resolved) lbPkgs.add(a.pkg);
        query(pm, Intent.CATEGORY_LAUNCHER, lbPkgs, infos, out.resolved);
        Map<String, long[]> times = new HashMap<>();
        for (AppPick.Activity a : AppPick.onePerPackage(out.resolved, new AppPick.Launch() {
            public String leanback(String pkg) { return flat(pm.getLeanbackLaunchIntentForPackage(pkg)); }
            public String main(String pkg) { return flat(pm.getLaunchIntentForPackage(pkg)); }
        })) {
            Entry e = make(pm, a, infos.get(a.component), times);
            if (e != null) out.entries.add(e);
        }
        return out;
    }

    private static void query(PackageManager pm, String category, Set<String> skip, Map<String, ResolveInfo> infos,
                              List<AppPick.Activity> out) {
        boolean leanback = Intent.CATEGORY_LEANBACK_LAUNCHER.equals(category);
        for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || skip.contains(ai.packageName)) continue;
            String cn = new ComponentName(ai.packageName, ai.name).flattenToString();
            if (infos.putIfAbsent(cn, ri) == null) out.add(new AppPick.Activity(ai.packageName, cn, leanback));
        }
    }

    private static String flat(Intent i) {
        ComponentName cn = i == null ? null : i.getComponent();
        return cn == null ? null : cn.flattenToString();
    }

    /** The entry of the picked activity: label from it (a resolved one, else its ActivityInfo, else the app's). */
    private static Entry make(PackageManager pm, AppPick.Activity pick, ResolveInfo ri, Map<String, long[]> times) {
        try {
            Entry a = new Entry();
            a.cn = ComponentName.unflattenFromString(pick.component);
            ActivityInfo ai = ri != null ? ri.activityInfo : pm.getActivityInfo(a.cn, 0);
            CharSequence l = ri != null ? ri.loadLabel(pm) : ai.loadLabel(pm);
            a.label = l == null ? pick.pkg : l.toString().trim();
            if (a.label.length() > 120) a.label = a.label.substring(0, 120);
            a.leanback = pick.leanback;
            ApplicationInfo app = ai.applicationInfo;
            a.system = app != null && (app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            long[] t = times.get(pick.pkg);
            if (t == null) {
                t = new long[]{0, 0};
                try {
                    PackageInfo pi = pm.getPackageInfo(pick.pkg, 0);
                    t[0] = pi.lastUpdateTime;
                    t[1] = pi.firstInstallTime;
                } catch (PackageManager.NameNotFoundException ignored) {
                }
                times.put(pick.pkg, t);
            }
            a.updated = t[0];
            a.installed = t[1];
            return a;
        } catch (Throwable t) {
            Log.w(App.TAG, "app entry " + pick.component + ": " + t);
            return null;
        }
    }

    /** package -> lastTimeUsed over the last 28 days (PACKAGE_USAGE_STATS; empty if not granted). */
    public static Map<String, Long> lastUsed(Context c) {
        HashMap<String, Long> m = new HashMap<>();
        try {
            UsageStatsManager um = c.getSystemService(UsageStatsManager.class);
            long now = System.currentTimeMillis();
            Map<String, UsageStats> agg = um.queryAndAggregateUsageStats(now - 28L * 86_400_000L, now);
            if (agg != null) {
                for (Map.Entry<String, UsageStats> e : agg.entrySet()) {
                    UsageStats s = e.getValue();
                    long t = Math.max(s.getLastTimeUsed(), s.getLastTimeVisible());
                    if (t > 0 && s.getTotalTimeInForeground() > 0) m.put(e.getKey(), t);
                }
            }
        } catch (Throwable t) {
            Log.w(App.TAG, "usage stats: " + t);
        }
        return m;
    }

    /** Art key of an app banner/tile: changes with the app version and its label. */
    public static String imageKey(Entry a) {
        // the generated tile renders the label, so a new label or language means new art
        // "|d" = direction D tiles (new size, new fallback look): one regeneration after the update
        return "app:" + a.cn.flattenToString() + "|" + a.updated + "|" + Integer.toHexString(a.label.hashCode()) + "|d";
    }
}
