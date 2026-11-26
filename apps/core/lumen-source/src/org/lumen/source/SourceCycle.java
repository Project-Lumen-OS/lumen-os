// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.source;

import java.util.Set;

/**
 * The stock cycle of the Source key (XRMService XrmShortcutEvent.jumpToHdmi, the same body on Z9X, HB, X50, RS30 and
 * Titan, gsi-stock analysis): not on an HDMI input, the first connected port, or the first port when
 * none is connected; on an HDMI input, the next port by number, connected or not, then the home screen (Play 6: the
 * first port again); a model with one port stays where it is. Positions are port numbers, {@link #HOME} or
 * {@link #NONE}. Pure.
 */
public final class SourceCycle {
    /** The home screen or any app: not on an HDMI input. */
    public static final int HOME = 0;
    /** No step: the key does nothing (stock "HDMI Empty or only one"). */
    public static final int NONE = -1;

    private SourceCycle() {}

    /**
     * @param ports     the model's HDMI ports in ascending order (the TV inputs org.lumen.tvinput registered)
     * @param connected ports whose input state is CONNECTED
     * @param current   the port on screen, or {@link #HOME}
     */
    public static int next(SourceRule rule, int[] ports, Set<Integer> connected, int current) {
        if (ports.length == 0) return NONE;
        if (current <= HOME) {
            for (int p : ports) if (connected.contains(p)) return p;
            return ports[0];
        }
        if (ports.length == 1) return NONE;
        for (int p : ports) if (p > current) return p;
        return rule.homeAfterLast ? HOME : ports[0];
    }
}
