package org.lumen.projector.panel;

import org.lumen.menu.Feature;
import org.lumen.menu.MenuProfile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * HB menu gates (apps/HbProjector panel/HbMenuGates) against the real profile devices/goat_elegant/menu.json, read by
 * the module's own parser: every profile feature has exactly one gate row (same order), the G0130 / G0148 / unknown
 * model / feature.xml-off gating, the game menu items, the option tokens of every table equal the profile's options,
 * the HB wire values (stock dapengse tree / allowed.tsv), the light range without the Boost step, and projection.
 *
 *   java -cp ... org.lumen.projector.panel.HbMenuGatesTest <devices/goat_elegant/menu.json>
 */
public final class HbMenuGatesTest {
    private static int pass, fail;

    private static void check(String what, boolean ok) {
        if (ok) pass++;
        else { fail++; System.out.println("FAIL " + what); }
    }

    private static void eq(String what, Object want, Object got) {
        check(what + ": want " + want + ", got " + got, want == null ? got == null : want.equals(got));
    }

    /** HbFeatures switches of a G0130 on 6.17.12 (every key the gates name ON). */
    private static final Predicate<String> G0130 = f -> true;
    /** G0148 / unknown model / unreadable feature.xml: HbFeatures.modelOff = BOOST and COLOR_TEMP off. */
    private static final Predicate<String> G0148 = f -> !f.equals("BOOST") && !f.equals("COLOR_TEMP");

    private static Predicate<String> without(String... off) {
        Set<String> s = new HashSet<>(Arrays.asList(off));
        return f -> !s.contains(f);
    }

    /** A feature waiting for the module: its option tokens are listed as "feature|token" under the profile's pending. */
    private static boolean pending(MenuProfile p, String feature) {
        for (String x : p.pending()) if (x.startsWith(feature + "|")) return true;
        return false;
    }

    public static void main(String[] a) throws Exception {
        String json = new String(Files.readAllBytes(Paths.get(a[0])), StandardCharsets.UTF_8);
        MenuProfile p = MenuProfile.parse(json);
        check("profile parses: " + p.problems(), p.ok());
        eq("device", "goat_elegant", p.device());

        // ---- one gate row per profile feature, in the profile order, every gate a feature id the module knows
        List<String> gateIds = new ArrayList<>(), shown = new ArrayList<>();
        for (String[] g : HbMenuGates.GATES) {
            gateIds.add(g[0]);
            if (!pending(p, g[0])) shown.add(g[0]);
        }
        eq("gate rows without the pending ones = profile features (order)", new ArrayList<>(p.features()), shown);
        eq("no gate row twice", gateIds.size(), new HashSet<>(gateIds).size());
        for (String id : gateIds) check("known feature " + id, Feature.known(id));
        for (String id : gateIds) check("gate row " + id + " is a profile feature or pending", p.has(id) || pending(p, id));

        // ---- presence per unit
        Set<String> all = HbMenuGates.implemented(G0130);
        eq("G0130: every gate row", new HashSet<>(gateIds), all);
        check("G0130: every profile feature", all.containsAll(p.features()));
        Set<String> g48 = HbMenuGates.implemented(G0148);
        check("G0148: no boost", !g48.contains("boost"));
        check("G0148: no colour temperature", !g48.contains("picture.color_temp"));
        check("G0148: light kept", g48.contains("lamp"));
        check("G0148: picture mode kept", g48.contains("picture.mode"));
        eq("G0148: exactly two fewer", all.size() - 2, g48.size());
        Set<String> noZoom = HbMenuGates.implemented(without("OPTICAL_ZOOM"));
        check("OPTICAL_ZOOM off: no zoom", !noZoom.contains("zoom"));
        eq("OPTICAL_ZOOM off: only zoom goes", all.size() - 1, noZoom.size());
        Set<String> noEye = HbMenuGates.implemented(without("EYE_PROTECTION"));
        check("EYE_PROTECTION off: no eye", !noEye.contains("eye"));
        Set<String> noAk = HbMenuGates.implemented(without("AUTO_KEYSTONE"));
        check("AUTO_KEYSTONE off: no AK tile, no AK at power-on",
                !noAk.contains("keystone.ak_now") && !noAk.contains("keystone.on_power"));
        Set<String> noFit = HbMenuGates.implemented(without("CURTAIN_FIT"));
        check("CURTAIN_FIT off: no fit now, no fit switch", !noFit.contains("keystone.fit_now") && !noFit.contains("keystone.curtain"));
        Set<String> noHdmi = HbMenuGates.implemented(without("HDMI"));
        check("HDMI off: no range, EDID, eARC", !noHdmi.contains("hdmi.range") && !noHdmi.contains("hdmi.edid")
                && !noHdmi.contains("sound.earc"));
        Set<String> noGame = HbMenuGates.implemented(without("GAME_MODE"));
        check("GAME_MODE off: no game rows", !noGame.contains("game.mode") && !noGame.contains("game.speed")
                && !noGame.contains("game.allm"));
        Set<String> noPic = HbMenuGates.implemented(without("PICTURE"));
        for (String f : new String[] {"picture.mode", "picture.pq", "picture.memc", "picture.pro", "picture.reset", "picture.reset_all"}) {
            check("PICTURE off: no " + f, !noPic.contains(f));
        }
        Set<String> none = HbMenuGates.implemented(f -> false);
        for (String[] g : HbMenuGates.GATES) check("all off: " + g[0] + (g[1] == null ? " kept (no switch)" : " gone"),
                none.contains(g[0]) == (g[1] == null));

        // ---- game menu items
        eq("game items G0130 = profile game_menu.items", p.gameItems(), new ArrayList<>(HbMenuGates.gameItems(G0130)));
        check("game: no super resolution", !HbMenuGates.gameItems(G0130).contains("sr"));
        check("game: no game sound", !HbMenuGates.gameItems(G0130).contains("snd"));
        check("game: GAME_MODE off = no game menu", HbMenuGates.gameItems(without("GAME_MODE")).isEmpty());
        check("game: GAME_BAR off = no game menu", HbMenuGates.gameItems(without("GAME_BAR")).isEmpty());
        check("game: GAME_VRR off = no vrr", !HbMenuGates.gameItems(without("GAME_VRR")).contains("vrr"));
        check("game: GAME_DARK_BOOST off = no dark", !HbMenuGates.gameItems(without("GAME_DARK_BOOST")).contains("dark"));
        eq("keys: gear long", "game_menu", p.gearLong());
        check("game menu enabled", p.gameMenu());

        // ---- every token table = the profile's options of its feature (same order)
        for (HbMenuGates.Table t : HbMenuGates.TABLES) {
            if (pending(p, t.feature)) {
                check("pending table " + t.feature + " is not a profile feature", !p.has(t.feature));
                for (String tok : t.wire.keySet()) check("pending token " + t.feature + "|" + tok, p.pending().contains(t.feature + "|" + tok));
                continue;
            }
            eq("tokens of " + t.feature, p.options(t.feature), new ArrayList<>(t.wire.keySet()));
            for (String tok : t.wire.keySet()) eq("round trip " + t.feature + " " + tok, tok, t.token(t.wire(tok)));
        }
        for (String f : p.features()) {
            Feature.Def d = Feature.def(f);
            if (d != null && d.kind == Feature.Kind.CHOICE && !f.equals("eye")) check("table for choice " + f, HbMenuGates.table(f) != null);
        }

        // ---- HB wire values (stock tree order, GmpfClient enums, allowed.tsv)
        eq("picture mode wires", Arrays.asList(10, 1, 9, 29, 26, 5), new ArrayList<>(HbMenuGates.PICTURE_MODE.wire.values()));
        eq("colour temperature wires", Arrays.asList(0, 16), new ArrayList<>(HbMenuGates.COLOR_TEMP.wire.values()));
        eq("aspect wires", Arrays.asList(2, 1, 0, 4), new ArrayList<>(HbMenuGates.ASPECT.wire.values()));
        eq("EDID wires (1.4, 2.0 only)", Arrays.asList(0, 1), new ArrayList<>(HbMenuGates.EDID.wire.values()));
        eq("sound mode wires (no Standard 20)", Arrays.asList(3, 2, 1, 4, 12), new ArrayList<>(HbMenuGates.SOUND_MODE.wire.values()));
        eq("game speed wires", Arrays.asList(1, 2), new ArrayList<>(HbMenuGates.GAME_SPEED.wire.values()));
        eq("game mode 0 off 1 on 2 auto", Arrays.asList(0, 1, 2), new ArrayList<>(HbMenuGates.GAME_MODE.wire.values()));
        eq("ALLM signal = true", Integer.valueOf(1), HbMenuGates.ALLM.wire("signal"));
        eq("unknown read-back shows nothing", null, HbMenuGates.PICTURE_MODE.token(4));
        eq("unknown token sends nothing", null, HbMenuGates.MEMC.wire("max"));
        eq("speed 0 shows as basic", "basic_hfr", HbMenuGates.gameSpeedToken(0));
        eq("speed 3 shows as top speed", "top_speed_hfr", HbMenuGates.gameSpeedToken(3));
        eq("speed unread", null, HbMenuGates.gameSpeedToken(null));
        eq("output bt kind", "BT", HbMenuGates.outputKind("bt"));
        eq("output kind back", "bt", HbMenuGates.outputToken("BT"));
        eq("output unknown", null, HbMenuGates.outputKind("hdmi"));

        // ---- light without the Boost step
        eq("light min", 1, p.number("lamp", "min", -1));
        eq("light max", 10, p.number("lamp", "max", -1));
        eq("Boost level", 11, p.number("boost", "level", -1));
        check("light 1..10 sent", HbMenuGates.lampValue(1) && HbMenuGates.lampValue(10));
        check("light 0 never sent", !HbMenuGates.lampValue(0));
        check("Boost step never sent as a light level", !HbMenuGates.lampValue(11));

        // ---- projection (stock ProjectionRepository)
        eq("auto flip on = auto", "auto", HbMenuGates.projectionToken(1, true));
        eq("front table", "table", HbMenuGates.projectionToken(0, false));
        eq("rear ceiling", "ceiling", HbMenuGates.projectionToken(3, false));
        eq("unread", null, HbMenuGates.projectionToken(null, false));
        eq("table keeps rear", 2, HbMenuGates.putModeFor("table", 3));
        eq("ceiling keeps front", 1, HbMenuGates.putModeFor("ceiling", 0));
        eq("auto is no put mode", -1, HbMenuGates.putModeFor("auto", 0));
        eq("rear on from front table", 2, HbMenuGates.putModeForRear(true, 0));
        eq("rear on from front ceiling", 3, HbMenuGates.putModeForRear(true, 1));
        eq("rear off from rear ceiling", 1, HbMenuGates.putModeForRear(false, 3));

        // ---- hints: only stock HB pictures
        eq("hint picture mode", "picture_mode", p.hint("picture.mode"));
        eq("hint rear", "rear_projection", p.hint("projection.rear"));
        check("no eye hint (the HB tree has none)", p.hint("eye") == null);

        System.out.println("HbMenuGatesTest: " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
