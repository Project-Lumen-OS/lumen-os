// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * The stock focus chart and edge frame as data (FocusChart, EdgeFrame), pinned to the measurements of the stock
 * drawables (cp01_focus_bk, bg_manuel_focus_short, img_logo_focus3, keystone_zoom_bk, keystone_zoom_move_bk; byte-equal
 * in the Z9X, HB, X50 and Titan builds), and every model's chart style from its profile.
 *   java org.lumen.menu.ChartTest MENU_VIEW_ROOT
 */
public final class ChartTest {
    private static int fails, n;

    public static void main(String[] a) throws Exception {
        // full chart: corner rings and the line (pixel runs of cp01_focus_bk along the corner rays)
        float[][] f = FocusChart.FULL_RINGS;
        eq("full rings", f.length, 4);
        near("full ring 1", f[0][0], 53f); near("full ring 1 out", f[0][1], 70.5f);
        near("full bars band", f[1][1], 108.5f); near("full bar width", f[1][3], 10.5f); near("full bar step", f[1][5], 15f);
        near("spokes from", f[3][0], 123f); near("spokes to", f[3][1], 230f); near("spoke line width", f[3][3], 3.5f);
        near("spoke first", f[3][4], 1f); near("spoke step", f[3][5], 4f);
        eq("spokes in a quarter (1 .. 89 every 4)", FocusChart.barAngles(f[3]).length, 23);
        eq("bars in a quarter (0 .. 90 every 15)", FocusChart.barAngles(f[1]).length, 7);
        float[] l = FocusChart.FULL_LINE;
        ok("line rows 848 .. 855, x 56 .. 1863", l[0] == 56f && l[1] == 848f && l[2] == 1864f && l[3] == 856f);
        eq("full page white", FocusChart.page(FocusChart.FULL), 0xFFFFFFFF);
        // short chart (support_af_type 2)
        float[][] s = FocusChart.SHORT_RINGS;
        near("short disc", s[0][1], 85.5f); near("short bars", s[2][4], 1.25f); near("short bar step", s[2][5], 12.5f);
        eq("short dashes in a quarter (0 .. 90, 14)", FocusChart.barAngles(s[6]).length, 14);
        near("short outer ring", s[7][1], 211.5f);
        eq("short page clear", FocusChart.page(FocusChart.SHORT), 0);
        ok("short chart has no line", FocusChart.line(FocusChart.SHORT) == null);
        // centre dial (img_logo_focus3, lefta_new, righta_new)
        eq("96 ticks", Math.round(360f / FocusChart.TICK_STEP), 96);
        eq("12 grey parts", Math.round(360f / FocusChart.GREY_STEP), 12);
        eq("30 inner dashes", Math.round(360f / FocusChart.INNER_STEP), 30);
        eq("4 outer arcs", Math.round(360f / FocusChart.OUTER_STEP), 4);
        near("rings turn 10 degrees per step", FocusChart.TURN_STEP, 10f);
        // styles
        ok("OFF opens without the chart", !FocusChart.shownAtOpen(FocusChart.OFF) && FocusChart.shownAtOpen(FocusChart.FULL)
                && FocusChart.shownAtOpen(FocusChart.SHORT));
        ok("OK on OFF draws the full chart", FocusChart.FULL.equals(FocusChart.drawn(FocusChart.OFF)));
        ok("no profile = full", FocusChart.FULL.equals(FocusChart.style(null)));
        // edge frame: keystone_zoom_bk 2 black (the page), 2 grey #858585, 4 white; keystone_zoom_move_bk 4 white
        float[][] fb = EdgeFrame.bands(EdgeFrame.FRAME);
        ok("frame bands 2..4 grey, 4..8 white", fb.length == 2 && fb[0][0] == 2 && fb[0][1] == 4 && fb[1][0] == 4 && fb[1][1] == 8
                && EdgeFrame.colours(EdgeFrame.FRAME)[0] == 0xFF858585 && EdgeFrame.colours(EdgeFrame.FRAME)[1] == 0xFFFFFFFF);
        float[][] gb = EdgeFrame.bands(EdgeFrame.GUIDES);
        ok("guides edge 0..4 white", gb.length == 1 && gb[0][0] == 0 && gb[0][1] == 4);
        eq("page black (color_33)", EdgeFrame.PAGE, 0xFF000000);
        eq("frame has no guides", EdgeFrame.guides(EdgeFrame.FRAME).length, 0);
        eq("guide lines", EdgeFrame.guides(EdgeFrame.GUIDES).length, 12);
        // dashes against the stock pixel runs
        List<float[]> d = EdgeFrame.dashes(9, 1911, 9);
        ok("y 135: first dash 9..14, last 1905..1910", d.get(0)[0] == 9 && d.get(0)[1] == 15 && d.get(d.size() - 1)[0] == 1905
                && d.get(d.size() - 1)[1] == 1911);
        eq("y 135: 159 dashes", d.size(), 159);
        d = EdgeFrame.dashes(9, 755, 9);
        ok("y 270 left ends in the cut dash 753..754", d.get(d.size() - 1)[0] == 753 && d.get(d.size() - 1)[1] == 755);
        d = EdgeFrame.dashes(1165, 1911, 9);
        ok("y 270 right starts with the cut dash 1165..1166, then 1173", d.get(0)[0] == 1165 && d.get(0)[1] == 1167 && d.get(1)[0] == 1173);
        d = EdgeFrame.dashes(4, 617, 11);
        ok("centre line left: 4, then 11..16, last 611..616", d.get(0)[0] == 4 && d.get(0)[1] == 5 && d.get(1)[0] == 11
                && d.get(d.size() - 1)[0] == 611 && d.get(d.size() - 1)[1] == 617);
        d = EdgeFrame.dashes(879, 1072, 10);
        ok("centre column bottom: 879, then 886, last 1066..1071", d.get(0)[0] == 879 && d.get(0)[1] == 880 && d.get(1)[0] == 886
                && d.get(d.size() - 1)[0] == 1066 && d.get(d.size() - 1)[1] == 1072);
        // every model's style: the MIRA line short (feature.xml support_af_type 2), the Play 6 off, every other full
        File root = new File(a[0], "devices");
        File[] devs = root.listFiles();
        int models = 0;
        for (File dev : devs == null ? new File[0] : devs) {
            File j = new File(dev, "menu.json");
            if (!j.isFile()) continue;
            MenuProfile p = MenuProfile.parse(new String(Files.readAllBytes(j.toPath()), StandardCharsets.UTF_8));
            if (!p.ok() || !p.has(FeatureId.FOCUS_MANUAL)) continue;
            models++;
            String raw = p.text(FeatureId.FOCUS_MANUAL, "chart");
            ok(dev.getName() + ": chart word known", raw == null || raw.equals(FocusChart.SHORT) || raw.equals(FocusChart.OFF)
                    || raw.equals(FocusChart.FULL));
            String want = dev.getName().matches("g010[49]|g0110") ? FocusChart.SHORT
                    : dev.getName().equals("dragon_healthy") ? FocusChart.OFF : FocusChart.FULL;
            ok(dev.getName() + ": chart " + want, want.equals(FocusChart.style(p)));
            ok(dev.getName() + ": geometry screens show the focus chart it draws", FocusChart.drawn(want).equals(FocusChart.geometry(p))
                    && !FocusChart.OFF.equals(FocusChart.geometry(p)));
        }
        ok("profiles with manual focus read (" + models + ")", models >= 20);
        // the geometry screens' test chart: the model's focus chart, the full one without a profile
        ok("geometry chart without a profile: full", FocusChart.FULL.equals(FocusChart.geometry(null)));
        // OkPress: OK on the geometry screens, a short press (the test chart) or a hold (the screen's second key)
        OkPress k = new OkPress();
        eq("short: nothing at its DOWN", k.feed(0, 0, 0, 0, false), OkPress.NONE);
        eq("short: SHORT at its UP", k.feed(1, 0, 0, 120, false), OkPress.SHORT);
        eq("a lone UP (its DOWN went to another surface): nothing", k.feed(1, 0, 3000, 3010, false), OkPress.NONE);
        k.feed(0, 0, 1000, 1000, false);
        eq("repeat before the hold time: nothing", k.feed(0, 1, 1000, 1500, false), OkPress.NONE);
        eq("hold: HOLD at the hold time", k.feed(0, 2, 1000, 1000 + OkPress.HOLD_MS, false), OkPress.HOLD);
        eq("hold: only once", k.feed(0, 3, 1000, 1050 + OkPress.HOLD_MS, false), OkPress.NONE);
        eq("hold: its release is nothing", k.feed(1, 0, 1000, 1100 + OkPress.HOLD_MS, false), OkPress.NONE);
        k.feed(0, 0, 4000, 4000, false);
        eq("a cancelled UP: nothing", k.feed(1, 0, 4000, 4050, true), OkPress.NONE);
        k.feed(0, 0, 5000, 5000, false);
        eq("after a cancel a new press is short again", k.feed(1, 0, 5000, 5100, false), OkPress.SHORT);
        ok("OK keys: centre, enter, numpad enter; not the arrows, MENU, BACK", OkPress.isOk(23) && OkPress.isOk(66)
                && OkPress.isOk(160) && !OkPress.isOk(21) && !OkPress.isOk(82) && !OkPress.isOk(4));
        eq("hold time = the keystone reset hold", OkPress.HOLD_MS, 800);
        System.out.println("ChartTest: " + (n - fails) + "/" + n + " passed");
        if (fails > 0) System.exit(1);
    }

    private static void ok(String what, boolean good) {
        n++;
        if (!good) { fails++; System.out.println("FAIL " + what); }
    }

    private static void eq(String what, long got, long want) { ok(what + " (" + got + " vs " + want + ")", got == want); }

    private static void near(String what, float got, float want) { ok(what + " (" + got + " vs " + want + ")", Math.abs(got - want) < 0.01f); }
}
