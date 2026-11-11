package org.lumen.projector.panel;

import org.lumen.menu.Feature;
import org.lumen.menu.MenuProfile;
import org.lumen.menu.MenuSpec;
import org.lumen.menu.MenuState;
import org.lumen.menu.ProjectorFeatures;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Play 6 menu rules (Play6MenuGates, plain Java) against the real profile devices/dragon_healthy/menu.json read by
 * the module's parser, and against the wire constants of hal/Play6Gmpf and game/GameMode (read from their source):
 *  - tokens: every option the profile offers (and every pending token) has a Play 6 wire value, one per token;
 *  - presence: every profile and pending id is implemented, eye only with the NUI reader (available), nothing unknown;
 *  - rule: in every picture mode, input, IGmpf2 and game state, a row is shown (not hidden) exactly where the
 *    stock tree has it, a shown value row always has its value, a hidden one never, and a write is let through
 *    exactly where the row is shown; the stock refusals before a press (blocked) and the stock questions (confirm);
 *  - ranges, projection and aspect mapping.
 * java ... Play6MenuGatesTest <menu.json> <Play6Gmpf.java> <GameMode.java>
 */
public final class Play6MenuGatesTest {
    private static int passed, failed;

    private static void check(boolean ok, String what) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL " + what);
        }
    }

    private static final String[] PQ = {"brightness", "contrast", "saturation", "sharpness"};
    /** Features whose value depends on the mode / input (refusal must follow the state). */
    private static final String[] GATED = {Feature.PICTURE_MODE, Feature.PICTURE_COLOR_TEMP, Feature.PICTURE_MEMC,
            Feature.PICTURE_DYNAMIC_CONTRAST, Feature.HDMI_RANGE, Feature.HDMI_EDID, Feature.PICTURE_ASPECT,
            Feature.GAME_MODE, Feature.FOCUS_SMART};

    public static void main(String[] a) throws Exception {
        MenuProfile p = MenuProfile.parse(new String(Files.readAllBytes(Paths.get(a[0])), StandardCharsets.UTF_8));
        check(p.ok(), "profile parses: " + p.problems());
        Map<String, Integer> gmpf = constants(a[1]);
        Map<String, Integer> game = constants(a[2]);
        wires(gmpf, game);
        tokens(p);
        presence(p);
        ownerRule();
        ranges();
        projection();
        aspect();
        hooks();
        System.out.println("play6 menu gates: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    /** {@code public static final int A = 1, B = 2;} of a source file. */
    private static Map<String, Integer> constants(String path) throws Exception {
        String src = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        Map<String, Integer> m = new HashMap<>();
        Matcher d = Pattern.compile("public static final int ([^;]+);").matcher(src);
        while (d.find()) {
            Matcher e = Pattern.compile("([A-Z0-9_]+)\\s*=\\s*(-?\\d+)").matcher(d.group(1));
            while (e.find()) m.put(e.group(1), Integer.parseInt(e.group(2)));
        }
        return m;
    }

    private static void same(Map<String, Integer> m, String name, int mine) {
        check(m.containsKey(name) && m.get(name) == mine, "wire " + name + ": gates " + mine + ", source " + m.get(name));
    }

    // ------------------------------------------------------------------ wire values = Play6Gmpf / GameMode

    private static void wires(Map<String, Integer> g, Map<String, Integer> game) {
        same(g, "PICTURE_AI", Play6MenuGates.PM_AI);
        same(g, "PICTURE_MOVIE", Play6MenuGates.PM_MOVIE);
        same(g, "PICTURE_SPORT", Play6MenuGates.PM_SPORT);
        same(g, "PICTURE_TV", Play6MenuGates.PM_TV);
        same(g, "PICTURE_CUSTOM", Play6MenuGates.PM_CUSTOM);
        same(g, "PICTURE_OFFICE", Play6MenuGates.PM_OFFICE);
        same(g, "PICTURE_PERFORMANCE", Play6MenuGates.PM_PERFORMANCE);
        same(g, "PICTURE_GAME", Play6MenuGates.PM_GAME);
        same(g, "SOURCE_HDMI1", Play6MenuGates.SOURCE_HDMI1);
        same(g, "ASPECT_4_3", Play6MenuGates.ASPECT_4_3);
        same(g, "ASPECT_16_9", Play6MenuGates.ASPECT_16_9);
        same(g, "ASPECT_AUTO", Play6MenuGates.ASPECT_AUTO);
        same(g, "ASPECT_POINT", Play6MenuGates.ASPECT_POINT);
        same(g, "ASPECT_ORIGINAL", Play6MenuGates.ASPECT_ORIGINAL);
        same(g, "EDID_14", Play6MenuGates.EDID_14);
        same(g, "EDID_20", Play6MenuGates.EDID_20);
        same(g, "EDID_14", Play6MenuGates.EDID.wire("compat"));             // compatible = HDMI 1.4
        same(g, "EDID_20", Play6MenuGates.EDID.wire("enhanced"));
        same(g, "ASPECT_4_3", Play6MenuGates.ASPECT.wire("r4_3"));
        same(g, "ASPECT_16_9", Play6MenuGates.ASPECT.wire("r16_9"));
        same(g, "ASPECT_AUTO", Play6MenuGates.ASPECT.wire("auto"));
        same(g, "ASPECT_ORIGINAL", Play6MenuGates.ASPECT.wire("original"));
        same(g, "HDMI_RANGE_AUTO", Play6MenuGates.HDMI_RANGE.wire("auto"));
        same(g, "HDMI_RANGE_LIMITED", Play6MenuGates.HDMI_RANGE.wire("limited"));
        String[][] sound = {{"ai", "SOUND_AI"}, {"music", "SOUND_MUSIC"}, {"movie", "SOUND_MOVIE"},
                {"karaoke", "SOUND_KARAOKE"}, {"sports", "SOUND_SPORTS"}, {"standard", "SOUND_STANDARD"}};
        for (String[] s : sound) same(g, s[1], Play6MenuGates.SOUND_MODE.wire(s[0]));
        check(Play6MenuGates.HDMI_RANGE.wire("full") == 1, "HDMI range full = 1 (635: 0 auto, 1 full, 2 limited)");
        for (String[] m : new String[][] {{"ai", "PICTURE_AI"}, {"movie", "PICTURE_MOVIE"}, {"sport", "PICTURE_SPORT"},
                {"tv", "PICTURE_TV"}, {"custom", "PICTURE_CUSTOM"}, {"office", "PICTURE_OFFICE"}, {"performance", "PICTURE_PERFORMANCE"}}) {
            same(g, m[1], Play6MenuGates.PICTURE_MODE.wire(m[0]));
        }
        check(Play6MenuGates.COLOR_TEMP.wire("cool") == 0 && Play6MenuGates.COLOR_TEMP.wire("standard") == 1
                && Play6MenuGates.COLOR_TEMP.wire("warm") == 2, "colour temperature wires (685: 0 cool, 1 standard, 2 warm, 3 custom)");
        same(game, "OFF", Play6MenuGates.GAME_OFF);
        same(game, "ON", Play6MenuGates.GAME_ON);
        same(game, "AUTO", Play6MenuGates.GAME_AUTO);
        check(Play6MenuGates.PICTURE_MODE.wire("custom") == Play6MenuGates.PM_CUSTOM, "picture custom wire");
        check(Play6MenuGates.COLOR_TEMP.wire("custom") == Play6MenuGates.CT_CUSTOM, "colour temperature custom wire");
    }

    // ------------------------------------------------------------------ tokens

    private static Play6MenuGates.Table table(String feature) {
        switch (feature) {
            case Feature.PICTURE_MODE: return Play6MenuGates.PICTURE_MODE;
            case Feature.PICTURE_COLOR_TEMP: return Play6MenuGates.COLOR_TEMP;
            case Feature.PICTURE_MEMC: return Play6MenuGates.MEMC;
            case Feature.HDMI_RANGE: return Play6MenuGates.HDMI_RANGE;
            case Feature.HDMI_EDID: return Play6MenuGates.EDID;
            case Feature.PICTURE_ASPECT: return Play6MenuGates.ASPECT;
            case Feature.SOUND_MODE: return Play6MenuGates.SOUND_MODE;
            case Feature.GAME_MODE: return Play6MenuGates.GAME_MODE;
            case Feature.SETTINGS_LAUNCHER: return Play6MenuGates.LAUNCHER;
            default: return null;
        }
    }

    /** True when the adapter can write {@code token} of {@code feature}. */
    private static boolean writable(String feature, String token) {
        Play6MenuGates.Table t = table(feature);
        if (t != null) return t.wire(token) != null;
        switch (feature) {
            case Feature.SOUND_OUTPUT: return Play6MenuGates.outputKind(token) != null;
            case Feature.PROJECTION_MODE: return "auto".equals(token) || Play6MenuGates.putModeFor(token, 0) >= 0;
            case Feature.EYE: return "on".equals(token) || "off".equals(token);
            default: return false;
        }
    }

    private static void tokens(MenuProfile p) {
        for (String f : p.features()) {
            Feature.Def d = Feature.def(f);
            if (d == null || d.kind != Feature.Kind.CHOICE) continue;
            List<String> opts = p.options(f);
            check(!opts.isEmpty(), f + ": options");
            for (String o : opts) check(writable(f, o), f + ": the adapter has no wire for option " + o);
        }
        for (String pend : p.pending()) {
            int c = pend.indexOf(':');
            if (c < 0) continue;
            check(writable(pend.substring(0, c), pend.substring(c + 1)), "pending " + pend + ": no wire");
        }
        for (Play6MenuGates.Table t : new Play6MenuGates.Table[] {Play6MenuGates.PICTURE_MODE, Play6MenuGates.COLOR_TEMP,
                Play6MenuGates.MEMC, Play6MenuGates.HDMI_RANGE, Play6MenuGates.EDID, Play6MenuGates.ASPECT,
                Play6MenuGates.SOUND_MODE, Play6MenuGates.GAME_MODE, Play6MenuGates.LAUNCHER}) {
            check(new LinkedHashSet<>(Arrays.asList(t.tokens)).size() == t.tokens.length, "table tokens unique " + Arrays.toString(t.tokens));
            Set<Integer> w = new LinkedHashSet<>();
            for (int x : t.wires) w.add(x);
            check(w.size() == t.wires.length, "table wires unique " + Arrays.toString(t.wires));
            for (int i = 0; i < t.tokens.length; i++) {
                check(t.token(t.wires[i]).equals(t.tokens[i]), "table round trip " + t.tokens[i]);
            }
            check(t.wire("nope") == null && t.token(-77) == null && t.token(null) == null && t.wire(null) == null,
                    "table refuses unknown " + Arrays.toString(t.tokens));
        }
        for (String o : new String[] {"auto", "speaker", "arc", "bt", "usb"}) {
            check(o.equals(Play6MenuGates.outputToken(Play6MenuGates.outputKind(o))), "output round trip " + o);
        }
        // the outputs connected now: only those, in the profile order, with the device names stock shows
        List<String> profileOrder = p.options(Feature.SOUND_OUTPUT);
        Play6MenuGates.Read r = read(1, 0, true, 0);
        r.outputKinds = Arrays.asList("AUTO", "SPEAKER", "USB", "BT", "WIRED");
        r.outputLabels.put("BT", "Soundcore");
        r.outputLabels.put("SPEAKER", "Speaker of the projector");
        r.outputKind = "BT";
        MenuState s = state(r);
        check(Arrays.asList("auto", "speaker", "bt", "usb").equals(s.options(Feature.SOUND_OUTPUT)), "connected outputs " + s.options(Feature.SOUND_OUTPUT));
        List<String> sub = new ArrayList<>(profileOrder);
        sub.retainAll(s.options(Feature.SOUND_OUTPUT));
        check(sub.equals(s.options(Feature.SOUND_OUTPUT)), "connected outputs in the profile order");
        check("Soundcore".equals(s.label(Feature.SOUND_OUTPUT, "bt")) && s.label(Feature.SOUND_OUTPUT, "speaker") == null
                && s.label(Feature.SOUND_OUTPUT, "usb") == null, "device name only for a named Bluetooth / USB / ARC output");
        check("bt".equals(s.get(Feature.SOUND_OUTPUT)), "stored output shown while connected");
        r.outputKind = "ARC";
        check("auto".equals(state(r).get(Feature.SOUND_OUTPUT)), "stored output that is gone shows as Auto");
        r.outputKinds = null;
        check(state(r).options(Feature.SOUND_OUTPUT) == null, "outputs not read: the profile's list");
        // aspect: Original only where it changes the picture
        r = read(1, 1, true, 0);
        r.hdmiInfo = new int[] {1920, 1080};
        check(Arrays.asList("auto", "r4_3", "r16_9").equals(state(r).options(Feature.PICTURE_ASPECT)), "no Original for a full panel signal");
        r.hdmiInfo = new int[] {1280, 720};
        check(state(r).options(Feature.PICTURE_ASPECT) == null, "Original for a smaller signal");
        for (String o : Arrays.asList("auto", "r4_3", "r16_9")) check(p.options(Feature.PICTURE_ASPECT).contains(o), "aspect subset of the profile " + o);
        check(Play6MenuGates.outputKind("wired") == null, "no wired headphone output on the Play 6");
    }

    // ------------------------------------------------------------------ presence

    private static void presence(MenuProfile p) {
        Set<String> on = Play6MenuGates.implemented();
        check(on.contains(Feature.EYE), "eye implemented (its row only where available)");
        for (boolean eye : new boolean[] {false, true}) for (boolean l : new boolean[] {false, true}) for (boolean bat : new boolean[] {false, true}) {
            String at = " [eye " + eye + " launcher " + l + " battery " + bat + "]";
            check(Play6MenuGates.available(Feature.EYE, eye, l, bat) == eye, "eye only with the NUI reader" + at);
            check(Play6MenuGates.available(Feature.SETTINGS_LAUNCHER, eye, l, bat) == l, "home screen choice only where it switches" + at);
            check(Play6MenuGates.available(Feature.LAMP_ECO, eye, l, bat) == bat, "power saving only on the battery model" + at);
            for (String f : on) {
                if (f.equals(Feature.EYE) || f.equals(Feature.SETTINGS_LAUNCHER) || f.equals(Feature.LAMP_ECO)) continue;
                check(Play6MenuGates.available(f, eye, l, bat), f + " available everywhere" + at);
            }
        }
        for (String f : p.features()) check(on.contains(f), f + ": in the profile, not implemented");
        Set<String> pend = new LinkedHashSet<>();
        for (String x : p.pending()) pend.add(x.contains(":") ? x.substring(0, x.indexOf(':')) : x);
        for (String f : pend) check(on.contains(f), "pending " + f + ": not implemented");
        for (String f : on) {
            check(Feature.def(f) != null || pend.contains(f), f + ": implemented, neither a module id nor pending");
            check(p.features().contains(f) || pend.contains(f), f + ": implemented, but not in the profile");
        }
        // absent on the Play 6 (no optical zoom, Boost, obstacle avoidance, curtain, ALLM, game menu)
        for (String f : new String[] {Feature.ZOOM, Feature.BOOST}) {
            check(!on.contains(f) && !p.features().contains(f), f + ": the Play 6 has none");
        }
    }

    // ------------------------------------------------------------------ rule

    private static Play6MenuGates.Read read(Integer pm, Integer src, boolean g2, int game) {
        Play6MenuGates.Read r = new Play6MenuGates.Read();
        r.pictureMode = pm;
        r.source = src;
        r.gmpf2 = g2;
        r.gameMode = game;
        r.lamp = 7;
        r.eco = false;
        r.soundEffect = 3;
        r.putMode = 0;
        r.autoReverse = false;
        r.eye = true;
        r.powerOnAk = true;
        r.moveAk = false;
        r.smartAf = true;
        r.memc = 2;
        r.colorTemp = 1;
        r.dynContrast = true;
        for (int i = 0; i < 4; i++) r.pq[i] = 50;
        r.aspect = 2;
        r.hdmiInfo = new int[] {1920, 1080};
        r.hdmiRange = 0;
        r.edid = 2;
        r.earc = false;
        r.outputKind = "SPEAKER";
        r.launcher = "lumen";
        return r;
    }

    private static MenuState state(Play6MenuGates.Read r) { return Play6MenuGates.state(r, name -> name); }

    /** The row of {@code f} is on screen: not hidden. */
    private static boolean shown(MenuState s, String f) { return !s.hidden(f); }

    private static void ownerRule() {
        List<Integer> modes = new ArrayList<>();
        modes.add(null);
        for (int i = 0; i <= 30; i++) modes.add(i);
        Integer[] sources = {null, 0, 1, 2};
        int combos = 0;
        for (Integer pm : modes) for (Integer src : sources) for (boolean g2 : new boolean[] {false, true})
            for (int game = -1; game <= 2; game++) for (Boolean eq : new Boolean[] {null, false, true}) {
                combos++;
                Play6MenuGates.Read r = read(pm, src, g2, game);
                r.eqOk = eq;
                MenuState s = state(r);
                String at = " [pm " + pm + " src " + src + " g2 " + g2 + " game " + game + " eq " + eq + "]";
                boolean custom = pm != null && pm == 3;
                boolean memc = pm != null && (pm == 1 || pm == 7 || pm == 3);
                boolean hdmi = src != null && src == 1;
                boolean ai = pm != null && pm >= 10 && pm <= 17;
                boolean knownMode = pm != null && (ai || pm == 1 || pm == 9 || pm == 7 || pm == 3 || pm == 25 || pm == 5);
                for (String it : PQ) check(shown(s, Feature.PICTURE_PQ + "." + it) == custom, "pq " + it + " only in Custom" + at);
                check(shown(s, Feature.PICTURE_COLOR_TEMP) == custom, "colour temperature only in Custom" + at);
                check(shown(s, Feature.PICTURE_DYNAMIC_CONTRAST) == (custom && g2), "dynamic contrast: Custom and IGmpf2" + at);
                check(shown(s, Feature.PICTURE_RESET) == custom, "picture reset only in Custom" + at);
                check(shown(s, Feature.PICTURE_MEMC) == memc, "motion compensation: Movie, TV, Custom" + at);
                check(shown(s, Feature.PICTURE_MODE) == knownMode, "picture mode: a stock mode, never Game" + at);
                check(("p6_pic_game_hint".equals(s.note(MenuSpec.PICTURE))) == (pm != null && pm == 4), "Game: the stock hint on the picture page" + at);
                if (ai) check("ai".equals(s.get(Feature.PICTURE_MODE)), "AI scene sub-mode shown as AI" + at);
                check(shown(s, Feature.HDMI_RANGE) == hdmi && shown(s, Feature.HDMI_EDID) == hdmi && shown(s, Feature.PICTURE_ASPECT) == hdmi,
                        "HDMI rows only on the HDMI input" + at);
                check(shown(s, Feature.GAME_MODE) == (hdmi && g2 && game >= 0), "game mode: HDMI, IGmpf2, read" + at);
                check(shown(s, Feature.FOCUS_SMART) == g2, "smart focus only with IGmpf2" + at);
                check(shown(s, Feature.SOUND_EQ) == !Boolean.FALSE.equals(eq), "equalizer entry hidden while it cannot work" + at);
                // always there (no mode rule)
                for (String f : new String[] {Feature.EYE, Feature.LAMP, Feature.SOUND_MODE, Feature.SOUND_EARC, Feature.SOUND_OUTPUT,
                        Feature.PROJECTION_MODE, Feature.PROJECTION_REAR, Feature.KEYSTONE_REALTIME, Feature.KEYSTONE_ON_POWER,
                        Feature.SETTINGS_LAUNCHER, Feature.LAMP_ECO}) {
                    check(shown(s, f) && s.has(f), f + " always shown with its value" + at);
                }
                // a shown value row has its value, a hidden one has none (never a value of another mode)
                List<String> rows = new ArrayList<>(Arrays.asList(Play6MenuGates.VALUE_ROWS));
                for (String it : PQ) rows.add(Feature.PICTURE_PQ + "." + it);
                for (String f : rows) check(s.has(f) != s.hidden(f), f + (s.has(f) ? ": a value on a hidden row" : ": a shown row without a value") + at);
                // a write is let through exactly where its row is shown
                for (String f : GATED) {
                    boolean let = Play6MenuGates.refusal(f, r) == null;
                    check(let == shown(s, f), "write " + f + (let ? " let through, row hidden" : " refused, row shown") + at);
                }
                for (String it : PQ) {
                    check((Play6MenuGates.refusal(Feature.PICTURE_PQ + "." + it, r) == null) == custom, "write pq " + it + at);
                }
                check((Play6MenuGates.refusal(Feature.PICTURE_RESET, r) == null) == custom, "picture reset write only in Custom" + at);
                if (pm != null && pm == 4) {
                    check("p6_pic_game_hint".equals(Play6MenuGates.refusal(Feature.PICTURE_MODE, r)), "Game: the stock hint" + at);
                }
                if (!hdmi) {
                    for (String f : new String[] {Feature.HDMI_RANGE, Feature.HDMI_EDID, Feature.PICTURE_ASPECT, Feature.GAME_MODE}) {
                        check("panel_game_hdmi_only".equals(Play6MenuGates.refusal(f, r)), f + ": HDMI note" + at);
                    }
                }
                // the stock refusals before a press
                ProjectorFeatures.Note gp = Play6MenuGates.blocked(MenuSpec.GAME, null, r);
                check(hdmi ? gp == null : gp != null && "panel_game_hdmi_only".equals(gp.res), "game page off HDMI: the stock note" + at);
                ProjectorFeatures.Note pmn = Play6MenuGates.blocked(Feature.PICTURE_MODE, "movie", r);
                check(pm != null && pm == 4 ? pmn != null && "p6_pic_game_hint".equals(pmn.res)
                        : (pmn == null) == (Play6MenuGates.refusal(Feature.PICTURE_MODE, r) == null
                        || Play6MenuGates.refusal(Feature.PICTURE_MODE, r).isEmpty()), "picture mode press" + at);
                for (String f : GATED) {
                    String no = Play6MenuGates.refusal(f, r);
                    ProjectorFeatures.Note n = Play6MenuGates.blocked(f, "x", r);
                    if (no != null && !no.isEmpty()) check(n != null && no.equals(n.res), f + ": blocked shows the refusal note" + at);
                    if (no == null) check(n == null, f + ": blocked although allowed" + at);
                }
            }
        check(combos == 32 * 4 * 2 * 4 * 3, "every combination walked");
        check(state(null).asMap().isEmpty() && state(null).hiddenSet().isEmpty(), "no read: no values, nothing hidden (not ready)");
        check(Play6MenuGates.blocked(MenuSpec.GAME, null, null) == null, "no read: no refusal (the menu's not ready path)");
        // nothing read in a read: no value, every value row hidden (its getter failed)
        Play6MenuGates.Read empty = new Play6MenuGates.Read();
        MenuState e = state(empty);
        for (String f : Play6MenuGates.VALUE_ROWS) check(!e.has(f) && e.hidden(f), f + ": shown without a value");
        check(Play6MenuGates.refusal(Feature.PICTURE_MODE, empty) != null, "picture mode write without a read");
        check(Play6MenuGates.refusal(Feature.PROJECTION_MODE, empty) != null && Play6MenuGates.refusal(Feature.PROJECTION_REAR, empty) != null,
                "projection write without a read");
    }

    // ------------------------------------------------------------------ ranges

    private static void ranges() {
        for (int v = -2; v <= 12; v++) {
            Play6MenuGates.Read r = read(3, 1, true, 0);
            r.lamp = v;
            MenuState s = state(r);
            check(s.has(Feature.LAMP) == (v >= 1 && v <= 10) && s.hidden(Feature.LAMP) == !(v >= 1 && v <= 10), "lamp " + v);
            check(Play6MenuGates.lampValue(v) == (v >= 1 && v <= 10), "lamp write " + v);
        }
        for (int v : new int[] {-1, 0, 100, 101}) {
            Play6MenuGates.Read r = read(3, 1, true, 0);
            r.pq[0] = v;
            MenuState s = state(r);
            boolean ok = v >= 0 && v <= 100;
            check(s.has(Feature.PICTURE_PQ + ".brightness") == ok && s.hidden(Feature.PICTURE_PQ + ".brightness") == !ok, "pq " + v);
        }
        Play6MenuGates.Read r = read(3, 1, true, 0);
        r.memc = 9;
        r.colorTemp = 8;
        r.soundEffect = 99;
        r.hdmiRange = 5;
        r.edid = 0;
        r.outputKind = "WIRED";
        MenuState s = state(r);
        for (String f : new String[] {Feature.PICTURE_MEMC, Feature.PICTURE_COLOR_TEMP, Feature.SOUND_MODE, Feature.HDMI_RANGE,
                Feature.HDMI_EDID, Feature.SOUND_OUTPUT}) {
            check(!s.has(f) && s.hidden(f), f + ": a value outside the stock list is shown");
        }
        for (int m = 0; m <= 30; m++) {
            boolean hdr = m == 10 || m == 1 || m == 9 || m == 7 || m == 3 || m == 25 || (m >= 10 && m <= 17);
            check(Play6MenuGates.hdrMode(m) == hdr, "HDR in mode " + m);
        }
        check(!Play6MenuGates.hdrMode(null) && !Play6MenuGates.memcMode(null) && !Play6MenuGates.custom(null), "no mode: no rows");
    }

    // ------------------------------------------------------------------ projection

    private static void projection() {
        check("auto".equals(Play6MenuGates.projectionToken(1, true)), "auto flip = auto");
        check("table".equals(Play6MenuGates.projectionToken(0, false)) && "table".equals(Play6MenuGates.projectionToken(2, false)), "table");
        check("ceiling".equals(Play6MenuGates.projectionToken(1, false)) && "ceiling".equals(Play6MenuGates.projectionToken(3, false)), "ceiling");
        check(Play6MenuGates.projectionToken(7, false) == null && Play6MenuGates.projectionToken(null, false) == null
                && Play6MenuGates.projectionToken(0, null) == null, "no put mode: no value");
        int[][] put = {{0, 0, 1}, {1, 0, 1}, {2, 2, 3}, {3, 2, 3}};        // cur, table, ceiling (rear side kept)
        for (int[] x : put) {
            check(Play6MenuGates.putModeFor("table", x[0]) == x[1], "table from " + x[0]);
            check(Play6MenuGates.putModeFor("ceiling", x[0]) == x[2], "ceiling from " + x[0]);
        }
        check(Play6MenuGates.putModeFor("auto", 0) < 0 && Play6MenuGates.putModeFor("rear", 0) < 0, "not a put mode");
        int[][] rear = {{0, 2, 0}, {2, 2, 0}, {1, 3, 1}, {3, 3, 1}};       // cur, rear on, rear off
        for (int[] x : rear) {
            check(Play6MenuGates.putModeForRear(true, x[0]) == x[1], "rear on from " + x[0]);
            check(Play6MenuGates.putModeForRear(false, x[0]) == x[2], "rear off from " + x[0]);
        }
        for (int pmode = 0; pmode <= 3; pmode++) {
            Play6MenuGates.Read r = read(1, 0, true, 0);
            r.putMode = pmode;
            check("on".equals(state(r).get(Feature.PROJECTION_REAR)) == (pmode >= 2), "rear value " + pmode);
        }
        Play6MenuGates.Read r = read(1, 0, true, 0);
        r.putMode = 9;
        check(!state(r).has(Feature.PROJECTION_REAR) && state(r).hidden(Feature.PROJECTION_REAR), "rear without a put mode");
    }

    // ------------------------------------------------------------------ aspect

    private static void aspect() {
        int[] small = {1280, 720}, full = {1920, 1080}, tiny = {320, 240};
        check(Play6MenuGates.originalFits(small) && !Play6MenuGates.originalFits(full) && !Play6MenuGates.originalFits(tiny)
                && !Play6MenuGates.originalFits(null), "original fits only a smaller signal");
        check("original".equals(Play6MenuGates.aspectToken(Play6MenuGates.ASPECT_POINT, true)), "point to point as Original");
        check("auto".equals(Play6MenuGates.aspectToken(Play6MenuGates.ASPECT_ORIGINAL, false)), "Original as Auto where it does not fit");
        check("original".equals(Play6MenuGates.aspectToken(Play6MenuGates.ASPECT_ORIGINAL, true)), "Original where it fits");
        check("r4_3".equals(Play6MenuGates.aspectToken(0, false)) && "r16_9".equals(Play6MenuGates.aspectToken(1, false))
                && "auto".equals(Play6MenuGates.aspectToken(2, false)), "aspect tokens");
        check("auto".equals(Play6MenuGates.aspectToken(9, true)) && Play6MenuGates.aspectToken(null, true) == null, "aspect repair");
    }

    // ------------------------------------------------------------------ the stock questions

    private static void asks(String what, ProjectorFeatures.Confirm c, String title, String msg, String ok) {
        check(c != null && title.equals(c.title) && msg.equals(c.message) && ok.equals(c.ok), what + ": " + c);
    }

    private static void hooks() {
        Play6MenuGates.Read r = read(1, 1, true, 0);
        asks("keystone restore", Play6MenuGates.confirm(Feature.KEYSTONE_RESTORE, null, null, false, false),
                "panel_reset_keystone", "panel_reset_keystone_msg", "panel_reset");
        asks("picture reset", Play6MenuGates.confirm(Feature.PICTURE_RESET, null, r, false, false),
                "panel_picture_reset", "p6_pic_reset_msg", "panel_reset");
        asks("Performance", Play6MenuGates.confirm(Feature.PICTURE_MODE, "performance", r, false, false),
                "panel_pm_performance", "panel_pm_performance_msg", "panel_switch");
        check(Play6MenuGates.confirm(Feature.PICTURE_MODE, "sport", r, false, false) == null, "another mode: no question");
        r.pictureMode = Play6MenuGates.PM_PERFORMANCE;
        check(Play6MenuGates.confirm(Feature.PICTURE_MODE, "performance", r, false, false) == null, "Performance already: no question");
        // projection: a flip asks, the same put mode does not
        r.putMode = 0;
        r.autoReverse = false;
        asks("ceiling", Play6MenuGates.confirm(Feature.PROJECTION_MODE, "ceiling", r, false, false),
                "panel_projection_mode", "panel_proj_confirm_flip", "panel_switch");
        asks("auto", Play6MenuGates.confirm(Feature.PROJECTION_MODE, "auto", r, false, false),
                "panel_projection_mode", "panel_proj_confirm_flip", "panel_switch");
        check(Play6MenuGates.confirm(Feature.PROJECTION_MODE, "table", r, false, false) == null, "table on table: no question");
        r.autoReverse = true;
        check(Play6MenuGates.confirm(Feature.PROJECTION_MODE, "table", r, false, false) == null, "auto off, same put mode: no question");
        check(Play6MenuGates.confirm(Feature.PROJECTION_MODE, "auto", r, false, false) == null, "auto on already: no question");
        asks("rear on", Play6MenuGates.confirm(Feature.PROJECTION_REAR, "on", r, false, false),
                "panel_projection_mode", "panel_proj_confirm_flip", "panel_switch");
        check(Play6MenuGates.confirm(Feature.PROJECTION_REAR, "off", r, false, false) == null, "rear off on front: no question");
        r.putMode = null;
        check(Play6MenuGates.confirm(Feature.PROJECTION_MODE, "ceiling", r, false, false) == null
                && Play6MenuGates.confirm(Feature.PROJECTION_REAR, "on", r, false, false) == null, "no put mode read: no question");
        // HDMI mode
        r = read(1, 1, true, 0);
        r.edid = Play6MenuGates.EDID_20;
        asks("HDMI mode", Play6MenuGates.confirm(Feature.HDMI_EDID, "compat", r, false, false), "hdmi_mode", "hdmi_mode_note", "panel_switch");
        check(Play6MenuGates.confirm(Feature.HDMI_EDID, "enhanced", r, false, false) == null, "same HDMI mode: no question");
        // home screen
        r.launcher = "lumen";
        asks("classic home", Play6MenuGates.confirm(Feature.SETTINGS_LAUNCHER, "classic", r, false, false),
                "launcher_row", "launcher_confirm_classic", "launcher_switch");
        r.launcher = "classic";
        asks("Lumen home", Play6MenuGates.confirm(Feature.SETTINGS_LAUNCHER, "lumen", r, false, false),
                "launcher_row", "launcher_confirm_lumen", "launcher_switch");
        check(Play6MenuGates.confirm(Feature.SETTINGS_LAUNCHER, "classic", r, false, false) == null, "same home screen: no question");
        // power saving: the first time each way
        r.eco = false;
        asks("power saving on", Play6MenuGates.confirm(Feature.LAMP_ECO, "on", r, false, false),
                "p6_eco_on_title", "p6_eco_on_msg", "p6_eco_turn_on");
        check(Play6MenuGates.confirm(Feature.LAMP_ECO, "on", r, true, false) == null, "power saving on asked once");
        r.eco = true;
        asks("power saving off", Play6MenuGates.confirm(Feature.LAMP_ECO, "off", r, true, false),
                "p6_eco_off_title", "p6_eco_off_msg", "p6_eco_turn_off");
        check(Play6MenuGates.confirm(Feature.LAMP_ECO, "off", r, true, true) == null, "power saving off asked once");
        // nothing else asks
        for (String f : new String[] {Feature.LAMP, Feature.SOUND_MODE, Feature.PICTURE_MEMC, Feature.HDMI_RANGE, Feature.GAME_MODE,
                Feature.EYE, Feature.FOCUS_AF_NOW, Feature.KEYSTONE_AK_NOW}) {
            check(Play6MenuGates.confirm(f, "on", r, false, false) == null, f + ": no stock question");
        }
    }
}
