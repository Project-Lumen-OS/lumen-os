// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;

/**
 * Host test of the shared power key rule (PowerPress) and the power menu items (PowerItem), run by test/run.sh. Most
 * cases run with the setting "Sleep" (a short press sleeps, a hold of 700 ms opens the power menu: time, not repeats, as
 * HB): a canceled press, a press that woke the projector, presses that began elsewhere, the light given back by a press
 * while it is off. Then the stock default "Power menu" (short press = menu, hold = sleep), the setting read at each
 * press, and the stock item order.
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

    /** The setting "Sleep": a short press sleeps, the hold opens the power menu. */
    private static final Supplier<PowerKeyChoice> SLEEP_FIRST = () -> PowerKeyChoice.SLEEP;

    public static void main(String[] args) {
        check(PowerPress.HOLD_MS == 700, "the HB hold: 700 ms (Z9X / HB PowerKey.LONG_PRESS_MS)");

        PowerPress p = new PowerPress(null, SLEEP_FIRST);
        check(press(p, 1_000, 0, false, false).equals(list(PowerAction.SLEEP)), "a short press sleeps on its UP");
        check(press(p, 2_000, 6, false, false).equals(list(PowerAction.SLEEP)), "650 ms (6 repeats) is still a short press");
        check(press(p, 3_000, 7, false, false).equals(list(PowerAction.MENU)), "the repeat at 700 ms opens the menu, its UP nothing");
        check(press(p, 4_000, 60, false, false).equals(list(PowerAction.MENU)), "a long hold opens the menu once");
        check(press(p, 10_000, 0, true, false).isEmpty(), "a canceled press does nothing");
        check(press(p, 11_000, 30, false, true).isEmpty(), "the press that woke the projector does nothing");
        check(press(p, 13_000, 0, false, false).equals(list(PowerAction.SLEEP)), "after a wake press the next press works");

        // the hold is the time the key is down, not the repeat count (HB: eventTime - downTime >= LONG_PRESS_MS)
        PowerPress t = new PowerPress(null, SLEEP_FIRST);
        t.on(true, 0, 20_000, 20_000, false, false);
        check(t.on(true, 30, 20_000, 20_699, false, false) == PowerAction.NONE, "30 fast repeats inside 699 ms are no hold");
        check(t.on(true, 1, 20_000, 20_700, false, false) == PowerAction.MENU, "one slow repeat at 700 ms is the hold");
        check(t.on(false, 0, 20_000, 20_800, false, false) == PowerAction.NONE, "the UP after the menu does not sleep");

        // events of a press this rule never armed
        PowerPress q = new PowerPress(null, SLEEP_FIRST);
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
        PowerPress l = new PowerPress(lamp, SLEEP_FIRST);
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

        // the wake (stock XgimiWindowManager mSystemActive and checkWakeUpStatus; X50 report: short press sleeps, the
        // first press lights up for a moment and the projector goes down again, the second press wakes it): from
        // SCREEN_OFF until the light reads on at a check after SCREEN_ON, never shorter than the HB wake echo, a press
        // belongs to the wake and does nothing, held or not
        check(PowerPress.WAKE_CHECK_MS == 1000 && PowerPress.WAKE_MAX_MS == 7000,
                "stock wake checks: the first 1 s after the wake, then every 1 s, the 7th ends the wake");
        PowerPress w = new PowerPress(null, SLEEP_FIRST);
        check(press(w, 40_000, 0, false, false).equals(list(PowerAction.SLEEP)), "the short press sleeps");
        w.onSleep();
        check(w.isAsleep(), "SCREEN_OFF: asleep");
        check(press(w, 50_000, 0, false, false).isEmpty(), "a press after SCREEN_OFF and before SCREEN_ON does nothing");
        check(press(w, 59_900, 0, false, true).isEmpty(), "the press that wakes it does nothing");
        w.onWake(60_000);
        check(!w.isAsleep(), "SCREEN_ON: awake");
        check(press(w, 61_200, 0, false, false).isEmpty(), "the wake echo 1.2 s after SCREEN_ON does not sleep again");
        check(press(w, 62_100, 7, false, false).isEmpty(), "a hold while the light is still dark opens no menu");
        check(w.lightCheck(61_000, false) == PowerPress.WAKE_CHECK_MS, "the light dark at 1 s: next check 1 s later");
        check(w.lightCheck(62_000, false) == PowerPress.WAKE_CHECK_MS, "the light dark at 2 s: next check 1 s later");
        check(w.lightCheck(63_000, true) == -1, "the light on at 3 s: the wake is over, no more checks");
        check(w.lightCheck(64_000, false) == -1, "no check after the wake is over");
        check(press(w, 62_900, 0, false, false).isEmpty(), "a press that went down before the light read on (late broadcast)");
        check(press(w, 63_000, 0, false, false).equals(list(PowerAction.SLEEP)), "with the light back a short press sleeps");
        check(press(w, 64_000, 7, false, false).equals(list(PowerAction.MENU)), "with the light back a hold opens the menu");

        // the light back at the first check: the HB wake echo (Z9X HB PowerKey.WAKE_GUARD_MS) is the shortest wake
        PowerPress e = new PowerPress(null, SLEEP_FIRST);
        e.onSleep();
        e.onWake(70_000);
        check(e.lightCheck(71_000, true) == -1, "the light on at the first check");
        check(press(e, 71_400, 0, false, false).isEmpty(), "1.4 s after SCREEN_ON is still the wake echo");
        check(press(e, 71_500, 0, false, false).equals(list(PowerAction.SLEEP)), "from 1.5 s the press is the user's");

        // the light never reads on: the 7th check ends the wake (stock mCheckCount > 6), and without any check too
        PowerPress d = new PowerPress(null, SLEEP_FIRST);
        d.onSleep();
        d.onWake(100_000);
        int checks = 0;
        long when = 100_000 + PowerPress.WAKE_CHECK_MS, next;
        while ((next = d.lightCheck(when, false)) > 0 && checks < 20) { checks++; when += next; }
        check(checks == 6 && when == 107_000, "a dark light: 6 checks go on, the 7th at 7 s ends the wake (" + checks + ", " + when + ")");
        check(press(d, 106_900, 0, false, false).isEmpty(), "6.9 s after SCREEN_ON with a dark light: still the wake");
        check(press(d, 107_000, 0, false, false).equals(list(PowerAction.SLEEP)), "from 7 s the press is the user's again");
        PowerPress n = new PowerPress(null, SLEEP_FIRST);
        n.onSleep();
        n.onWake(200_000);
        check(press(n, 206_999, 0, false, false).isEmpty(), "no light check ran: the wake still holds until 7 s");
        check(press(n, 207_000, 0, false, false).equals(list(PowerAction.SLEEP)), "no light check ran: the wake ends at 7 s");
        check(new PowerPress(null, SLEEP_FIRST).lightCheck(5, false) == -1, "no wake: no light check");

        // a new sleep during the wake starts over; an app started while awake has no wake to wait for
        PowerPress s = new PowerPress(null, SLEEP_FIRST);
        s.onSleep();
        s.onWake(300_000);
        s.onSleep();
        check(press(s, 309_000, 0, false, false).isEmpty(), "asleep again: no press acts, the old wake does not end it");
        s.onWake(310_000);
        check(s.lightCheck(311_000, true) == -1 && press(s, 312_000, 0, false, false).equals(list(PowerAction.SLEEP)),
                "the second wake ends with its own light");
        check(press(new PowerPress(null, SLEEP_FIRST), 5, 0, false, false).equals(list(PowerAction.SLEEP)), "no SCREEN_OFF seen: presses act");

        // the light off by Turn off screen still comes back on a press during the wake (stock kPPowerPress relights)
        FakeLight wl = new FakeLight();
        LampOff wlamp = new LampOff(wl, new FakeMark());
        PowerPress r = new PowerPress(wlamp, SLEEP_FIRST);
        r.onSleep();
        r.onWake(400_000);
        wlamp.turnOff(ok -> { });
        check(press(r, 400_500, 0, false, false).equals(list(PowerAction.RELIGHT)), "the dark light comes back during the wake");

        // the owner's sequence: sleep, one wake press, the screen on, no second shutdown
        PowerPress o = new PowerPress(null, SLEEP_FIRST);
        List<PowerAction> all = new ArrayList<>(press(o, 500_000, 0, false, false));
        o.onSleep();
        all.addAll(press(o, 530_000, 0, false, true));
        o.onWake(530_050);
        all.addAll(press(o, 532_300, 0, false, false));
        for (long c = 531_050; o.lightCheck(c, c >= 533_050) > 0; c += PowerPress.WAKE_CHECK_MS) { }
        check(all.equals(list(PowerAction.SLEEP)), "one sleep, one wake press, no second sleep: " + all);

        // the setting (2026-11-26): stock "Power menu" by default, the other action on the hold
        check(PowerKeyChoice.of("power_menu") == PowerKeyChoice.POWER_MENU && PowerKeyChoice.of("sleep") == PowerKeyChoice.SLEEP
                && PowerKeyChoice.of("menu") == null && PowerKeyChoice.of(null) == null, "the tokens of the setting");
        check(PowerKeyChoice.POWER_MENU.shortPress() == PowerAction.MENU && PowerKeyChoice.POWER_MENU.hold() == PowerAction.SLEEP
                && PowerKeyChoice.SLEEP.shortPress() == PowerAction.SLEEP && PowerKeyChoice.SLEEP.hold() == PowerAction.MENU,
                "the hold always does the other action");
        PowerPress m = new PowerPress(null, () -> PowerKeyChoice.POWER_MENU);
        check(press(m, 600_000, 0, false, false).equals(list(PowerAction.MENU)), "Power menu: a short press opens the menu on its UP");
        check(press(m, 601_000, 6, false, false).equals(list(PowerAction.MENU)), "Power menu: 650 ms is still a short press");
        check(press(m, 602_000, 7, false, false).equals(list(PowerAction.SLEEP)), "Power menu: the 700 ms hold sleeps, its UP nothing");
        check(press(m, 603_000, 60, false, false).equals(list(PowerAction.SLEEP)), "Power menu: a long hold sleeps once");
        check(press(m, 604_000, 0, true, false).isEmpty(), "Power menu: a canceled press does nothing");
        m.onSleep();
        check(press(m, 605_000, 0, false, true).isEmpty(), "Power menu: the press that woke the projector opens no menu");
        check(press(new PowerPress(null, null), 5, 0, false, false).equals(list(PowerAction.MENU)), "no setting source: stock");
        check(press(new PowerPress(null, () -> null), 5, 7, false, false).equals(list(PowerAction.SLEEP)), "no stored value: stock");
        FakeLight ml = new FakeLight();
        LampOff mlamp = new LampOff(ml, new FakeMark());
        PowerPress mr = new PowerPress(mlamp, () -> PowerKeyChoice.POWER_MENU);
        mlamp.turnOff(ok -> { });
        check(press(mr, 700_000, 0, false, false).equals(list(PowerAction.RELIGHT)), "Power menu: a press in the dark only relights");
        PowerKeyChoice[] now = {PowerKeyChoice.SLEEP};
        PowerPress c = new PowerPress(null, () -> now[0]);
        check(press(c, 800_000, 0, false, false).equals(list(PowerAction.SLEEP)), "set to Sleep: sleeps");
        now[0] = PowerKeyChoice.POWER_MENU;
        check(press(c, 801_000, 0, false, false).equals(list(PowerAction.MENU)), "changed in the menu: the next press opens the menu");
        check(press(c, 802_000, 7, false, false).equals(list(PowerAction.SLEEP)), "changed in the menu: the next hold sleeps");

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
