package org.lumen.home.watch;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.util.Log;

import org.lumen.home.App;
import org.lumen.home.Launch;
import org.lumen.home.R;
import org.lumen.home.Safe;
import org.lumen.home.data.Card;
import org.lumen.home.search.Relevance;
import org.lumen.home.search.SearchActivity;
import org.lumen.home.search.SuggestProviders;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * The Android side of {@link WatchPick}: the table from the assets, what PackageManager says about each app, the
 * intents, and the watch button. In an official app a title first goes through that app's own search results: an
 * exact title opens its page directly (Kinopoisk publishes its results to the TV search), else the app's search opens
 * with the title. Lampa opens the title's card by its TMDB id. Without an app the button reads "Найти" and opens
 * Lumen search with the title in the field.
 */
public final class WatchLaunch {
    private static volatile WatchApps sTable;

    private WatchLaunch() {}

    public static WatchApps table(Context c) {
        WatchApps t = sTable;
        if (t != null) return t;
        try (InputStream in = c.getAssets().open(WatchApps.ASSET)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            t = WatchApps.parse(new String(b.toByteArray(), StandardCharsets.UTF_8));
        } catch (Throwable e) {
            Log.w(App.TAG, "watch table: " + e);
            t = WatchApps.parse("");
        }
        sTable = t;
        return t;
    }

    /** What this device answers for the table's rows. */
    public static WatchPick.Device device(Context c) {
        PackageManager pm = c.getPackageManager();
        return new WatchPick.Device() {
            @Override
            public String installed(WatchApps.Row r) {
                for (String p : r.pkgs) {
                    try {
                        ApplicationInfo ai = pm.getApplicationInfo(p, 0);
                        if (ai.enabled) return p;
                    } catch (Throwable ignored) {
                    }
                }
                return null;
            }

            @Override
            public boolean opens(WatchApps.Row r, String pkg) {
                return titleIntent(pm, r, pkg, false, 1, "x") != null;
            }
        };
    }

    /** The intent that opens a title in the app, or null when the app has no reliable way. */
    static Intent titleIntent(PackageManager pm, WatchApps.Row r, String pkg, boolean tv, long id, String title) {
        Intent i = null;
        switch (r.open) {
            case TMDB: {
                i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.themoviedb.org/" + (tv ? "tv/" : "movie/") + id))
                        .setPackage(pkg);
                if (pm.resolveActivity(i, 0) == null) {
                    // older Lampa builds: the launcher activity reads id + media (its Android TV channel cards)
                    i = pm.getLeanbackLaunchIntentForPackage(pkg);
                    if (i == null) i = pm.getLaunchIntentForPackage(pkg);
                    if (i != null) i.putExtra("id", String.valueOf(id)).putExtra("media", tv ? "tv" : "movie").putExtra("source", "tmdb");
                    return i == null ? null : i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                break;
            }
            case SEARCH:
                i = searchComponent(pm, pkg, title);
                break;
            case URL:
                i = urlIntent(r, pkg, title);
                break;
            default:
                return null;
        }
        if (i == null || pm.resolveActivity(i, 0) == null) return null;
        return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private static Intent urlIntent(WatchApps.Row r, String pkg, String q) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(r.url.replace("{q}", Uri.encode(q)))).setPackage(pkg);
    }

    /** ACTION_SEARCH to the app's exported search activity, or null. */
    private static Intent searchComponent(PackageManager pm, String pkg, String q) {
        List<ResolveInfo> l = pm.queryIntentActivities(new Intent(Intent.ACTION_SEARCH).setPackage(pkg), 0);
        for (ResolveInfo ri : l) {
            if (ri.activityInfo == null || !ri.activityInfo.exported) continue;
            return new Intent(Intent.ACTION_SEARCH).setComponent(new ComponentName(pkg, ri.activityInfo.name))
                    .putExtra(SearchManager.QUERY, q);
        }
        return null;
    }

    /**
     * "Искать в" for one app: its url (YouTube, SmartTube), else its declared search, else null (the app only opens,
     * the query stays in Lumen search).
     */
    public static Intent searchIn(Context c, String pkg, String q) {
        PackageManager pm = c.getPackageManager();
        WatchApps.Row r = table(c).of(pkg);
        Intent i = r != null && r.open == WatchApps.Open.URL ? urlIntent(r, pkg, q) : searchComponent(pm, pkg, q);
        if (i == null || pm.resolveActivity(i, 0) == null) return null;
        return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** The button text for a pick: "Смотреть в Кинопоиске", "Watch on Lampa"; "Найти" without one. */
    public static String label(Context c, WatchPick.Pick p) {
        if (p == null) return c.getString(R.string.hero_find);
        boolean ru = "ru".equals(Locale.getDefault().getLanguage());
        return c.getString(R.string.hero_watch_in, ru ? p.row.inRu : p.row.name);
    }

    /** "Смотреть" on a showcase title: the picked app, else Lumen search with the title. */
    public static void open(Activity a, Card k) {
        WatchApps.Row r = k.watchPkg.isEmpty() ? null : table(a).of(k.watchPkg);
        if (r == null) {
            find(a, k.title);
            return;
        }
        String[] id = k.id.split(":");
        boolean tv = id.length > 1 && "tv".equals(id[1]);
        long tmdb = id.length > 2 ? parse(id[2]) : 0;
        Context app = a.getApplicationContext();
        if (r.open != WatchApps.Open.SEARCH) {
            Intent i = titleIntent(a.getPackageManager(), r, k.watchPkg, tv, tmdb, k.title);
            if (i == null) find(a, k.title);
            else Launch.start(a, i, "watch " + k.id + " in " + k.watchPkg);
            return;
        }
        App.get().io().post(() -> Safe.run("watch", () -> {
            Card best = null;
            for (Card s : SuggestProviders.query(app, k.title, k.watchPkg)) {
                if (Relevance.score(k.title, s.title) == Relevance.EXACT) {
                    best = s;
                    break;
                }
            }
            final Card hit = best;
            a.runOnUiThread(() -> {
                if (hit != null && Launch.open(a, hit)) return;
                Intent i = titleIntent(a.getPackageManager(), r, k.watchPkg, tv, tmdb, k.title);
                if (i == null) find(a, k.title);
                else Launch.start(a, i, "watch " + k.id + " search in " + k.watchPkg);
            });
        }));
    }

    /** Lumen search with the query in the field. */
    public static void find(Context c, String q) {
        Launch.start(c, new Intent(c, SearchActivity.class).putExtra(SearchManager.QUERY, q)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "find " + q.length());
    }

    private static long parse(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
