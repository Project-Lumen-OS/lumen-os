package org.lumen.home.tmdb;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.lumen.home.App;
import org.lumen.home.R;
import org.lumen.home.data.Card;
import org.lumen.home.img.ArtUrl;
import org.lumen.home.watch.Region;
import org.lumen.home.watch.Showcase;
import org.lumen.home.watch.WatchApps;
import org.lumen.home.watch.WatchLaunch;
import org.lumen.home.watch.WatchPick;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * "Сейчас популярно" and the showcase: what to watch, only titles a user can watch now
 * ({@link Showcase}). Candidates: the popular released titles of the official apps installed here (TMDB discover by
 * their watch providers in the user's region, {@link Region}), then TMDB trending of the week. Each gets its facts
 * (genres, running time, age rating, rating, home release, watch providers in the region) in one call. The app a
 * title opens in is picked per card from what is installed ({@link WatchPick}). Kept on disk: refreshed after 6 hours
 * online or when the language, region or the installed official apps change, the last answer stays while offline; no
 * key and no cache means no showcase (the calm screen). Read on the screen's data lane ({@link #load}), fetched on the
 * network lane ({@link #refresh}). The key never reaches the log.
 */
public final class Trending {
    public static final int HERO = 5, POPULAR = 10;
    static final long TTL_MS = 6 * 3_600_000L;
    static final int CANDIDATES = 20, FROM_APPS = 6, DETAILS = 16;
    static final long DETAILS_BUDGET_MS = 12_000;
    private static final String FILE = "trending.json";
    private static final String API = "https://api.themoviedb.org/3/";
    private static final int MAX_BYTES = 1 << 20;

    private Trending() {}

    /** The cached list (no network, the screen's data lane): the showcase titles in their order, empty without a cache. */
    public static List<Card> load(Context c) {
        JSONObject cache;
        synchronized (Trending.class) {
            cache = read(new File(c.getFilesDir(), FILE));
        }
        return cache == null ? new ArrayList<>() : cards(c, cache, WatchLaunch.table(c), WatchLaunch.device(c));
    }

    /**
     * Network lane only: fetches the list when the cache is older than {@link #TTL_MS} or of another language, region
     * or set of installed official apps, and keeps it; true when a new list was stored (the screen then reloads from the
     * cache). The file lock is not held while the requests run, so the screen's own read never waits for TMDB.
     */
    public static boolean refresh(Context c) {
        String lang = Locale.getDefault().toLanguageTag();
        String region = Region.of(TimeZone.getDefault().getID(), Locale.getDefault().getCountry());
        List<Integer> pids = WatchPick.installedProviderIds(WatchLaunch.table(c), WatchLaunch.device(c));
        String ident = lang + "|" + region + "|" + pids;
        File f = new File(c.getFilesDir(), FILE);
        JSONObject cache;
        synchronized (Trending.class) {
            cache = read(f);
        }
        boolean fresh = cache != null && ident.equals(cache.optString("ident"))
                && System.currentTimeMillis() - cache.optLong("time") < TTL_MS;
        String key = TmdbKey.read(c);
        if (fresh || key == null) return false;
        long t0 = android.os.SystemClock.uptimeMillis();
        try {
            JSONObject now = fetch(key, lang, region, pids);
            now.put("ident", ident);
            synchronized (Trending.class) {
                Files.write(f.toPath(), now.toString().getBytes(StandardCharsets.UTF_8));
            }
            Log.i(App.TAG, "trending fetched ms=" + (android.os.SystemClock.uptimeMillis() - t0));
            return true;
        } catch (Throwable t) {
            Log.w(App.TAG, "trending: " + t.getClass().getSimpleName() + " ms=" + (android.os.SystemClock.uptimeMillis() - t0));
            return false;
        }
    }

    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
    }

    private static JSONObject read(File f) {
        try {
            if (!f.exists()) return null;
            return new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (Throwable t) {
            return null;
        }
    }

    private static JSONObject fetch(String key, String lang, String region, List<Integer> pids) throws Exception {
        String q = "?api_key=" + key + "&language=" + lang, today = today();
        LinkedHashMap<String, JSONObject> cand = new LinkedHashMap<>();
        if (!pids.isEmpty()) {
            StringBuilder ids = new StringBuilder();
            for (int id : pids) ids.append(ids.length() == 0 ? "" : "|").append(id);
            for (String type : new String[] { "movie", "tv" }) {
                String until = type.equals("movie") ? "&primary_release_date.lte=" : "&first_air_date.lte=";
                try {
                    String url = API + "discover/" + type + q + "&watch_region=" + region + "&with_watch_providers="
                            + ids + "&sort_by=popularity.desc" + until + today;
                    add(cand, new JSONObject(TmdbNet.get(url, MAX_BYTES)).optJSONArray("results"), type, FROM_APPS, today);
                } catch (Throwable t) {
                    Log.w(App.TAG, "trending discover: " + t.getClass().getSimpleName());
                }
            }
        }
        JSONArray tr = new JSONObject(TmdbNet.get(API + "trending/all/week" + q, MAX_BYTES)).optJSONArray("results");
        add(cand, tr, null, CANDIDATES, today);
        JSONArray items = new JSONArray();
        long until = System.currentTimeMillis() + DETAILS_BUDGET_MS;
        int n = 0;
        for (JSONObject it : cand.values()) {
            if (n++ < DETAILS && System.currentTimeMillis() < until) details(it, q, region);
            items.put(it);
        }
        JSONObject out = new JSONObject();
        out.put("time", System.currentTimeMillis());
        out.put("lang", lang);
        out.put("region", region);
        out.put("items", items);
        return out;
    }

    /** Released titles with a backdrop, each once, until {@code max} were added from this list. */
    private static void add(LinkedHashMap<String, JSONObject> cand, JSONArray res, String type, int max, String today)
            throws Exception {
        int added = 0;
        for (int i = 0; res != null && i < res.length() && added < max && cand.size() < CANDIDATES; i++) {
            JSONObject r = res.getJSONObject(i);
            String t = type != null ? type : r.optString("media_type");
            if (!t.equals("movie") && !t.equals("tv")) continue;
            String backdrop = r.optString("backdrop_path", "");
            String date = r.optString(t.equals("movie") ? "release_date" : "first_air_date", "");
            if (backdrop.isEmpty() || "null".equals(backdrop) || !Showcase.released(date, today)) continue;
            String k = t + ":" + r.optLong("id");
            if (cand.containsKey(k)) continue;
            JSONObject it = new JSONObject();
            it.put("id", r.optLong("id"));
            it.put("type", t);
            it.put("title", r.optString(t.equals("movie") ? "title" : "name"));
            it.put("date", date);
            it.put("year", date.substring(0, 4));
            it.put("overview", r.optString("overview", ""));
            it.put("backdrop", backdrop);
            double vote = r.optDouble("vote_average", 0);
            if (r.optInt("vote_count", 0) >= 20 && vote > 0) it.put("vote", String.format(Locale.ROOT, "%.1f", vote));
            cand.put(k, it);
            added++;
        }
    }

    /** Genres, running time, the age rating for the region (US as the fallback), home release, watch providers. */
    private static void details(JSONObject it, String q, String region) {
        String type = it.optString("type");
        try {
            String extra = type.equals("movie") ? "release_dates" : "content_ratings";
            JSONObject d = new JSONObject(TmdbNet.get(API + type + "/" + it.optLong("id") + q + "&append_to_response="
                    + extra + ",watch/providers", MAX_BYTES));
            JSONArray g = d.optJSONArray("genres");
            JSONArray names = new JSONArray();
            for (int i = 0; g != null && i < g.length() && i < 2; i++) names.put(g.getJSONObject(i).optString("name"));
            it.put("genres", names);
            int run = d.optInt("runtime", 0);
            JSONArray ert = d.optJSONArray("episode_run_time");
            if (run == 0 && ert != null && ert.length() > 0) run = ert.optInt(0);
            it.put("runtime", run);
            it.put("cert", cert(d.optJSONObject(extra), type, region));
            if (type.equals("movie")) it.put("home", homeDate(d.optJSONObject(extra)));
            JSONObject wp = d.optJSONObject("watch/providers");
            JSONObject all = wp == null ? null : wp.optJSONObject("results");
            it.put("anyProv", all != null && all.length() > 0);
            JSONArray ids = new JSONArray(), pn = new JSONArray();
            JSONObject mine = all == null ? null : all.optJSONObject(region);
            for (String kind : new String[] { "flatrate", "free", "ads", "rent", "buy" }) {
                JSONArray l = mine == null ? null : mine.optJSONArray(kind);
                for (int i = 0; l != null && i < l.length(); i++) {
                    JSONObject p = l.getJSONObject(i);
                    int id = p.optInt("provider_id");
                    boolean seen = false;
                    for (int k = 0; k < ids.length(); k++) seen |= ids.optInt(k) == id;
                    if (seen) continue;
                    ids.put(id);
                    pn.put(p.optString("provider_name"));
                }
            }
            it.put("prov", ids);
            it.put("provNames", pn);
            it.put("det", true);
        } catch (Throwable t) {
            Log.w(App.TAG, "trending details: " + t.getClass().getSimpleName());
        }
    }

    /** The earliest digital (4), disc (5) or TV (6) release of a film in any country, "" none. */
    static String homeDate(JSONObject block) {
        String best = "";
        JSONArray rs = block == null ? null : block.optJSONArray("results");
        for (int i = 0; rs != null && i < rs.length(); i++) {
            JSONArray ds = rs.optJSONObject(i) == null ? null : rs.optJSONObject(i).optJSONArray("release_dates");
            for (int k = 0; ds != null && k < ds.length(); k++) {
                JSONObject x = ds.optJSONObject(k);
                int t = x == null ? 0 : x.optInt("type");
                String date = x == null ? "" : x.optString("release_date", "");
                if (t >= 4 && t <= 6 && date.length() >= 10 && (best.isEmpty() || date.compareTo(best) < 0)) best = date.substring(0, 10);
            }
        }
        return best;
    }

    static String cert(JSONObject block, String type, String region) {
        if (block == null) return "";
        JSONArray rs = block.optJSONArray("results");
        String us = "";
        for (int i = 0; rs != null && i < rs.length(); i++) {
            JSONObject r = rs.optJSONObject(i);
            if (r == null) continue;
            String cc = r.optString("iso_3166_1");
            String v = "";
            if (type.equals("tv")) v = r.optString("rating", "");
            else {
                JSONArray ds = r.optJSONArray("release_dates");
                for (int k = 0; ds != null && k < ds.length() && v.isEmpty(); k++) v = ds.optJSONObject(k).optString("certification", "");
            }
            if (cc.equalsIgnoreCase(region) && !v.isEmpty()) return age(v);
            if (cc.equals("US") && !v.isEmpty()) us = v;
        }
        return age(us);
    }

    /** "16" becomes "16+"; letter ratings (PG-13) stay as they are. */
    static String age(String v) {
        v = v.trim();
        return v.matches("\\d{1,2}") ? v + "+" : v;
    }

    private static List<Integer> ints(JSONArray a) {
        ArrayList<Integer> out = new ArrayList<>();
        for (int i = 0; a != null && i < a.length(); i++) out.add(a.optInt(i));
        return out;
    }

    /** What {@link Showcase} needs of a cached title (an old cache without dates shows nothing). */
    static Showcase.Item item(JSONObject it) {
        boolean det = it.optBoolean("det", false);
        return new Showcase.Item("tv".equals(it.optString("type")), it.optString("date", ""),
                det ? it.optString("home", "") : null, ints(it.optJSONArray("prov")), it.optBoolean("anyProv", false));
    }

    private static List<Card> cards(Context c, JSONObject cache, WatchApps table, WatchPick.Device dev) {
        Resources res = c.getResources();
        ArrayList<JSONObject> all = new ArrayList<>();
        JSONArray items = cache.optJSONArray("items");
        for (int i = 0; items != null && i < items.length(); i++) if (items.optJSONObject(i) != null) all.add(items.optJSONObject(i));
        List<JSONObject> shown = Showcase.order(all, Trending::item, today(),
                p -> WatchPick.inInstalledProvider(table, p, dev));
        ArrayList<Card> out = new ArrayList<>();
        boolean lowerRest = Locale.getDefault().getLanguage().matches("ru|uk|be|kk|pl");
        for (JSONObject it : shown) {
            if (out.size() >= POPULAR) break;
            Card k = new Card(Card.FEATURE, "tmdb:" + it.optString("type") + ":" + it.optLong("id"), it.optString("title"));
            k.year = it.optString("year");
            k.desc = it.optString("overview");
            k.episodic = "tv".equals(it.optString("type"));
            String b = it.optString("backdrop");
            k.image = ArtUrl.TMDB_IMG + "w1280" + b;
            k.image2 = ArtUrl.TMDB_IMG + "w780" + b;
            ArrayList<String> parts = new ArrayList<>();
            if (!k.year.isEmpty()) parts.add(k.year);
            JSONArray g = it.optJSONArray("genres");
            if (g != null && g.length() > 0) {
                StringBuilder s = new StringBuilder(g.optString(0));
                for (int j = 1; j < g.length(); j++) {
                    String n = g.optString(j);
                    s.append(", ").append(lowerRest ? n.toLowerCase(Locale.getDefault()) : n);
                }
                parts.add(s.toString());
            }
            int run = it.optInt("runtime", 0);
            if (run > 0) {
                parts.add(run < 60 ? res.getString(R.string.dur_min, run)
                        : res.getString(R.string.dur_h_min, run / 60, run % 60));
            }
            String cert = it.optString("cert", "");
            if (!cert.isEmpty()) parts.add(cert);
            k.meta = String.join(" · ", parts);
            k.left = k.year;   // the card caption: year
            if (g != null && g.length() > 0) k.left = k.year.isEmpty() ? g.optString(0) : k.year + " · " + g.optString(0);
            k.rating = it.optString("vote", "");
            List<Integer> prov = ints(it.optJSONArray("prov"));
            WatchPick.Pick pick = WatchPick.forTitle(table, prov, dev);
            k.watchPkg = pick == null ? "" : pick.pkg;
            k.watchLabel = WatchLaunch.label(c, pick);
            k.where = where(table, prov, it.optJSONArray("provNames"), pick);
            out.add(k);
        }
        return out;
    }

    /** Where to watch for Подробнее: the region's providers (our names for the apps we know), the picked app first. */
    static String where(WatchApps table, List<Integer> prov, JSONArray names, WatchPick.Pick pick) {
        ArrayList<String> out = new ArrayList<>();
        if (pick != null) out.add(pick.row.name);
        for (int i = 0; i < prov.size(); i++) {
            String n = names == null ? "" : names.optString(i, "");
            for (WatchApps.Row r : table.rows()) if (r.offers(prov.get(i))) n = r.name;
            if (!n.isEmpty() && !out.contains(n)) out.add(n);
        }
        return String.join(", ", out);
    }
}
