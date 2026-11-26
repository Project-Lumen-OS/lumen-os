// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hdmi;

/**
 * The audio HAL keys of the HDMI input, as stock sends them (XRMService hdmi/SignalChangeReceiver, its GmTvManager
 * event listener, Z9X V6.15.62): the audio HAL hears when the HDMI source has a stable signal and when it lost it.
 * <ul>
 *   <li>event 0 "status:N": N = 3 (stable) sends xgimi_source_hdmi=1, every other N sends xgimi_source_hdmi=0 and
 *       500 ms later xgimi_source_hdmi_delay=0</li>
 *   <li>event 4 sends xgimi_source_hdmi=0</li>
 *   <li>event 1 "x:SOURCE,state:0" (cable out) sends xgimi_source_hdmi=0 when SOURCE is the vendor's current input
 *       source</li>
 * </ul>
 * Plain Java: the host hands in where the keys go and how the current source is read.
 */
public final class HdmiAudioKeys {
    private HdmiAudioKeys() {}

    /** GmTvManager event types stock tests for these keys. */
    public static final int EVENT_SIGNAL = 0, EVENT_HOT_PLUG = 1, EVENT_OFF = 4;
    public static final String ON = "xgimi_source_hdmi=1", OFF = "xgimi_source_hdmi=0",
            OFF_DELAYED = "xgimi_source_hdmi_delay=0";
    public static final long DELAY_MS = 500;
    private static final String STABLE = "3", UNPLUGGED = "0";
    private static final int NO_SOURCE = -1;

    /** Where the keys go: now, or after a delay. */
    public interface Out {
        void now(String keyValue);

        void later(String keyValue, long delayMs);
    }

    /** The vendor's current input source (getCurrentInputSource), read only for a cable out event. */
    public interface Source {
        int current() throws Exception;
    }

    /** An event type with a key rule: the other events need no thread hop. */
    public static boolean handles(int type) {
        return type == EVENT_SIGNAL || type == EVENT_HOT_PLUG || type == EVENT_OFF;
    }

    /** One GmTvManager event: what stock sends for it, nothing for another event or a message that does not parse. */
    public static void onTvEvent(int type, String msg, Source source, Out out) {
        if (type == EVENT_OFF) { out.now(OFF); return; }
        if (msg == null) return;
        if (type == EVENT_SIGNAL) {
            String[] p = msg.split(":");
            if (p.length < 2 || !"status".equals(p[0])) return;
            if (STABLE.equals(p[1])) {
                out.now(ON);
            } else {
                out.now(OFF);
                out.later(OFF_DELAYED, DELAY_MS);
            }
        } else if (type == EVENT_HOT_PLUG) {
            int unplugged = unpluggedSource(msg);
            if (unplugged == NO_SOURCE) return;
            int current;
            try {
                current = source.current();
            } catch (Exception e) {
                return;
            }
            if (unplugged == current) out.now(OFF);
        }
    }

    /** The source of a cable out message "x:SOURCE,state:0", NO_SOURCE for a cable in or anything else. */
    private static int unpluggedSource(String msg) {
        String[] parts = msg.split(",");
        if (parts.length < 2) return NO_SOURCE;
        String[] dev = parts[0].split(":"), state = parts[1].split(":");
        if (dev.length < 2 || state.length < 2 || !"state".equals(state[0]) || !UNPLUGGED.equals(state[1])) return NO_SOURCE;
        try {
            return Integer.parseInt(dev[1]);
        } catch (NumberFormatException e) {
            return NO_SOURCE;
        }
    }
}
