// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.tvinput;

/** Host test of ExitSelect: the TV takes the active source back before the sleep (stock order). */
public final class ExitSelectTest {
    private static int checks, fails;

    private static void eq(String what, long want, long got) {
        checks++;
        if (want != got) {
            fails++;
            System.out.println("FAIL " + what + ": want " + want + ", got " + got);
        }
    }

    public static void main(String[] a) {
        eq("going to sleep (X50 live 14:10:02.666, session released): at once", 0, ExitSelect.delayMs(false, true, false));
        eq("going to sleep, the exit choice on: still at once", 0, ExitSelect.delayMs(false, true, true));
        eq("going to sleep, CEC control off: never", -1, ExitSelect.delayMs(false, false, false));
        eq("awake, exit choice off (default): never", -1, ExitSelect.delayMs(true, true, false));
        eq("awake, exit choice on: 1 s later", ExitSelect.ON_EXIT_DELAY_MS, ExitSelect.delayMs(true, true, true));
        eq("awake, CEC control off, exit choice on: never", -1, ExitSelect.delayMs(true, false, true));
        System.out.println("ExitSelectTest: " + checks + " checks, " + fails + " failures");
        if (fails > 0) System.exit(1);
    }
}
