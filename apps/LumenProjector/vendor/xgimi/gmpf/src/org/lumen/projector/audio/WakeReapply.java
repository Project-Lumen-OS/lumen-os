// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.audio;

import org.lumen.projector.hal.AudioPath;

/**
 * When Lumen writes a sound setting of the built-in speaker again after a wake (plain Java, host tested by
 * apps/core/lumen-menu/test/sound/run.sh).
 *
 * Stock restores the sound settings once per wake: the vendor's own Resume restore, one run. Lumen's settings that this
 * restore can undo (the SoundEq curve, the AI sound sub-mode, the Play 6 equalizer) are written again ONCE: when
 * VendorSoundWatch sees the vendor restore run (the vendor is done with it), or, where no watch reads it, one timer
 * after SCREEN_ON. A second write only when the HAL refused the first (its reply: the PEQ has no getter, the reply of
 * 50 / 51 is the read-back). Never while the output is HDMI ARC / eARC: these settings sit on the built-in speaker path.
 */
final class WakeReapply {
    private WakeReapply() {}

    /** The one timer after SCREEN_ON when no watch reports the vendor restore. */
    static final long SCREEN_ON_MS = 3_000;
    /** The one retry after a refused write. */
    static final long RETRY_MS = 3_000;
    /** Outcomes of one write: nothing sent, written, refused by the HAL. */
    static final int NOTHING = 0, WRITTEN = 1, REFUSED = 2;

    /** The delays one SCREEN_ON schedules: none while the vendor watch reports the restore itself, else one. */
    static long[] screenOn(boolean watching) {
        return watching ? new long[0] : new long[] {SCREEN_ON_MS};
    }

    /** A second write only after the HAL refused the first. */
    static boolean retry(int outcome, int attempt) {
        return outcome == REFUSED && attempt == 0;
    }

    /** A built-in speaker setting is not written while the confirmed output is HDMI ARC / eARC (vendor path 2). */
    static boolean skipOutput(int confirmedPath) {
        return confirmedPath == AudioPath.ARC.wire;
    }
}
