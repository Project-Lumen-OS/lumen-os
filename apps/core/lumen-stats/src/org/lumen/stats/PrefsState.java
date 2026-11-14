// SPDX-License-Identifier: Apache-2.0
package org.lumen.stats;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The statistics state of an app without an updater (the projector app of the Play 6, the X50 and the RS30) in its
 * own preferences file lumen_stats. Its settings show the switch (on, setOn); turning it off forgets the install id
 * and what was sent, so a later send starts as a new install.
 */
public final class PrefsState implements StatsState {
    private static PrefsState sInstance;
    private final SharedPreferences p;

    private PrefsState(Context c) {
        p = c.getApplicationContext().getSharedPreferences("lumen_stats", Context.MODE_PRIVATE);
    }

    public static synchronized PrefsState get(Context c) {
        if (sInstance == null) sInstance = new PrefsState(c);
        return sInstance;
    }

    @Override public boolean on() { return p.getBoolean("on", Ping.DEFAULT_ON); }

    public void setOn(boolean on) {
        SharedPreferences.Editor e = p.edit().putBoolean("on", on);
        if (!on) e.remove("id").remove("day").remove("try").remove("vc").remove("version").remove("boot");
        e.apply();
    }

    @Override public synchronized String id() {
        String id = p.getString("id", "");
        if (id.isEmpty()) {
            id = Ping.newId();
            p.edit().putString("id", id).apply();
        }
        return id;
    }

    @Override public String sentDay() { return p.getString("day", ""); }
    @Override public int sentVc() { return p.getInt("vc", 0); }
    @Override public String sentVersion() { return p.getString("version", ""); }
    @Override public int sentBoot() { return p.getInt("boot", -1); }
    @Override public long lastTry() { return p.getLong("try", 0); }
    @Override public void setTry(long t) { p.edit().putLong("try", t).apply(); }

    @Override public void setSent(String day, int vc, String version, int boot) {
        p.edit().putString("day", day).putInt("vc", vc).putString("version", version).putInt("boot", boot).apply();
    }

    /** No updater: an image change is always the installer's (method "installer"). */
    @Override public String methodBuild() { return ""; }
    @Override public String method() { return ""; }
}
