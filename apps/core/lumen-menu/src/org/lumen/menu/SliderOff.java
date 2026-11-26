// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

/**
 * "Turn off screen" from the brightness slider (MenuController, SliderRow edge), asked first like the Boost step: a
 * fresh press towards the start at the lowest level shows the hint, the same press again while the hint is up turns the
 * light off the row's own way (lamp.off: the menu closes first, only the power key gives the light back, see
 * {@link LampOff}). Any other key, the hint running out or a closed menu drop the question, a held key never turns the
 * light off and a press towards the end never does. Only where the profile offers lamp.off. Plain Java, main thread.
 */
public final class SliderOff {
    private final PressAgain ask;

    /** {@code hintMs}: how long the hint stays on screen, the second press counts only within it. */
    public SliderOff(long hintMs) {
        ask = new PressAgain(hintMs);
    }

    /** A press at the slider's edge. {@code dir} -1 = towards the start, {@code value} null = not read yet. */
    public SliderOffStep edge(boolean offered, Integer value, int min, int dir, int repeatCount, long now) {
        if (!offPress(offered, value, min, dir, repeatCount)) return SliderOffStep.NONE;
        return ask.press(dir, now) ? SliderOffStep.OFF : SliderOffStep.HINT;
    }

    /** Every key down the menu sees, before the rows: only a press towards the start on the slider keeps the question. */
    public void key(boolean sliderTowardsStart) {
        ask.key(sliderTowardsStart ? -1 : 0);
    }

    /** The menu closed: the question is gone. */
    public void cancel() { ask.cancel(); }

    static boolean offPress(boolean offered, Integer value, int min, int dir, int repeatCount) {
        return offered && dir < 0 && repeatCount == 0 && value != null && value <= min;
    }
}
