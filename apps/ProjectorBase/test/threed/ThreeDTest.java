// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.display;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lumen.menu.FeatureId;
import org.lumen.menu.MenuProfile;
import org.lumen.menu.MenuRequest;
import org.lumen.menu.MenuSpec;
import org.lumen.menu.ThreeDMode;
import org.lumen.projector.hal.Mode3D;

/** Quick 3D: stock calls, read-back, profiles, labels, the remote's request (sh test/threed/run.sh). */
public final class ThreeDTest {
    private static int pass, fail;

    private static void check(boolean ok, String what) {
        if (ok) pass++;
        else {
            fail++;
            System.out.println("FAIL " + what);
        }
    }

    private static String read(Path p) throws Exception { return new String(Files.readAllBytes(p), StandardCharsets.UTF_8); }

    /** IGmpf code of a HalCodes constant (the Z9X binder table). */
    private static int code(String src, String name) {
        Matcher m = Pattern.compile("static final int " + name + " = (\\d+);").matcher(src);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** "190(2)": the stock call ThreeD sends for {@code m}, "-" for none (codes from HalCodes.java). */
    private static String call(ThreeDMode m, int enable, int to2d) {
        Mode3D e = ThreeDCall.enable3D(m);
        Boolean tb = ThreeDCall.to2dTopBottom(m);
        if (e != null && tb != null) return "both";
        if (e != null) return enable + "(" + e.wire + ")";
        if (tb != null) return to2d + "(" + (tb ? Mode3D.TOP_BOTTOM : Mode3D.SIDE_BY_SIDE).wire + ")";
        return "-";
    }

    public static void main(String[] a) throws Exception {
        Path g = Paths.get(a[0]);
        String codes = read(g.resolve("apps/ProjectorBase/src/org/lumen/projector/hal/HalCodes.java"));
        int en = code(codes, "GMPF_ENABLE_3D"), to2d = code(codes, "GMPF_ENABLE_3D_TO_2D");
        int fmt = code(codes, "GMPF_GET_CURRENT_3D_FORMAT"), is2d = code(codes, "GMPF_IS_3D_TO_2D_ENABLED");
        // the stock IGmpf codes of the Z9X (V6.15.58 dump, IGmpf.aidl order)
        check(en == 190 && to2d == 191 && fmt == 192 && is2d == 193, "IGmpf 3D codes " + en + " " + to2d + " " + fmt + " " + is2d);
        check(Mode3D.OFF.wire == 1 && Mode3D.SIDE_BY_SIDE.wire == 2 && Mode3D.TOP_BOTTOM.wire == 3, "Mode3D bytes");

        // each mode's call as stock PictureRepository.setImage3D: close, lr, tb by enable3D, lrTo2d, tbTo2d by enable3DTo2D
        String[][] want = {{"off", "190(1)"}, {"sbs", "190(2)"}, {"tb", "190(3)"}, {"sbs_2d", "191(2)"},
                {"tb_2d", "191(3)"}, {"frame_packing", "-"}};
        for (String[] w : want) {
            ThreeDMode m = ThreeDMode.of(w[0]);
            check(m != null && w[1].equals(call(m, en, to2d)), w[0] + ": " + (m == null ? "no mode" : call(m, en, to2d)));
        }
        check(ThreeDCall.enable3D(null) == null && ThreeDCall.to2dTopBottom(null) == null, "no mode, no call");
        check(ThreeDMode.values().length == want.length, "a mode without its stock call in this test");

        // read-back as stock get3dModeToResult (the same code in the Z9X and X50 newsettings): by the format, the 3D to 2D
        // flag read only for 2 (lrTo2d) and 3 (tbTo2d), 4 frame packing whatever the flag, anything else off
        String[][] rb = {{"1", "false", "off"}, {"2", "false", "sbs"}, {"3", "false", "tb"}, {"4", "false", "frame_packing"},
                {"0", "false", "off"}, {"5", "false", "off"}, {"2", "true", "sbs_2d"}, {"3", "true", "tb_2d"},
                {"1", "true", "off"}, {"4", "true", "frame_packing"}, {"0", "true", "off"}};
        for (String[] r : rb) {
            ThreeDMode m = ThreeDMode.read(Integer.valueOf(r[0]), Boolean.valueOf(r[1]));
            check(m != null && r[2].equals(m.token), "read " + r[0] + "/" + r[1] + " -> " + (m == null ? null : m.token));
        }
        check(ThreeDMode.read(null, false) == null && ThreeDMode.read(2, null) == null, "an unread value is no mode");
        for (ThreeDMode m : ThreeDMode.values()) {
            check(ThreeDMode.read(m.format, m.to2d) == m, m.token + ": the vendor's read-back after its call");
            check(m.on() == (m != ThreeDMode.OFF), m.token + ": on");
        }

        // profiles: the stock list per model (stock settings tree of each model), none where stock has no 3D
        String[][] dev = {
                {"horse_popular", "off sbs tb sbs_2d tb_2d"},         // Z9X dapeng image_3d_info_state
                {"goat_elegant", ""},                                  // HB dapengse: no image_3d nodes
                {"dragon_healthy", ""},                                // Play 6 dayao: no image_3d nodes
                {"g0111", "off sbs tb sbs_2d tb_2d frame_packing"},   // X50 Ultra dianapro (X50Projector X50ThreeD)
                {"g0100", "off sbs tb sbs_2d tb_2d frame_packing"},   // X50 Ultra Max dianamax
                {"h0061", "off sbs tb sbs_2d tb_2d frame_packing"},   // TITAN Noir dianahy
                {"h0062", "off sbs tb sbs_2d tb_2d frame_packing"},   // TITAN Noir Pro dianahypro
                {"h0063", "off sbs tb sbs_2d tb_2d frame_packing"},   // TITAN Noir Max dianahymax
                {"g0155", "off sbs tb sbs_2d tb_2d"},                 // RS30 Ultra Max athena35ultramax: no frame packing
        };
        for (String[] d : dev) {
            MenuProfile p = MenuProfile.parse(read(g.resolve("devices/" + d[0] + "/menu.json")));
            check(p.ok(), d[0] + ": profile " + p.problems());
            String got = p.has(FeatureId.PICTURE_3D) ? String.join(" ", p.options(FeatureId.PICTURE_3D)) : "";
            check(d[1].equals(got), d[0] + ": 3D modes '" + got + "'");
            MenuSpec s = MenuSpec.of(p);
            boolean up = s.round.size() == 3 && "network".equals(s.round.get(0).id);
            check(up == !d[1].isEmpty(), d[0] + ": network on top only with 3D");
            check(s.hasTile("picture.3d") == !d[1].isEmpty(), d[0] + ": 3D tile only with 3D");
        }

        // labels: every mode has its MenuLabels case and its text in every language of the menu
        String labels = read(g.resolve("apps/core/lumen-menu/src/org/lumen/projector/panel/MenuLabels.java"));
        // the stock texts: Off is stock "Disable 3D" (image_set_3d_close_text), not the menu's plain Off
        for (ThreeDMode m : ThreeDMode.values()) {
            String res = m == ThreeDMode.OFF ? "lumen_m_3d_off" : "lumen_m_3d_" + m.token;
            check(labels.contains("case \"picture.3d|" + m.token + "\": return R.string." + res + ";"), m.token + ": label");
        }
        check(labels.contains("case \"picture.3d\": return R.drawable.lumen_qpd_ic_3d;"), "3D tile icon");
        check(labels.contains("case \"picture.3d\": return R.string.lumen_m_3d_title;")
                && labels.contains("case \"picture.3d\": return R.string.lumen_m_3d;"), "row title 3D Settings, tile 3D");
        List<String> names = Arrays.asList("lumen_m_3d", "lumen_m_3d_title", "lumen_m_3d_off", "lumen_m_3d_sbs",
                "lumen_m_3d_tb", "lumen_m_3d_sbs_2d", "lumen_m_3d_tb_2d", "lumen_m_3d_summary", "lumen_m_3d_frame_packing");
        int dirs = 0;
        try (java.util.stream.Stream<Path> st = Files.list(g.resolve("apps/core/lumen-menu/res"))) {
            for (Path v : (Iterable<Path>) st.filter(x -> x.getFileName().toString().startsWith("values"))::iterator) {
                if (!Files.exists(v.resolve("lumen_strings_v3.xml")) && !Files.exists(v.resolve("lumen_strings_3d.xml"))) continue;
                dirs++;
                Path f = v.resolve("lumen_strings_3d.xml");
                String x = Files.exists(f) ? read(f) : "";
                for (String n : names) {
                    Matcher mm = Pattern.compile("<string name=\"" + n + "\">([^<]+)</string>").matcher(x);
                    check(mm.find() && !mm.group(1).contains(";"), v.getFileName() + ": " + n);
                }
            }
        }
        check(dirs >= 10, "3D texts in " + dirs + " languages");
        // the stock words of XGIMI settings (newsettings, en-US and ru), stock order
        String enX = read(g.resolve("apps/core/lumen-menu/res/values/lumen_strings_3d.xml"));
        String ruX = read(g.resolve("apps/core/lumen-menu/res/values-ru/lumen_strings_3d.xml"));
        String[][] stock = {{"lumen_m_3d_title", "3D Settings", "Настройки 3D"}, {"lumen_m_3d_off", "Disable 3D", "Выключить 3D"},
                {"lumen_m_3d_sbs", "Side by Side", "Бок о бок"}, {"lumen_m_3d_tb", "Top and Bottom", "Верх и низ"},
                {"lumen_m_3d_sbs_2d", "3D to 2D (Side by Side)", "3D в 2D (Бок о бок)"},
                {"lumen_m_3d_tb_2d", "3D to 2D (Top and Bottom)", "3D в 2D (Верх-низ)"}};
        for (String[] w : stock) {
            check(enX.contains("<string name=\"" + w[0] + "\">" + w[1] + "</string>"), "en " + w[0]);
            check(ruX.contains("<string name=\"" + w[0] + "\">" + w[2] + "</string>"), "ru " + w[0]);
        }

        // the wiring the host cannot run: source lines of the menu, the tile and the Z9X adapter
        String mc = read(g.resolve("apps/core/lumen-menu/src/org/lumen/projector/panel/MenuController.java"));
        String[] pins = {
                "td.get(i).setChecked(opts.get(i).equals(now));",                 // popup marks the mode read back
                "td.setState(m == null || !m.on() ? null : optionLabel(FeatureId.PICTURE_3D, m.token));",
                "td.setActive(m != null && m.on());",                             // the dot while 3D is on
                "if (state != null && o.equals(state.get(f))) return;",           // the mode on now: nothing sent
                "            FeatureId.PICTURE_3D);",                             // SKIP_SAME
        };
        for (String pin : pins) check(mc.contains(pin), "MenuController: " + pin.trim());
        String style = read(g.resolve("apps/core/lumen-menu/src/org/lumen/projector/panel/QpdStyle.java"));
        check(style.contains("if (!motion()) return;") && style.contains("ValueAnimator.areAnimatorsEnabled()"),
                "icon motion off while the system animations are off");
        String tile = read(g.resolve("apps/core/lumen-menu/src/org/lumen/projector/panel/QpdTile.java"));
        check(tile.contains("CharSequence title = state != null && state.length() > 0 ? state : this.title;"),
                "the small tile label: the 3D mode, else its title");
        String rules = read(g.resolve("apps/ProjectorBase/src/org/lumen/projector/panel/Z9xMenuRules.java"));
        check(rules.contains("ThreeDMode m3d = ThreeDMode.read(in.fmt3d, in.is3dTo2d);")
                && rules.contains("if (m3d != null) b.put(FeatureId.PICTURE_3D, m3d.token);"), "Z9X reads 192 / 193");
        String rows = read(g.resolve("apps/ProjectorBase/src/org/lumen/projector/panel/Z9xPictureRows.java"));
        check(rows.contains("int refusal = ThreeD.apply(g, g2, m);")
                && rows.contains("ThreeD.READBACK_DELAY_MS, in -> ThreeD.watchIfOn(ThreeDMode.read(in.fmt3d, in.is3dTo2d)), cb);"),
                "Z9X write: stock call, then the read-back after the vendor's black frame");
        String td = read(g.resolve("apps/ProjectorBase/src/org/lumen/projector/display/ThreeD.java"));
        check(td.contains("boolean ok = m != null ? g.set3DMode(m) : g.set3DTo2D(tb);")
                && td.contains("if (m == null && tb == null) return R.string.panel_3d_not_now;"), "ThreeD sends ThreeDCall's call");
        check(Files.exists(g.resolve("apps/core/lumen-menu/res/drawable/lumen_qpd_ic_3d.xml")), "3D tile vector");

        // the remote's 3D key (lumen-remote key action 3d) opens the 3D popup through the panel receiver
        check("org.lumen.projector.action.QUICK_3D".equals(MenuRequest.QUICK_3D), "the key action's intent");
        check("picture.3d".equals(MenuRequest.place(MenuRequest.QUICK_3D, null))
                && "picture.3d".equals(MenuRequest.place(MenuRequest.QUICK_3D, "sound")), "3D key: the 3D popup");
        check(MenuRequest.place("org.lumen.projector.action.SHOW_PANEL", "") == null
                && "sound".equals(MenuRequest.place("org.lumen.projector.action.SHOW_PANEL", "sound")), "SHOW_PANEL as before");
        check(MenuRequest.ours(MenuRequest.QUICK_3D) && !MenuRequest.ours("org.lumen.projector.action.SHOW_PANEL")
                && !MenuRequest.ours(null), "ours");
        String man = read(g.resolve("apps/ProjectorBase/AndroidManifest.xml"));
        int r = man.indexOf("android:name=\".home.PanelRequestReceiver\""), end = man.indexOf("</receiver>", r);
        check(r > 0 && man.substring(r, end).contains("<action android:name=\"" + MenuRequest.QUICK_3D + "\" />"),
                "the Z9X panel receiver takes the 3D key");
        String qpc = read(g.resolve("apps/ProjectorBase/src/org/lumen/projector/panel/QuickPanelController.java"));
        check(qpc.contains("if (FeatureId.PICTURE_3D.id.equals(section)) return section;"), "the 3D popup is a place");

        System.out.println("threed: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
