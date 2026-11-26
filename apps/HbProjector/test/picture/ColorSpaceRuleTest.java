// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

public final class ColorSpaceRuleTest {
    private static int bad;

    private static void ok(String what, boolean c) {
        System.out.println((c ? "ok   " : "FAIL ") + what);
        if (!c) bad++;
    }

    public static void main(String[] a) {
        // stock PictureFactory: no HDR condition on picture_gamut_athenaultra (the tester's HDMI 2 forced to HDR10)
        ok("shown in Standard (0)", ColorSpaceRule.shown(0, 4));
        ok("shown in Movie (1) with BT.2020 read", ColorSpaceRule.shown(1, 1));
        ok("shown in colour accuracy (29)", ColorSpaceRule.shown(29, 3));
        for (int m = 10; m <= 17; m++) ok("hidden in AI mode " + m, !ColorSpaceRule.shown(m, 4));
        ok("shown in mode 9 (below AI)", ColorSpaceRule.shown(9, 4));
        ok("shown in mode 18 (above AI)", ColorSpaceRule.shown(18, 4));
        ok("hidden without a read", !ColorSpaceRule.shown(0, null));
        ok("shown without a mode read", ColorSpaceRule.shown(null, 4));
        // the HDR note: only when HDR plays and the read-back disagrees
        ok("HDR, read-back differs: note", ColorSpaceRule.hdrRefused(true, 1, 4));
        ok("HDR, read-back equal: no note", !ColorSpaceRule.hdrRefused(true, 1, 1));
        ok("SDR, read-back differs: no note", !ColorSpaceRule.hdrRefused(false, 1, 4));
        ok("HDR, no read-back: no note", !ColorSpaceRule.hdrRefused(true, 1, null));
        if (bad > 0) { System.out.println("FAIL ColorSpaceRuleTest: " + bad); System.exit(1); }
        System.out.println("ok   ColorSpaceRuleTest");
    }
}
