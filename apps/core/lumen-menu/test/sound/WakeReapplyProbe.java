// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.audio;

import org.lumen.projector.hal.AudioPath;

import java.util.function.BiConsumer;

/**
 * WakeReapply through its package (sh test/sound/run.sh): after a wake a sound setting of the built-in speaker is
 * written once, as stock restores once (LR-VRE2Z1 / LR-5MVYWT: SoundEq and AiSoundEngine had five timers each).
 */
public final class WakeReapplyProbe {
    private WakeReapplyProbe() {}

    /** Writes one wake costs: the SCREEN_ON timers, each written unless the output is ARC, a retry per refusal. */
    static int wakeWrites(boolean watching, int path, boolean refuseFirst) {
        int writes = 0;
        for (long d : WakeReapply.screenOn(watching)) {
            if (WakeReapply.skipOutput(path)) continue;
            int attempt = 0;
            int outcome = refuseFirst ? WakeReapply.REFUSED : WakeReapply.WRITTEN;
            writes++;
            while (WakeReapply.retry(outcome, attempt)) {
                attempt++;
                outcome = WakeReapply.WRITTEN;
                writes++;
            }
        }
        return writes;
    }

    public static void run(BiConsumer<Boolean, String> ok) {
        int sp = AudioPath.SPEAKER.wire, arc = AudioPath.ARC.wire;
        ok.accept(wakeWrites(false, sp, false) == 1, "wake, no vendor watch, speaker: one write (stock restores once)");
        ok.accept(wakeWrites(true, sp, false) == 0, "wake with the vendor watch: no timer (the watch re-applies when the vendor restore ran)");
        ok.accept(wakeWrites(false, sp, true) == 2, "wake, the HAL refused the first write: one retry");
        ok.accept(wakeWrites(false, arc, false) == 0, "wake on HDMI ARC / eARC: nothing written to the built-in speaker path");
        ok.accept(WakeReapply.screenOn(false).length == 1 && WakeReapply.screenOn(false)[0] == WakeReapply.SCREEN_ON_MS,
                "one timer at SCREEN_ON_MS");
        ok.accept(!WakeReapply.retry(WakeReapply.REFUSED, 1), "never a second retry");
        ok.accept(!WakeReapply.retry(WakeReapply.WRITTEN, 0) && !WakeReapply.retry(WakeReapply.NOTHING, 0),
                "no retry after a write or a skip");
        ok.accept(!WakeReapply.skipOutput(sp) && !WakeReapply.skipOutput(-1) && !WakeReapply.skipOutput(AudioPath.BT_A2DP.wire),
                "speaker, unknown and BT outputs are not skipped by the ARC rule");
    }
}
