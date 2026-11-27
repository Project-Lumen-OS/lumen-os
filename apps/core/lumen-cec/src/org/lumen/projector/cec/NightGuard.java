// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.cec;

/**
 * The night guard of the CEC page (Lumen's own: no stock XGIMI system has one): hours in which an HDMI device may not
 * turn the projector on. Off unless the user picks hours on the CEC page: stock wakes on &lt;Image View On&gt; /
 * &lt;Text View On&gt; at any hour once "Turn on with an HDMI device" is on, and the X50 family never had a guard.
 * evleo790 LR-9MEQZP (HB beta27, 05:52-06:10 local): the old default 01:00-07:00 switched tv_wake_on_one_touch_play off
 * at every sleep (CecPolicy.suspendOtp), so all 13 &lt;Text View On&gt; of his Yandex module got &lt;Feature Abort&gt;
 * "refused" (0B:00:0D:04) and "Alice, turn on the projector" did nothing. Plain Java, host tested (test/cecwake).
 */
public final class NightGuard {
    /** The choices of the page: {from hour, to hour} local time, {-1, -1} = off. */
    public static final int[][] CHOICES = {{-1, -1}, {22, 7}, {23, 7}, {0, 7}, {1, 7}};
    /** A unit that never stored a choice: off, as stock. */
    public static final int DEFAULT = 0;

    private NightGuard() {}

    /** A stored choice (null = none stored) as a valid index; anything else is the default. */
    public static int choice(Integer stored) {
        return stored != null && stored >= 0 && stored < CHOICES.length ? stored : DEFAULT;
    }

    /** True when choice {@code i} refuses a CEC wake at {@code hourOfDay} (0-23, local time). */
    public static boolean blocks(int i, int hourOfDay) {
        int[] r = CHOICES[choice(i)];
        if (r[0] < 0) return false;
        return r[0] < r[1] ? (hourOfDay >= r[0] && hourOfDay < r[1]) : (hourOfDay >= r[0] || hourOfDay < r[1]);
    }

    /** "off" or "1:00-7:00". */
    public static String describe(int i) {
        int[] r = CHOICES[choice(i)];
        return r[0] < 0 ? "off" : r[0] + ":00-" + r[1] + ":00";
    }
}
