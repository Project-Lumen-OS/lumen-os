// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.audio;

/**
 * What one SoundEq apply sends (plain Java, host tested by apps/core/lumen-menu/test/sound/run.sh).
 *
 * The stock firmware has no user equalizer (no SetPEQ caller in newsettings, XRMService or XgimiService), so a flat
 * curve must cost no vendor call that stock does not make: the stock volume leveling switch is 42 alone, the stock
 * Harman Kardon / DTS choice is 44 + 27 alone (GmAudioManager.setSoundProcessTypeFusion). With a flat curve only a
 * user action writes 51(false); after a vendor call it is written only when we had turned the PEQ on.
 */
final class EqWrite {
    private EqWrite() {}

    /** Kinds: an automatic re-apply, right after a vendor call that may reset the PEQ, a user action. */
    static final int TRIGGER = 0, VENDOR = 1, FORCE = 2;
    /** Plans: no call, 51(false), the curve (50 x 13, 51(true)). */
    static final int NOTHING = 0, OFF = 1, CURVE = 2;

    /** {@code sameRecent}: the same curve was written within the dedup window. */
    static int plan(int kind, boolean flat, boolean onByUs, boolean sameRecent) {
        if (flat) return kind != FORCE && !onByUs ? NOTHING : OFF;
        if (kind == TRIGGER && onByUs && sameRecent) return NOTHING;
        return CURVE;
    }
}
