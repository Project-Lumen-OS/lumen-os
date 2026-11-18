// SPDX-License-Identifier: Apache-2.0
package org.lumen.source;

import java.util.Set;

/**
 * One press of the Source key and the presses that follow it while the card is still up. The screen needs a moment
 * to show a new input, so a press inside that window goes on from the last step, not from what the screen reports:
 * three quick presses from the home screen end on the third step, never twice on the same one. Pure, one thread.
 */
public final class SourcePress {
    private int last = SourceCycle.NONE;
    private long lastAt;

    /**
     * The step for a press at {@code now}: where to switch ({@link SourceCycle#NONE} = stay) and what the card names.
     * {@code onScreen} is the live position, used only when no card of an earlier press is up.
     */
    public SourceStep press(long now, SourceRule rule, int[] ports, Set<Integer> connected, int onScreen) {
        int from = last != SourceCycle.NONE && now - lastAt < rule.osdMs ? last : onScreen;
        int go = SourceCycle.next(rule, ports, connected, from);
        int shown = go == SourceCycle.NONE ? from : go;
        last = shown;
        lastAt = now;
        return new SourceStep(go, shown, from);
    }

    /** True while the card of the last press is still up at {@code now}. */
    public boolean burst(long now, SourceRule rule) {
        return last != SourceCycle.NONE && now - lastAt < rule.osdMs;
    }
}
