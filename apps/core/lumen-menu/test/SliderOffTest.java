// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

import java.io.File;
import java.nio.file.Files;
import java.util.Set;
import java.util.TreeSet;

/**
 * Host test of the brightness slider's light-off (SliderOff, PressAgain), run by test/run.sh: LEFT at the lowest level
 * only shows the hint, LEFT again while the hint is up turns the light off, any other key, the hint running out, a
 * closed menu and a held key never do, and only the profiles with lamp.off get it.
 */
public final class SliderOffTest {
    private static final long HINT_MS = 6_000;
    private static int pass, fail;

    private static void check(boolean ok, String what) {
        if (ok) pass++;
        else { fail++; System.out.println("FAIL " + what); }
    }

    /**
     * The quick menu as MenuController wires it over SliderRow: every key down goes to {@link SliderOff#key} first,
     * then the slider steps one level per press and calls its edge only on a fresh press at the minimum or maximum
     * (nothing while the level is not read yet). Counts the hints shown and the light-off requests sent.
     */
    private static final class Menu {
        final SliderOff off = new SliderOff(HINT_MS);
        final boolean offered;
        final int min, max;
        Integer value;
        long now = 1_000;
        int hints, offs;

        Menu(boolean offered, int min, int max, Integer value) {
            this.offered = offered;
            this.min = min;
            this.max = max;
            this.value = value;
        }

        Menu left(int repeat) { return slide(-1, repeat); }

        Menu right(int repeat) { return slide(1, repeat); }

        /** Any other key of the menu (UP, DOWN, OK, BACK on a row that stays open). */
        Menu other() {
            off.key(false);
            return this;
        }

        Menu waitMs(long ms) {
            now += ms;
            return this;
        }

        Menu close() {
            off.cancel();
            return this;
        }

        private Menu slide(int dir, int repeat) {
            off.key(dir < 0);
            now += 300;
            if (value == null) return this;
            int nv = value + dir;
            if (nv >= min && nv <= max) {
                value = nv;
                return this;
            }
            if (repeat != 0) return this;
            SliderOffStep s = off.edge(offered, value, min, dir, 0, now);
            if (s == SliderOffStep.HINT) hints++;
            if (s == SliderOffStep.OFF) offs++;
            return this;
        }
    }

    private static Menu atMin() { return new Menu(true, 1, 10, 1); }

    private static void sequence() {
        Menu m = atMin().left(0);
        check(m.hints == 1 && m.offs == 0 && m.value == 1, "one LEFT at the minimum only shows the hint");
        m.left(0);
        check(m.hints == 1 && m.offs == 1, "LEFT again while the hint is up turns the light off");
        m.left(0);
        check(m.hints == 2 && m.offs == 1, "after the light-off the next LEFT asks again");

        m = atMin().left(0).other().left(0);
        check(m.offs == 0 && m.hints == 2, "another key between the two presses: no light-off, the hint again");
        m = atMin().left(0).right(0).left(0).left(0);
        check(m.offs == 0 && m.value == 1, "RIGHT between the presses drops the question (one step up and back)");
        m = atMin().left(0).close().left(0);
        check(m.offs == 0 && m.hints == 2, "a closed menu drops the question");

        m = atMin().left(0).waitMs(HINT_MS).left(0);
        check(m.offs == 0 && m.hints == 2, "the hint ran out: no light-off, the hint again");
        m = atMin().left(0).waitMs(HINT_MS - 400).left(0);
        check(m.offs == 1, "LEFT again just before the hint goes still turns the light off");

        m = atMin();
        for (int r = 0; r < 25; r++) m.left(r);
        check(m.offs == 0 && m.hints == 1, "holding LEFT at the minimum shows the hint once and never turns the light off");
        m = new Menu(true, 1, 10, 5);
        for (int r = 0; r < 25; r++) m.left(r);
        check(m.value == 1 && m.offs == 0 && m.hints == 0, "a held LEFT that reaches the minimum stops there, no hint");
        m.left(0);
        check(m.hints == 1 && m.offs == 0, "...the next LEFT asks");

        m = new Menu(true, 1, 10, 3).left(0).left(0);
        check(m.value == 1 && m.hints == 0 && m.offs == 0, "LEFT above the minimum only steps down");
        m = atMin().right(0);
        check(m.value == 2 && m.hints == 0 && m.offs == 0, "RIGHT at the minimum steps up");
        m = new Menu(true, 1, 10, 10).right(0).right(0);
        check(m.value == 10 && m.hints == 0 && m.offs == 0, "RIGHT at the maximum never asks and never turns the light off");
        m = new Menu(false, 1, 10, 1).left(0).left(0);
        check(m.hints == 0 && m.offs == 0, "without lamp.off LEFT at the minimum does nothing");
        m = new Menu(true, 1, 10, null).left(0).left(0);
        check(m.hints == 0 && m.offs == 0, "an unread level never asks");

        SliderOff o = new SliderOff(HINT_MS);
        check(o.edge(true, 1, 1, -1, 0, 0) == SliderOffStep.HINT, "the rule: a fresh LEFT at the minimum asks");
        check(o.edge(true, 1, 1, -1, 3, 100) == SliderOffStep.NONE, "the rule: a repeat of a held LEFT is never the answer");
        check(o.edge(true, 1, 1, 1, 0, 200) == SliderOffStep.NONE, "the rule: RIGHT is never the answer");
        check(o.edge(true, 1, 1, -1, 0, 300) == SliderOffStep.OFF, "the rule: the next fresh LEFT turns the light off");
        check(o.edge(true, 2, 1, -1, 0, 400) == SliderOffStep.NONE, "the rule: never above the minimum");
    }

    /** Boost and the slider ask the same way: the same step twice within the window, nothing else. */
    private static void pressAgain() {
        PressAgain p = new PressAgain(3_000);
        check(!p.press(1, 0), "first press arms");
        check(p.press(1, 2_999), "the same press within the window answers");
        check(!p.press(1, 3_000), "after the answer the next press arms again");
        check(!p.press(-1, 3_100), "another step arms that step instead");
        check(p.press(-1, 3_200), "...and answers it");
        p.press(1, 10_000);
        check(!p.press(1, 13_000), "the same press after the window only arms again");
        p.press(1, 20_000);
        p.cancel();
        check(!p.press(1, 20_100), "a cancelled question is not answered");
        p.press(1, 30_000);
        p.key(1);
        check(p.press(1, 30_100), "the question's own key keeps it");
        p.press(1, 40_000);
        p.key(0);
        check(!p.press(1, 40_100), "any other key drops the question");
        p.press(1, 50_000);
        p.key(-1);
        check(!p.press(1, 50_100), "the other direction's key drops the question");
        p.press(-1, 60_000);
        p.key(-1);
        check(p.press(-1, 60_100), "leaving Boost: its own key keeps the question");
    }

    /**
     * The real profiles with a light level: exactly the ones the layers give lamp.off (WANT, from test/run.sh: every model
     * with a light level, unless a layer switches the row off with the reason) ask, and only the second LEFT turns it off.
     */
    private static void profiles(String devices, String models) throws java.io.IOException {
        Set<String> want = new TreeSet<>(java.util.Arrays.asList(models.trim().split("\\s+")));
        check(!models.trim().isEmpty(), "the models that offer lamp.off are given (test/run.sh)");
        Set<String> got = new TreeSet<>();
        File[] dirs = new File(devices).listFiles();
        check(dirs != null && dirs.length > 0, "the device profiles are found in " + devices);
        if (dirs == null) return;
        for (File d : dirs) {
            File j = new File(d, "menu.json");
            if (!j.isFile()) continue;
            MenuProfile p = MenuProfile.parse(new String(Files.readAllBytes(j.toPath()), "UTF-8"));
            if (!p.has(FeatureId.LAMP)) continue;
            boolean offered = p.has(FeatureId.LAMP_OFF), w = want.contains(d.getName());
            int min = p.number(FeatureId.LAMP, "min", 1), max = p.number(FeatureId.LAMP, "max", 10);
            Menu m = new Menu(offered, min, max, min).left(0);
            check(m.offs == 0 && m.hints == (w ? 1 : 0) && m.value == min, d.getName() + ": one LEFT at the minimum "
                    + (w ? "shows the hint only" : "does nothing"));
            m.left(0);
            check(m.offs == (w ? 1 : 0), d.getName() + ": LEFT again " + (w ? "turns the light off" : "does nothing"));
            if (offered) got.add(d.getName());
        }
        check(got.equals(new TreeSet<>(want)), "lamp.off is offered by exactly " + want + ", got " + got);
    }

    public static void main(String[] a) throws java.io.IOException {
        sequence();
        pressAgain();
        profiles(a.length > 0 ? a[0] + "/devices" : "devices", a.length > 1 ? a[1] : "");
        System.out.println("SliderOffTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
