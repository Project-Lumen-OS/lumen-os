// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

/**
 * ChoiceStep: a stepped choice the device did not take shows the device's value at once (HB rig 2026-11-14: the output
 * row kept "Bluetooth" after the HB refused it and sent back Headphones, the stored choice).
 */
public final class ChoiceStepTest {
    private static int passed, failed;

    private static void ok(String what, boolean cond) {
        if (cond) passed++;
        else { failed++; System.out.println("FAIL " + what); }
    }

    public static void main(String[] a) {
        ok("refused: the row shows the device value", ChoiceStep.forget(ResultKind.REFUSED));
        ok("failed: the row shows the device value", ChoiceStep.forget(ResultKind.FAILED));
        ok("not ready: the row shows the device value", ChoiceStep.forget(ResultKind.NOT_READY));
        ok("done: the step stays until the read-back", !ChoiceStep.forget(ResultKind.DONE));
        ok("a question: the step stays", !ChoiceStep.forget(ResultKind.ASK));
        ok("no answer kind: nothing forgotten", !ChoiceStep.forget(null));
        System.out.println("ChoiceStepTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
