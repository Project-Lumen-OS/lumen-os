// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/**
 * Host test of the shared power key rule (PowerPress, the Z9X HB rule) and the power menu items (PowerItem), run by
 * test/run.sh: a short press sleeps, a hold of 700 ms opens the power menu (time, not repeats, as HB), a canceled press,
 * a press that woke the projector, presses that began elsewhere, the light given back by a press while it is off, and
 * the stock item order.
 */
public final class PowerPressTest {
    private static int pass, fail;

    private static void check(boolean ok, String what) {
        if (ok) pass++;
        else { fail++; System.out.println("FAIL " + what); }
    }

    private static final class FakeLight implements LampOff.Light {
        final List<String> calls = new ArrayList<>();
        @Override public void off(java.util.function.Consumer<Boolean> done) { calls.add("off"); done.accept(true); }
        @Override public void on() { calls.add("on"); }
    }

    private static final class FakeMark implements LampOff.Marker {
        boolean set;
        @Override public boolean thisBoot() { return set; }
        @Override public boolean earlierBoot() { return false; }
        @Override public void mark(boolean off) { set = off; }
    }

    /** eventTime of the r-th repeat of a press that went down at t: the framework's 400 ms, then every 50 ms. */
    private static long at(long t, int r) {
        return r == 0 ? t : t + 400 + (r - 1) * 50L;
    }

    /** A whole press: DOWN, {@code repeats} repeated DOWNs, UP; the actions in order (NONE left out). */
    private static List<PowerAction> press(PowerPress p, long downTime, int repeats, boolean canceled, boolean asleep) {
        List<PowerAction> out = new ArrayList<>();
        add(out, p.on(true, 0, downTime, downTime, false, asleep));
        for (int r = 1; r <= repeats; r++) add(out, p.on(true, r, downTime, at(downTime, r), false, asleep));
        add(out, p.on(false, 0, downTime, at(downTime, repeats) + 20, canceled, asleep));
        return out;
    }

    private static void add(List<PowerAction> out, PowerAction a) {
        if (a != PowerAction.NONE) out.add(a);
    }

    private static List<PowerAction> list(PowerAction... a) { return Arrays.asList(a); }

    public static void main(String[] args) {
        check(PowerPress.HOLD_MS == 700, "the HB hold: 700 ms (Z9X / HB PowerKey.LONG_PRESS_MS)");

        PowerPress p = new PowerPress(null);
        check(press(p, 1_000, 0, false, false).equals(list(PowerAction.SLEEP)), "a short press sleeps on its UP");
        check(press(p, 2_000, 6, false, false).equals(list(PowerAction.SLEEP)), "650 ms (6 repeats) is still a short press");
        check(press(p, 3_000, 7, false, false).equals(list(PowerAction.MENU)), "the repeat at 700 ms opens the menu, its UP nothing");
        check(press(p, 4_000, 60, false, false).equals(list(PowerAction.MENU)), "a long hold opens the menu once");
        check(press(p, 10_000, 0, true, false).isEmpty(), "a canceled press does nothing");
        check(press(p, 11_000, 30, false, true).isEmpty(), "the press that woke the projector does nothing");
        check(press(p, 13_000, 0, false, false).equals(list(PowerAction.SLEEP)), "after a wake press the next press works");

        // the hold is the time the key is down, not the repeat count (HB: eventTime - downTime >= LONG_PRESS_MS)
        PowerPress t = new PowerPress(null);
        t.on(true, 0, 20_000, 20_000, false, false);
        check(t.on(true, 30, 20_000, 20_699, false, false) == PowerAction.NONE, "30 fast repeats inside 699 ms are no hold");
        check(t.on(true, 1, 20_000, 20_700, false, false) == PowerAction.MENU, "one slow repeat at 700 ms is the hold");
        check(t.on(false, 0, 20_000, 20_800, false, false) == PowerAction.NONE, "the UP after the menu does not sleep");

        // events of a press this rule never armed
        PowerPress q = new PowerPress(null);
        check(q.on(false, 0, 10, 50, false, false) == PowerAction.NONE, "an UP without its DOWN does nothing");
        check(q.on(true, 25, 10, 2_000, false, false) == PowerAction.NONE, "a repeat without its DOWN does nothing");
        q.on(true, 0, 20, 20, false, false);
        check(q.on(true, 20, 21, 2_000, false, false) == PowerAction.NONE, "a repeat of another press is no hold");
        check(q.on(false, 0, 21, 2_100, false, false) == PowerAction.NONE, "an UP of another press is no short press");
        check(q.on(false, 0, 20, 2_200, false, false) == PowerAction.NONE, "every UP ends the armed press (as GearPress)");
        q.on(true, 0, 3_000, 3_000, false, false);
        check(q.on(false, 0, 3_000, 3_100, false, false) == PowerAction.SLEEP, "the armed press ends as a short press");
        check(q.on(false, 0, 3_000, 3_200, false, false) == PowerAction.NONE, "a second UP of the same press is no second sleep");
        q.on(true, 0, 4_000, 4_000, false, false);
        q.on(true, 0, 4_001, 4_001, false, false);
        check(q.on(true, 20, 4_000, 5_000, false, false) == PowerAction.NONE, "a new DOWN takes over: the older press is gone");
        check(q.on(false, 0, 4_001, 4_100, false, false) == PowerAction.SLEEP, "the newer press ends as a short press");

        // the light off ("Turn off screen"): the press gives it back and nothing else, held or not
        FakeLight light = new FakeLight();
        LampOff lamp = new LampOff(light, new FakeMark());
        PowerPress l = new PowerPress(lamp);
        lamp.turnOff(ok -> { });
        check(lamp.isOff(), "light off");
        check(press(l, 30_000, 40, false, false).equals(list(PowerAction.RELIGHT)), "a press in the dark relights, never sleep or menu");
        check(light.calls.equals(Arrays.asList("off", "on")) && !lamp.isOff(), "the light is back once");
        check(press(l, 32_000, 0, false, false).equals(list(PowerAction.SLEEP)), "with the light back a short press sleeps");
        check(press(l, 34_000, 7, false, false).equals(list(PowerAction.MENU)), "with the light back a hold opens the menu");
        lamp.turnOff(ok -> { });
        check(l.on(true, 0, 36_000, 36_000, false, false) == PowerAction.RELIGHT, "the relighting DOWN");
        check(l.on(true, 7, 36_000, 36_700, false, false) == PowerAction.NONE, "its 700 ms repeat is no menu");
        check(l.on(false, 0, 36_000, 36_800, false, false) == PowerAction.NONE, "its UP is no sleep");
        check(light.calls.size() == 4, "one relight per press");

        // the power menu items: the stock order, feature-backed ones only where the menu offers them
        check(PowerItem.offered(f -> true).equals(Arrays.asList(PowerItem.SCREEN_OFF, PowerItem.POWER_OFF, PowerItem.RESTART,
                PowerItem.SLEEP_TIMER)), "stock order: Turn off screen, Power off, Restart, Sleep timer");
        check(PowerItem.offered(f -> false).equals(Arrays.asList(PowerItem.POWER_OFF, PowerItem.RESTART)),
                "without lamp.off and the sleep timer only Power off and Restart");
        EnumSet<FeatureId> some = EnumSet.of(FeatureId.SETTINGS_SLEEP_TIMER);
        check(PowerItem.offered(some::contains).equals(Arrays.asList(PowerItem.POWER_OFF, PowerItem.RESTART, PowerItem.SLEEP_TIMER)),
                "Turn off screen only with lamp.off");
        check(PowerItem.SCREEN_OFF.feature == FeatureId.LAMP_OFF && PowerItem.SLEEP_TIMER.feature == FeatureId.SETTINGS_SLEEP_TIMER,
                "the items use the menu's own features");

        System.out.println("PowerPressTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
