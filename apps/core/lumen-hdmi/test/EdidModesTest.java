// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hdmi;

/**
 * Host test of EdidModes (apps/core/lumen-hdmi/test/run.sh): the stock HDMI mode rows and their vendor values, as
 * newsettings SignalEnum and gmuiapi GmTvManager give them, held to the live X50 run of 2026-11-15
 * (devices/g0111/stock analysis).
 */
public final class EdidModesTest {
    private static int checks, failures;

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL " + what);
        }
    }

    private static boolean refused(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    public static void main(String[] args) {
        // vendor values: libxgimi EN_EDID_VERSION = gmpf GmTvManager EDID_VERSION_14 0, _20 1, _AUTO 2 and 2.1 3
        check(EdidModes.RAW_14 == 0 && EdidModes.RAW_20 == 1 && EdidModes.RAW_GAME == 2 && EdidModes.RAW_21 == 3, "raw values");
        check(EdidModes.COMPATIBLE == 0 && EdidModes.ENHANCED == 1, "rows in the stock order");

        // every model Lumen runs on: edid_2_version_val=1, SignalEnum 1 / 2, gmuiapi sends 0 / 1
        EdidModes m = EdidModes.of(EdidModes.FEATURE_HDMI_20);
        check(m.wire(EdidModes.COMPATIBLE) == EdidModes.RAW_14, "Compatible writes 0 (live: edid14_2.bin)");
        check(m.wire(EdidModes.ENHANCED) == EdidModes.RAW_20, "Enhanced writes 1 (live: edid20_non_hrr_2.bin)");
        check(m.row(1) == EdidModes.ENHANCED, "1 shows Enhanced");
        for (int r : new int[] {0, 2, 3, 4, -1, 7}) check(m.row(r) == EdidModes.COMPATIBLE, r + " shows Compatible (getHdmiVersion)");
        check(m.writable(0) && m.writable(1), "0 and 1 may be written");
        for (int r : new int[] {2, 3, -1, 4}) check(!m.writable(r), r + " is never written");
        int[] w = m.wires();
        check(w.length == 2 && w[0] == 0 && w[1] == 1, "wires Compatible first");
        w[0] = 9;
        check(m.wires()[0] == 0, "wires is a copy");

        // the write rule: only when the row's own value is not stored (a beta 7 value 2 is rewritten)
        check(!m.needsWrite(1, EdidModes.ENHANCED) && !m.needsWrite(0, EdidModes.COMPATIBLE), "same value: no write");
        check(m.needsWrite(0, EdidModes.ENHANCED) && m.needsWrite(1, EdidModes.COMPATIBLE), "other row: write");
        check(m.needsWrite(2, EdidModes.COMPATIBLE), "beta 7 Enhanced (2) shown Compatible still gets 0");
        check(m.needsWrite(2, EdidModes.ENHANCED) && m.needsWrite(3, EdidModes.ENHANCED), "foreign values get Enhanced");

        // one step of the old X50 menu row
        check(m.other(1) == 0 && m.other(0) == 1 && m.other(2) == 1 && m.other(-1) == 1, "other row");

        // a model whose Enhanced is HDMI 2.1 (edid_2_version_val=3): SignalEnum 4, the vendor 3; none in Lumen yet
        EdidModes m21 = EdidModes.of(EdidModes.FEATURE_HDMI_21);
        check(m21.wire(EdidModes.COMPATIBLE) == 0 && m21.wire(EdidModes.ENHANCED) == EdidModes.RAW_21, "2.1 model: 0 / 3");
        check(m21.row(3) == EdidModes.ENHANCED && m21.row(1) == EdidModes.COMPATIBLE, "2.1 model: only 3 shows Enhanced");
        check(!m21.writable(1) && m21.writable(3), "2.1 model never writes 1");

        check(refused(() -> EdidModes.of(2)) && refused(() -> EdidModes.of(0)), "unknown edid_2_version_val refused");
        check(refused(() -> m.wire(2)) && refused(() -> m.wire(-1)), "unknown row refused");

        System.out.println("EdidModesTest: " + checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
