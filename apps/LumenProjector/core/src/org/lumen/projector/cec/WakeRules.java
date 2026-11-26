// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
/*
 * WakeRules: the decisions of cec/CecPolicy around a wake, kept free of Android classes so test/cecwake/run.sh replays
 * them on the host (stock X50 sniffer trace, Lumen HB logs of 2026-11-04).
 */
package org.lumen.projector.cec;

import java.util.ArrayDeque;

public final class WakeRules {
    /** After a wake, the app's &lt;System Audio Mode Request&gt; for "Turn on the soundbar with the projector". */
    public static final long AVR_ON_DELAY_MS = 4_000;
    /** A sleep this soon after a CEC wake counts towards the storm limit. */
    public static final long QUICK_SLEEP_MS = 120_000;
    public static final long STORM_WINDOW_MS = 30 * 60_000L;
    public static final int STORM_MAX = 3;

    private WakeRules() {}

    /**
     * May the app ask the soundbar for System Audio after this wake? Only when Android itself did not sleep (the
     * lamp-only standby ends, no CEC restart in the framework). After an Android wake HdmiControlService allocates
     * its address and runs a DeviceDiscoveryAction (12.5 s in the stock X50 trace, Report Physical Address 11:46:57.060
     * to Give System Audio Mode Status 11:47:09.552); at its end SystemAudioAutoInitiationAction asks the soundbar
     * and starts ARC, the only System Audio step stock takes (no stock app calls setSystemAudioMode). An app request
     * while the discovery runs makes AOSP turn System Audio OFF and terminate ARC (HdmiCecLocalDeviceTv
     * .changeSystemAudioMode, Lumen services.jar :386-392), and every ARC end and start rewrites the HDMI inputs'
     * EDID on the HB (vendor "updateEdidInfo: bEnableArc:0|1", log 2026-11-04 15:40:08 / 15:40:15).
     */
    public static boolean appSystemAudioRequest(boolean androidWoke) {
        return !androidWoke;
    }

    /**
     * The storm limit of CEC wakes: more than {@link #STORM_MAX} unwanted CEC wakes within {@link #STORM_WINDOW_MS}
     * pause the CEC wake until the next remote key. Unwanted = nobody there (the unattended card ran out) or a sleep
     * within {@link #QUICK_SLEEP_MS} that the HDMI device did not ask for: a device that turns the projector on and
     * off by itself (a voice speaker's "turn on" / "turn off", its &lt;Standby&gt;, or the watched device turning off,
     * both sleep reason HDMI) is the user's choice, as on stock, which has no such limit. Not thread safe: the main
     * thread only. Times are uptime ms.
     */
    public static final class Storm {
        private final ArrayDeque<Long> bad = new ArrayDeque<>();
        private boolean paused;

        /**
         * The projector goes dark; {@code cecWakeAt} = the accepted CEC wake of this viewing, -1 none. Returns the
         * reason it counted, null when it did not.
         */
        public String onSleep(long now, long cecWakeAt, boolean byHdmiDevice) {
            if (cecWakeAt < 0 || byHdmiDevice) return null;
            long since = now - cecWakeAt;
            if (since < 0 || since >= QUICK_SLEEP_MS) return null;
            record(now);
            return "sleep " + since / 1000 + " s after a CEC wake";
        }

        /** The unattended card ran out after a CEC wake. */
        public void onNobodyThere(long now) {
            record(now);
        }

        private void record(long now) {
            bad.addLast(now);
            while (!bad.isEmpty() && now - bad.peekFirst() > STORM_WINDOW_MS) bad.removeFirst();
            if (bad.size() > STORM_MAX) paused = true;
        }

        /** Unwanted CEC wakes in the window (the last record's view). */
        public int count() {
            return bad.size();
        }

        public boolean paused() {
            return paused;
        }

        /** A remote key: the CEC wake works again. True when it was paused. */
        public boolean onUserKey() {
            boolean was = paused;
            paused = false;
            bad.clear();
            return was;
        }
    }
}
