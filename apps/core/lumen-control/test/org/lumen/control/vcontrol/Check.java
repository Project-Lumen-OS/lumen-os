// SPDX-License-Identifier: Apache-2.0
package org.lumen.control.vcontrol;

import java.util.Objects;

/** The tiny assertion kit of the host tests: every failure is printed, the run fails at the end. */
final class Check {
    static int failures;
    static int passes;

    private Check() {}

    static void eq(String what, Object want, Object got) {
        if (Objects.equals(want, got)) {
            passes++;
            return;
        }
        failures++;
        System.out.println("FAIL " + what + "\n  want: " + want + "\n  got:  " + got);
    }

    static void yes(String what, boolean ok) {
        eq(what, true, ok);
    }
}
