// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The signal format the picture page names (stock PictureUtils.getCurrentHdrType over the vendor getHdrType) and the
 * content kind the support lists read from the same value: java org.lumen.menu.HdrFormatTest &lt;gsi root&gt;.
 */
public final class HdrFormatTest {
    private static int pass, fail;

    private static void eq(Object want, Object got, String what) {
        boolean ok = want == null ? got == null : want.equals(got);
        if (ok) pass++;
        else { fail++; System.out.println("FAIL " + what + ": want " + want + " got " + got); }
    }

    public static void main(String[] a) throws Exception {
        Path root = Paths.get(a.length > 0 ? a[0] : ".");
        // every vendor value of every model (Z9X / Play 6 IGmpf 620, HB / X50 family GmTvManager 78)
        Object[][] table = {
                {null, null, null}, {-1, null, null}, {0, HdrFormat.SDR, Signal.SDR}, {1, HdrFormat.HDR10, Signal.HDR10},
                {2, HdrFormat.DOLBY_VISION, Signal.DOLBY}, {3, HdrFormat.HDR, Signal.HDR}, {4, HdrFormat.HLG, Signal.HDR},
                {5, HdrFormat.HDR10_PLUS, Signal.HDR}, {6, HdrFormat.HDR_VIVID, Signal.HDR_VIVID}, {7, HdrFormat.HDR, Signal.HDR},
        };
        for (Object[] r : table) {
            Integer t = (Integer) r[0];
            eq(r[1], HdrFormat.ofHdrType(t), "format of " + t);
            eq(r[2], Signal.ofHdrType(t), "kind of " + t);
        }
        // HDR10+ is never named HDR10 (4PDA report: HDR10+ films shown as HDR10)
        eq(false, HdrFormat.HDR10_PLUS.label.equals(HdrFormat.HDR10.label), "HDR10+ has its own label");
        String res = new String(Files.readAllBytes(root.resolve("apps/core/lumen-menu/res/values/lumen_strings_menu.xml")), StandardCharsets.UTF_8);
        String[] shown = {"SDR", "HDR", "HDR10", "HDR10+", "HLG", "Dolby Vision", "HDR Vivid"};
        for (HdrFormat f : HdrFormat.values()) {
            eq(true, res.contains("<string name=\"" + f.label + "\" translatable=\"false\">" + shown[f.ordinal()] + "</string>"),
                    "label " + f.label + " = " + shown[f.ordinal()]);
        }
        // the state carries the format; a live update without one keeps the shown one
        MenuState st = MenuState.builder().format(HdrFormat.DOLBY_VISION).build();
        eq(HdrFormat.DOLBY_VISION, st.format(), "format carried");
        eq(HdrFormat.DOLBY_VISION, st.with(MenuState.builder().build()).format(), "merge keeps the format");
        eq(HdrFormat.HLG, st.with(MenuState.builder().format(HdrFormat.HLG).build()).format(), "merge takes a new format");
        eq(null, MenuState.builder().build().format(), "not read = null");
        eq(true, st.toString().contains("format DOLBY_VISION"), "toString names it");
        System.out.println("HdrFormatTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
