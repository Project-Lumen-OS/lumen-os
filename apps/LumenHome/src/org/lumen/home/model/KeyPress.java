// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Who saw a key go down, for the keys Home acts on when they come up (OK, BACK, MENU, the input key). Android gives a
 * window the release of a key that went down in another window, so a release alone proves nothing: the side panel's
 * confirm row that closed the panel, the projector menu's row that opened the backup page, a Home built anew while
 * the key was down, an app that closed on the press. A release acts only on the layer (Home, its side panel, the film
 * card, the source list) that saw the same press go down and is still on top; every other release is no one's. One
 * press is the key with the down time Android gives its down, its repeats and its release.
 */
public final class KeyPress {
    public static final int NONE = 0, HOME = 1, PANEL = 2, DETAILS = 3, SOURCE = 4;

    private final Map<Integer, long[]> mDown = new HashMap<>();

    /** The key went down (the first event, not a repeat) while {@code layer} was on top. */
    public void down(int code, long downTime, int layer) {
        mDown.put(code, new long[] { downTime, layer });
    }

    /** A repeat of a held key: true when this window saw that press go down (else it was held in another one). */
    public boolean held(int code, long downTime) {
        long[] d = mDown.get(code);
        return d != null && d[0] == downTime;
    }

    /** The key came up: the layer that saw this press go down, NONE when it went down elsewhere. The press is over. */
    public int up(int code, long downTime) {
        if (!held(code, downTime)) return NONE;
        return (int) mDown.remove(code)[1];
    }

    /** The release acts: the layer that saw the press is the one on top now. */
    public boolean upActs(int code, long downTime, int top) {
        int layer = up(code, downTime);
        return layer != NONE && layer == top;
    }
}
