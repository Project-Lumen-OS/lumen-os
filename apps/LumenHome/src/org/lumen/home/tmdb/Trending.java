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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Сейчас популярно" and the showcase: TMDB trending of the week in the system language, free for every
 * user with the build's key (TmdbKey). The first five get their facts (genres, running time, age rating for
 * the system region). Kept on disk: refreshed after 6 hours online, the last answer stays while offline; no key and
 * no cache means no showcase (the calm screen). The key never reaches the log.
 */
public final class Trending {
    public static final int HERO = 5, POPULAR = 10;
    static final long TTL_MS = 6 * 3_600_000L;
    private static final String FILE = "trending.json";
    private static final String API = "https://api.themoviedb.org/3/";
    private static final int MAX_BYTES = 1 << 20;

    private Trending() {}

    /** The cached list, refreshed first when {@code network} and the cache is old or of another language. */
    public static synchronized List<Card> load(Context c, boolean network) {
        String lang = Locale.getDefault().toLanguageTag();
        File f = new File(c.getFilesDir(), FILE);
        JSONObject cache = read(f);
        boolean fresh = cache != null && lang.equals(cache.optString("lang"))
                && System.currentTimeMillis() - cache.optLong("time") < TTL_MS;
        String key = TmdbKey.read(c);
        if (network && !fresh && key != null) {
            try {
                JSONObject now = fetch(key, lang);
                Files.write(f.toPath(), now.toString().getBytes(StandardCharsets.UTF_8));
                cache = now;
            } catch (Throwable t) {
                Log.w(App.TAG, "trending: " + t.getClass().getSimpleName());
            }
        }
        return cache == null ? new ArrayList<>() : cards(c.getResources(), cache);
    }

    private static JSONObject read(File f) {
        try {
            if (!f.exists()) return null;
            return new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (Throwable t) {
            return null;
        }
    }

    private static JSONObject fetch(String key, String lang) throws Exception {
        String q = "?api_key=" + key + "&language=" + lang;
        JSONArray res = new JSONObject(TmdbNet.get(API + "trending/all/week" + q, MAX_BYTES)).optJSONArray("results");
        JSONArray items = new JSONArray();
        String region = Locale.getDefault().getCountry();
        for (int i = 0; res != null && i < res.length() && items.length() < POPULAR; i++) {
            JSONObject r = res.getJSONObject(i);
            String type = r.optString("media_type");
            if (!type.equals("movie") && !type.equals("tv")) continue;
            String backdrop = r.optString("backdrop_path", "");
            if (backdrop.isEmpty() || "null".equals(backdrop)) continue;
            JSONObject it = new JSONObject();
            it.put("id", r.optLong("id"));
            it.put("type", type);
            it.put("title", r.optString(type.equals("movie") ? "title" : "name"));
            String date = r.optString(type.equals("movie") ? "release_date" : "first_air_date", "");
            it.put("year", date.length() >= 4 ? date.substring(0, 4) : "");
            it.put("overview", r.optString("overview", ""));
            it.put("backdrop", backdrop);
            if (items.length() < HERO) details(it, type, r.optLong("id"), q, region);
            items.put(it);
        }
        JSONObject out = new JSONObject();
        out.put("time", System.currentTimeMillis());
        out.put("lang", lang);
        out.put("items", items);
        return out;
    }

    /** Genres, running time and the age rating for the region (US as the fallback). */
    private static void details(JSONObject it, String type, long id, String q, String region) {
        try {
            String extra = type.equals("movie") ? "release_dates" : "content_ratings";
            JSONObject d = new JSONObject(TmdbNet.get(API + type + "/" + id + q + "&append_to_response=" + extra, MAX_BYTES));
            JSONArray g = d.optJSONArray("genres");
            JSONArray names = new JSONArray();
            for (int i = 0; g != null && i < g.length() && i < 2; i++) names.put(g.getJSONObject(i).optString("name"));
            it.put("genres", names);
            int run = d.optInt("runtime", 0);
            JSONArray ert = d.optJSONArray("episode_run_time");
            if (run == 0 && ert != null && ert.length() > 0) run = ert.optInt(0);
            it.put("runtime", run);
            it.put("cert", cert(d.optJSONObject(extra), type, region));
        } catch (Throwable t) {
            Log.w(App.TAG, "trending details: " + t.getClass().getSimpleName());
        }
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

    private static List<Card> cards(Resources res, JSONObject cache) {
        ArrayList<Card> out = new ArrayList<>();
        JSONArray items = cache.optJSONArray("items");
        boolean lowerRest = Locale.getDefault().getLanguage().matches("ru|uk|be|kk|pl");
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null) continue;
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
            out.add(k);
        }
        return out;
    }
}
