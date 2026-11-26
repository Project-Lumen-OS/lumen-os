// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.system;

import android.content.Context;
import android.provider.Settings;

/** ColourVision against a map-backed Settings.Secure: the stock tokens, modes and the on/off pair. */
public final class ColourVisionTest {
    private static int failed;

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "ok   " : "FAIL ") + what);
        if (!ok) failed++;
    }

    private static Integer v(String key) { return Settings.Secure.VALUES.get(key); }

    public static void main(String[] a) {
        Context c = new Context();
        check("off".equals(ColourVision.get(c)), "nothing stored: off");
        String[] tokens = {"gray", "red_green", "green_red", "blue_yellow"};
        int[] modes = {0, 11, 12, 13};
        check(java.util.Arrays.equals(ColourVision.TOKENS, tokens), "the stock filter order: gray, red-green, green-red, blue-yellow");
        for (int i = 0; i < tokens.length; i++) {
            check(ColourVision.set(c, tokens[i]), tokens[i] + ": set");
            check(Integer.valueOf(modes[i]).equals(v(ColourVision.MODE)), tokens[i] + ": daltonizer mode " + modes[i] + ", got " + v(ColourVision.MODE));
            check(Integer.valueOf(1).equals(v(ColourVision.ENABLED)), tokens[i] + ": correction on");
            check(tokens[i].equals(ColourVision.get(c)), tokens[i] + ": read back");
        }
        check(ColourVision.set(c, "off"), "off: set");
        check(Integer.valueOf(0).equals(v(ColourVision.ENABLED)), "off: correction off");
        check(Integer.valueOf(13).equals(v(ColourVision.MODE)), "off: the last filter is kept");
        check("off".equals(ColourVision.get(c)), "off: read back");
        check(!ColourVision.set(c, "sepia"), "an unknown token is refused");
        check(Integer.valueOf(0).equals(v(ColourVision.ENABLED)) && Integer.valueOf(13).equals(v(ColourVision.MODE)), "a refused token writes nothing");
        Settings.Secure.VALUES.put(ColourVision.ENABLED, 1);
        Settings.Secure.VALUES.put(ColourVision.MODE, 7);
        check("off".equals(ColourVision.get(c)), "a mode outside the stock list reads as off");
        Settings.Secure.VALUES.remove(ColourVision.MODE);
        check("green_red".equals(ColourVision.get(c)), "on without a mode: Android's default deuteranomaly (12) = green-red");
        check("accessibility_display_daltonizer_enabled".equals(ColourVision.ENABLED) && "accessibility_display_daltonizer".equals(ColourVision.MODE),
                "the Android setting names the stock VisionManager writes");
        Settings.Secure.FAIL = true;
        check("off".equals(ColourVision.get(c)), "a refusing provider reads as off");
        check(!ColourVision.set(c, "gray"), "a refusing provider: set reports failure");
        Settings.Secure.FAIL = false;
        if (failed > 0) System.exit(1);
    }
}
