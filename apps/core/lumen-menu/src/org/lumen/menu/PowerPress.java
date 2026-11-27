// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The remote power key of a projector whose Lumen app receives it (a global key, also while asleep), one rule for
 * every such app (apps/LumenProjector/core power/PowerKey keeps the same timing). The light off by "Turn off screen" ->
 * the press gives it back and does nothing else ({@link LampOff#eats}); a press that woke the projector does nothing
 * more; held {@link #HOLD_MS} -> {@link PowerKeyChoice#hold}, once, while the key is still down; released before that
 * -> {@link PowerKeyChoice#shortPress} (2026-11-26: the user's setting, stock power menu by default; the choice is
 * read at each press, so a change in the menu counts from the next press); while the power menu is open that short
 * press powers off (stock, {@link PowerKeyChoice#shortPress(boolean)}). A canceled UP, a repeat or an UP of a press
 * that began elsewhere do nothing; every UP ends the armed press (as {@link GearPress}).
 *
 * <p>The wake (stock XgimiWindowManager: mSystemActive false from the sleep until checkWakeUpStatus reads the light on,
 * 1 s after the wake and then every 1 s, the 7th check ends it; until then PhoneWindowManager only wakes on a power key
 * and xgimiGotoSleep refuses): a press that goes down after {@link #onSleep} and before the wake is over does nothing,
 * held or not, so a second key event of the waking press never sleeps the projector again. The wake ends when
 * {@link #lightCheck} reads the light on, never before {@link #WAKE_ECHO_MS} (the Z9X HB wake echo), at the latest
 * {@link #WAKE_MAX_MS} after {@link #onWake}, also when no check ran.
 *
 * <p>Plain Java, no Android types: one instance per receiver, main thread only.
 */
public final class PowerPress {
    /** The HB hold: the power menu once the key is held this long (Z9X / HB PowerKey.LONG_PRESS_MS, ~7 key repeats). */
    public static final long HOLD_MS = 700;
    /** Stock checkWakeUpStatus: the first light check this long after the wake, then one every this long. */
    public static final long WAKE_CHECK_MS = 1000;
    /** The shortest wake: the Z9X HB wake echo (PowerKey.WAKE_GUARD_MS), presses this soon belong to the wake. */
    public static final long WAKE_ECHO_MS = 1500;
    /** The longest wake: the 7th stock check ends it (mCheckCount > 6), the light read on or not. */
    public static final long WAKE_MAX_MS = 7000;
    private static final long NO_PRESS = -1;

    /** The device's "Turn off screen", null where it has none. */
    private final LampOff lampOff;
    /** The user's setting, read at each press (null: stock). */
    private final Supplier<PowerKeyChoice> choice;
    /** The power menu is on screen, read at the UP of a short press (null: never). */
    private final BooleanSupplier menuOpen;
    /** downTime of the armed press, {@link #NO_PRESS} when none. */
    private long downTime = NO_PRESS;
    /** The armed press already opened the power menu. */
    private boolean held;
    /** SCREEN_OFF seen, no SCREEN_ON since. */
    private boolean asleep;
    /** Uptime of the last SCREEN_ON, {@link #NO_PRESS} when there was none since the last sleep. */
    private long wokeAt = NO_PRESS;
    /** Uptime of the check that ended that wake, {@link #NO_PRESS} while it lasts. */
    private long lightAt = NO_PRESS;

    public PowerPress(LampOff lampOff, Supplier<PowerKeyChoice> choice) {
        this(lampOff, choice, null);
    }

    public PowerPress(LampOff lampOff, Supplier<PowerKeyChoice> choice, BooleanSupplier menuOpen) {
        this.lampOff = lampOff;
        this.choice = choice;
        this.menuOpen = menuOpen;
    }

    /** The user's choice, the stock one when it cannot be read. */
    private PowerKeyChoice choice() {
        PowerKeyChoice c = choice == null ? null : choice.get();
        return c == null ? PowerKeyChoice.POWER_MENU : c;
    }

    /** Android went to sleep (SCREEN_OFF): every press belongs to the coming wake. */
    public void onSleep() {
        asleep = true;
        wokeAt = NO_PRESS;
        lightAt = NO_PRESS;
        disarm();
    }

    /** Android woke (SCREEN_ON) at {@code uptime}: the wake lasts until the light is back (see the class note). */
    public void onWake(long uptime) {
        asleep = false;
        wokeAt = uptime;
        lightAt = NO_PRESS;
        disarm();
    }

    /** SCREEN_OFF seen and no SCREEN_ON since. */
    public boolean isAsleep() {
        return asleep;
    }

    /**
     * One light check of the wake at {@code now} (stock checkWakeUpStatus): the delay to the next check, or -1 when the
     * wake is over (the light on, or the last check).
     */
    public long lightCheck(long now, boolean lightOn) {
        if (asleep || wokeAt == NO_PRESS || lightAt != NO_PRESS) return -1;
        if (!lightOn && now - wokeAt < WAKE_MAX_MS) return WAKE_CHECK_MS;
        lightAt = now;
        return -1;
    }

    /** The press that went down at {@code downTime} belongs to the wake. */
    private boolean waking(long downTime) {
        if (asleep) return true;
        if (wokeAt == NO_PRESS) return false;
        long end = lightAt == NO_PRESS ? wokeAt + WAKE_MAX_MS : Math.max(lightAt, wokeAt + WAKE_ECHO_MS);
        return downTime < end;
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
        if (down && repeatCount == 0 && waking(eventDownTime)) {
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
            return choice().hold();
        }
        disarm();
        return armed && !canceled ? choice().shortPress(menuOpen != null && menuOpen.getAsBoolean()) : PowerAction.NONE;
    }

    private void disarm() {
        downTime = NO_PRESS;
        held = false;
    }
}
