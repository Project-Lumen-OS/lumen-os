// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Section headers of every page (MenuSpec.headerShown, what MenuController.apply runs): a header is on screen only
 * while one of its rows is, greyed rows keep it. java org.lumen.menu.HeaderTest &lt;gsi root&gt;.
 */
public final class HeaderTest {
    private static int pass, fail;

    private static void check(boolean ok, String what) {
        if (ok) pass++;
        else { fail++; System.out.println("FAIL " + what); }
    }

    public static void main(String[] a) throws Exception {
        Path root = Paths.get(a[0]);
        MenuProfile hb = MenuProfile.parse(new String(Files.readAllBytes(root.resolve("devices/goat_elegant/menu.json")),
                StandardCharsets.UTF_8));
        MenuSpec spec = MenuSpec.of(hb);
        for (String sec : spec.sections.keySet()) {
            for (MenuSpec.Group g : spec.sections.get(sec)) {
                if (g.header == null) continue;
                MenuState.Builder all = MenuState.builder();
                for (FeatureId f : g.rows) {
                    all.hide(f);
                    for (String it : hb.items(f)) all.hide(FeatureId.pq(it));
                }
                check(!spec.headerShown(g.rows, all.build()), sec + "/" + g.header + ": every row hidden = no header");
                for (FeatureId keep : g.rows) {
                    MenuState.Builder one = MenuState.builder();
                    for (FeatureId f : g.rows) if (f != keep) one.hide(f);
                    check(spec.headerShown(g.rows, one.build()), sec + "/" + g.header + ": " + keep + " alone keeps the header");
                }
                check(spec.headerShown(g.rows, MenuState.builder().disable(g.rows.get(0)).build()),
                        sec + "/" + g.header + ": greyed rows keep their header");
            }
        }
        MenuState pqGone = MenuState.builder().hide(FeatureId.pq("brightness")).hide(FeatureId.pq("contrast"))
                .hide(FeatureId.pq("saturation")).hide(FeatureId.pq("sharpness")).build();
        check(!spec.rowShown(FeatureId.PICTURE_PQ, pqGone), "the picture sliders count as gone when all of them are");
        check(spec.rowShown(FeatureId.PICTURE_PQ, MenuState.builder().hide(FeatureId.pq("brightness")).build()),
                "one picture slider left keeps them");

        // the stock line between two groups (Z9X, Play 6 Keystone page): never at the top, never without rows below
        for (String dev : new String[] {"horse_popular", "dragon_healthy"}) {
            MenuProfile p = MenuProfile.parse(new String(Files.readAllBytes(root.resolve("devices/" + dev + "/menu.json")),
                    StandardCharsets.UTF_8));
            MenuSpec s = MenuSpec.of(p);
            java.util.List<MenuSpec.Group> gs = s.sections.get(MenuSpec.KEYSTONE);
            int at = -1;
            for (int i = 0; i < gs.size(); i++) if (MenuSpec.LINE.equals(gs.get(i).header)) at = i;
            check(at > 0, dev + ": the Keystone page has its stock line between two groups");
            if (at <= 0) continue;
            java.util.List<FeatureId> above = new java.util.ArrayList<>(), rows = gs.get(at).rows;
            for (int i = 0; i < at; i++) above.addAll(gs.get(i).rows);
            MenuState.Builder none = MenuState.builder(), below = MenuState.builder();
            for (FeatureId f : above) none.hide(f);
            for (FeatureId f : rows) below.hide(f);
            check(s.lineShown(above, rows, MenuState.builder().build()), dev + ": the line shows between shown rows");
            check(!s.lineShown(above, rows, none.build()), dev + ": every row above hidden = no line on top");
            check(!s.lineShown(above, rows, below.build()), dev + ": every row below hidden = no line");
            for (FeatureId keep : above) {
                MenuState.Builder one = MenuState.builder();
                for (FeatureId f : above) if (f != keep) one.hide(f);
                check(s.lineShown(above, rows, one.build()), dev + ": " + keep + " alone above keeps the line");
            }
            check(s.lineShown(above, rows, MenuState.builder().disable(above.get(0)).build()), dev + ": a greyed row keeps it");
        }

        System.out.println("HeaderTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
