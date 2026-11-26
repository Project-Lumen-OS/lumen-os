// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

/**
 * Sleep or power off, the stock rule of every XGIMI model (PowerManagerService.getShutdownMode on G0082, G0130,
 * G0111): a sleep is a real power off instead of STR while the user setup is not complete and on the
 * {@code persist.xgimi.max_str_cnt}th sleep of a boot (default {@link #STOCK_MAX_STR}: strSuspendCnt starts at 1 at
 * boot, grows with every STR, and a sleep with strSuspendCnt >= max powers off). Plain Java.
 */
public final class SleepRule {
    /** persist.xgimi.max_str_cnt when unset (stock default). */
    public static final int STOCK_MAX_STR = 15;
    public static final String MAX_STR_PROP = "persist.xgimi.max_str_cnt";

    private SleepRule() {}

    /**
     * Why the coming sleep powers the projector off, null = STR. {@code sleeps}: sleeps of this boot before it;
     * {@code maxStr}: the property's value ({@code <= 0} or unset = the stock default).
     */
    public static String powerOffReason(int sleeps, int maxStr, boolean setupComplete) {
        if (!setupComplete) return "the user setup is not complete";
        int max = maxStr > 0 ? maxStr : STOCK_MAX_STR;
        int n = sleeps + 1;
        return n >= max ? "sleep " + n + " of this boot (stock: the " + max + "th sleep of a boot powers off)" : null;
    }
}
