// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu.sound;

import org.lumen.menu.FeatureId;

/**
 * When the sound output rows read again on their own (every projector): a tester turned eARC on, the sound went to the
 * soundbar, and the output page kept "Auto, speaker" until he left and opened it again, because the rows were read
 * once at the switch and the soundbar's ARC link comes up seconds later (stock HB renegotiates ARC three times in about
 * 22 s after eARC on, rig 2026-11-17). So while the menu is open every audio device change (the HDMI audio and
 * headset plugs, Bluetooth A2DP, a media route change) reads the rows again after a short calm, and a change
 * of eARC or of the output reads them again over the time the link needs. Plain Java, host tested
 * (test/outputs/OutputRefreshTest.java); the Android side is lumen-menu OutputWatch.
 */
public final class OutputRefresh {
    /** Events closer than this read once (a soundbar coming up sends several). */
    public static final long CALM_MS = 300L;
    /** Re-reads after eARC on or off: the ARC link renegotiates up to three times in about 22 s. */
    static final long[] AFTER_EARC_MS = {1_500L, 4_000L, 8_000L, 14_000L, 23_000L};
    /** Re-reads after an output choice: the route and the vendor path settle within a few seconds. */
    static final long[] AFTER_OUTPUT_MS = {1_000L, 3_000L, 6_000L};
    private static final long[] NONE = {};

    private OutputRefresh() {}

    /** The re-reads after a change of {@code f}, in ms after it; none for a feature that moves no output. */
    public static long[] after(FeatureId f) {
        if (f == FeatureId.SOUND_EARC) return AFTER_EARC_MS.clone();
        if (f == FeatureId.SOUND_OUTPUT) return AFTER_OUTPUT_MS.clone();
        return NONE;
    }

    /** The menu watches the audio devices while it is open when it has a row that shows them. */
    public static boolean watch(boolean hasOutputRow, boolean hasEarcRow) {
        return hasOutputRow || hasEarcRow;
    }
}
