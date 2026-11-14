// SPDX-License-Identifier: Apache-2.0
package org.lumen.updater;

import android.content.Context;
import android.util.Log;

import org.lumen.stats.Device;
import org.lumen.stats.Ping;
import org.lumen.stats.Release;
import org.lumen.stats.StatsClient;
import org.lumen.stats.StatsState;

/**
 * Anonymous usage statistics (README "Privacy") of the updater, sent by the one client of every projector
 * (apps/core/lumen-stats: the body, the once per start / once a day rule, the model from the device registry, the
 * serial, a DSU trial run). This app keeps only its own part: the state lives in Store (the install id of an
 * installed projector stays the same across updates), the update method recorded at an install, and nothing is sent
 * while the one-time notice after an update is due (Notice, Store.noticeDue). One shared switch (Store.statsOn): the
 * projector settings and the setup wizard (StateProvider "stats" / "stats_set").
 */
public final class Stats {
    private Stats() {}

    public static final boolean DEFAULT_ON = Ping.DEFAULT_ON;
    public static final String[] URLS = Ping.URLS;

    /** The updater shows no statistics switch: the only one is in the projector settings, Diagnostics. */
    static final boolean SWITCH_IN_UPDATER = false;

    public static boolean configured() { return Ping.configured(); }

    /** Called at the start of every online check (Checker.check), when the notice closes and after boot (CheckJob
     * JOB_STATS): never blocks, never throws. */
    static void maybeSend(Context c) {
        try {
            Store st = Store.get(c);
            StatsClient.maybeSend(c, new State(st), new Release(Ota.currentVersion(), Ota.currentVersionCode(),
                    Ota.buildId()), st.noticeDue());
        } catch (Throwable t) {
            Log.i(Ota.TAG, "stats: " + t);
        }
    }

    static boolean setupDone(Context c) { return Device.setupDone(c); }

    static String newId() { return Ping.newId(); }

    /** The statistics state in the updater's Store. */
    static final class State implements StatsState {
        private final Store st;

        State(Store st) { this.st = st; }

        @Override public boolean on() { return st.statsOn(); }
        @Override public String id() { return st.statsId(); }
        @Override public String sentDay() { return st.statsDay(); }
        @Override public int sentVc() { return st.statsVc(); }
        @Override public String sentVersion() { return st.statsVersion(); }
        @Override public int sentBoot() { return st.statsBoot(); }
        @Override public long lastTry() { return st.statsTry(); }
        @Override public void setTry(long t) { st.setStatsTry(t); }
        @Override public void setSent(String day, int vc, String version, int boot) { st.setStatsSent(day, vc, version, boot); }
        @Override public String methodBuild() { return st.statsMethodBuild(); }
        @Override public String method() { return st.statsMethod(); }
    }
}
