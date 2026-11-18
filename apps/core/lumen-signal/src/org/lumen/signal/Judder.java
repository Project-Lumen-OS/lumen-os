// SPDX-License-Identifier: Apache-2.0
package org.lumen.signal;

/** The answer of the judder line: does the chip show every source frame for the same time. */
public enum Judder {
    /** a rate is missing (no HDMI signal, no chip rate on this model): no line at all */
    UNKNOWN,
    /** the chip rate is a whole multiple of the source rate: every frame stays on screen equally long */
    SMOOTH,
    /** it is not (24 frames on 60 Hz): some frames stay longer, smooth pans judder */
    JUDDER;

    /** Rounding of the vendor rates (23.976 read as 24, 59.94 as 60) stays inside this share. */
    static final double TOLERANCE = 0.003;

    /** The verdict for a source rate and a chip rate, both in 1/100 Hz (0 or less = unknown). */
    public static Judder of(int sourceCenti, int chipCenti) {
        if (sourceCenti <= 0 || chipCenti <= 0) return UNKNOWN;
        double ratio = (double) chipCenti / sourceCenti;
        long whole = Math.round(ratio);
        if (whole < 1) return JUDDER;
        return Math.abs(ratio - whole) / whole <= TOLERANCE ? SMOOTH : JUDDER;
    }
}
