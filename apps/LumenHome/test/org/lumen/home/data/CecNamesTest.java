// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.data;


/**
 * The friendly names and marks of CEC devices on the Inputs cards (CecNames, switch cec_names): placeholders
 * never shown, known devices by name or by an unambiguous vendor id, their own model names kept, the
 * console accents readable on the card.
 */
final class CecNamesTest {
    private static final int SURFACE2 = 0xFF26221E; // res/values/colors_lumen.xml lumen_surface2, the card

    static void run() {
        int soft = CecNames.softAccent(0xFF5A9BEA);
        T.ok(contrast(soft, SURFACE2) >= 4.0, "a softened accent stays readable on the card");
        T.ok(CecNames.mix(0xFF000000, 0xFFFFFFFF, 0f) == 0xFF000000, "mix 0 keeps the first colour");
        T.ok(CecNames.mix(0xFF000000, 0xFFFFFFFF, 1f) == 0xFFFFFFFF, "mix 1 is the second colour");
        PortChipTest.run();
    }

    private static double lum(int c) {
        double[] v = {((c >> 16) & 0xFF) / 255.0, ((c >> 8) & 0xFF) / 255.0, (c & 0xFF) / 255.0};
        for (int i = 0; i < 3; i++) v[i] = v[i] <= 0.03928 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
    }

    private static double contrast(int a, int b) {
        double x = lum(a) + 0.05, y = lum(b) + 0.05;
        return Math.max(x, y) / Math.min(x, y);
    }
}
