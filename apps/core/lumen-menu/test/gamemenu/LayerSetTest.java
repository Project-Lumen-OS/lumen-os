// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.ui;

import java.util.ArrayList;
import java.util.List;

/** LayerSet: the cover rule of the layers over the game (X50 crosshair and frame counter vs the eye-guard cover). */
public final class LayerSetTest {
    private static int pass, fail;

    private static void ok(boolean c, String what) {
        if (c) pass++; else { fail++; System.out.println("FAIL " + what); }
    }

    static final class Wm implements LayerSet.Windows<String, String> {
        final List<String> on = new ArrayList<>();
        final List<String> log = new ArrayList<>();
        boolean failAdd;

        @Override public void add(String v, String lp) throws Exception {
            log.add("add " + v + " " + lp);
            if (failAdd) throw new IllegalStateException("BadTokenException");
            on.add(v);
        }

        @Override public void remove(String v) { log.add("remove " + v); on.remove(v); }
    }

    public static void main(String[] a) {
        Boolean[] cover = {false};
        boolean[] throwing = {false};
        Wm wm = new Wm();
        LayerSet<String, String> s = new LayerSet<>(wm, () -> {
            if (throwing[0]) throw new IllegalStateException("no tasks");
            return cover[0];
        });
        ok(LayerSet.onScreen(false) && !LayerSet.onScreen(true) && !LayerSet.onScreen(null), "rule: only a known-down cover shows layers");
        ok(s.add("aim", "lp1") && wm.on.contains("aim") && s.isShown("aim"), "cover down: the layer goes on screen with its params");
        ok(wm.log.contains("add aim lp1"), "the host's window params are used");
        cover[0] = true;
        s.sync();
        ok(!wm.on.contains("aim") && !s.isShown("aim"), "cover up: the layer leaves the screen at the front task change");
        int n = wm.log.size();
        s.sync();
        ok(wm.log.size() == n, "cover still up: nothing sent again");
        ok(s.add("fps", "lp2") && !wm.on.contains("fps"), "a layer added under the cover is held off the screen");
        cover[0] = false;
        s.sync();
        ok(wm.on.contains("aim") && wm.on.contains("fps"), "cover gone: both layers back on screen");
        n = wm.log.size();
        s.sync();
        ok(wm.log.size() == n, "cover still down: nothing added twice");
        cover[0] = null;
        s.sync();
        ok(wm.on.isEmpty(), "front task unknown: fail closed, every layer off");
        cover[0] = false;
        s.sync();
        throwing[0] = true;
        s.sync();
        ok(wm.on.isEmpty(), "cover check throws: fail closed");
        throwing[0] = false;
        s.sync();
        s.remove("fps");
        ok(!wm.on.contains("fps") && wm.on.contains("aim") && s.count() == 1, "removed layer off the screen, the other stays");
        cover[0] = true;
        s.sync();
        cover[0] = false;
        s.sync();
        ok(!wm.on.contains("fps") && wm.on.contains("aim"), "a removed layer never comes back");
        s.remove("aim");
        cover[0] = true;
        s.sync();
        ok(wm.on.isEmpty() && s.count() == 0, "nothing left");
        cover[0] = false;
        wm.failAdd = true;
        s.add("aim", "lp1");
        ok(!s.isShown("aim"), "an add the window manager refuses is not counted as shown");
        wm.failAdd = false;
        s.sync();
        ok(s.isShown("aim") && wm.on.contains("aim"), "the next sync shows it");
        System.out.println("LayerSetTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
