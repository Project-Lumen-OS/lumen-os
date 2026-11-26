// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.audio;

import java.util.Collection;

/**
 * MediaPath: the vendor audio path a media route really plays on, the same rule on every Lumen projector. Plain Java,
 * host-tested (test/run.sh).
 *
 * Paths are the stock EN_XGIMI_AUDIO_PATH wire values (libxgimiaudiopolicy threadInit, the 59 / AUDIO 2 setAudioOutput
 * argument and the HAL's xgimi_audiopath_switch_end): 0 speaker, 2 HDMI ARC or eARC, 3 Bluetooth A2DP, 4 the 3.5 mm
 * jack, 6 USB audio. The vendor HAL plays on ONE sink: on Bluetooth its loopback writes A2DP only while the sink is 3.
 *
 * Why a priority and not the list order: AOSP 14 adds an available HDMI ARC / eARC and SPDIF to every media route
 * (enginedefault Engine.cpp, STRATEGY_MEDIA, "ARC, SPDIF and AUX_LINE can co-exist with others"), and
 * getDevicesForAttributes returns a DeviceVector, a SortedVector of sp&lt;DeviceDescriptor&gt; ordered by pointer
 * (managerdefinitions DeviceDescriptor.h). With a soundbar on ARC the route of connected headphones is {bt_a2dp, hdmi_arc}
 * in heap order, so the first entry that is not the speaker sent the vendor to ARC whenever the soundbar's descriptor came
 * first: the headphones showed connected and stayed silent.
 */
public final class MediaPath {
    public static final int NONE = -1, SPEAKER = 0, ARC = 2, BT = 3, WIRED = 4, USB = 6;
    /** The 59 / AUDIO 2 value of a switch start: the vendor mutes every TV path until the next real path. */
    public static final int SWITCHING = 100;
    /** Stock threadInit: 50 x 20 ms after the last change (libxgimiaudiopolicy.so). */
    public static final long STOCK_SWITCH_MS = 1_000L;
    /** Lumen 1.0.6 wait for the other switches (Bluetooth back to the speaker was silent for 2 s with 1 s). */
    public static final long SHORT_SWITCH_MS = 300L;
    /** A switch_start whose switch_end did not follow within this time gets its switch_end anyway (never stay muted). */
    public static final long SWITCH_END_SAFETY_MS = 6_000L;

    /** What the listener hears: the output he wears or plugged in, then the soundbar, then the projector itself. */
    private static final int[] HEARD = {BT, WIRED, USB, ARC, SPEAKER};

    private MediaPath() {}

    /** The path of a media route (or of the connected outputs): by HEARD, never by list order. NONE when none is a path. */
    public static int heard(Collection<Integer> paths) {
        if (paths == null) return NONE;
        for (int want : HEARD) if (paths.contains(want)) return want;
        return NONE;
    }

    /**
     * The digital output format goes to the HAL again on a switch to another path (stock setXgimiPathForDeviceDelay sends
     * digital_output_format with every switch start), so the HDMI ARC / eARC mode the user chose (PCM, Passthrough, Auto)
     * holds after the vendor switched its paths. prev: the path reported before, NONE at the first report of a boot.
     */
    public static boolean formatAgain(int prev, int next) {
        return switchStart(prev, next);
    }

    /**
     * The digital output format at an ARC connect (the HAL may have reset it): only while the media already plays on
     * ARC (confirmed path) and nothing sent it since ARC came; otherwise the switch start to ARC sends it (stock).
     * evleo790 LR-5MVYWT 18:23:40: the switch start 0 -> 2 sent pcm at .167, the ARC connect sent it again at .177.
     */
    public static boolean formatOnArc(int confirmed, boolean sentSinceArc) {
        return confirmed == ARC && !sentSinceArc;
    }

    /**
     * The stock switch start (setXgimiPathForDeviceDelay: xgimi_audiopath_switch_start=P, 59(100) and the digital format
     * at once) on every change from a path reported before to another one; nothing for the first report of a process.
     */
    public static boolean switchStart(int prev, int next) {
        return prev != NONE && next != NONE && prev != next;
    }

    /** The wait before 59(P) after a change: stock 1 s around an HDMI ARC switch, 300 ms otherwise (Lumen 1.0.6). */
    public static long switchDelayMs(int prev, int next) {
        return switchStart(prev, next) && (prev == ARC || next == ARC) ? STOCK_SWITCH_MS : SHORT_SWITCH_MS;
    }
}
