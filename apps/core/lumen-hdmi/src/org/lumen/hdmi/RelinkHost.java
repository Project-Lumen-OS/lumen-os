// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hdmi;

import android.content.Context;

import java.util.function.Consumer;

/**
 * One projector's side of the HDMI relink ({@link HdmiRelink}): its vendor calls on its own HAL thread, its stock EDID
 * versions, its lamp-only standby and tvinput's shown ports. Z9X / HB: source/GmpfRelinkHost (IGmpf 592 / TV 106,
 * 660 / 661 and TV 39 / 40), X50 family: X50RelinkHost (TV 106, TV 39 / 40). The relink logic itself is not here.
 */
public interface RelinkHost {
    /** The vendor HAL as the relink uses it, on the host's HAL thread only. */
    interface Vendor {
        /** getSignalStatus: {@link HdmiLink#STABLE} = a stable picture. */
        int signalStatus() throws Exception;

        int edidVersion() throws Exception;

        boolean setEdidVersion(int v) throws Exception;

        /** The stock EDID versions this projector may write (and so write back). */
        int[] edidVersions();
    }

    interface Job {
        void run(Vendor v) throws Exception;
    }

    interface Read<T> {
        T run(Vendor v) throws Exception;
    }

    /** Log tag of this model's relink lines. */
    String tag();

    /** The flip order: the first stock version, else the second (HdmiLink.flipTo). */
    int edidFirst();

    int edidSecond();

    /** Runs {@code r} on the HAL thread; {@code onMain} gets its value on the main thread, null when it failed or the HAL is not there. */
    <T> void query(Read<T> r, Consumer<T> onMain);

    /** Runs {@code j} on the HAL thread; false = not queued. */
    boolean run(Job j);

    /** {@code j} on the HAL thread after {@code ms}; {@code ifSkipped} there when the HAL is not connected then. false = not queued. */
    boolean runDelayed(Job j, long ms, Runnable ifSkipped);

    /** {@code j} on the HAL thread after every connect of the HAL (a host without connects: once, now). */
    void onConnect(Job j);

    /** The lamp-only standby runs (the screen looks off while Android is awake). */
    boolean standby();

    /**
     * Family data (Family.INACTIVE_SOURCE_SLEEP, false on every family until the owner decides): the projector sleeps
     * when the watched device says {@code <Inactive Source>} and is not back within 5 s, no key pressed
     * (InactiveSleep; reads the CEC history only while on).
     */
    boolean inactiveSourceSleep();

    /** tvinput's shown HDMI ports: [] none, null unknown. Never the main thread. */
    int[] shownPorts(Context ctx);
}
