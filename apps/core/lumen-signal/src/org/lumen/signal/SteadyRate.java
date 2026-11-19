// SPDX-License-Identifier: Apache-2.0
package org.lumen.signal;

/**
 * The input rate the card shows. The vendor's getHdmiInfo rate is the receiver's measurement (its vsync period, or the
 * pixel clock over htotal x vtotal), so during a mode switch, and frame by frame under VRR, it can read a rate no source
 * sends (a tester saw "133 Hz" once in game mode on the Z9X HB, 2026-11-19). So the card shows the nominal rate of the
 * timing table, and a new rate only once the reads agree:
 * <ul>
 *   <li>a read within 0.5 % of a table rate counts as that rate (CTA-861 and the common PC rates, 23.976 to 240 Hz);</li>
 *   <li>the first read of a panel session shows at once when it is a table rate (the card pops only on the vendor's
 *       "stable" event or by a key);</li>
 *   <li>later, a table rate other than the one shown shows once two reads in a row agree on it; while the size changed
 *       too, the line shows the size alone meanwhile, else the old rate stays;</li>
 *   <li>a read outside the table shows only after three reads in a row agree on it (a PC timing the table lacks);</li>
 *   <li>with VRR on, reads that keep moving show "VRR" instead of a number; a rate that holds for two reads shows.</li>
 * </ul>
 * Plain Java, host tested (SignalTest). Rates in 1/100 Hz, as everywhere in this module.
 */
public final class SteadyRate {
    /** The nominal rates, 1/100 Hz in the vendor's own notation (23.976 Hz = 2397). */
    static final int[] TABLE = {2397, 2400, 2500, 2997, 3000, 4795, 4800, 5000, 5600, 5994, 6000, 7000, 7200, 7500,
            8500, 9000, 10000, 11988, 12000, 14386, 14400, 16500, 23976, 24000};
    /** Agreeing reads before a new rate shows: a table rate, a rate outside the table. */
    static final int TABLE_READS = 2, OTHER_READS = 3;
    /** The next read while a rate waits for its confirmation (the card reads its items every 2 s otherwise). */
    public static final long CONFIRM_MS = 700L;

    private int shown, candidate, agree, width, height;
    private boolean session = true, vrrMoving;

    /** The nominal table rate {@code centi} counts as, 0 when it is none (or no rate). */
    static int nominal(int centi) {
        if (centi <= 0) return 0;
        int best = 0, bestDiff = Integer.MAX_VALUE;
        for (int t : TABLE) {
            int d = Math.abs(centi - t);
            if (d < bestDiff) { best = t; bestDiff = d; }
        }
        return bestDiff * 200 <= best ? best : 0;
    }

    /** Two reads that name the same rate (the same table rate, or within 0.5 % outside the table). */
    private static boolean same(int a, int b) {
        int na = nominal(a), nb = nominal(b);
        if (na != 0 || nb != 0) return na == nb;
        return a > 0 && b > 0 && Math.abs(a - b) * 200 <= Math.max(a, b);
    }

    /** What to show for one read: {@code w} x {@code h} at {@code centi} (0 = no signal), {@code vrr} = VRR runs now. */
    public int next(int w, int h, int centi, boolean vrr) {
        boolean first = session;
        session = false;
        if (centi <= 0 || w <= 0 || h <= 0) {
            shown = candidate = agree = width = height = 0;
            vrrMoving = false;
            return 0;
        }
        boolean resized = w != width || h != height;
        width = w;
        height = h;
        int nom = nominal(centi);
        if (shown != 0 && !resized && same(centi, shown)) {
            candidate = agree = 0;
            vrrMoving = false;
            return shown;
        }
        if (first && nom != 0 && !vrr) {
            shown = nom;
            candidate = agree = 0;
            return shown;
        }
        if (candidate != 0 && same(centi, candidate)) agree++;
        else { candidate = centi; agree = 1; }
        int need = nominal(candidate) != 0 ? TABLE_READS : OTHER_READS;
        if (agree >= need) {
            shown = nominal(candidate) != 0 ? nominal(candidate) : candidate;
            candidate = agree = 0;
            vrrMoving = false;
            return shown;
        }
        vrrMoving = vrr;
        if (vrr || resized) shown = 0;
        return shown;
    }

    /** VRR runs and the last reads moved: the card says "VRR" instead of a rate. */
    public boolean vrrMoving() { return vrrMoving; }

    /** A rate waits for its confirmation: read the input again soon ({@link #CONFIRM_MS}). */
    public boolean pending() { return candidate != 0; }
}
