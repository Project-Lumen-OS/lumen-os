// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.data;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import org.lumen.home.App;
import org.lumen.home.Prefs;
import org.lumen.home.model.HomeLayout;
import org.lumen.home.model.Looks;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Home's layout in Settings.Secure {@link HomeLayout#KEY} of the current user: the only class that touches it. Secure
 * settings are per user and the projector app can read them too, so the settings backup carries the layout with the
 * menus. A Home that never stored one shows the layout of its old settings (order, hidden apps, Continue, weather)
 * and writes nothing until the user changes something. The looks are data: {@code assets/home/looks.tsv}.
 */
public final class LayoutStore {
    public static final String LOOKS_ASSET = "home/looks.tsv";
    /** The old settings went into the stored layout once: a missing value from then on is the default (a reset). */
    static final String K_MOVED = "layout_moved";
    private final Context mApp;
    private final java.util.function.Supplier<Prefs> mPrefs;
    private HomeLayout mCached;
    private String mCachedRaw;
    private List<Looks.Look> mLooks;

    /** {@code p} gives the old settings, null before the user is unlocked (they are credential protected). */
    public LayoutStore(Context c, java.util.function.Supplier<Prefs> p) {
        mApp = c.getApplicationContext();
        mPrefs = p;
    }

    /** The layout now (a copy: change it and {@link #put} it). */
    public synchronized HomeLayout get() {
        return current().copy();
    }

    /**
     * The layout now, shared: read it, never change it. Read again when the stored value changed (a restore by the
     * settings backup, another user); before the unlock without a stored value it is the default, not kept.
     */
    public synchronized HomeLayout current() {
        String raw = read();
        if (mCached != null && same(raw, mCachedRaw)) return mCached;
        HomeLayout l = HomeLayout.parse(raw);
        if (l == null) {
            if (raw != null && !raw.isEmpty()) Log.w(App.TAG, "layout: stored value not readable, the old settings are shown");
            Prefs p = mPrefs.get();
            if (p == null) return new HomeLayout();
            l = p.bool(K_MOVED, false) ? new HomeLayout() : HomeLayout.fromOld(p.list(Prefs.K_FAVORITES), p.bool(Prefs.K_FAVORITES_SET, false),
                    p.set(Prefs.K_HIDDEN_APPS), p.set(HomeLoader.K_UNHIDDEN_APPS), p.bool(Prefs.K_CONTINUE_HOME, true),
                    p.bool(Prefs.K_WEATHER_ON, true));
        }
        mCached = l;
        mCachedRaw = raw;
        return l;
    }

    /** Stores {@code l}; false when Android refused (the screen keeps the old layout then). */
    public synchronized boolean put(HomeLayout l) {
        String v = l.write();
        try {
            if (!Settings.Secure.putString(mApp.getContentResolver(), HomeLayout.KEY, v)) return false;
        } catch (Throwable t) {
            Log.w(App.TAG, "layout: not stored: " + t);
            return false;
        }
        mCached = HomeLayout.parse(v);
        mCachedRaw = v;
        Prefs p = mPrefs.get();
        if (p != null && !p.bool(K_MOVED, false)) p.putBool(K_MOVED, true);   // a reset now gives the default layout
        return true;
    }

    private String read() {
        try {
            return Settings.Secure.getString(mApp.getContentResolver(), HomeLayout.KEY);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean same(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    /** The looks of the data file (Classic first, always there). */
    public synchronized List<Looks.Look> looks() {
        if (mLooks == null) mLooks = Looks.parse(asset(mApp, LOOKS_ASSET));
        return mLooks;
    }

    static String asset(Context c, String name) {
        try (InputStream in = c.getAssets().open(name)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.w(App.TAG, "looks: " + e);
            return null;
        }
    }
}
