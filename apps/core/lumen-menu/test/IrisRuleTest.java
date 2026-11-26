// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The lens iris row (picture.iris) and its light level rule (IrisRule, stock DisplayMutexManager.setDlpLumenLevel):
 * java org.lumen.menu.IrisRuleTest &lt;gsi root&gt;.
 */
public final class IrisRuleTest {
    private static int pass, fail;

    private static void check(boolean ok, String what) {
        if (ok) pass++;
        else { fail++; System.out.println("FAIL " + what); }
    }

    private static MenuProfile parse(String features) {
        return MenuProfile.parse("{\"schema\": 1, \"device\": \"t\", \"model\": \"T\", \"adapter\": \"a\", \"features\": {" + features
                + "}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": false}}");
    }

    private static MenuState iris(String v) { return MenuState.builder().put(FeatureId.PICTURE_IRIS, v).build(); }

    private static String note(MenuProfile p, FeatureId f, MenuState s) {
        ProjectorFeatures.Note n = IrisRule.blocked(p, f, s);
        return n == null ? null : n.res;
    }

    private static String read(Path p) throws Exception { return new String(Files.readAllBytes(p), StandardCharsets.UTF_8); }

    public static void main(String[] a) throws Exception {
        Path root = Paths.get(a[0]);
        Path mod = root.resolve("apps/core/lumen-menu");
        MenuProfile both = parse("\"lamp\": {\"min\": 1, \"max\": 10}, "
                + "\"picture.iris\": {\"options\": [\"dynamic\", \"f2\", \"f3\", \"f4\", \"f5_5\", \"f7\"]}");
        check(both.ok(), "a profile with the lamp and the iris is valid " + both.problems());
        // ---- the rule
        check("lumen_m_iris_lamp_dynamic".equals(note(both, FeatureId.LAMP, iris("dynamic"))), "dynamic iris holds the light level");
        check("lumen_m_iris_lamp_held".equals(note(both, FeatureId.LAMP, iris("f5_5"))), "f/5.5 holds the light level");
        check("lumen_m_iris_lamp_held".equals(note(both, FeatureId.LAMP, iris("f7"))), "f/7.0 holds the light level");
        for (String free : new String[] {"f2", "f3", "f4"}) {
            check(note(both, FeatureId.LAMP, iris(free)) == null, "the light level moves at " + free);
        }
        check(note(both, FeatureId.LAMP, MenuState.builder().build()) == null, "iris not read: the slider moves (nothing invented)");
        check(note(both, FeatureId.LAMP, iris("f5")) == null, "an unknown iris value holds nothing");
        check(note(both, FeatureId.LAMP, null) == null, "no state yet: allowed");
        check(note(both, FeatureId.PQ_BRIGHTNESS, iris("dynamic")) == null, "the picture brightness slider is not the light level");
        check(note(both, FeatureId.PICTURE_IRIS, iris("dynamic")) == null, "the iris row itself is never refused by the rule");
        MenuProfile lampOnly = parse("\"lamp\": {\"min\": 1, \"max\": 10}");
        check(note(lampOnly, FeatureId.LAMP, iris("dynamic")) == null, "a model without the iris row: no iris refusal");
        check(note(null, FeatureId.LAMP, iris("dynamic")) == null, "no profile: allowed");
        check("lumen_m_iris_lamp_dynamic".equals(IrisRule.heldNote("dynamic")) && IrisRule.heldNote("f2") == null
                && IrisRule.heldNote(null) == null, "heldNote for the adapters");
        // ---- the place: first row of the picture page, before the picture mode (stock image page)
        MenuProfile pic = parse("\"lamp\": {\"min\": 1, \"max\": 10}, \"picture.mode\": {\"options\": [\"ai\", \"soft\"]}, "
                + "\"picture.iris\": {\"options\": [\"dynamic\", \"f2\", \"f3\", \"f4\", \"f5_5\", \"f7\"]}");
        List<FeatureId> rows = MenuSpec.of(pic).rowsOf(MenuSpec.PICTURE);
        check(rows.size() > 1 && rows.get(0) == FeatureId.PICTURE_IRIS && rows.get(1) == FeatureId.PICTURE_MODE,
                "the iris is the first picture row, the mode follows " + rows);
        MenuSpec ps = MenuSpec.of(pic);
        boolean inTileRows = false;
        for (List<MenuSpec.Tile> r : ps.rows) for (MenuSpec.Tile t : r) inTileRows |= t.id.equals(FeatureId.PICTURE_IRIS.id);
        check(!inTileRows && ps.sliders.size() == 2 && ps.sliders.get(0).feature == FeatureId.LAMP
                        && ps.sliders.get(1).feature == FeatureId.PICTURE_IRIS && MenuSpec.SLIDER.equals(ps.sliders.get(1).size),
                "the iris has no tile in the tile rows, its slider tile sits right under the brightness slider (x50-iris-quick)");
        // ---- the X50 Ultra profile: the stock tree map, in its order
        MenuProfile u = MenuProfile.parse(read(root.resolve("devices/g0111/menu.json")));
        check(u.options(FeatureId.PICTURE_IRIS).equals(Arrays.asList("dynamic", "f2", "f3", "f4", "f5_5", "f7")),
                "g0111 iris options = the stock picture_iris_ring_adjustment_diana map order");
        // its stock image page (sections.picture): the light level, the iris right under it, then the mode
        List<FeatureId> ur = MenuSpec.of(u).rowsOf(MenuSpec.PICTURE);
        check(ur.size() > 2 && ur.get(0) == FeatureId.LAMP && ur.get(1) == FeatureId.PICTURE_IRIS && ur.get(2) == FeatureId.PICTURE_MODE,
                "g0111: the light level, then the iris, then the mode " + ur);
        // ---- labels: every option token of every profile's iris row has a label, the strings exist in every locale
        String labels = read(mod.resolve("src/org/lumen/projector/panel/MenuLabels.java"));
        check(labels.contains("case \"picture.iris\": return R.string.lumen_m_iris;"), "MenuLabels titles the iris row");
        for (String o : pic.options(FeatureId.PICTURE_IRIS)) {
            check(labels.contains("case \"picture.iris|" + o + "\": return R.string.lumen_m_iris_" + o + ";"), "label of iris option " + o);
        }
        File devs = root.resolve("devices").toFile();
        for (File d : devs.listFiles()) {
            File j = new File(d, "menu.json");
            if (!j.isFile()) continue;
            MenuProfile p = MenuProfile.parse(read(j.toPath()));
            for (String o : p.options(FeatureId.PICTURE_IRIS)) {
                check(labels.contains("case \"picture.iris|" + o + "\": return R.string.lumen_m_iris_" + o + ";"),
                        d.getName() + ": label of iris option " + o);
            }
        }
        Pattern str = Pattern.compile("<string name=\"(lumen_m_iris[a-z0-9_]*)\"([^>]*)>([^<]*)</string>");
        String[] translated = {"lumen_m_iris", "lumen_m_iris_dynamic", "lumen_m_iris_lamp_dynamic", "lumen_m_iris_lamp_held"};
        int locales = 0;
        for (File d : mod.resolve("res").toFile().listFiles()) {
            if (!d.getName().equals("values") && !d.getName().startsWith("values-")) continue;
            locales++;
            String s = read(new File(d, "lumen_strings_v3.xml").toPath());
            for (String n : translated) check(s.contains("<string name=\"" + n + "\">"), d.getName() + " has " + n);
            Matcher m = str.matcher(s);
            while (m.find()) {
                String t = m.group(3);
                check(t.indexOf(';') < 0 && t.indexOf('\u2014') < 0 && t.indexOf('\u2013') < 0,
                        d.getName() + " " + m.group(1) + ": no semicolon, no dash");
                if (!d.getName().equals("values")) check(!m.group(2).contains("translatable"), d.getName() + " " + m.group(1) + " is a translation");
            }
        }
        check(locales == 18, "the iris strings in 18 resource folders (default + 17 locales): " + locales);
        // the row title is the word the stock iris notes use where a stock build has the language (TITAN 1.2.99
        // newsettings, RS30 SystemUI toasts), not the stock row title IRIS (devices/g0111/stock analysis)
        String[][] words = {{"ru", "Диафрагма"}, {"de", "Blende"}, {"es", "Apertura"}, {"it", "Apertura"},
                {"fr", "Ouverture"}, {"pl", "Przysłona"}, {"pt", "Abertura"}, {"pt-rBR", "Abertura"}};
        for (String[] w : words) {
            check(read(mod.resolve("res/values-" + w[0] + "/lumen_strings_v3.xml")).contains(
                    "<string name=\"lumen_m_iris\">" + w[1] + "</string>"), w[0] + ": the iris row is " + w[1]);
        }
        String def = read(mod.resolve("res/values/lumen_strings_v3.xml"));
        String[][] stops = {{"f2", "f/2.0"}, {"f3", "f/3.0"}, {"f4", "f/4.0"}, {"f5_5", "f/5.5"}, {"f7", "f/7.0"}};
        for (String[] f : stops) {
            check(def.contains("<string name=\"lumen_m_iris_" + f[0] + "\" translatable=\"false\">" + f[1] + "</string>"),
                    "the stock f-number " + f[1] + " is one text in every language");
        }
        System.out.println("IrisRuleTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
