// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.signal;

/**
 * The answer of the judder line: does the chip show every source frame for the same time. The two rates are compared
 * as the ranges their reads allow, never as the printed numbers. Our vendors read the input in steps of 0.1 Hz
 * (MI_EXTIN timing: a PS5 at 59.94 Hz reads 5990 on the Z9X and the Z9X HB, a 23.976 Hz film reads 2390 or 2400) and
 * the chip in whole Hz cut down (libxgimi DispDispatchV3 getDispOutputFps is the chip's mHz / 1000 on the Z9X and the
 * X50 family, so a 23.976 Hz chip reads 23). A warning comes only when no whole multiple of the source fits the chip
 * inside those ranges: 23.976 against 24 warns only where both reads are fine enough to tell them apart.
 */
public enum Judder {
    /** a rate or its precision is missing (no HDMI signal, no chip rate on this model): no line at all */
    UNKNOWN,
    /** the chip rate can be a whole multiple of the source rate: every frame stays on screen equally long */
    SMOOTH,
    /** it cannot be (24 frames on 60 Hz): some frames stay longer, smooth pans judder */
    JUDDER;

    /** Clock slack on top of the read steps, in mHz: two reads of one locked clock still differ a little. */
    static final long SLACK_MILLI = 5;
    /** 1/100 Hz in mHz. */
    private static final long CENTI_MILLI = 10;

    /**
     * The verdict for a source rate read in steps of {@code sourceStep} and a chip rate cut down to steps of
     * {@code chipStep}, all in 1/100 Hz (0 or less = unknown). The source read may be rounded or cut, so it spans one
     * step either side; the chip read spans one step upward.
     */
    public static Judder of(int sourceCenti, int sourceStep, int chipCenti, int chipStep) {
        if (sourceCenti <= 0 || chipCenti <= 0 || sourceStep <= 0 || chipStep <= 0) return UNKNOWN;
        long source = SignalFormat.milli(sourceCenti), chip = SignalFormat.milli(chipCenti);
        long sLo = source - sourceStep * CENTI_MILLI, sHi = source + sourceStep * CENTI_MILLI;
        long cLo = chip - SLACK_MILLI, cHi = chip + chipStep * CENTI_MILLI - 1 + SLACK_MILLI;
        if (sLo <= 0) return UNKNOWN;
        for (long k = Math.max(1, cLo / sHi); k <= cHi / sLo; k++) {
            if (k * sLo <= cHi && k * sHi >= cLo) return SMOOTH;
        }
        return JUDDER;
    }
}
