// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.ui;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tile labels in every language with the real Inter font (X50 live audit 2026-11-24: \"Install Lumen OS fo...\",
 * \"HDMI 1 (ARC) No...\"): the installer's name under its dock tile (248 + 80 px at 24 px), an input chip's port and
 * state on one line (292 px), an input tile's port and state lines (276 px) fit with LabelFit, nothing cut. The font
 * is measured at its default weight, plus WEIGHT for the semibold the tiles draw. Also: the old cut (scale 1) would
 * have cut the installer's name, so the test sees the case it guards.
 *   java -Dgsi=GSI org.lumen.home.ui.LabelFitTest
 */
public final class LabelFitTest {
    private static final float WEIGHT = 1.06f;
    private static int fails, oks;
    private static Font inter;
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    private static void ok(String what, boolean v) {
        if (v) oks++;
        else { fails++; System.out.println("FAIL " + what); }
    }

    private static float width(String s, float px) {
        return (float) inter.deriveFont(px).getStringBounds(s, FRC).getWidth() * WEIGHT;
    }

    /** The shared string module: a key of res/values that reads @string/lumen_x is that text in every locale. */
    static File shared;

    /** {locale dir: text} of one string of one app (values = English), an alias read as the APK resolves it. */
    private static List<String[]> texts(File res, String name) throws Exception {
        List<String[]> own = own(res, name);
        for (String[] t : own) {
            if ("values".equals(t[0]) && t[1].startsWith("@string/")) return own(shared, t[1].substring(8));
        }
        return own;
    }

    private static List<String[]> own(File res, String name) throws Exception {
        List<String[]> out = new ArrayList<>();
        Pattern p = Pattern.compile("<string name=\"" + name + "\"[^>]*>([^<]*)</string>");
        File[] dirs = res.listFiles();
        if (dirs == null) return out;
        for (File d : dirs) {
            if (!d.getName().startsWith("values")) continue;
            File[] fs = d.listFiles((x, n) -> n.endsWith(".xml"));
            if (fs == null) continue;
            for (File f : fs) {
                Matcher m = p.matcher(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
                if (m.find()) out.add(new String[]{d.getName(), m.group(1).replace("\\'", "'")});
            }
        }
        return out;
    }

    public static void main(String[] a) throws Exception {
        File g = new File(System.getProperty("gsi", "."));
        // the one Inter font of the tree (Home takes it by a recipe file line)
        inter = Font.createFont(Font.TRUETYPE_FONT, new File(g, "apps/core/lumen-menu/assets/fonts/InterVariable.ttf"));
        shared = new File(g, "apps/core/lumen-common-strings/res");
        File home = new File(g, "apps/LumenHome/res"), dsu = new File(g, "apps/LumenDsuInstall/res");
        float dock = 248 + 80, chip = 392 - (24 + 32 + 16) - 28, input = 392 - (28 + 40 + 20) - 28;
        List<String[]> names = texts(dsu, "app_name");
        ok("the installer has its name in English and Russian", names.size() >= 2);
        boolean cutBefore = false;
        for (String[] n : names) {
            float w = width(n[1], 24);
            cutBefore |= w > dock;
            ok("dock label " + n[0] + " \"" + n[1] + "\" fits (" + Math.round(w) + " of " + dock + ")", LabelFit.fits(w, dock));
        }
        ok("the old one-size line cut the installer's name (the case this guards)", cutBefore);
        String[] ports = {"HDMI 1 (ARC)", "HDMI 2", "USB"};
        for (String state : new String[]{"input_no_signal", "input_not_connected"}) {
            for (String[] t : texts(home, state)) {
                for (String port : ports) {
                    float w = width(port, 24) + 16 + width(t[1], 22);
                    ok("chip " + t[0] + " \"" + port + " " + t[1] + "\" fits (" + Math.round(w) + " of " + chip + ")",
                            LabelFit.fits(w, chip));
                    ok("input tile " + t[0] + " state \"" + t[1] + "\" fits", LabelFit.fits(width(t[1], 20), input));
                }
            }
        }
        ok("a fitting label keeps its size", LabelFit.scale(100, 200) == 1f);
        ok("a label never gets smaller than MIN_SCALE", LabelFit.scale(1000, 100) == LabelFit.MIN_SCALE);
        ok("a far too long label is cut", !LabelFit.fits(1000, 100));
        System.out.println("LabelFitTest: " + oks + " ok, " + fails + " failed");
        if (fails > 0) System.exit(1);
    }
}
