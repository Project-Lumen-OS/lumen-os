package org.lumen.menu;

/**
 * A choice row that was stepped (LEFT / RIGHT, then the commit after a short delay) and the device's answer to it.
 *
 * Why (HB rig 2026-11-14, Slava): the output row listed "Bluetooth" while no Bluetooth output could be chosen, the HB
 * refused the step ("output bt: not offered now") and sent its real value back (Headphones) inside the step window, so
 * the row kept showing "Bluetooth" over the stored choice. Rule: on every answer that did not take the step (refused,
 * failed, not ready) the row forgets the step at once and shows the device's value; a done step or a question keeps it
 * until the read-back. Plain Java, host tested (test/ChoiceStepTest). Used by MenuController.after.
 */
public final class ChoiceStep {
    private ChoiceStep() {}

    /** The stepped option is dropped and the row shows the device's value again. */
    public static boolean forget(ResultKind answer) {
        return answer == ResultKind.REFUSED || answer == ResultKind.FAILED || answer == ResultKind.NOT_READY;
    }
}
