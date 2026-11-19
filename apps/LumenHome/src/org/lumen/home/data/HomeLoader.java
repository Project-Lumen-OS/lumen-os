package org.lumen.home.data;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import org.lumen.home.App;
import org.lumen.home.Prefs;
import org.lumen.home.R;
import org.lumen.home.model.AppOrder;
import org.lumen.home.model.AppPick;
import org.lumen.home.model.ContinueRules;
import org.lumen.home.model.ContinueText;
import org.lumen.home.model.RowPlan;
import org.lumen.home.proj.MirrorReceiver;
import org.lumen.home.tmdb.TmdbArt;
import org.lumen.home.tmdb.Trending;
import org.lumen.home.tvp.ProgramsInitializer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds {@link HomeData} on the screen's data lane (model/Lanes.LOAD): apps, Watch Next, inputs and the cached TMDB
 * showcase. Local reads only: nothing here waits for a server (the showcase and the key art are fetched on the network
 * lane and stored, then a reload reads them). Each step's time goes to the log line, a slow one is named.
 */
public final class HomeLoader {
    public static final String K_UNHIDDEN_APPS = "unhidden_apps";
    private static final String K_LAST_MODE = "last_launcher_mode";
    private static final String SECURE_LAUNCHER = "z9x_launcher";

    private HomeLoader() {}

    /** A step slower than this is named in the log (slow= list), the live measurement script greps for it. */
    static final long SLOW_MS = 700;

    public static HomeData load(Context c, App app) {
        long t0 = SystemClock.uptimeMillis();
        Steps st = new Steps();
        Prefs p = app.prefs();
        HomeData d = new HomeData();
        Resources res = c.getResources();
        AppSource.Apps found = AppSource.load(c);
        List<AppSource.Entry> raw = found.entries;
        trackPackages(c, p, raw);
        st.done("apps");
        migrateAppKeys(p, found.resolved);
        Map<String, AppSource.Entry> byPkg = new HashMap<>();
        Map<String, String> labels = new HashMap<>();
        ArrayList<AppOrder.App> all = new ArrayList<>();
        org.lumen.parental.ParentalState lock = org.lumen.parental.Parental.load(c);
        for (AppSource.Entry a : raw) {
            String pkg = a.cn.getPackageName();
            if (!org.lumen.parental.ParentalRules.visible(lock, pkg)) continue;   // child mode: the parent's picks only
            byPkg.put(pkg, a);
            all.add(new AppOrder.App(pkg, a.label));
            labels.put(pkg, a.label);
        }
        Set<String> hidden = p.set(Prefs.K_HIDDEN_APPS), unhidden = p.set(K_UNHIDDEN_APPS);
        List<String> first = Arrays.asList(AppSource.PKG_YOUTUBE, AppSource.PKG_KINOPOISK, AppSource.PKG_PLAY);
        Locale loc = Locale.getDefault();
        for (AppOrder.App a : AppOrder.order(all, p.list(Prefs.K_FAVORITES), hidden, unhidden, AppOrder.DEFAULT_HIDDEN, first, loc)) {
            d.apps.add(appCard(byPkg.get(a.pkg)));
        }
        for (AppOrder.App a : AppOrder.hiddenOnes(all, hidden, unhidden, AppOrder.DEFAULT_HIDDEN, loc)) {
            d.hidden.add(appCard(byPkg.get(a.pkg)));
        }
        TvpSource.Result tvp = p.continueOnHome() ? TvpSource.load(c, labels) : TvpSource.off();
        st.done("tvp");
        TmdbArt tmdb = app.tmdb();
        List<Card> cont = ContinueRules.pick(tvp.watchNext, ContinueRules.MAX);
        if (tmdb != null) tmdb.apply(cont);
        ContinueText.Words words = words(res);
        for (Card k : cont) {
            k.meta = ContinueText.seasonLine(k.season, k.episode, k.episodeTitle, k.title, words);
            k.left = ContinueText.leftLine(k.wnType, k.durationMs, k.positionMs, words);
            k.desc = ContinueText.description(k.desc, k.episodic, k.episodeTitle, "");
            k.progress = ContinueText.showsProgress(k.wnType, k.durationMs, k.positionMs)
                    ? ContinueText.permille(k.durationMs, k.positionMs) : -1;
            d.cont.add(k);
        }
        st.done("tmdb");
        inputs(c, p, d);
        st.done("inputs");
        d.online = online(c);
        List<Card> tr = Trending.load(c);
        for (int i = 0; i < tr.size() && i < RowPlan.MAX_HERO; i++) d.hero.add(tr.get(i));
        st.done("showcase");
        d.plan = RowPlan.of(d.hero.size(), d.cont.size(), d.apps.size());
        Log.i(App.TAG, "home apps=" + d.apps.size() + " cont=" + d.cont.size() + " hero=" + d.hero.size()
                + " inputs=" + d.inputs.size() + " online=" + d.online + " calm=" + d.plan.calm
                + " ms=" + (SystemClock.uptimeMillis() - t0) + st.slow());
        return d;
    }

    /** Times of the loader's steps; only the slow ones reach the log. */
    private static final class Steps {
        private long mAt = SystemClock.uptimeMillis();
        private final StringBuilder mSlow = new StringBuilder();

        void done(String name) {
            long now = SystemClock.uptimeMillis();
            if (now - mAt >= SLOW_MS) mSlow.append(mSlow.length() == 0 ? " slow=" : ",").append(name).append(':').append(now - mAt);
            mAt = now;
        }

        String slow() {
            return mSlow.toString();
        }
    }

    static Card appCard(AppSource.Entry a) {
        Card k = new Card(Card.APP, a.cn.getPackageName(), a.label);
        k.pkg = a.cn.getPackageName();
        k.appLabel = a.label;
        k.system = a.system;
        k.image = AppSource.imageKey(a);
        k.intent = a.cn.flattenToString();
        k.color = TvpSource.placeholder(k.pkg);
        return k;
    }

    /** Order and hidden apps stored by component (Home before one app per package) move to packages, once. */
    private static void migrateAppKeys(Prefs p, List<AppPick.Activity> resolved) {
        List<String> order = p.list(Prefs.K_FAVORITES);
        Set<String> hidden = p.set(Prefs.K_HIDDEN_APPS), shown = p.set(K_UNHIDDEN_APPS);
        if (AppPick.oldKeys(order)) p.putList(Prefs.K_FAVORITES, AppPick.order(order));
        if (AppPick.oldKeys(hidden)) p.putSet(Prefs.K_HIDDEN_APPS, AppPick.hidden(hidden, resolved));
        if (AppPick.oldKeys(shown)) p.putSet(K_UNHIDDEN_APPS, AppPick.shown(shown));
    }

    public static ContinueText.Words words(Resources r) {
        return new ContinueText.Words() {
            public String seasonEpisode(String s, String e) { return r.getString(R.string.se_line, s, e); }
            public String episode(String e) { return r.getString(R.string.meta_episode, e); }
            public String leftMin(int m) { return r.getString(R.string.left_min, m); }
            public String leftHour(int h) { return r.getString(R.string.left_h, h); }
            public String leftHourMin(int h, int m) { return r.getString(R.string.left_h_min, h, m); }
            public String nextEpisode() { return r.getString(R.string.hero_kind_next); }
            public String newEpisode() { return r.getString(R.string.new_episode); }
        };
    }

    /** HDMI ports with their CEC names, screen mirroring, USB drives ("Входы"). */
    private static void inputs(Context c, Prefs p, HomeData d) {
        Resources r = c.getResources();
        for (Card k : InputSource.inputs(c, p.map(Prefs.K_INPUT_LABELS))) {
            boolean none = k.state == TvInputManager.INPUT_STATE_DISCONNECTED || k.state == Card.DEV_DISCONNECTED;
            String port = k.desc != null && !k.desc.isEmpty() ? k.desc : k.title;
            String dev = k.desc != null && !k.desc.isEmpty() ? k.title : "";
            k.title = port;
            k.meta = none || dev.isEmpty() ? r.getString(R.string.nothing_connected) : dev;
            if (!none && dev.isEmpty()) k.meta = InputSource.stateText(c, k.state);
            k.icon = R.drawable.ic_hdmi;
            d.inputs.add(k);
        }
        if (MirrorReceiver.offered(c)) {
            Card m = new Card(Card.TILE, "miracast", r.getString(R.string.tile_miracast));
            m.meta = r.getString(R.string.mirror_sub);
            m.intent = "miracast";
            m.icon = R.drawable.ic_cast;
            d.inputs.add(m);
        }
        for (UsbVolumes.Drive v : UsbVolumes.list(c)) {
            Card u = new Card(Card.MORE_APPS, "usb:" + v.name, r.getString(R.string.usb_drive));
            String size = v.bytes > 0 ? r.getString(R.string.size_gb, UsbVolumes.gb(v.bytes)) : "";
            u.meta = v.name.isEmpty() ? size : size.isEmpty() ? v.name : r.getString(R.string.name_size, v.name, size);
            u.intent = "files";
            u.icon = R.drawable.ic_usb;
            d.inputs.add(u);
        }
    }

    public static boolean online(Context c) {
        try {
            ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
            return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * INITIALIZE_PROGRAMS (kept from 1.x): to every package on the first run and after a switch back from the classic
     * launcher, then to each new package, so apps publish their channels and Watch Next.
     */
    private static void trackPackages(Context c, Prefs p, List<AppSource.Entry> raw) {
        HashSet<String> current = new HashSet<>();
        for (AppSource.Entry a : raw) current.add(a.cn.getPackageName());
        Map<String, String> known = p.map(Prefs.K_KNOWN_PKGS);
        boolean first = !p.bool(Prefs.K_INIT_DONE, false);
        String mode = Settings.Secure.getString(c.getContentResolver(), SECURE_LAUNCHER);
        String lastMode = p.str(K_LAST_MODE, "");
        boolean backFromClassic = "classic".equals(lastMode) && !"classic".equals(mode);
        if (mode != null && !mode.equals(lastMode)) p.putStr(K_LAST_MODE, mode);
        ArrayList<String> fresh = new ArrayList<>();
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (String pkg : current) {
            if (!known.containsKey(pkg)) {
                if (!first) fresh.add(pkg);
                known.put(pkg, Long.toString(first ? 0 : now));
                changed = true;
            }
        }
        if (known.keySet().retainAll(current)) changed = true;
        if (changed) p.putMap(Prefs.K_KNOWN_PKGS, known);
        if (first || backFromClassic) {
            int n = ProgramsInitializer.send(c, current);
            p.putBool(Prefs.K_INIT_DONE, true);
            Log.i(App.TAG, "init-programs all receivers=" + n);
        } else if (!fresh.isEmpty()) {
            ProgramsInitializer.send(c, fresh);
        }
    }

    /** The classic Android TV launcher can be chosen only if it is installed (possibly disabled). */
    public static boolean classicAvailable(Context c) {
        try {
            c.getPackageManager().getApplicationInfo("com.google.android.tvlauncher", PackageManager.MATCH_DISABLED_COMPONENTS);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
