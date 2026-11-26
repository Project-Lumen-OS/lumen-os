// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

/**
 * The remote power key of a projector whose Lumen app receives it (a global key, also while asleep), one rule for
 * every such app: the Z9X HB rule (2026-11-13: the power key acts as on the HB on every model, not as the stock
 * kPPowerPress; apps/ProjectorBase power/PowerKey). The light off by "Turn off screen" -> the press gives it back and
 * does nothing else ({@link LampOff#eats}); a press that woke the projector does nothing more; held {@link #HOLD_MS}
 * -> the power menu, once, while the key is still down; released before that -> sleep. A canceled UP, a repeat or an
 * UP of a press that began elsewhere do nothing; every UP ends the armed press (as {@link GearPress}).
 *
 * <p>Plain Java, no Android types: one instance per receiver, main thread only.
 */
public final class PowerPress {
    /** The HB hold: the power menu once the key is held this long (Z9X / HB PowerKey.LONG_PRESS_MS, ~7 key repeats). */
    public static final long HOLD_MS = 700;
    private static final long NO_PRESS = -1;

    /** The device's "Turn off screen", null where it has none. */
    private final LampOff lampOff;
    /** downTime of the armed press, {@link #NO_PRESS} when none. */
    private long downTime = NO_PRESS;
    /** The armed press already opened the power menu. */
    private boolean held;

    public PowerPress(LampOff lampOff) {
        this.lampOff = lampOff;
    }

    /** One event of the power key (the eventTime of a repeat decides the hold, as HB): what the device does with it. */
    public PowerAction on(boolean down, int repeatCount, long eventDownTime, long eventTime, boolean canceled,
                          boolean beganAsleep) {
        if (lampOff != null && lampOff.eats(LampOffKey.POWER, down, repeatCount, eventDownTime)) {
            disarm();
            return down && repeatCount == 0 ? PowerAction.RELIGHT : PowerAction.NONE;
        }
        if (beganAsleep) {
            disarm();
            return PowerAction.NONE;
        }
        if (down && repeatCount == 0) {
            downTime = eventDownTime;
            held = false;
            return PowerAction.NONE;
        }
        boolean armed = eventDownTime == downTime && !held;
        if (down) {
            if (!armed || eventTime - eventDownTime < HOLD_MS) return PowerAction.NONE;
            held = true;
            return PowerAction.MENU;
        }
        disarm();
        return armed && !canceled ? PowerAction.SLEEP : PowerAction.NONE;
    }

    private void disarm() {
        downTime = NO_PRESS;
        held = false;
    }
}
