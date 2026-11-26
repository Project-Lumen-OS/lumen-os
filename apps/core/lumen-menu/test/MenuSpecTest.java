// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Host tests of the menu gate, run by test/run.sh with the gsi/ root and every
 * devices/&lt;c&gt;/menu.json: (1) each profile is valid; (2) its adapter names every feature of the profile; (3) for
 * EVERY feature of EVERY profile, the profile without it has no tile, row, hint or category of it, and the rest
 * of the menu stays as it was (no re-layout); (4) broken profiles show no menu at all; (5) the card reaches every
 * row of a section tile once: as a tile of its own or in the popup, never both.
 */
public final class MenuSpecTest {
    private static int pass, fail;

    private static void check(boolean ok, String what) {
        if (ok) pass++;
        else { fail++; System.out.println("FAIL " + what); }
    }

    /** Every word of a snapshot that names a feature or a section. */
    private static List<String> tokens(String snap) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("[a-z0-9_]+(\\.[a-z0-9_]+)*").matcher(snap);
        while (m.find()) out.add(m.group());
        return out;
    }

    /** The profile text without one feature (and its hint line entry). */
    public static String without(String json, String feature) {
        String q = Pattern.quote("\"" + feature + "\"");
        String s = json.replaceAll("(?m)^\\s*" + q + ":\\s*\\{[^\\n]*\\},?\\n", "");
        s = s.replaceAll(q + ":\\s*\"[a-z_]+\",?\\s*", "");
        s = s.replaceAll(",(\\s*[}\\]])", "$1");
        return s;
    }


    public static void main(String[] a) throws Exception {
        Path root = Paths.get(a[0]);
        for (int k = 1; k < a.length; k++) {
            Path f = Paths.get(a[k]);
            String json = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
            String dev = f.getParent().getFileName().toString();
            MenuProfile p = MenuProfile.parse(json);
            check(p.ok(), dev + ": profile problems " + p.problems());
            check(dev.equals(p.device()), dev + ": device is " + p.device());
            if (!p.ok()) continue;
            // (2) the adapter implements every feature it declares
            Path ad = root.resolve(p.adapter());
            if (!Files.isRegularFile(ad)) {
                check(false, dev + ": adapter " + p.adapter() + " missing");
            } else {
                String src = new String(Files.readAllBytes(ad), StandardCharsets.UTF_8);
                check(src.contains("implements ProjectorFeatures"), dev + ": adapter does not implement ProjectorFeatures");
                String all = src + sections(ad);
                for (FeatureId id : p.features()) {
                    if (MenuSpec.module(id)) {
                        check(!all.contains("FeatureId." + id.name()) && !all.contains("case " + id.name() + ":"),
                                dev + ": adapter handles the module screen or setting " + id.name() + " itself (the menu does)");
                        continue;
                    }
                    check(all.contains("FeatureId." + id.name()) || all.contains("case " + id.name() + ":"),
                            dev + ": adapter never names FeatureId." + id.name());
                }
            }
            // every option token and every row of the profile has a label in MenuLabels (never a raw token on screen)
            String labels = new String(Files.readAllBytes(root.resolve(
                    "apps/core/lumen-menu/src/org/lumen/projector/panel/MenuLabels.java")), StandardCharsets.UTF_8);
            int fs = labels.indexOf("static int optionFormat(");
            String formats = fs < 0 ? "" : labels.substring(fs, labels.indexOf("\n    }\n", fs));
            int os = labels.indexOf("static boolean ownLabels(");
            String own = os < 0 ? "" : labels.substring(os, labels.indexOf("\n    }\n", os));
            for (FeatureId id : p.features()) {
                for (String o : p.options(id)) check(labels.contains("case \"" + id + "|" + o + "\":")
                                || o.matches("[0-9]{1,6}") && formats.contains("case \"" + id + "\":")
                                || o.matches("[a-z]|[0-9]{1,2}") && own.contains("case \"" + id + "\": return true;"),
                        dev + ": no label for " + id + " option " + o);
                FeatureKind kind = id.kind;
                if (kind != FeatureKind.TILE && FeatureId.LAMP != id && FeatureId.PICTURE_PQ != id) {
                    check(labels.contains("case \"" + id + "\":"), dev + ": no title for " + id);
                }
            }
            // every group header of the profile's pages has its text (a line has none, HDMI is its own name)
            int hs = labels.indexOf("static int header(");
            String headerLabels = hs < 0 ? "" : labels.substring(hs, labels.indexOf("\n    }\n", hs));
            List<List<MenuSpec.Group>> grouped = new ArrayList<>(MenuSpec.of(p).sections.values());
            grouped.addAll(MenuSpec.of(p).pages.values());        // the profile's own pages (picture.settings) have headers too
            for (List<MenuSpec.Group> gs : grouped) {
                for (MenuSpec.Group g : gs) {
                    if (g.header == null || MenuSpec.LINE.equals(g.header) || "hdmi".equals(g.header)) continue;
                    check(headerLabels.contains("case \"" + g.header + "\":"), dev + ": no text for the header " + g.header);
                }
            }
            // 2026-11-19: the developer tools are one page of their own, the last of All settings (as on Android),
            // never rows of General or another page, a tile or a popup; the page has its title and its rail icon
            MenuSpec dm = MenuSpec.of(p);
            List<FeatureId> devRows = new ArrayList<>();
            for (FeatureId d : new FeatureId[]{FeatureId.SIGNAL_PANEL, FeatureId.SENSORS_PANEL, FeatureId.SIGNAL_PIN,
                    FeatureId.SIGNAL_REFRESH, FeatureId.SETTINGS_TEMPERATURE, FeatureId.SETTINGS_DIAGNOSTICS, FeatureId.MIRROR_DIAG,
                    FeatureId.SETTINGS_UI_RESOLUTION, FeatureId.DEV_SYSTEM_INFO, FeatureId.DEV_NETWORK_ADB, FeatureId.DEV_ANDROID}) {
                if (p.has(d)) devRows.add(d);
            }
            // owner option B 2026-11-26: General is the first page of All settings, Send a problem report opens its last
            // group (Help), picture off has no row there but stays in the quick menu editor
            check(MenuSpec.GENERAL.equals(dm.allSettings.get(0)), dev + ": General is not the first page " + dm.allSettings);
            List<MenuSpec.Group> gen = dm.sections.get(MenuSpec.GENERAL);
            MenuSpec.Group help = gen == null ? null : gen.get(gen.size() - 1);
            check(!p.has(FeatureId.SETTINGS_REPORT) || help != null && MenuSpec.HELP.equals(help.header)
                    && help.rows.get(0) == FeatureId.SETTINGS_REPORT, dev + ": Send a problem report does not open Help at the bottom of General");
            check(!dm.rowsOf(MenuSpec.GENERAL).contains(FeatureId.LAMP_OFF), dev + ": picture off is a row of General");
            check(!p.has(FeatureId.LAMP_OFF) || org.lumen.menu.quick.QuickCatalog.of(dm, id -> true).has(FeatureId.LAMP_OFF.id),
                    dev + ": picture off left the quick menu editor");
            check(dm.rowsOf(MenuSpec.DEVELOPER).equals(devRows),
                    dev + ": For developers rows " + dm.rowsOf(MenuSpec.DEVELOPER) + ", want " + devRows);
            String lastPage = dm.allSettings.get(dm.allSettings.size() - 1);
            check(devRows.isEmpty() ? !dm.allSettings.contains(MenuSpec.DEVELOPER) : MenuSpec.DEVELOPER.equals(lastPage),
                    dev + ": For developers is not the last page of All settings " + dm.allSettings);
            for (FeatureId d : devRows) {
                check(MenuSpec.DEVELOPER.equals(MenuSpec.sectionOf(d)), dev + ": " + d + " is a row of " + MenuSpec.sectionOf(d));
                for (String sec : dm.sections.keySet()) {
                    check(MenuSpec.DEVELOPER.equals(sec) || !dm.rowsOf(sec).contains(d), dev + ": " + d + " also on page " + sec);
                }
                check(!dm.hasTile(d.id), dev + ": developer row " + d + " is a tile of the card");
            }
            check(!dm.hasTile(MenuSpec.DEVELOPER) && MenuSpec.allSettingsOnly(MenuSpec.DEVELOPER)
                            && MenuSpec.allSettingsOnly(MenuSpec.GENERAL) && !MenuSpec.allSettingsOnly(MenuSpec.PICTURE),
                    dev + ": For developers is a tile or a popup");
            int ss = labels.indexOf("static int section(");
            String sectionLabels = ss < 0 ? "" : labels.substring(ss, labels.indexOf("\n    }\n", ss));
            check(sectionLabels.contains("case \"developer\": return R.string.lumen_m_hdr_developer;")
                            && labels.contains("case \"developer\": return R.drawable.lumen_qpd_ic_dev;")
                            && Files.isRegularFile(root.resolve("apps/core/lumen-menu/res/drawable/lumen_qpd_ic_dev.xml")),
                    dev + ": For developers has no page title or rail icon");
            // x50-iris-quick: the slider tiles of the card are the brightness slider, then the iris right under it, each
            // only where the profile has its feature (model data only); the iris steps through the profile's options in
            // their stock order, one step per option, and a step and its token map both ways (the card slider and the
            // page row show the one value read)
            MenuSpec sliderSpec = MenuSpec.of(p);
            List<String> wantSliders = new ArrayList<>();
            if (p.has(FeatureId.LAMP)) wantSliders.add("lamp");
            if (p.has(FeatureId.PICTURE_IRIS)) wantSliders.add("picture.iris");
            List<String> gotSliders = new ArrayList<>();
            for (MenuSpec.Tile t : sliderSpec.sliders) {
                gotSliders.add(t.id);
                check(MenuSpec.SLIDER.equals(t.size) && t.feature != null && MenuSpec.isSliderTile(t.id) && sliderSpec.hasTile(t.id),
                        dev + ": slider tile " + t + " is no registered slider tile of the card");
            }
            check(gotSliders.equals(wantSliders), dev + ": slider tiles " + gotSliders + ", want " + wantSliders);
            if (p.has(FeatureId.PICTURE_IRIS)) {
                List<String> opts = p.options(FeatureId.PICTURE_IRIS);
                int[] range = sliderSpec.sliderRange(FeatureId.PICTURE_IRIS);
                check(range != null && range[0] == 0 && range[1] == opts.size() - 1,
                        dev + ": iris slider range " + java.util.Arrays.toString(range) + " for " + opts.size() + " options");
                for (int i = 0; i < opts.size(); i++) {
                    check(Integer.valueOf(i).equals(MenuSpec.sliderStep(opts, opts.get(i)))
                                    && opts.get(i).equals(MenuSpec.sliderToken(opts, i)),
                            dev + ": iris step " + i + " and token " + opts.get(i) + " do not map both ways");
                }
                check(MenuSpec.sliderToken(opts, -1) == null && MenuSpec.sliderToken(opts, opts.size()) == null
                                && MenuSpec.sliderStep(opts, null) == null && MenuSpec.sliderStep(opts, "f9") == null,
                        dev + ": iris slider invents a step or a token outside the stock options");
                check(sliderSpec.rowsOf(MenuSpec.PICTURE).contains(FeatureId.PICTURE_IRIS),
                        dev + ": the iris slider has no page row to stay in step with");
            }
            int[] lampRange = p.has(FeatureId.LAMP) ? sliderSpec.sliderRange(FeatureId.LAMP) : null;
            check(lampRange == null || lampRange[0] == p.number(FeatureId.LAMP, "min", -1) && lampRange[1] == p.number(FeatureId.LAMP, "max", -1),
                    dev + ": the brightness slider range is not the profile's");
            String full = MenuSnapshot.render(p);
            // (3) removing one feature removes exactly its rows, tiles and hints
            for (FeatureId id : p.features()) {
                String j2 = without(json, id.id);
                MenuProfile p2 = MenuProfile.parse(j2);
                if (!p2.ok()) {
                    // a feature others depend on (boost needs lamp): the profile without it must say so
                    check(!p2.problems().isEmpty(), dev + " -" + id + ": invalid but silent");
                    continue;
                }
                check(!p2.has(id), dev + " -" + id + ": still has it");
                String snap = MenuSnapshot.render(p2);
                check(!tokens(snap).contains(id.id), dev + " -" + id + ": the menu still shows it:\n" + snap);
                MenuSpec m2 = MenuSpec.of(p2);
                check(!m2.hasTile(id.id), dev + " -" + id + ": tile still there");
                for (String sec : m2.sections.keySet()) {
                    check(!m2.rowsOf(sec).contains(id), dev + " -" + id + ": row still in " + sec);
                }
                check(p2.hint(id) == null, dev + " -" + id + ": hint still there");
                // nothing else moved: same tiles in the same rows and the same rows in the same sections, minus it
                MenuSpec m1 = MenuSpec.of(p);
                // a section tile goes with the last row of its popup, so a vanished popup is no change
                List<String> wantCard = minus(cardIds(m1), id.id);
                // (a tile that is a feature of its own, Bluetooth or Network, stays without its popup)
                for (String s : m1.popups.keySet()) {
                    if (!m2.popups.containsKey(s) && (FeatureId.known(s) == null || !p2.has(FeatureId.known(s)))) wantCard = minus(wantCard, s);
                }
                // a tile that pushed another one up (3D, network): without it the other one is back on the card
                FeatureId up = MenuSpec.pushesOff(id);
                if (up != null && p.has(up)) {
                    List<String> r0 = new ArrayList<>(java.util.Arrays.asList(wantCard.get(0).split(" ")));
                    r0.remove(up.id);
                    wantCard.set(0, String.join(" ", r0));
                }
                check(cardIds(m2).equals(wantCard), dev + " -" + id + ": the card changed beyond it: "
                        + cardIds(m1) + " -> " + cardIds(m2));
                for (String sec : m1.sections.keySet()) {
                    List<FeatureId> want = new ArrayList<>(m1.rowsOf(sec));
                    want.remove(id);
                    want.removeIf(pg -> java.util.Collections.singletonList(id).equals(m1.pageRowsOf(pg)));   // a page emptied by it goes
                    check(want.equals(m2.rowsOf(sec)), dev + " -" + id + ": section " + sec + " changed beyond it");
                    List<FeatureId> wantPopup = new ArrayList<>(m1.popupRowsOf(sec));
                    wantPopup.remove(id);
                    wantPopup.removeIf(pg -> java.util.Collections.singletonList(id).equals(m1.pageRowsOf(pg)));
                    check(wantPopup.equals(m2.popupRowsOf(sec)), dev + " -" + id + ": popup " + sec + " changed beyond it");
                }
                // the pages of the menu's own rows: the same rows in the same order, minus it
                for (FeatureId pg : m1.pages.keySet()) {
                    List<FeatureId> want = new ArrayList<>(m1.pageRowsOf(pg));
                    want.remove(id);
                    check(want.equals(m2.pageRowsOf(pg)), dev + " -" + id + ": page " + pg + " changed beyond it");
                }
                check(m2.boostLevel() == (FeatureId.BOOST == id ? -1 : m1.boostLevel()), dev + " -" + id + ": Boost moved");
            }
            // every feature of the profile shows up somewhere (no dead profile entries)
            List<String> t = tokens(full);
            for (FeatureId id : p.features()) {
                check(t.contains(id.id) || MenuSpec.QUICK_ONLY.contains(id), dev + ": " + id + " in the profile but not in the menu");
            }
            reachedOnce(dev, MenuSpec.of(p));
        }
        // (4) broken profiles: no menu (fail closed)
        String[] bad = {
                "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {\"lamp\": {\"min\": 1, \"max\": 10}, \"eye\": {\"options\": [\"off\",\"on\"]}, \"lens.warp\": {}}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": false}}",
                "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {\"eye\": {\"options\": [\"off\",\"on\"]}}, \"hints\": {\"picture.mode\": \"picture_mode\"}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": false}}",
                "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {\"boost\": {\"level\": 11}}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": false}}",
                "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {\"picture.mode\": {}}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": false}}",
                "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {\"eye\": {\"options\": [\"on\",\"off\"]}}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": false}}",
                "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"game_menu\"}, \"game_menu\": {\"enabled\": false}}",
                "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": true, \"items\": [\"vrr\", \"lat\"], \"themes\": [], \"cec_detect\": false}}",
                "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {\"eye\": {\"options\": [\"off\",\"on\"]}, \"eye\": {}}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": false}}",
                "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {\"picture.mode\": {\"options\": []}}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": false}}",
                "{\"schema\": 1, \"device\": \"x\", \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": false}}",
                "{\"schema\": 2}",
                // a later row of a page without its page (MenuSpec.pageOf reads every row of a group)
                "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {\"eye\": {\"options\": [\"off\",\"on\"]}, \"picture.cc.lightness\": {\"min\": 0, \"max\": 100}}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": false}}",
                // a row of a page without its page (picture.wb2.red_gain needs picture.wb2, which needs picture.wb)
                "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {\"eye\": {\"options\": [\"off\",\"on\"]}, \"picture.wb2\": {}, \"picture.wb2.red_gain\": {\"min\": 0, \"max\": 100}}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": false}}",
                "not json",
                // game_menu.sound: a prefix of off surround story clarity balanced, at least three, only with snd
                "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": true, \"items\": [\"lat\", \"snd\"], \"sound\": [\"off\", \"story\", \"surround\"], \"themes\": [], \"cec_detect\": false}}",
                "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": true, \"items\": [\"lat\", \"snd\"], \"sound\": [\"off\", \"surround\"], \"themes\": [], \"cec_detect\": false}}",
                "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": true, \"items\": [\"lat\"], \"sound\": [\"off\", \"surround\", \"story\"], \"themes\": [], \"cec_detect\": false}}",
                "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": true, \"items\": [\"lat\", \"snd\"], \"sound\": [\"off\", \"surround\", \"story\", \"balanced\"], \"themes\": [], \"cec_detect\": false}}",
                "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": true, \"items\": [\"lat\", \"snd\"], \"sound\": [\"off\", \"surround\", \"story\", \"clarity\", \"balanced\", \"loud\"], \"themes\": [], \"cec_detect\": false}}",
        };
        for (String b : bad) {
            MenuProfile p = MenuProfile.parse(b);
            check(!p.ok(), "broken profile accepted: " + b);
            check(!p.has(FeatureId.EYE) && p.features().isEmpty() && !p.gameMenu(), "broken profile still has features: " + b);
            check(p.hint(FeatureId.PICTURE_MODE) == null && p.hint(FeatureId.EYE) == null && p.hints().isEmpty(),
                    "broken profile still has a hint picture: " + b);
            check(p.number(FeatureId.LAMP, "max", -7) == -7 && p.options(FeatureId.EYE).isEmpty(),
                    "broken profile still gives parameters: " + b);
            MenuSpec m = MenuSpec.of(p);
            check(m.sections.isEmpty() && m.rows.isEmpty(), "broken profile has a menu: " + b);
        }
        stockOrders();
        hotspotProfile(root);
        // game_menu.sound: the default three without it, the named list with it (X50: five)
        String gs = "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": true, \"items\": [\"lat\", \"snd\"], %s\"themes\": [], \"cec_detect\": false}}";
        MenuProfile g3 = MenuProfile.parse(String.format(gs, ""));
        check(g3.ok() && g3.gameSounds().equals(java.util.Arrays.asList("off", "surround", "story")) && !g3.gameSoundNamed(),
                "game sound default: off surround story");
        MenuProfile g5 = MenuProfile.parse(String.format(gs, "\"sound\": [\"off\", \"surround\", \"story\", \"clarity\", \"balanced\"], "));
        check(g5.ok() && g5.gameSounds().size() == 5 && g5.gameSoundNamed(), "game sound: five named values");
        // game_menu.vrr_top_speed (the RS30's support_vrr_only_at_top_speed): a boolean, only with the vrr and lat items
        String gv = "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": %s, \"items\": [%s], \"themes\": [], \"cec_detect\": false%s}}";
        MenuProfile v0 = MenuProfile.parse(String.format(gv, "true", "\"lat\", \"vrr\"", ""));
        check(v0.ok() && !v0.gameVrrTopSpeed(), "vrr_top_speed: off without the key");
        MenuProfile v1 = MenuProfile.parse(String.format(gv, "true", "\"lat\", \"vrr\"", ", \"vrr_top_speed\": true"));
        check(v1.ok() && v1.gameVrrTopSpeed(), "vrr_top_speed: true read");
        MenuProfile v2 = MenuProfile.parse(String.format(gv, "true", "\"lat\", \"vrr\"", ", \"vrr_top_speed\": false"));
        check(v2.ok() && !v2.gameVrrTopSpeed(), "vrr_top_speed: false read");
        MenuProfile v3 = MenuProfile.parse(String.format(gv, "true", "\"lat\", \"vrr\"", ", \"vrr_top_speed\": true")
                .replace("\"features\": {}", "\"features\": {\"no.such\": {}}"));
        check(!v3.ok() && !v3.gameVrrTopSpeed(), "vrr_top_speed: none on a broken profile");
        for (String[] b : new String[][] {{"\"lat\", \"vrr\"", ", \"vrr_top_speed\": 1"}, {"\"lat\", \"vrr\"", ", \"vrr_top_speed\": \"true\""},
                {"\"lat\"", ", \"vrr_top_speed\": true"}, {"\"vrr\"", ", \"vrr_top_speed\": true"}}) {
            MenuProfile vb = MenuProfile.parse(String.format(gv, "true", b[0], b[1]));
            check(!vb.ok() && !vb.gameVrrTopSpeed(), "vrr_top_speed refused: " + b[0] + b[1]);
        }
        // an empty but valid profile: only the gear (All settings with nothing but itself)
        MenuProfile empty = MenuProfile.parse("{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": false}}");
        check(empty.ok(), "empty profile invalid " + empty.problems());
        MenuSpec me = MenuSpec.of(empty);
        check(me.round.size() == 2 && "theme".equals(me.round.get(0).id) && "general".equals(me.round.get(1).id)
                && me.rows.isEmpty() && me.sections.isEmpty(), "empty profile shows more than the theme button and gear");
        cardRows(root);
        check(me.boostLevel() == -1, "Boost without the feature");
        // a section whose rows all have tiles of their own: no section tile (an empty popup), All settings keeps it
        MenuSpec tiled = MenuSpec.of(MenuProfile.parse("{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {\"keystone.ak_now\": {}, \"zoom\": {}}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": false}}"));
        check(tiled.hasSection("keystone") && !tiled.popups.containsKey("keystone") && !tiled.hasTile("keystone")
                && tiled.hasTile("keystone.ak_now") && tiled.hasTile("zoom") && tiled.allSettings.contains("keystone"),
                "tiled-only section: tile or All settings wrong " + cardIds(tiled) + " " + tiled.allSettings);
        // both zoom candidates: the slot shows the first, so the second stays a popup row (reached once)
        MenuSpec both = MenuSpec.of(MenuProfile.parse("{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {\"keystone.manual\": {}, \"zoom\": {}, \"zoom.digital\": {}}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": false}}"));
        check(both.popupRowsOf("keystone").equals(java.util.Arrays.asList(FeatureId.KEYSTONE_MANUAL, FeatureId.ZOOM_DIGITAL)),
                "both zoom candidates: popup " + both.popupRowsOf("keystone"));
        reachedOnce("both zoom candidates", both);
        // a page whose rows the profile lacks is no page, and its row is gone from its section or page
        MenuProfile bare = MenuProfile.parse("{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {\"eye\": {\"options\": [\"off\",\"on\"]}, \"picture.wb\": {}, \"picture.wb2\": {}, \"picture.cc\": {}, \"picture.cc.hue\": {\"min\": 0, \"max\": 100}}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": false}}");
        MenuSpec bs = MenuSpec.of(bare);
        check(bare.ok() && !bs.pages.containsKey(FeatureId.PICTURE_WB2) && !bs.pages.containsKey(FeatureId.PICTURE_WB)
                && !bs.rowsOf(MenuSpec.PICTURE).contains(FeatureId.PICTURE_WB) && bs.rowsOf(MenuSpec.PICTURE).contains(FeatureId.PICTURE_CC)
                && bs.pageRowsOf(FeatureId.PICTURE_CC).equals(java.util.Collections.singletonList(FeatureId.PICTURE_CC_HUE)),
                "a page without rows: no page, no row for it");
        // x50-iris-quick: the stock iris orders (X50 and TITAN dynamic first, RS30 dynamic last) give each option its
        // own step, a choice of one option is no slider tile, a profile without the iris has the brightness slider only
        String irisHead = "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {\"lamp\": {\"min\": 1, \"max\": 10}";
        String irisTail = "}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": false}}";
        MenuSpec diana = MenuSpec.of(MenuProfile.parse(irisHead + ", \"picture.iris\": {\"options\": [\"dynamic\", \"f2\", \"f3\", \"f4\", \"f5_5\", \"f7\"]}" + irisTail));
        MenuSpec athena = MenuSpec.of(MenuProfile.parse(irisHead + ", \"picture.iris\": {\"options\": [\"f2\", \"f3\", \"f4\", \"f5_5\", \"f7\", \"dynamic\"]}" + irisTail));
        MenuSpec noIris = MenuSpec.of(MenuProfile.parse(irisHead + irisTail));
        List<String> dOpts = diana.profile.options(FeatureId.PICTURE_IRIS), aOpts = athena.profile.options(FeatureId.PICTURE_IRIS);
        check(diana.sliders.size() == 2 && diana.sliders.get(0).feature == FeatureId.LAMP
                && diana.sliders.get(1).feature == FeatureId.PICTURE_IRIS, "iris slider: right under the brightness slider " + diana.sliders);
        check(Integer.valueOf(0).equals(MenuSpec.sliderStep(dOpts, "dynamic")) && Integer.valueOf(5).equals(MenuSpec.sliderStep(dOpts, "f7"))
                && Integer.valueOf(5).equals(MenuSpec.sliderStep(aOpts, "dynamic")) && Integer.valueOf(0).equals(MenuSpec.sliderStep(aOpts, "f2"))
                && "f5_5".equals(MenuSpec.sliderToken(dOpts, 4)) && "f7".equals(MenuSpec.sliderToken(aOpts, 4)),
                "iris slider: the steps follow each stock order");
        check(java.util.Arrays.equals(new int[]{0, 5}, diana.sliderRange(FeatureId.PICTURE_IRIS)), "iris slider: six stock steps");
        check(noIris.sliders.size() == 1 && noIris.sliders.get(0).feature == FeatureId.LAMP && !noIris.hasTile("picture.iris"),
                "no iris in the profile: the brightness slider only");
        check(!MenuSpec.isSliderTile("picture.mode") && !MenuSpec.isSliderTile("picture") && MenuSpec.isSliderTile("lamp"),
                "only the registered features are slider tiles");
        moduleRows(root);
        System.out.println("menu spec: " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 && pass > 0 ? 0 : 1);
    }

    /**
     * The key sounds, colour vision and the focus picture are the menu's own rows on every model whose stock tree has
     * them: Android settings through lumen-system with a read back, the picture from the profile hint.
     */
    static void moduleRows(Path root) throws Exception {
        check(MenuSpec.MODULE_SETTINGS.contains(FeatureId.SOUND_KEY_TONE)
                && MenuSpec.MODULE_SETTINGS.contains(FeatureId.SETTINGS_COLOUR_VISION)
                && MenuSpec.MODULE_INFO.contains(FeatureId.FOCUS_GUIDE) && MenuSpec.module(FeatureId.FOCUS_GUIDE),
                "key sounds, colour vision and the focus picture are module rows");
        check(!MenuSpec.MODULE_SETTINGS.contains(FeatureId.FOCUS_GUIDE) && !MenuSpec.MODULE_INFO.contains(FeatureId.SOUND_KEY_TONE),
                "the focus picture takes no value, the key sounds are a setting");
        String ms = new String(Files.readAllBytes(root.resolve(
                "apps/core/lumen-menu/src/org/lumen/projector/panel/ModuleScreens.java")), StandardCharsets.UTF_8);
        check(ms.contains("if (f == FeatureId.SOUND_KEY_TONE) return KeyTone.on(c) ? ON : OFF;")
                && ms.contains("if (f == FeatureId.SETTINGS_COLOUR_VISION) return ColourVision.get(c);"),
                "module rows: key sounds and colour vision read from lumen-system");
        check(ms.contains("            KeyTone.set(c, ON.equals(value));\n            return KeyTone.on(c) == ON.equals(value);")
                && ms.contains("return ColourVision.set(c, value) && value.equals(ColourVision.get(c));"),
                "module rows: every write reads back");
        check(ms.contains("if (f == FeatureId.SOUND_KEY_TONE || f == FeatureId.SETTINGS_COLOUR_VISION) return true;")
                && ms.contains("if (MenuSpec.MODULE_INFO.contains(f)) return true;"),
                "module rows: shown wherever the profile names them");
        // every module setting needs a rule in ModuleScreens.available, whose fallback is false: the power-on sound row
        // had none and was hidden on every model from 2026-11-19 although every profile listed it
        int a1 = ms.indexOf("static boolean available(Context c, MenuProfile p, FeatureId f)");
        int a2 = ms.indexOf("\n    }\n", Math.max(0, ms.indexOf("static boolean available(Context c, FeatureId f)")));
        String avail = a1 < 0 || a2 < a1 ? "" : ms.substring(a1, a2);
        for (FeatureId f : MenuSpec.MODULE_SETTINGS) {
            String n = "FeatureId." + f.name();
            check(avail.contains(n + ")") || avail.contains(n + " ")
                    || MenuSpec.HOTSPOT_ROWS.contains(f) && avail.contains("MenuSpec.HOTSPOT_ROWS.contains(f)"),
                    "module setting " + f.name() + " has a rule in ModuleScreens.available (no rule = hidden)");
        }
        check(ms.contains("if (f == FeatureId.POWER_ON_MUSIC) return true;"), "the power-on sound row shows on every model that lists it");
        check(MenuSpec.MODULE_SCREENS.contains(FeatureId.SETTINGS_SCREENSAVER_NOW)
                && MenuSpec.MODULE_SETTINGS.contains(FeatureId.SETTINGS_SCREENSAVER_DELAY), "the screensaver rows are module rows");
        check(ms.contains("if (f == FeatureId.SETTINGS_SCREENSAVER_NOW) return ScreensaverStart.now(c);")
                && ms.contains("if (f == FeatureId.SETTINGS_SCREENSAVER_DELAY) return ScreensaverDelay.get(c);")
                && ms.contains("            return ScreensaverDelay.set(c, value);"), "screensaver rows: lumen-system start, read, write with read back");
        // stock trees (gsi-stock analysis): screen_saver_simple on Z9X, HB, X50, X50 Max, RS30, Play 6, not
        // TITAN; the Z9X and HB keep their own screensaver page in Projector settings
        String[][] saver = {{"g0111", "1"}, {"g0100", "1"}, {"g0155", "1"}, {"g0170", "1"}, {"dragon_healthy", "1"}, {"h0061", "0"},
                {"h0062", "0"}, {"h0063", "0"}, {"horse_popular", "0"}, {"goat_elegant", "0"}};
        for (String[] d : saver) {
            MenuProfile p = MenuProfile.parse(new String(Files.readAllBytes(root.resolve("devices/" + d[0] + "/menu.json")),
                    StandardCharsets.UTF_8));
            boolean has = p.features().contains(FeatureId.SETTINGS_SCREENSAVER_NOW) && p.features().contains(FeatureId.SETTINGS_SCREENSAVER_DELAY);
            check(has == "1".equals(d[1]), d[0] + ": screensaver rows only where the stock tree has them");
            if (has) check(p.options(FeatureId.SETTINGS_SCREENSAVER_DELAY).equals(java.util.Arrays.asList("never", "m1", "m5", "m10", "m30", "m60")),
                    d[0] + ": the stock start after list");
            check(p.features().contains(FeatureId.SETTINGS_TEMPERATURE), d[0] + ": the system temperature row (sys_tempe, every tree)");
        }
    }

    /**
     * (6) sections: a section in its model's stock order. Broken orders give no menu (fail closed), a row the profile
     * lacks is skipped, a line never opens a page or popup, and the order changes nothing but the order.
     */
    private static void stockOrders() {
        String base = "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {\"eye\": {\"options\": [\"off\",\"on\"]}, "
                + "\"keystone.ak_now\": {}, \"keystone.manual\": {}, \"keystone.on_power\": {}, \"keystone.restore\": {}}, "
                + "\"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": false}%s}";
        String ok = ", \"sections\": {\"keystone\": {\"stock\": \"t\", \"groups\": [{\"rows\": [\"keystone.on_power\", \"keystone.ak_now\"]}, "
                + "{\"header\": \"line\", \"rows\": [\"keystone.rotation\", \"keystone.manual\"]}, {\"header\": \"more\", \"rows\": [\"keystone.restore\"]}]}}";
        MenuProfile p = MenuProfile.parse(String.format(base, ok));
        check(p.ok(), "stock order: a valid order refused " + p.problems());
        MenuSpec m = MenuSpec.of(p);
        check(m.rowsOf(MenuSpec.KEYSTONE).equals(java.util.Arrays.asList(FeatureId.KEYSTONE_ON_POWER, FeatureId.KEYSTONE_AK_NOW,
                FeatureId.KEYSTONE_MANUAL, FeatureId.KEYSTONE_RESTORE)), "stock order: rows in the profile's order, absent rotation skipped "
                + m.rowsOf(MenuSpec.KEYSTONE));
        List<MenuSpec.Group> gs = m.sections.get(MenuSpec.KEYSTONE);
        check(gs.size() == 3 && gs.get(0).header == null && MenuSpec.LINE.equals(gs.get(1).header) && "more".equals(gs.get(2).header),
                "stock order: the groups and headers of the profile");
        check("t".equals(p.orderSource(MenuSpec.KEYSTONE)) && p.orderSource(MenuSpec.PICTURE) == null, "stock order: its source");
        // the popup leaves out Auto keystone now (its tile): the line stays between power-on and manual
        check(m.popups.get(MenuSpec.KEYSTONE).get(1).header != null, "stock order: the popup keeps the line between two groups");
        // a first group of tile rows only: in the popup the line would open it, so it is no line there
        String tileFirst = ", \"sections\": {\"keystone\": {\"stock\": \"t\", \"groups\": [{\"rows\": [\"keystone.ak_now\"]}, "
                + "{\"header\": \"line\", \"rows\": [\"keystone.manual\", \"keystone.on_power\", \"keystone.restore\"]}]}}";
        MenuSpec tf = MenuSpec.of(MenuProfile.parse(String.format(base, tileFirst)));
        check(MenuSpec.LINE.equals(tf.sections.get(MenuSpec.KEYSTONE).get(1).header) && tf.popups.get(MenuSpec.KEYSTONE).size() == 1
                && tf.popups.get(MenuSpec.KEYSTONE).get(0).header == null, "stock order: a line never opens the popup");
        // without an order: the table's own (rows in table order, the table's headers)
        MenuSpec plain = MenuSpec.of(MenuProfile.parse(String.format(base, "")));
        check(plain.rowsOf(MenuSpec.KEYSTONE).equals(java.util.Arrays.asList(FeatureId.KEYSTONE_AK_NOW, FeatureId.KEYSTONE_MANUAL,
                FeatureId.KEYSTONE_ON_POWER, FeatureId.KEYSTONE_RESTORE)), "stock order: none = the table's order");
        check(new java.util.HashSet<>(plain.rowsOf(MenuSpec.KEYSTONE)).equals(new java.util.HashSet<>(m.rowsOf(MenuSpec.KEYSTONE)))
                && plain.cardRows(s -> true).toString().equals(m.cardRows(s -> true).toString()),
                "stock order: the order moves rows only (same rows, same card)");
        // the table's groups where a profile gives none: obstacle before curtain, the angles under More
        String wide = base.replace("\"keystone.restore\": {}}", "\"keystone.restore\": {}, \"keystone.obstacle\": {}, "
                + "\"keystone.curtain\": {}, \"zoom.memory\": {}, \"keystone.angle_h\": {}}");
        MenuProfile wp = MenuProfile.parse(String.format(wide, ""));
        check(wp.ok() && MenuSpec.of(wp).rowsOf(MenuSpec.KEYSTONE).equals(java.util.Arrays.asList(FeatureId.KEYSTONE_AK_NOW,
                FeatureId.KEYSTONE_MANUAL, FeatureId.ZOOM_MEMORY, FeatureId.KEYSTONE_ON_POWER, FeatureId.KEYSTONE_OBSTACLE,
                FeatureId.KEYSTONE_CURTAIN, FeatureId.KEYSTONE_RESTORE, FeatureId.KEYSTONE_ANGLE_H)),
                "stock order: none = the table's groups " + wp.problems() + " " + MenuSpec.of(wp).rowsOf(MenuSpec.KEYSTONE));
        String[] broken = {
                ", \"sections\": {\"lens\": {\"stock\": \"t\", \"groups\": [{\"rows\": [\"keystone.manual\"]}]}}",                       // no such section
                ", \"sections\": {\"keystone\": {\"stock\": \"t\", \"groups\": [{\"rows\": [\"keystone.ak_now\", \"keystone.manual\", \"keystone.on_power\"]}]}}", // restore has no place
                ", \"sections\": {\"keystone\": {\"stock\": \"t\", \"groups\": [{\"rows\": [\"keystone.ak_now\", \"keystone.manual\", \"keystone.on_power\", \"keystone.restore\", \"eye\"]}]}}", // an Eye protection row (focus rows may stand here: MenuSpec.MOVABLE)
                ", \"sections\": {\"keystone\": {\"stock\": \"t\", \"groups\": [{\"rows\": [\"keystone.ak_now\", \"keystone.manual\", \"keystone.on_power\", \"keystone.restore\", \"keystone.ak_now\"]}]}}", // twice
                ", \"sections\": {\"keystone\": {\"stock\": \"t\", \"groups\": [{\"header\": \"line\", \"rows\": [\"keystone.ak_now\", \"keystone.manual\", \"keystone.on_power\", \"keystone.restore\"]}]}}", // a line opens it
                ", \"sections\": {\"keystone\": {\"stock\": \"t\", \"groups\": [{\"header\": \"lens\", \"rows\": [\"keystone.ak_now\", \"keystone.manual\", \"keystone.on_power\", \"keystone.restore\"]}]}}", // unknown header
                ", \"sections\": {\"keystone\": {\"stock\": \"t\", \"groups\": [{\"rows\": [\"keystone.ak_now\", \"keystone.manual\", \"keystone.on_power\", \"keystone.restore\"]}, {\"header\": \"more\", \"rows\": []}]}}", // empty group
                ", \"sections\": {\"keystone\": {\"stock\": \"t\", \"groups\": [{\"rows\": [\"keystone.ak_now\", \"keystone.manual\", \"keystone.on_power\", \"keystone.restore\", \"keystone.warp\"]}]}}", // unknown row
                ", \"sections\": {\"keystone\": {\"groups\": [{\"rows\": [\"keystone.ak_now\", \"keystone.manual\", \"keystone.on_power\", \"keystone.restore\"]}]}}", // no stock source
                ", \"sections\": {\"keystone\": {\"stock\": \"t\", \"groups\": []}}",                                                     // no groups
                ", \"sections\": {\"keystone\": {\"stock\": \"t\", \"order\": [], \"groups\": [{\"rows\": [\"keystone.ak_now\", \"keystone.manual\", \"keystone.on_power\", \"keystone.restore\"]}]}}", // unknown key
                ", \"sections\": {\"keystone\": {\"stock\": \"t\", \"groups\": [{\"title\": \"more\", \"rows\": [\"keystone.ak_now\", \"keystone.manual\", \"keystone.on_power\", \"keystone.restore\"]}]}}", // unknown group key
                ", \"sections\": [\"keystone\"]",                                                                                            // not an object
        };
        for (String b : broken) {
            MenuProfile bp = MenuProfile.parse(String.format(base, b));
            check(!bp.ok() && MenuSpec.of(bp).sections.isEmpty(), "stock order: broken order accepted " + b);
        }
        stockPages();
    }

    /**
     * x50-picture-stock: a section order may hold the model's own stock sub page ({"page": id, "groups": [...]}, the
     * picture settings page) and the light level on the Picture page (MenuSpec.alsoIn); the page row stands in the
     * section, its rows open behind it with their headers, and every malformed page is refused (fail closed).
     */
    private static void stockPages() {
        String base = "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {\"eye\": {\"options\": [\"off\",\"on\"]}, "
                + "\"lamp\": {\"min\": 1, \"max\": 10}, \"picture.mode\": {\"options\": [\"ai\", \"sport\"]}, \"picture.settings\": {}, "
                + "\"picture.dynamic_black\": {}, \"picture.black_tuning\": {\"min\": -40, \"max\": 40}, \"picture.reset\": {}%s}, "
                + "\"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": false}%s}";
        String page = "{\"page\": \"picture.settings\", \"groups\": [{\"header\": \"contrast\", \"rows\": [\"picture.dynamic_black\", "
                + "\"picture.black_tuning\"]}, {\"header\": \"image_more\", \"rows\": [\"picture.reset\"]}]}";
        String ok = ", \"sections\": {\"picture\": {\"stock\": \"t\", \"groups\": [{\"header\": \"projector_brightness\", \"rows\": [\"lamp\"]}, "
                + "{\"header\": \"image\", \"rows\": [\"picture.mode\", " + page + "]}]}}";
        MenuProfile p = MenuProfile.parse(String.format(base, "", ok));
        check(p.ok(), "stock page: a valid page refused " + p.problems());
        MenuSpec m = MenuSpec.of(p);
        check(m.rowsOf(MenuSpec.PICTURE).equals(java.util.Arrays.asList(FeatureId.LAMP, FeatureId.PICTURE_MODE, FeatureId.PICTURE_SETTINGS)),
                "stock page: the light level, the mode, the page row " + m.rowsOf(MenuSpec.PICTURE));
        List<MenuSpec.Group> pg = m.pages.get(FeatureId.PICTURE_SETTINGS);
        check(pg != null && pg.size() == 2 && "contrast".equals(pg.get(0).header) && "image_more".equals(pg.get(1).header)
                && pg.get(0).rows.equals(java.util.Arrays.asList(FeatureId.PICTURE_DYNAMIC_BLACK, FeatureId.PICTURE_BLACK_TUNING))
                && pg.get(1).rows.equals(java.util.Collections.singletonList(FeatureId.PICTURE_RESET)), "stock page: its groups and headers " + pg);
        check(m.rowsOf(MenuSpec.LAMP).contains(FeatureId.LAMP), "stock page: the light bar keeps its slider");
        check(MenuSpec.alsoIn(MenuSpec.PICTURE, FeatureId.LAMP) && !MenuSpec.alsoIn(MenuSpec.KEYSTONE, FeatureId.LAMP)
                && !MenuSpec.alsoIn(MenuSpec.PICTURE, FeatureId.BOOST) && !MenuSpec.alsoIn(MenuSpec.PICTURE, FeatureId.EYE),
                "stock page: only the light level may stand on the Picture page too");
        check(!MenuSpec.isTablePage(FeatureId.PICTURE_SETTINGS) && MenuSpec.isTablePage(FeatureId.PICTURE_WB), "stock page: table pages");
        // without its page row the page has no place: the profile says so
        MenuProfile none = MenuProfile.parse(String.format(base, "", ""));
        check(!none.ok(), "stock page: a profile page without its rows in sections accepted");
        String[] broken = {
                ok.replace(page, page.replace("\"picture.reset\"]}]}", "\"picture.reset\", {\"page\": \"picture.settings\", \"groups\": []}]}]}")), // nested: the only profile page twice
                ok.replace("\"page\": \"picture.settings\"", "\"page\": \"picture.wb\""),                  // a fixed page of the table
                ok.replace("\"page\": \"picture.settings\"", "\"page\": \"picture.mode\""),                // not a page
                ok.replace("\"page\": \"picture.settings\"", "\"page\": \"picture.nothing\""),             // unknown
                ok.replace("{\"page\": \"picture.settings\", ", "{\"page\": \"picture.settings\", \"title\": 1, "), // unknown page key
                ok.replace(", \"groups\": [{\"header\": \"contrast\"", ", \"groups\": [{\"header\": \"keystone\""), // unknown header
                ok.replace("\"rows\": [\"lamp\"]", "\"rows\": [\"lamp\", \"eye\"]"),                     // a row of another section
                ok.replace("\"picture.black_tuning\"]}", "\"picture.black_tuning\", \"picture.mode\"]}"),  // a row twice
        };
        for (String b : broken) {
            MenuProfile bp = MenuProfile.parse(String.format(base, "", b));
            check(!bp.ok() && MenuSpec.of(bp).sections.isEmpty(), "stock page: broken page accepted " + b);
        }
        // a fixed page of the table or a plain row as the profile's page, with no other fault in the profile
        String tablePage = String.format(base, ", \"picture.wb\": {}", ok.replace("\"page\": \"picture.settings\"", "\"page\": \"picture.wb\""));
        check(!MenuProfile.parse(tablePage.replace("\"picture.settings\": {}, ", "")).ok(), "stock page: a fixed table page as a profile page accepted");
        String rowPage = String.format(base, "", ok.replace("\"rows\": [\"picture.mode\", {\"page\": \"picture.settings\"", "\"rows\": [{\"page\": \"picture.mode\""));
        check(!MenuProfile.parse(rowPage.replace("\"picture.settings\": {}, ", "")).ok(), "stock page: a plain row as a profile page accepted");
        // the page named in sections but not a feature of the profile: its rows would vanish
        MenuProfile gone = MenuProfile.parse(String.format(base, "", ok).replace("\"picture.settings\": {}, ", ""));
        check(!gone.ok(), "stock page: a page row without its feature accepted");
        // the light level of another section than Picture
        String keystoneLamp = ", \"sections\": {\"keystone\": {\"stock\": \"t\", \"groups\": [{\"rows\": [\"lamp\"]}]}, "
                + ok.substring(", \"sections\": {".length());
        check(!MenuProfile.parse(String.format(base, "", keystoneLamp)).ok(), "stock page: the light level in another section accepted");
    }

    /**
     * The Network popup (owner variant A, 2026-11-19): Wi-Fi, a line, the four stock hotspot rows, opened by the
     * network tile and its round button; a profile names all five rows or none, with the network tile, and
     * network.hotspot carries the default name and the vendor AP interface. Play 6 has none until a live check.
     */
    private static void hotspotProfile(Path root) throws java.io.IOException {
        MenuProfile z = MenuProfile.parse(new String(Files.readAllBytes(root.resolve("devices/horse_popular/menu.json")),
                StandardCharsets.UTF_8));
        MenuSpec m = MenuSpec.of(z);
        check(m.popupRowsOf(MenuSpec.NETWORK).equals(java.util.Arrays.asList(FeatureId.NETWORK_WIFI, FeatureId.NETWORK_HOTSPOT,
                FeatureId.NETWORK_HOTSPOT_NAME, FeatureId.NETWORK_HOTSPOT_PASSWORD, FeatureId.NETWORK_HOTSPOT_RESET)),
                "hotspot: the Network popup is Wi-Fi, switch, name, password, restore " + m.popupRowsOf(MenuSpec.NETWORK));
        List<MenuSpec.Group> g = m.popups.get(MenuSpec.NETWORK);
        check(g != null && g.size() == 2 && g.get(0).header == null && MenuSpec.LINE.equals(g.get(1).header),
                "hotspot: a line parts Wi-Fi from the hotspot rows");
        // the stock 1.3.38 rail (X50 live audit 2026-11-24): Network is a category of All settings too
        check(m.allSettings.contains(MenuSpec.NETWORK), "hotspot: Network is a category of All settings (stock rail)");
        check("Lumen Z9X".equals(z.text(FeatureId.NETWORK_HOTSPOT, "name")) && "ap0".equals(z.text(FeatureId.NETWORK_HOTSPOT, "iface")),
                "hotspot: the Z9X default name and AP interface");
        for (FeatureId f : MenuSpec.HOTSPOT_ROWS) check(MenuSpec.module(f), "hotspot: " + f + " is a module row");
        MenuProfile p6 = MenuProfile.parse(new String(Files.readAllBytes(root.resolve("devices/dragon_healthy/menu.json")),
                StandardCharsets.UTF_8));
        check(!p6.has(FeatureId.NETWORK_HOTSPOT) && !MenuSpec.of(p6).hasSection(MenuSpec.NETWORK),
                "hotspot: Play 6 has no hotspot rows until its live check");
        String head = "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {\"eye\": {\"options\": [\"off\",\"on\"]}, ";
        String rows = "\"network.wifi\": {}, \"network.hotspot\": {\"name\": \"Lumen X\", \"iface\": \"ap0\"}, \"network.hotspot_name\": {}, "
                + "\"network.hotspot_password\": {}, \"network.hotspot_reset\": {}";
        String tail = "}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": false}}";
        MenuProfile good = MenuProfile.parse(head + "\"network\": {}, " + rows + tail);
        check(good.ok() && MenuSpec.of(good).hasSection(MenuSpec.NETWORK), "hotspot: the five rows with the tile are a profile " + good.problems());
        check(!MenuProfile.parse(head + rows + tail).ok(), "hotspot: the rows without the network tile are refused");
        check(!MenuProfile.parse(head + "\"network\": {}, " + rows.replace(", \"network.hotspot_reset\": {}", "") + tail).ok(),
                "hotspot: four of the five rows are refused");
        check(!MenuProfile.parse(head + "\"network\": {}, " + rows.replace("\"iface\": \"ap0\"", "\"iface\": \"ap\"") + tail).ok(),
                "hotspot: an iface without its number is refused");
        check(!MenuProfile.parse(head + "\"network\": {}, " + rows.replace("\"name\": \"Lumen X\", ", "") + tail).ok(),
                "hotspot: no default name is refused");
        check(!MenuProfile.parse(head + "\"network\": {}, " + rows.replace("Lumen X", "x".repeat(33)) + tail).ok(),
                "hotspot: a default name over 32 bytes is refused");
        check(!MenuProfile.parse(head + "\"network\": {\"name\": \"x\"}, " + rows + tail).ok(),
                "hotspot: a name on another feature is refused");
    }

    /** (5) every row of a section tile is reached from the card once: its own tile or the popup, never both. */
    private static void reachedOnce(String dev, MenuSpec m) {
        List<FeatureId> tiles = new ArrayList<>();
        List<String> sectionTiles = new ArrayList<>();
        for (MenuSpec.Tile t : m.round) if (m.hasSection(t.id)) sectionTiles.add(t.id);
        for (List<MenuSpec.Tile> row : m.rows) {
            for (MenuSpec.Tile t : row) {
                if (t.feature != null) tiles.add(t.feature);
                else sectionTiles.add(t.id);
            }
        }
        for (String sec : sectionTiles) {
            List<FeatureId> popup = m.popupRowsOf(sec), want = new ArrayList<>(m.rowsOf(sec));
            want.removeAll(tiles);
            want.removeIf(f -> m.moved.containsKey(f) && !sec.equals(m.moved.get(f)));   // reached through its own section's tile
            check(!popup.isEmpty(), dev + ": tile " + sec + " opens an empty popup");
            check(popup.equals(want), dev + ": popup " + sec + " " + popup + ", want the section without the card tiles " + want);
            for (FeatureId f : m.rowsOf(sec)) {
                if (m.moved.containsKey(f) && !sec.equals(m.moved.get(f))) continue;
                int n = (tiles.contains(f) ? 1 : 0) + (popup.contains(f) ? 1 : 0);
                check(n == 1, dev + ": " + f + " reached " + n + " times from the card");
            }
        }
        for (String sec : m.popups.keySet()) {
            check(m.sections.containsKey(sec), dev + ": popup " + sec + " without its section");
            for (MenuSpec.Group g : m.popups.get(sec)) check(!g.rows.isEmpty(), dev + ": popup " + sec + " keeps an empty group");
        }
    }

    /**
     * MenuSpec.cardRows (2026-11-13): eye protection is the last tile of row 4, screen mirroring takes the place
     * of projection where its receiver is installed, the row never holds more than one wide and two small tiles, no
     * USB tile, the theme button and the gear on top.
     */
    private static void cardRows(Path root) throws java.io.IOException {
        MenuProfile z = MenuProfile.parse(new String(Files.readAllBytes(root.resolve("devices/horse_popular/menu.json")),
                StandardCharsets.UTF_8));
        MenuSpec m = MenuSpec.of(z);
        // 3D (a Z9X user, 2026-11-14): the 3D tile in the network place, network a round button left of the theme button
        check("network theme general".equals(ids(m.round)),
                "top row is network, the theme button, the gear: " + ids(m.round));
        check("input bluetooth picture.3d".equals(ids(m.cardRows(id -> true).get(2))), "3D takes the network place");
        check("network theme general".equals(ids(m.roundRow(id -> true))), "network on top while 3D is usable");
        // the signal panel (2026-11-18): a developer feature, never a round button, one row in All settings
        check(!ids(m.roundRow(id -> true)).contains("signal.panel"), "no signal button on the top row: "
                + ids(m.roundRow(id -> true)));
        check("input bluetooth network".equals(ids(m.cardRows(id -> !"picture.3d".equals(id)).get(2)))
                && "theme general".equals(ids(m.roundRow(id -> !"picture.3d".equals(id)))),
                "3D not usable now: network back in its card place, not on top");
        check("theme general".equals(ids(m.roundRow(id -> !"network".equals(id))))
                && "input bluetooth picture.3d".equals(ids(m.cardRows(id -> !"network".equals(id)).get(2))),
                "network not usable: no round network, 3D keeps its place");
        check(MenuSpec.pushesOff(FeatureId.PICTURE_3D) == FeatureId.NETWORK && MenuSpec.pushesOff(FeatureId.NETWORK) == null,
                "only 3D pushes network up");
        for (String dev : new String[]{"goat_elegant", "dragon_healthy"}) {
            MenuSpec o = MenuSpec.of(MenuProfile.parse(new String(Files.readAllBytes(
                    root.resolve("devices/" + dev + "/menu.json")), StandardCharsets.UTF_8)));
            check(!o.profile.has(FeatureId.PICTURE_3D) && "theme general".equals(ids(o.round))
                    && "input bluetooth network".equals(ids(o.cardRows(id -> true).get(2))),
                    dev + ": no stock 3D, the layout of today: " + ids(o.round));
        }
        check("game projection mirroring eye".equals(ids(m.rows.get(3))), "row 4 candidates: " + ids(m.rows.get(3)));
        check(m.rows.get(3).get(1).yields && !m.rows.get(3).get(2).yields && !m.rows.get(3).get(3).yields,
                "only projection yields");
        check(!m.hasTile("usb_files") && !m.hasTile("usb"), "a USB tile is back");
        java.util.function.Predicate<String> all = id -> true;
        List<List<MenuSpec.Tile>> on = m.cardRows(all);
        check("game mirroring eye".equals(ids(on.get(3))), "receiver installed: " + ids(on.get(3)));
        for (List<MenuSpec.Tile> r : on) check(r.size() <= MenuSpec.ROW_PLACES, "a row over its places: " + ids(r));
        check("game projection eye".equals(ids(m.cardRows(id -> !"mirroring".equals(id)).get(3))), "no receiver");
        check("game projection mirroring".equals(ids(m.cardRows(id -> !"eye".equals(id)).get(3))),
                "no eye protection: projection keeps its place");
        check("game projection".equals(ids(m.cardRows(id -> !"eye".equals(id) && !"mirroring".equals(id)).get(3))),
                "neither: a shorter row, no placeholder");
        check("game mirroring eye".equals(ids(m.cardRows(id -> !"projection".equals(id)).get(3))), "no projection");
        check("keystone.ak_now keystone zoom".equals(ids(on.get(0))) && on.size() == 4, "rows 1 to 3 unchanged");
        Path p6 = root.resolve("devices/dragon_healthy/menu.json");
        List<List<MenuSpec.Tile>> r6 = MenuSpec.of(MenuProfile.parse(new String(Files.readAllBytes(p6),
                StandardCharsets.UTF_8))).cardRows(all);
        check("keystone.ak_now keystone zoom.digital".equals(ids(r6.get(0)))
                && r6.get(0).get(2).feature == FeatureId.ZOOM_DIGITAL, "a slot's second candidate keeps its feature");
        check(MenuSpec.of(z).cardRows(id -> false).isEmpty(), "nothing usable: no rows");
    }

    private static String ids(List<MenuSpec.Tile> row) {
        StringBuilder b = new StringBuilder();
        for (MenuSpec.Tile t : row) b.append(b.length() == 0 ? "" : " ").append(t.id);
        return b.toString();
    }

    /** The card as a list of rows of tile ids ("|" between the two candidates of the last slot). */
    private static List<String> cardIds(MenuSpec m) {
        List<String> out = new ArrayList<>();
        StringBuilder r0 = new StringBuilder();
        for (MenuSpec.Tile t : m.round) r0.append(t.id).append(' ');
        out.add(r0.toString().trim());
        for (List<MenuSpec.Tile> row : m.rows) {
            StringBuilder r = new StringBuilder();
            for (MenuSpec.Tile t : row) r.append(t.id).append(t.orElse == null ? "" : "|" + t.orElse).append(' ');
            out.add(r.toString().trim());
        }
        return out;
    }

    /** The same rows without one id (a tile row that becomes empty goes; a "a|b" slot keeps the other). */
    private static List<String> minus(List<String> rows, String id) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            String r = rows.get(i);
            if (r.equals(id)) continue;
            if (!r.contains(" ") && !r.contains("|")) { out.add(r); continue; }
            StringBuilder b = new StringBuilder();
            for (String x : r.split(" ")) {
                if (x.isEmpty()) continue;
                if (x.equals(id)) continue;
                if (x.startsWith(id + "|")) x = x.substring(id.length() + 1);
                else if (x.endsWith("|" + id)) x = x.substring(0, x.length() - id.length() - 1);
                b.append(x).append(' ');
            }
            String s = b.toString().trim();
            if (!s.isEmpty() || i == 0) out.add(s);
        }
        return out;
    }

    /** The adapter's section files (the {@code *Rows.java} next to it), as one text. */
    static String sections(Path adapter) throws java.io.IOException {
        StringBuilder b = new StringBuilder();
        try (java.nio.file.DirectoryStream<Path> d = Files.newDirectoryStream(adapter.getParent(), "*Rows.java")) {
            for (Path f : d) b.append(new String(Files.readAllBytes(f), StandardCharsets.UTF_8));
        }
        // the rows of the old Projector settings page every Z9X-family adapter delegates to (apps/common/projector)
        String own = new String(Files.readAllBytes(adapter), StandardCharsets.UTF_8);
        Path shared = adapter.getParent();
        while (shared != null && !shared.endsWith("apps")) shared = shared.getParent();
        if (own.contains("new SettingsRows(") && shared != null) {
            Path rows = shared.resolve("common/projector/src/org/lumen/projector/panel/SettingsRows.java");
            if (Files.isRegularFile(rows)) b.append(new String(Files.readAllBytes(rows), StandardCharsets.UTF_8));
        }
        return b.toString();
    }
}
