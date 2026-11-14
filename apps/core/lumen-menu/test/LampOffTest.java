// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

import java.util.ArrayList;
import java.util.List;

/**
 * Host test of the shared "Turn off screen" rule (LampOff), run by test/run.sh: the menu row, the power key that gives
 * the light back and never sleeps, the eaten focus keys, the other keys, a sleep, a restart of the app and a reboot.
 */
public final class LampOffTest {
    private static int pass, fail;

    private static void check(boolean ok, String what) {
        if (ok) pass++;
        else { fail++; System.out.println("FAIL " + what); }
    }

    /** Records the light calls, the off call answers with {@code reads}. */
    private static final class FakeLight implements LampOff.Light {
        final List<String> calls = new ArrayList<>();
        boolean reads = true;
        @Override public void off(java.util.function.Consumer<Boolean> done) { calls.add("off"); done.accept(reads); }
        @Override public void on() { calls.add("on"); }
    }

    /** A mark as the preferences would keep it: the boot it was set in. */
    private static final class FakeMark implements LampOff.Marker {
        String saved = "", boot = "b1";
        @Override public boolean thisBoot() { return boot.equals(saved); }
        @Override public boolean earlierBoot() { return !saved.isEmpty() && !boot.equals(saved); }
        @Override public void mark(boolean off) { saved = off ? boot : ""; }
    }

    private static boolean power(LampOff o, boolean down, int repeat, long downTime) {
        return o.eats(LampOffKey.POWER, down, repeat, downTime);
    }

    /**
     * The brightness slider as SliderRow steps it (one step per press, the edge listener at min / max) with the
     * MenuController edge rule on top: counts the light-off requests it sends to the "Turn off screen" path.
     */
    private static final class Slider {
        final boolean offered;
        final int min, max;
        Integer value;
        int offs;

        Slider(boolean offered, int min, int max, Integer value) {
            this.offered = offered;
            this.min = min;
            this.max = max;
            this.value = value;
        }

        Slider press(int dir, int repeat) {
            if (value == null) {
                if (LampOff.offFromSlider(offered, null, min, dir, repeat)) offs++;
                return this;
            }
            int nv = value + dir;
            if (nv < min || nv > max) {
                if (LampOff.offFromSlider(offered, value, min, dir, repeat)) offs++;
                return this;
            }
            value = nv;
            return this;
        }
    }

    /** LEFT at the lowest level turns the light off where lamp.off is offered, every other press just steps. */
    private static void slider(String devices) throws java.io.IOException {
        Slider s = new Slider(true, 1, 10, 1).press(-1, 0);
        check(s.offs == 1 && s.value == 1, "left at the minimum asks for the light-off once");
        s.press(-1, 1).press(-1, 7);
        check(s.offs == 1, "holding left at the minimum asks only once");
        s.press(-1, 0);
        check(s.offs == 2, "a new left press at the minimum asks again (the row does it again too)");
        s = new Slider(true, 1, 10, 3).press(-1, 0);
        check(s.offs == 0 && s.value == 2, "left above the minimum only steps down");
        s = new Slider(true, 1, 10, 5);
        for (int r = 0; r < 6; r++) s.press(-1, r);
        check(s.value == 1 && s.offs == 0, "a held left that reaches the minimum never turns the light off");
        s = new Slider(true, 1, 10, 1).press(1, 0);
        check(s.offs == 0 && s.value == 2, "right at the minimum steps up, never a light-off");
        s = new Slider(true, 1, 10, 10).press(1, 0).press(1, 1);
        check(s.offs == 0 && s.value == 10, "right at the maximum never turns the light off");
        check(!LampOff.offFromSlider(true, 1, 1, 1, 0), "right is never a light-off");
        s = new Slider(false, 1, 10, 1).press(-1, 0);
        check(s.offs == 0 && s.value == 1, "without lamp.off left at the minimum does nothing");
        s = new Slider(true, 1, 10, null).press(-1, 0);
        check(s.offs == 0, "an unread level never turns the light off");

        // the real profiles: only the ones that offer lamp.off (Z9X, HB, Play 6) get it, every slider starts at 1
        java.util.Set<String> want = java.util.Set.of("horse_popular", "goat_elegant", "dragon_healthy");
        java.util.Set<String> got = new java.util.TreeSet<>();
        java.io.File[] dirs = new java.io.File(devices).listFiles();
        check(dirs != null && dirs.length > 0, "the device profiles are found in " + devices);
        if (dirs == null) return;
        for (java.io.File d : dirs) {
            java.io.File j = new java.io.File(d, "menu.json");
            if (!j.isFile()) continue;
            MenuProfile p = MenuProfile.parse(new String(java.nio.file.Files.readAllBytes(j.toPath()), "UTF-8"));
            if (!p.has(FeatureId.LAMP)) continue;
            boolean offered = p.has(FeatureId.LAMP_OFF);
            int min = p.number(FeatureId.LAMP, "min", 1), max = p.number(FeatureId.LAMP, "max", 10);
            Slider t = new Slider(offered, min, max, min).press(-1, 0);
            check(t.offs == (want.contains(d.getName()) ? 1 : 0) && t.value == min,
                    d.getName() + ": left at the minimum " + (want.contains(d.getName()) ? "turns the light off" : "does nothing"));
            if (offered) got.add(d.getName());
        }
        check(got.equals(new java.util.TreeSet<>(want)), "lamp.off is offered by exactly " + want + ", got " + got);
    }

    public static void main(String[] a) throws java.io.IOException {
        FakeLight l = new FakeLight();
        FakeMark m = new FakeMark();
        LampOff o = new LampOff(l, m);
        o.restore();
        check(!o.isOff() && l.calls.isEmpty(), "a fresh boot sends nothing");
        check(!power(o, true, 0, 1) && !power(o, false, 0, 1), "the power key is the device's while the light is on");
        check(!o.eats(LampOffKey.FOCUS, true, 0, 2), "focus keys work while the light is on");

        boolean[] got = new boolean[1];
        o.turnOff(ok -> got[0] = ok);
        check(got[0] && o.isOff() && l.calls.equals(List.of("off")), "the row turns the light off");
        check(m.thisBoot(), "the off light is marked for this boot");
        check(!o.eats(LampOffKey.OTHER, true, 0, 3), "other keys go on as usual (stock)");
        check(o.isOff(), "other keys leave the light off (stock)");
        check(o.eats(LampOffKey.FOCUS, true, 0, 4) && o.eats(LampOffKey.FOCUS, false, 0, 4), "focus keys are eaten (stock)");

        check(power(o, true, 0, 10), "the power DOWN is eaten");
        check(!o.isOff() && l.calls.equals(List.of("off", "on")), "the power key gives the light back");
        check(!m.thisBoot() && !m.earlierBoot(), "the mark is cleared");
        check(power(o, true, 1, 10) && power(o, true, 20, 10), "holding that press never reaches the sleep");
        check(power(o, false, 0, 10), "the UP of that press is eaten (no power menu)");
        check(!power(o, true, 0, 11) && !power(o, false, 0, 11), "the next power press is the device's again");
        check(l.calls.size() == 2, "one press sends one light-on");

        o.turnOff(ok -> { });
        check(!power(o, true, 5, 12), "a repeat of a press that began before the light went off is the device's");
        check(o.isOff(), "...and leaves the light off");

        // a sleep while the light is off: the device's sleep path owns the light (its wake lights it)
        o.onSleep();
        check(!o.isOff() && !m.thisBoot(), "a sleep ends the light-off state and its mark");
        check(!power(o, true, 0, 20), "the power key wakes / sleeps as usual after that");
        int n = l.calls.size();
        o.onSleep();
        check(l.calls.size() == n && !o.isOff(), "a sleep with the light on changes nothing");

        // the light did not go off: nothing is marked, the power key stays the device's
        l.reads = false;
        o.turnOff(ok -> got[0] = ok);
        check(!got[0] && !o.isOff() && !m.thisBoot(), "a failed light-off changes nothing");
        l.reads = true;

        // the app restarts in the same boot while the light is off: the power key still gives it back
        o.turnOff(ok -> { });
        LampOff again = new LampOff(l, m);
        again.restore();
        check(again.isOff(), "a restart in the same boot keeps the light-off state");
        check(power(again, true, 0, 30), "...and the power key still gives the light back");

        // a reboot with a mark left: the light comes back once, the mark goes
        o.turnOff(ok -> { });
        m.boot = "b2";
        LampOff boot2 = new LampOff(l, m);
        n = l.calls.size();
        boot2.restore();
        check(!boot2.isOff(), "a reboot never keeps the light off");
        check(l.calls.size() == n + 1 && l.calls.get(n).equals("on"), "a mark from an earlier boot gives the light back");
        check(!m.earlierBoot() && !m.thisBoot(), "...and is cleared");
        boot2.restore();
        check(l.calls.size() == n + 1, "only once");

        slider(a.length > 0 ? a[0] + "/devices" : "devices");

        System.out.println("LampOffTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
