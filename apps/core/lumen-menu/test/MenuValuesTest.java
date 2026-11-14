// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu.utils;

import org.lumen.menu.FeatureId;
import org.lumen.menu.MenuState;

public final class MenuValuesTest {
    private static int passed, failed;

    private static void ok(String what, boolean cond) {
        if (cond) passed++;
        else { failed++; System.out.println("FAIL " + what); }
    }

    private static boolean throwsOnOff(String v) {
        try { MenuValues.onOff(v); return false; } catch (IllegalArgumentException e) { return true; }
    }

    public static void main(String[] a) {
        ok("on is true", MenuValues.onOff("on"));
        ok("off is false", !MenuValues.onOff("off"));
        ok("other tokens are errors", throwsOnOff("On") && throwsOnOff("") && throwsOnOff(null));
        String[] t = {"auto", "full", "limited"};
        ok("index of a token", MenuValues.indexOf(t, "full") == 1 && MenuValues.indexOf(t, "auto") == 0);
        ok("unknown token is -1", MenuValues.indexOf(t, "x") == -1 && MenuValues.indexOf(t, null) == -1);
        MenuState.Builder b = MenuState.builder();
        MenuValues.read(b, x -> x.put(FeatureId.EYE, "1"));
        MenuValues.read(b, x -> { throw new Exception("vendor"); });
        MenuValues.read(b, x -> { x.put(FeatureId.LAMP, "2"); throw new IllegalStateException("late"); });
        MenuState s = b.build();
        ok("a read puts its value", "1".equals(s.get(FeatureId.EYE)));
        ok("a failed read stops there, earlier puts stay", "2".equals(s.get(FeatureId.LAMP)) && s.asMap().size() == 2);
        MenuState.Builder c = MenuState.builder().put(FeatureId.ZOOM, "9");
        MenuValues.copyValues(s, c);
        MenuState cs = c.build();
        ok("copy keeps both sides", cs.asMap().size() == 3 && "9".equals(cs.get(FeatureId.ZOOM)) && "1".equals(cs.get(FeatureId.EYE)));
        System.out.println("MenuValuesTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
