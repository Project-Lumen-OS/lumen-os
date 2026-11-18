// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

/**
 * A step that asks first (MenuController: Boost, the light-off at the lowest brightness): the first press arms it and
 * the menu shows its hint, the same press again within the window does it. Any other key ({@link #key}) or
 * {@link #cancel} drops the question. Plain Java, main thread.
 */
public final class PressAgain {
    private final long windowMs;
    private boolean armed;
    private int step;
    private long armedAt;

    public PressAgain(long windowMs) {
        this.windowMs = windowMs;
    }

    /** A press of {@code s} (e.g. the direction) at {@code now} ms: true = the second press, do the step now. */
    public boolean press(int s, long now) {
        if (armed && step == s && now - armedAt < windowMs) {
            armed = false;
            return true;
        }
        armed = true;
        step = s;
        armedAt = now;
        return false;
    }

    /** Every key down the menu sees, as the step it would press (0: none): any other step drops the question. */
    public void key(int s) {
        if (s != step) armed = false;
    }

    public void cancel() { armed = false; }
}
