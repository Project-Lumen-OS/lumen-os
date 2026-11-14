// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

/**
 * Host test of the shared gear press (GearPress) and the profile's keys.eye_gear_short, run by test/run.sh. The cases
 * are the ones the six old copies (three KeyReceiver, three EyeGuard) handled: short press, held gear once, a canceled
 * UP, repeats and UPs of a press that began elsewhere, a new press after a held one.
 */
public final class GearPressTest {
    private static int pass, fail;

    private static void check(boolean ok, String what) {
        if (ok) pass++;
        else { fail++; System.out.println("FAIL " + what); }
    }

    private static String profile(String keys) {
        return "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {}, \"keys\": " + keys
                + ", \"game_menu\": {\"enabled\": false}}";
    }

    public static void main(String[] a) {
        GearPress g = new GearPress();
        // short press: DOWN, UP of the same press
        check(!g.down(0, 100), "a first DOWN is no long press");
        check(g.up(100, false), "UP of the armed press without a repeat is the short press");
        // held gear: the first repeat fires once, the UP is not short
        check(!g.down(0, 200), "arm");
        check(g.down(1, 200), "the first repeat of the armed press is the long press");
        check(!g.down(2, 200), "the second repeat does not fire again");
        check(!g.down(3, 200), "nor the third");
        check(!g.up(200, false), "the UP after a long press is not a short press");
        // canceled UP
        check(!g.down(0, 300), "arm");
        check(!g.up(300, true), "a canceled UP is not a short press");
        check(!g.up(300, false), "an UP after the disarm is nothing");
        // a press that began elsewhere (before the mask came up): only eaten
        check(!g.down(5, 400), "a repeat without an armed press is nothing");
        check(!g.up(400, false), "an UP without an armed press is nothing");
        check(!g.down(0, 500), "arm");
        check(!g.down(1, 499), "a repeat of another press is nothing");
        check(!g.up(499, false), "an UP of another press is nothing");
        check(!g.up(500, false), "that UP disarmed the press: its own UP is nothing now");
        // a new press after a held one fires its own long press
        check(!g.down(0, 600), "arm");
        check(g.down(1, 600), "long press");
        check(!g.down(0, 700), "a new press re-arms");
        check(g.down(1, 700), "and fires its own long press");
        check(!g.up(700, false), "not short");
        check(!g.down(0, 800) && g.up(800, false), "a short press after all that");
        // reset (the mask came up): the press that began before it is neither long nor short
        check(!g.down(0, 900), "arm");
        g.reset();
        check(!g.down(1, 900), "no long press after a reset");
        check(!g.up(900, false), "no short press after a reset");
        check(!g.down(0, 950), "arm");
        check(g.down(1, 950), "a held gear that began after the reset is a long press");
        g.reset();
        check(!g.down(0, 960) && g.up(960, false), "a reset leaves no long press behind: the next press is short");

        // keys.eye_gear_short: optional, restore (default) or restore_off, anything else is a problem
        MenuProfile none = MenuProfile.parse(profile("{\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}"));
        check(none.ok() && none.eyeGearShort() == null && !none.eyeGearShortOff(), "no eye_gear_short: restore only");
        MenuProfile restore = MenuProfile.parse(profile(
                "{\"gear_short\": \"quick_menu\", \"gear_long\": \"none\", \"eye_gear_short\": \"restore\"}"));
        check(restore.ok() && "restore".equals(restore.eyeGearShort()) && !restore.eyeGearShortOff(), "restore");
        MenuProfile off = MenuProfile.parse(profile(
                "{\"gear_short\": \"quick_menu\", \"gear_long\": \"none\", \"eye_gear_short\": \"restore_off\"}"));
        check(off.ok() && off.eyeGearShortOff(), "restore_off turns eye protection off");
        MenuProfile bad = MenuProfile.parse(profile(
                "{\"gear_short\": \"quick_menu\", \"gear_long\": \"none\", \"eye_gear_short\": \"off\"}"));
        check(!bad.ok() && !bad.eyeGearShortOff(), "an unknown eye_gear_short is a problem and keeps eye protection on");
        MenuProfile broken = MenuProfile.parse(profile(
                "{\"gear_short\": \"quick_menu\", \"gear_long\": \"nothing\", \"eye_gear_short\": \"restore_off\"}"));
        check(!broken.ok() && !broken.eyeGearShortOff(), "a broken profile keeps eye protection on");

        System.out.println("GearPressTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
