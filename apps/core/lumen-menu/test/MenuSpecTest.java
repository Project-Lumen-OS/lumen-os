// SPDX-License-Identifier: Apache-2.0
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
 * of the menu stays as it was (no re-layout); (4) broken profiles show no menu at all.
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
    static String without(String json, String feature) {
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
                for (String id : p.features()) {
                    String c = constOf(id);
                    check(src.contains("Feature." + c), dev + ": adapter never names Feature." + c);
                }
            }
            // every option token and every row of the profile has a label in MenuLabels (never a raw token on screen)
            String labels = new String(Files.readAllBytes(root.resolve(
                    "apps/core/lumen-menu/src/org/lumen/projector/panel/MenuLabels.java")), StandardCharsets.UTF_8);
            for (String id : p.features()) {
                for (String o : p.options(id)) check(labels.contains("case \"" + id + "|" + o + "\":"),
                        dev + ": no label for " + id + " option " + o);
                Feature.Kind kind = Feature.def(id).kind;
                if (kind != Feature.Kind.TILE && !Feature.LAMP.equals(id) && !Feature.PICTURE_PQ.equals(id)) {
                    check(labels.contains("case \"" + id + "\":"), dev + ": no title for " + id);
                }
            }
            String full = MenuSnapshot.render(p);
            // (3) removing one feature removes exactly its rows, tiles and hints
            for (String id : p.features()) {
                String j2 = without(json, id);
                MenuProfile p2 = MenuProfile.parse(j2);
                if (!p2.ok()) {
                    // a feature others depend on (boost needs lamp): the profile without it must say so
                    check(!p2.problems().isEmpty(), dev + " -" + id + ": invalid but silent");
                    continue;
                }
                check(!p2.has(id), dev + " -" + id + ": still has it");
                String snap = MenuSnapshot.render(p2);
                check(!tokens(snap).contains(id), dev + " -" + id + ": the menu still shows it:\n" + snap);
                MenuSpec m2 = MenuSpec.of(p2);
                check(!m2.hasTile(id), dev + " -" + id + ": tile still there");
                for (String sec : m2.sections.keySet()) {
                    check(!m2.rowsOf(sec).contains(id), dev + " -" + id + ": row still in " + sec);
                }
                check(p2.hint(id) == null, dev + " -" + id + ": hint still there");
                // nothing else moved: same tiles in the same rows and the same rows in the same sections, minus it
                MenuSpec m1 = MenuSpec.of(p);
                // a section tile goes with the last row of its section, so a vanished section is no change
                List<String> wantCard = minus(cardIds(m1), id);
                for (String s : m1.sections.keySet()) if (!m2.sections.containsKey(s)) wantCard = minus(wantCard, s);
                check(cardIds(m2).equals(wantCard), dev + " -" + id + ": the card changed beyond it: "
                        + cardIds(m1) + " -> " + cardIds(m2));
                for (String sec : m1.sections.keySet()) {
                    List<String> want = minus(m1.rowsOf(sec), id);
                    check(want.equals(m2.rowsOf(sec)), dev + " -" + id + ": section " + sec + " changed beyond it");
                }
                check(m2.boostLevel() == (Feature.BOOST.equals(id) ? -1 : m1.boostLevel()), dev + " -" + id + ": Boost moved");
            }
            // every feature of the profile shows up somewhere (no dead profile entries)
            List<String> t = tokens(full);
            for (String id : p.features()) check(t.contains(id), dev + ": " + id + " in the profile but not in the menu");
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
            check(!p.has(Feature.EYE) && p.features().isEmpty() && !p.gameMenu(), "broken profile still has features: " + b);
            check(p.hint(Feature.PICTURE_MODE) == null && p.hint(Feature.EYE) == null && p.hints().isEmpty(),
                    "broken profile still has a hint picture: " + b);
            check(p.number(Feature.LAMP, "max", -7) == -7 && p.options(Feature.EYE).isEmpty(),
                    "broken profile still gives parameters: " + b);
            MenuSpec m = MenuSpec.of(p);
            check(m.sections.isEmpty() && m.rows.isEmpty(), "broken profile has a menu: " + b);
        }
        // game_menu.sound: the default three without it, the named list with it (X50: five)
        String gs = "{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": true, \"items\": [\"lat\", \"snd\"], %s\"themes\": [], \"cec_detect\": false}}";
        MenuProfile g3 = MenuProfile.parse(String.format(gs, ""));
        check(g3.ok() && g3.gameSounds().equals(java.util.Arrays.asList("off", "surround", "story")) && !g3.gameSoundNamed(),
                "game sound default: off surround story");
        MenuProfile g5 = MenuProfile.parse(String.format(gs, "\"sound\": [\"off\", \"surround\", \"story\", \"clarity\", \"balanced\"], "));
        check(g5.ok() && g5.gameSounds().size() == 5 && g5.gameSoundNamed(), "game sound: five named values");
        // an empty but valid profile: only the gear (All settings with nothing but itself)
        MenuProfile empty = MenuProfile.parse("{\"schema\": 1, \"device\": \"x\", \"model\": \"m\", \"adapter\": \"a\", \"features\": {}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": false}}");
        check(empty.ok(), "empty profile invalid " + empty.problems());
        MenuSpec me = MenuSpec.of(empty);
        check(me.round.size() == 1 && "general".equals(me.round.get(0).id) && me.rows.isEmpty() && me.sections.isEmpty(),
                "empty profile shows more than the gear");
        check(me.boostLevel() == -1, "Boost without the feature");
        System.out.println("menu spec: " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 && pass > 0 ? 0 : 1);
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

    private static String constOf(String id) { return id.toUpperCase().replace('.', '_'); }



}
