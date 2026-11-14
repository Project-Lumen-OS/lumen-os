// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

/**
 * One press of the remote's gear key (KEYCODE_SETTINGS), the one rule every Lumen projector app shares: the
 * KeyReceiver of the Z9X, HB and Play 6 apps (quick menu on a short press, the profile's long action on a held gear)
 * and their eye guard while its mask is up (a short press gives the picture back, a held gear also turns eye
 * protection off).
 *
 * <p>Android delivers a held key as a DOWN with repeat count 0, repeated DOWNs from about 400 ms and one UP, which may
 * be canceled. The DOWN with repeat 0 arms a press. The first repeated DOWN of that same press (same downTime) is the
 * long press, once per press. The UP of that same press is the short press when no long press fired and the UP is
 * not canceled; every UP disarms. A repeat or an UP of a press that began elsewhere (another downTime, e.g. a press
 * that started before the eye-protection mask came up) is neither.
 *
 * <p>The remote's own gear hold (the stock MENU scancode, KEYCODE_TV_MEDIA_CONTEXT_MENU) is a separate one-shot key
 * and does not go through this class. Plain Java, no Android types: one instance per receiver, main thread only.
 */
public final class GearPress {
    /** downTime of the armed press, -1 when none. */
    private long downTime = -1;
    /** The armed press already fired its long press. */
    private boolean longFired;

    /** A DOWN of the gear key: true when it is the long press of the armed press (its first repeat). */
    public boolean down(int repeatCount, long eventDownTime) {
        if (repeatCount == 0) {
            longFired = false;
            downTime = eventDownTime;
            return false;
        }
        if (!longFired && eventDownTime == downTime) {
            longFired = true;
            return true;
        }
        return false;
    }

    /** Forget the armed press (the eye-protection mask came up: a press that began before it is only eaten). */
    public void reset() {
        downTime = -1;
        longFired = false;
    }

    /** The UP of the gear key: true when it ends the armed press as a short press. Disarms in every case. */
    public boolean up(long eventDownTime, boolean canceled) {
        boolean shortPress = !longFired && !canceled && eventDownTime == downTime;
        longFired = false;
        downTime = -1;
        return shortPress;
    }
}
