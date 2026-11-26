// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu.quick;

import org.lumen.menu.FeatureId;
import org.lumen.menu.MenuProfile;
import org.lumen.menu.MenuSpec;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * The quick menu editor's plain Java part (2026-11-18, pencil): the default menu packs to today's card on every
 * profile, the catalogue per profile (test/snapshots/quick), storage, repair, capacity, every look of every item kind,
 * pinned items, moves and reset.
 *   java org.lumen.menu.quick.QuickTest GSI [--update]
 */
public final class QuickTest {
    private static int fails, checks;

    static void ok(String what, boolean good) {
        checks++;
        if (!good) { fails++; System.out.println("FAIL " + what); }
    }

    static MenuSpec spec(File gsi, String dev) throws Exception {
        String j = new String(Files.readAllBytes(new File(gsi, "devices/" + dev + "/menu.json").toPath()), StandardCharsets.UTF_8);
        MenuProfile p = MenuProfile.parse(j);
        if (!p.ok()) throw new IllegalStateException(dev + " " + p.problems());
        return MenuSpec.of(p);
    }

    static final Predicate<String> ALL = id -> true;

    public static void main(String[] a) throws Exception {
        File gsi = new File(a[0]);
        boolean update = a.length > 1 && "--update".equals(a[1]);
        List<String> devs = new ArrayList<>();
        for (File d : new File(gsi, "devices").listFiles()) if (new File(d, "menu.json").isFile()) devs.add(d.getName());
        Collections.sort(devs);
        ok("a profile for every model (" + devs.size() + ", RS 20 and the RS30 old line came 2026-11-24)", devs.size() >= 10);
        for (String dev : devs) {
            MenuSpec spec = spec(gsi, dev);
            parity(dev, spec, ALL);
            parity(dev + " without mirroring", spec, id -> !FeatureId.MIRRORING.id.equals(id));
            snapshot(gsi, dev, spec, update);
            looks(dev, spec);
            everyRow(gsi, dev, spec);
        }
        MenuSpec z9x = spec(gsi, "horse_popular"), x50 = spec(gsi, "g0111");
        json(z9x);
        repair(z9x);
        capacity(z9x, x50);
        moves(z9x);
        catalogue(z9x, x50);
        anySetting(gsi, z9x, x50);
        System.out.println((fails == 0 ? "ok   " : "FAIL ") + "quick menu: " + checks + " checks, " + fails + " failed");
        System.exit(fails == 0 ? 0 : 1);
    }

    /** The default menu packed is today's card: the same rows, the pencil before the gear, the same height. */
    static void parity(String dev, MenuSpec spec, Predicate<String> usable) {
        QuickLayout d = QuickDefaults.of(spec, usable);
        QuickPacker.Card c = QuickPacker.pack(d);
        List<String> top = new ArrayList<>();
        for (MenuSpec.Tile t : spec.roundRow(usable)) {
            if (MenuSpec.GENERAL.equals(t.id)) top.add(QuickCatalog.EDIT);
            top.add(t.id);
        }
        List<List<String>> want = new ArrayList<>();
        want.add(top);
        List<String> wides = new ArrayList<>(), smalls = new ArrayList<>();
        for (List<MenuSpec.Tile> row : spec.cardRows(usable)) for (MenuSpec.Tile t : row) (MenuSpec.WIDE.equals(t.size) ? wides : smalls).add(t.id);
        int s = 0;
        for (String w : wides) {                         // QpdLayout.rows: per wide tile the next two small ones
            List<String> r = new ArrayList<>(Collections.singletonList(w));
            for (int k = 0; k < 2 && s < smalls.size(); k++) r.add(smalls.get(s++));
            want.add(r);
        }
        int sliders = 0;
        for (MenuSpec.Tile t : spec.sliders) if (usable.test(t.id)) { want.add(Collections.singletonList(t.id)); sliders++; }
        ok(dev + ": the default menu packs to today's card " + c.keyRows() + " vs " + want, c.keyRows().equals(want));
        int rows = wides.size();
        int h = 24 + 124 + rows * 120 + (rows - 1) * 16 + (sliders > 0 ? 16 + 120 + (sliders - 1) * 112 : 0) + 24;
        ok(dev + ": the default card is today's height " + h + " (" + c.height + ")", c.height == h);
        ok(dev + ": the default card fits", c.fits());
        int y = 24 + 124;
        for (QuickPacker.Line l : c.lines) {
            if (l.kind == LineKind.TILES) {
                ok(dev + ": tile row at today's place " + l.top, l.top == y && l.height == 120);
                y += 136;
            }
        }
        if (sliders > 0) ok(dev + ": the first slider at today's place", c.lines.get(rows).top == y + 6);
        ok(dev + ": nothing stored, the default shows", QuickLayoutJson.reconcile(null, QuickCatalog.of(spec, usable), d) == d);
    }

    static void snapshot(File gsi, String dev, MenuSpec spec, boolean update) throws Exception {
        QuickCatalog cat = QuickCatalog.of(spec, ALL);
        StringBuilder b = new StringBuilder("quick menu of " + dev + " (every runtime check passing)\ncatalogue\n");
        for (String g : cat.groups()) {
            b.append("  ").append(g).append('\n');
            for (QuickItem i : cat.group(g)) b.append("    ").append(i.id).append(' ').append(i.kind.name().toLowerCase()).append(' ').append(lookTokens(i)).append('\n');
        }
        QuickLayout d = QuickDefaults.of(spec, ALL);
        b.append("default ").append(d).append('\n').append(QuickPacker.pack(d)).append('\n');
        File f = new File(gsi, "apps/core/lumen-menu/test/snapshots/quick/" + dev + ".txt");
        if (update) {
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("updated " + f);
            return;
        }
        String want = f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "(none)";
        ok(dev + ": quick snapshot equals " + f + "\n--- want\n" + want + "\n--- got\n" + b, want.equals(b.toString()));
    }

    static String lookTokens(QuickItem i) {
        StringBuilder b = new StringBuilder();
        for (Presentation p : i.allowed()) b.append(b.length() == 0 ? "" : "/").append(p.token);
        return b.toString();
    }

    /** Every look of every item: the packer puts it in its zone, in tiles and in the list. */
    static void looks(String dev, MenuSpec spec) {
        QuickCatalog cat = QuickCatalog.of(spec, ALL);
        QuickLayout base = new QuickLayout(1, Density.TILES, Arrays.asList(new Placement("general", Presentation.ROUND),
                new Placement("edit", Presentation.ROUND)), Collections.emptyList());
        for (QuickItem it : cat.items()) {
            for (Presentation p : Presentation.values()) {
                QuickEditor e = new QuickEditor(cat, base, base);
                boolean in = it.pinned() || e.add(it.id) == EditResult.DONE;
                ok(dev + " " + it.id + ": can be added", in);
                EditResult r = e.setLook(it.id, p);
                if (!it.allows(p)) { ok(dev + " " + it.id + " as " + p + ": refused", r == EditResult.UNKNOWN); continue; }
                ok(dev + " " + it.id + " as " + p + ": taken", r == EditResult.DONE || r == EditResult.SAME);
                for (Density d : Density.values()) {
                    QuickPacker.Card c = QuickPacker.pack(e.layout().with(d));
                    String zone = zone(c, it.id);
                    String want = p == Presentation.ROUND ? "round" : p == Presentation.FOOTER ? "footer"
                            : d == Density.LIST ? (p == Presentation.SLIDER ? "list_slider" : "list")
                            : p == Presentation.SLIDER ? "slider" : "tiles";
                    ok(dev + " " + it.id + " as " + p + " in " + d + ": drawn in " + want + " (" + zone + ")", want.equals(zone));
                }
            }
        }
        // the presentation table: a choice is never a slider, a pinned item only a circle or a footer button
        ok("table: sliders slide, sections never", Presentations.of(ItemKind.SLIDER).get(0) == Presentation.SLIDER
                && !Presentations.of(ItemKind.SECTION).contains(Presentation.SLIDER)
                && Presentations.of(ItemKind.PINNED).equals(Arrays.asList(Presentation.ROUND, Presentation.FOOTER))
                && !Presentations.of(ItemKind.CHOICE).contains(Presentation.FOOTER));
    }

    static String zone(QuickPacker.Card c, String id) {
        for (Placement p : c.round) if (p.id.equals(id)) return "round";
        for (Placement p : c.footer) if (p.id.equals(id)) return "footer";
        int l = c.lineOf(id);
        return l < 0 ? "none" : c.lines.get(l).kind.name().toLowerCase();
    }

    static void json(MenuSpec spec) {
        QuickCatalog cat = QuickCatalog.of(spec, ALL);
        QuickLayout d = QuickDefaults.of(spec, ALL);
        QuickLayout l = new QuickLayout(1, Density.LIST, d.items, Arrays.asList("lamp", "eye"));
        QuickLayout back = QuickLayoutJson.parse(QuickLayoutJson.write(l));
        ok("json: round trip", back != null && back.sameMenu(l) && back.seen.equals(l.seen) && back.version == 1);
        ok("json: an id with a quote survives", QuickLayoutJson.parse(QuickLayoutJson.write(new QuickLayout(1, Density.TILES,
                Collections.singletonList(new Placement("a\"b\\c", Presentation.WIDE)), Collections.emptyList()))).items.get(0).id.equals("a\"b\\c"));
        String w = QuickLayoutJson.write(l);
        for (String bad : new String[]{null, "", "  ", "not json", "[]", "{}", w.substring(0, w.length() / 2),
                w.replace("\"v\": 1", "\"v\": 2"), w.replace("\"v\": 1", "\"v\": 0"), w.replace("\"v\": 1", "\"v\": \"1\""),
                w.replace("\"density\": \"list\"", "\"density\": \"grid\""), w.replace("\"items\": [", "\"items\": [7, "),
                w.replace("{\"id\": \"network\"", "{\"id\": \"\""), "{\"v\": 1, \"density\": \"tiles\"}"}) {
            ok("json: unreadable gives the default, never overwritten (" + bad + ")", QuickLayoutJson.parse(bad) == null);
        }
        QuickLayout odd = QuickLayoutJson.parse(w.replace("\"as\": \"round\"}", "\"as\": \"hexagon\"}"));
        ok("json: an unknown look reads as none", odd != null && odd.items.get(0).as == null);
        QuickLayout fixed = QuickLayoutJson.reconcile(odd, cat, d);
        ok("json: an unknown look becomes the item's preferred one", fixed.items.get(0).as == cat.get(fixed.items.get(0).id).preferred());
    }

    static void repair(MenuSpec spec) {
        QuickCatalog cat = QuickCatalog.of(spec, ALL), noMirror = QuickCatalog.of(spec, id -> !"mirroring".equals(id));
        QuickLayout d = QuickDefaults.of(spec, ALL);
        List<Placement> items = new ArrayList<>(Arrays.asList(new Placement("lens.warp", Presentation.WIDE),
                new Placement("eye", Presentation.SLIDER), new Placement("mirroring", Presentation.SMALL),
                new Placement("eye", Presentation.WIDE), new Placement("input", Presentation.WIDE)));
        QuickLayout stored = new QuickLayout(1, Density.TILES, items, Collections.emptyList());
        QuickLayout shown = QuickLayoutJson.reconcile(stored, cat, d);
        ok("repair: an id the profile lacks is not shown", !shown.has("lens.warp"));
        ok("repair: a look the item lacks becomes its preferred one", shown.get("eye").as == Presentation.WIDE);
        ok("repair: a duplicate after the first goes", shown.items.stream().filter(p -> p.id.equals("eye")).count() == 1);
        ok("repair: All settings and the pencil come back", shown.get("general") != null && shown.get("edit") != null
                && shown.get("general").as == Presentation.ROUND && shown.get("edit").as == Presentation.ROUND);
        QuickLayout gone = QuickLayoutJson.reconcile(stored, noMirror, d);
        ok("repair: an item unusable now is hidden", !gone.has("mirroring") && shown.has("mirroring"));
        QuickLayout kept = QuickLayoutJson.toStore(stored, gone, noMirror);
        ok("repair: the stored value keeps hidden ids after the item they followed " + kept.items,
                kept.indexOf("lens.warp") == 0 && kept.indexOf("mirroring") == kept.indexOf("eye") + 1);
        ok("repair: storing marks every catalogue id seen", kept.seen.containsAll(noMirror.ids()));
        // too tall: the card loses items from its end, pinned ones stay
        List<Placement> many = new ArrayList<>();
        for (QuickItem it : cat.items()) many.add(new Placement(it.id, it.allows(Presentation.SLIDER) ? Presentation.SLIDER : Presentation.WIDE));
        QuickLayout tall = QuickLayoutJson.reconcile(new QuickLayout(1, Density.TILES, many, Collections.emptyList()), cat, d);
        ok("repair: a menu too tall is cut until it fits " + QuickPacker.pack(tall).height, QuickPacker.pack(tall).fits()
                && tall.has("general") && tall.has("edit") && tall.items.size() < many.size());
        QuickLayout list = QuickLayoutJson.reconcile(new QuickLayout(1, Density.LIST, d.items, Collections.emptyList()), cat, d);
        ok("repair: a list too long is cut until it fits", QuickPacker.pack(list).fits() && list.has("general"));
        ok("new: nothing stored, nothing marked", !QuickEditor.isNew(null, "sound.volume"));
        ok("new: a stored menu marks what the user never saw", QuickEditor.isNew(kept.seen(Collections.singletonList("lamp")), "sound.volume")
                && !QuickEditor.isNew(kept.seen(Collections.singletonList("sound.volume")), "sound.volume"));
    }

    static void capacity(MenuSpec z9x, MenuSpec x50) {
        QuickCatalog cat = QuickCatalog.of(z9x, ALL);
        QuickLayout d = QuickDefaults.of(z9x, ALL);
        QuickEditor e = new QuickEditor(cat, d, d);
        ok("capacity: the volume slider fits the Z9X card", e.add("sound.volume") == EditResult.DONE
                && e.look("sound.volume") == Presentation.SLIDER && QuickPacker.pack(e.layout()).height == 948);
        ok("capacity: a second slider is refused (taller than the screen)", e.add("picture.pq.contrast") == EditResult.FULL
                && !e.inMenu("picture.pq.contrast"));
        ok("capacity: the list does not take 16 items", e.setDensity(Density.LIST) == EditResult.FULL);
        ok("capacity: a smaller look still works", e.setLook("sound.volume", Presentation.SMALL) == EditResult.DONE);
        ok("capacity: the volume as a small tile opens a new row of its own", QuickPacker.pack(e.layout()).lineOf("sound.volume") == 5);
        ok("capacity: the contrast slider is still too much", e.add("picture.pq.contrast") == EditResult.FULL);
        ok("capacity: without the volume the contrast slider fits", e.remove("sound.volume") == EditResult.DONE
                && e.add("picture.pq.contrast") == EditResult.DONE);
        QuickEditor x = new QuickEditor(QuickCatalog.of(x50, ALL), QuickDefaults.of(x50, ALL), QuickDefaults.of(x50, ALL));
        ok("capacity: X50 (light and iris sliders) has no room for the volume slider", x.add("sound.volume") == EditResult.FULL);
        QuickEditor s = new QuickEditor(cat, d, d);
        for (String id : new String[]{"keystone.ak_now", "keystone", "zoom", "focus", "bluetooth", "picture.3d", "game",
                "mirroring", "network", "theme"}) ok("remove " + id, s.remove(id) == EditResult.DONE);
        ok("remove: projection gave its place to mirroring, not in the menu", s.remove("projection") == EditResult.UNKNOWN);
        ok("capacity: a short menu becomes a list", s.setDensity(Density.LIST) == EditResult.DONE);
        ok("list: one item per row", QuickPacker.pack(s.layout()).lines.size() == 5);
        ok("pinned: All settings stays", s.remove("general") == EditResult.PINNED && s.remove("edit") == EditResult.PINNED);
        ok("pinned: All settings may be a footer button, never a tile", s.setLook("general", Presentation.FOOTER) == EditResult.DONE
                && s.setLook("general", Presentation.WIDE) == EditResult.UNKNOWN && QuickPacker.pack(s.layout()).footer.size() == 1);
        QuickEditor circles = new QuickEditor(cat, d, d);
        EditResult five = circles.setLook("eye", Presentation.ROUND), six = circles.setLook("bluetooth", Presentation.ROUND);
        ok("capacity: five circles fit, a sixth is refused", five == EditResult.DONE && six == EditResult.FULL
                && circles.layout().items.stream().filter(p -> p.as == Presentation.ROUND).count() == 5);
        // squares stay square, rows hold four units
        for (QuickPacker.Line l : QuickPacker.pack(d).lines) {
            if (l.kind != LineKind.TILES) continue;
            int w = 0, wides = 0;
            for (Placement p : l.cells) { w += p.as == Presentation.WIDE ? 272 : 120; if (p.as == Presentation.WIDE) wides++; }
            w += 16 * (l.cells.size() - 1);
            ok("row " + l + ": 544 wide, one wide tile at most", w <= 544 && wides <= 1);
        }
        QuickLayout four = new QuickLayout(1, Density.TILES, Arrays.asList(new Placement("sound", Presentation.SMALL),
                new Placement("focus", Presentation.SMALL), new Placement("eye", Presentation.SMALL),
                new Placement("bluetooth", Presentation.SMALL), new Placement("input", Presentation.SMALL)), Collections.emptyList());
        QuickPacker.Line l4 = QuickPacker.pack(four).lines.get(0);
        ok("a fifth small tile opens the next row", QuickPacker.pack(four).lines.size() == 2 && QuickPacker.pack(four).lines.get(1).height == 120);
        ok("row of four small tiles: 124 px squares " + l4, l4.cells.size() == 4 && l4.four() && l4.height == 124);
    }

    static void moves(MenuSpec spec) {
        QuickCatalog cat = QuickCatalog.of(spec, ALL);
        QuickLayout d = QuickDefaults.of(spec, ALL);
        QuickEditor e = new QuickEditor(cat, d, d);
        int at = e.layout().indexOf("sound");
        ok("move: right after the next tile", e.move("sound", QuickEditor.RIGHT) == EditResult.DONE
                && e.layout().indexOf("sound") == at + 1 && e.layout().indexOf("focus") == at);
        ok("move: left back", e.move("sound", QuickEditor.LEFT) == EditResult.DONE && e.layout().sameMenu(d));
        ok("move: up into the row above", e.move("sound", QuickEditor.UP) == EditResult.DONE
                && QuickPacker.pack(e.layout()).lineOf("sound") == 0);
        ok("move: down again", e.move("sound", QuickEditor.DOWN) == EditResult.DONE
                && QuickPacker.pack(e.layout()).lineOf("sound") == 1);
        QuickEditor w = new QuickEditor(cat, d, d);
        String firstWide = null, secondWide = null;
        for (QuickPacker.Line ln : QuickPacker.pack(d).lines) {
            for (Placement p : ln.cells) {
                if (p.as != Presentation.WIDE) continue;
                if (firstWide == null) firstWide = p.id;
                else if (secondWide == null && QuickPacker.pack(d).lineOf(p.id) == QuickPacker.pack(d).lineOf(firstWide) + 1) secondWide = p.id;
            }
        }
        if (secondWide != null) {
            int lines = QuickPacker.pack(d).lines.size();
            ok("move: a wide tile up swaps with the wide tile above, no hole", w.move(secondWide, QuickEditor.UP) == EditResult.DONE
                    && QuickPacker.pack(w.layout()).lineOf(secondWide) == 0 && QuickPacker.pack(w.layout()).lineOf(firstWide) == 1
                    && QuickPacker.pack(w.layout()).lines.size() == lines);
        }
        QuickEditor r = new QuickEditor(cat, d, d);
        ok("move: a circle only among circles", r.move("general", QuickEditor.LEFT) == EditResult.DONE
                && r.layout().indexOf("general") < r.layout().indexOf("edit") && r.move("general", QuickEditor.UP) == EditResult.SAME);
        ok("move: the first circle stays first", r.move("network", QuickEditor.LEFT) == EditResult.SAME);
        QuickEditor back = new QuickEditor(cat, d, r.layout());
        back.reset();
        ok("reset: back to the default, the stored value goes", back.changed() && back.wasReset() && back.toStore(r.layout()) == null
                && back.layout().sameMenu(d));
        QuickEditor again = new QuickEditor(cat, d, r.layout());
        again.reset();
        again.remove("eye");
        ok("reset then a change: stored again", again.toStore(null) != null && !again.toStore(null).has("eye"));
        QuickEditor none = new QuickEditor(cat, d, d);
        ok("no change: nothing to store", !none.changed());
    }

    static void catalogue(MenuSpec z9x, MenuSpec x50) {
        QuickCatalog z = QuickCatalog.of(z9x, ALL), x = QuickCatalog.of(x50, ALL);
        ok("catalogue: the iris only where the profile has it", x.has("picture.iris") && !z.has("picture.iris"));
        ok("catalogue: the volume on every model, never in the default", z.has("sound.volume") && x.has("sound.volume")
                && !QuickDefaults.of(z9x, ALL).has("sound.volume") && z.get("sound.volume").group.equals("sound"));
        ok("catalogue: mirroring only with its receiver", !QuickCatalog.of(z9x, id -> !"mirroring".equals(id)).has("mirroring"));
        ok("catalogue: the picture sliders of the profile", z.has("picture.pq.contrast") && z.get("picture.pq.contrast").kind == ItemKind.SLIDER);
        ok("catalogue: the pencil and All settings are pinned", z.get("edit").pinned() && z.get("general").pinned());
        ok("catalogue: the light level is a slider under Picture", z.get("lamp").kind == ItemKind.SLIDER && z.get("lamp").group.equals("picture"));
        ok("catalogue: eye protection is a choice under General", z.get("eye").kind == ItemKind.CHOICE && z.get("eye").group.equals("general"));
    }

    /**
     * 2026-11-24: every setting of the model's full menu can be put into the quick menu, the catalogue from one
     * place. Its full menu rows equal the quick capable rows of the full menu as test/snapshots/&lt;device&gt;.txt
     * records it (MenuSpecTest holds that file to MenuSpec): each row of each All settings category, the rows of a page
     * after the page row, the picture sliders one by one, no readout and no light level; nothing else beyond the card's
     * own items and the volume. The editor lists every item, a developer tool never as a circle of the top row.
     */
    static void everyRow(File gsi, String dev, MenuSpec spec) throws Exception {
        QuickCatalog cat = QuickCatalog.of(spec, ALL);
        List<String> want = fullMenuOf(new File(gsi, "apps/core/lumen-menu/test/snapshots/" + dev + ".txt"));
        List<String> missing = new ArrayList<>(), extra = new ArrayList<>(), card = new ArrayList<>(), unlisted = new ArrayList<>();
        for (String id : want) if (!cat.has(id) || cat.get(id).section == null) missing.add(id);
        QuickLayout d = QuickDefaults.of(spec, ALL);
        for (MenuSpec.Tile t : spec.round) card.add(t.id);
        for (List<MenuSpec.Tile> row : spec.rows) for (MenuSpec.Tile t : row) { card.add(t.id); card.add(t.orElse); }
        for (MenuSpec.Tile t : spec.sliders) card.add(t.id);
        for (QuickItem i : cat.items()) {
            boolean own = card.contains(i.id) || d.has(i.id) || MenuSpec.EVERY_MODEL.contains(FeatureId.of(i.id))
                    || MenuSpec.QUICK_ONLY.contains(FeatureId.of(i.id)) && spec.profile.has(FeatureId.of(i.id));
            if (!want.contains(i.id) && !own || i.section != null && !want.contains(i.id)) extra.add(i.id);
            if (!cat.group(i.group).contains(i) || !cat.groups().contains(i.group)) unlisted.add(i.id);
        }
        ok(dev + ": the catalogue has every quick capable row of the full menu " + missing, missing.isEmpty() && !want.isEmpty());
        ok(dev + ": nothing in the catalogue beyond the full menu, the card, the volume and picture off " + extra, extra.isEmpty());
        ok(dev + ": the editor lists every item of the catalogue " + unlisted, unlisted.isEmpty());
        ok(dev + ": the full menu rows of the catalogue are QuickCatalog.fullMenu", QuickCatalog.fullMenu(spec).size() == want.size());
        boolean devRound = false, info = false, absent = false;
        for (QuickItem i : cat.items()) {
            FeatureId f = FeatureId.of(i.id);
            if (MenuSpec.DEVELOPER.equals(i.section) && i.allows(Presentation.ROUND)) devRound = true;
            if (f != null && f.kind == org.lumen.menu.FeatureKind.INFO) info = true;
            if (f != null && i.section != null && !spec.profile.has(f.parent != null ? f.parent : f)) absent = true;
        }
        ok(dev + ": a developer tool is never a circle of the top row", !devRound);
        ok(dev + ": a readout is no tile", !info);
        ok(dev + ": only rows the profile has", !absent);
        QuickLayout top = topOnly(d);
        for (QuickItem i : cat.items()) {
            if (i.section == null || d.has(i.id)) continue;
            QuickEditor e = new QuickEditor(cat, d, top);
            if (e.add(i.id) != EditResult.DONE || !e.layout().has(i.id) || e.layout().get(i.id).as != i.preferred()) {
                ok(dev + ": " + i.id + " can be added in its preferred look", false);
            }
            boolean tile = i.kind == ItemKind.TOGGLE || i.kind == ItemKind.CHOICE, slider = i.kind == ItemKind.SLIDER;
            if (tile && !(i.preferred() == Presentation.SMALL || i.preferred() == Presentation.WIDE)
                    || slider && i.preferred() != Presentation.SLIDER) ok(dev + ": " + i.id + " a tile, a range a slider row", false);
        }
    }

    /** The default with only its top row (room for any item on every model; the full card refuses with a note). */
    static QuickLayout topOnly(QuickLayout d) {
        List<Placement> keep = new ArrayList<>();
        for (Placement p : d.items) if (p.as == Presentation.ROUND) keep.add(p);
        return d.with(keep);
    }

    /**
     * The quick capable rows of a recorded full menu (test/snapshots/&lt;device&gt;.txt): from each category of its
     * "all settings:" line, each row of that section, a page row's own page after it, "slider picture.pq items [..]"
     * as one slider per item, a row at the bottom of an output page; no info row and no light level.
     */
    static List<String> fullMenuOf(File snap) throws Exception {
        java.util.Map<String, List<String[]>> blocks = new java.util.LinkedHashMap<>();
        List<String> cats = new ArrayList<>();
        List<String[]> cur = null;
        for (String line : Files.readAllLines(snap.toPath(), StandardCharsets.UTF_8)) {
            if (line.startsWith("all settings: ")) cats.addAll(Arrays.asList(line.substring(14).trim().split(" ")));
            if (!line.startsWith(" ")) {
                String[] w = line.split(" +");
                cur = (w[0].equals("section") || w[0].equals("page")) && w.length > 1 ? new ArrayList<>() : null;
                if (cur != null) blocks.put(w[0] + " " + w[1], cur);
                continue;
            }
            if (cur == null || line.trim().startsWith("--")) continue;
            String t = line.trim().replaceFirst("^output page ", "page ").replaceFirst("^at its bottom ", "choice ");
            String[] w = t.split(" +");
            if (w.length > 1) cur.add(w.length > 3 && "items".equals(w[2]) ? new String[]{w[0], w[1], t} : new String[]{w[0], w[1]});
        }
        List<String> out = new ArrayList<>();
        for (String c : cats) fullRows(blocks, blocks.get("section " + c), out);
        return out;
    }

    private static void fullRows(java.util.Map<String, List<String[]>> blocks, List<String[]> rows, List<String> out) {
        if (rows == null) return;
        for (String[] r : rows) {
            if ("info".equals(r[0]) || "lamp".equals(r[1])) continue;
            if (r.length > 2) {
                String items = r[2].substring(r[2].indexOf('[') + 1, r[2].indexOf(']'));
                for (String it : items.split(", ")) if (!out.contains(r[1] + "." + it)) out.add(r[1] + "." + it);
                continue;
            }
            if (!out.contains(r[1])) out.add(r[1]);
            if ("page".equals(r[0])) fullRows(blocks, blocks.get("page " + r[1]), out);
        }
    }

    /** MEMC and dynamic black as tiles, the model rules, the stored value across models, the search. */
    static void anySetting(File gsi, MenuSpec z9x, MenuSpec x50) throws Exception {
        QuickCatalog z = QuickCatalog.of(z9x, ALL), x = QuickCatalog.of(x50, ALL);
        ok("any setting: MEMC is a choice of Picture on Z9X and X50", z.get("picture.memc") != null
                && z.get("picture.memc").kind == ItemKind.CHOICE && "picture".equals(z.get("picture.memc").section)
                && x.get("picture.memc") != null && "picture".equals(x.get("picture.memc").group));
        ok("any setting: dynamic black is a switch of Picture on X50", x.get("picture.dynamic_black") != null
                && x.get("picture.dynamic_black").kind == ItemKind.TOGGLE && "picture".equals(x.get("picture.dynamic_black").section));
        ok("any setting: no dynamic black on Z9X (its profile has none)", !z.has("picture.dynamic_black"));
        ok("any setting: the X50 dynamic black page and its rows", x.has("picture.db_page") && x.has("picture.db_page.switch")
                && x.get("picture.db_page.switch").section.equals("picture"));
        ok("any setting: a slider row of a page (white balance)", !x.has("picture.wb2.red_gain")
                || x.get("picture.wb2.red_gain").kind == ItemKind.SLIDER);
        String j = new String(Files.readAllBytes(new File(gsi, "devices/g0111/menu.json").toPath()), StandardCharsets.UTF_8);
        MenuProfile noMemc = MenuProfile.parse(org.lumen.menu.MenuSpecTest.without(j, "picture.memc"));
        ok("any setting: the profile without MEMC parses " + noMemc.problems(), noMemc.ok());
        QuickCatalog n = QuickCatalog.of(MenuSpec.of(noMemc), ALL);
        ok("any setting: a model without MEMC does not offer it", !n.has("picture.memc") && n.has("picture.dynamic_black"));
        ok("any setting: MEMC only while the menu has its row now",
                !QuickCatalog.of(x50, ALL, id -> !"picture.memc".equals(id)).has("picture.memc"));
        QuickLayout d = QuickDefaults.of(x50, ALL);
        QuickEditor full = new QuickEditor(x, d, d);
        EditResult fr = full.add("picture.memc");
        ok("any setting: a card with no room refuses MEMC with the note (CONTRACT 22), no tile goes (" + fr + ")",
                fr == EditResult.DONE || fr == EditResult.FULL && full.layout().sameMenu(d));
        QuickEditor e = new QuickEditor(x, d, topOnly(d));
        ok("any setting: MEMC added", e.add("picture.memc") == EditResult.DONE && e.layout().has("picture.memc"));
        ok("any setting: dynamic black added", e.add("picture.dynamic_black") == EditResult.DONE);
        QuickLayout stored = e.toStore(null);
        QuickLayout back = QuickLayoutJson.reconcile(QuickLayoutJson.parse(QuickLayoutJson.write(stored)), x, d);
        ok("any setting: order and looks survive the stored value (the settings backup holds it)",
                back.has("picture.memc") && back.has("picture.dynamic_black")
                        && back.indexOf("picture.memc") < back.indexOf("picture.dynamic_black") && back.sameMenu(stored));
        QuickLayout onZ = QuickLayoutJson.reconcile(QuickLayoutJson.parse(QuickLayoutJson.write(stored)), z,
                QuickDefaults.of(z9x, ALL));
        ok("any setting: an X50 menu restored on Z9X keeps MEMC, drops dynamic black", onZ.has("picture.memc")
                && !onZ.has("picture.dynamic_black"));
        QuickLayout onN = QuickLayoutJson.reconcile(QuickLayoutJson.parse(QuickLayoutJson.write(stored)), n, d);
        ok("any setting: on a model without MEMC the stored tile is dropped", !onN.has("picture.memc") && onN.has("picture.dynamic_black"));
        ok("any setting: remove", e.remove("picture.memc") == EditResult.DONE && !e.layout().has("picture.memc"));
        ok("search: case and yo ignored", QuickCatalog.matches("Динамический чёрный", "ЧЕРН") && QuickCatalog.matches("MEMC", "memc"));
        ok("search: every word", QuickCatalog.matches("Dynamic black", "dyn bla") && !QuickCatalog.matches("Dynamic black", "dyn white"));
        ok("search: empty finds nothing", !QuickCatalog.matches("MEMC", " ") && !QuickCatalog.matches(null, "m"));
        List<QuickItem> found = x.find("memc", id -> id.equals("picture.memc") ? "Motion smoothing" : "x");
        ok("search: the menu's own word finds MEMC whatever its name " + found, found.size() == 1 && found.get(0).id.equals("picture.memc"));
        List<QuickItem> black = x.find("Чёрн", id -> id.equals("picture.dynamic_black") ? "Динамический черный" : "x");
        ok("search: a part of the name finds dynamic black " + black, black.size() >= 1 && black.get(0).id.equals("picture.dynamic_black"));
        QuickEditor at = new QuickEditor(x, d, topOnly(d));
        at.add("picture.pq.contrast");
        at.add("picture.memc");
        ok("add: a new tile goes above the sliders", at.layout().indexOf("picture.memc") < at.layout().indexOf("picture.pq.contrast"));
        ok("editor: the groups in the rail order (General first, option B)", x.groups().indexOf("general") == 0
                && x.groups().indexOf("keystone") < x.groups().indexOf("picture"));
        List<QuickItem> pic = x.group("picture");
        ok("editor: MEMC, AI contrast and dynamic black in the list itself, no detour " + pic, pic.contains(x.get("picture.memc"))
                && pic.contains(x.get("picture.ai_contrast")) && pic.contains(x.get("picture.dynamic_black")));
        ok("editor: a switch comes as a small tile, a choice as a wide one, a range as a slider row",
                x.get("picture.ai_contrast").preferred() == Presentation.SMALL && x.get("picture.memc").preferred() == Presentation.WIDE
                        && x.get("picture.black_tuning").preferred() == Presentation.SLIDER);
        QuickEditor three = new QuickEditor(x, d, d);
        for (String id : new String[]{"game", "mirroring", "eye"}) three.remove(id);
        ok("X50: MEMC, dynamic black and AI contrast fit in place of the last tile row",
                three.add("picture.memc") == EditResult.DONE && three.add("picture.dynamic_black") == EditResult.DONE
                        && three.add("picture.ai_contrast") == EditResult.DONE && QuickPacker.pack(three.layout()).fits());
        QuickLayout kept = QuickLayoutJson.reconcile(QuickLayoutJson.parse(QuickLayoutJson.write(three.toStore(null))), x, d);
        ok("X50: the three tiles survive the stored value", kept.has("picture.memc") && kept.has("picture.dynamic_black")
                && kept.has("picture.ai_contrast") && kept.sameMenu(three.layout()));
        three.reset();
        ok("X50: reset gives the default card and stores nothing", three.layout().sameMenu(d) && three.toStore(null) == null);
    }
}
