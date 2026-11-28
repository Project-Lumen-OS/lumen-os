// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One device's menu profile, devices/&lt;codename&gt;/menu.json. It says which features the
 * projector really has, their option lists and ranges, which rows have a hint picture, what the gear key does and
 * whether the game menu exists. Read strictly: anything unknown is a problem, and a profile with problems shows no
 * menu (fail closed) and fails the host tests.
 */
public final class MenuProfile {
    public static final int SCHEMA = 1;

    /**
     * Hint pictures lumen-menu can draw (QpdTip). A profile names one only where the stock menu has that picture. The
     * focus guide has one per remote kind: the model's own remote decides it (devices/_remotes/focus_guide.tsv).
     */
    public static final Set<String> HINT_PICTURES = set("picture_mode", "rear_projection", "eye_follow", "eye_rest",
            "game_mode", "curtain_fit", "dynamic_black", "focus_guide_front", "focus_guide_side");
    /** What a short / long gear press may do. */
    public static final Set<String> GEAR_SHORT = set("quick_menu");
    public static final Set<String> GEAR_LONG = set("game_menu", "keystone_reset", "none");
    /**
     * What a short gear press does on the eye-protection mask (keys.eye_gear_short, optional): restore = give the
     * picture back (the default; Z9X and Play 6 stock), restore_off = also turn eye protection off (HB stock SystemUI).
     */
    public static final Set<String> EYE_GEAR_SHORT = set("restore", "restore_off");
    /** Game menu dock items in their fixed order (GmModel.ORDER) and controller themes (GmModel.TH_*). */
    public static final List<String> GAME_ITEMS = Collections.unmodifiableList(Arrays.asList(
            "lat", "vrr", "hfr", "fs", "pic", "cs", "dark", "sr", "ar", "so", "hdr", "snd", "aim", "fps"));
    /** Game menu colour spaces in menu order (GmModel.CS_TOKENS); a profile's game_menu.cs is a subset in this order. */
    public static final List<String> GAME_CS = Collections.unmodifiableList(Arrays.asList(
            "native", "bt2020", "adobe", "p3", "bt709"));
    /** Boost hint texts a profile can name (features.boost.summary): what Boost means on that model's stock. */
    public static final Set<String> BOOST_SUMMARIES = set("paler", "brighter");
    /**
     * Stock game sound values in menu order (wire 190 + index); a profile's game_menu.sound is a prefix of this list
     * with at least the first three (Z9X / HB stock: off, surround, story; X50 stock adds clarity 193, balanced 194).
     */
    public static final List<String> GAME_SOUNDS = Collections.unmodifiableList(Arrays.asList(
            "off", "surround", "story", "clarity", "balanced"));
    public static final Set<String> GAME_THEMES = set("ps", "xbox", "switch", "pad", "apple", "windows", "computer");
    public static final Set<String> PQ_ITEMS = set("brightness", "contrast", "saturation", "sharpness");

    private static final Set<String> TOP = set("schema", "device", "model", "adapter", "features", "hints", "keys",
            "game_menu", "sections", "pending", "note");
    private static final Set<String> PARAMS = set("options", "min", "max", "level", "items", "note", "summary", "support", "jack",
            "name", "iface", "chart");
    /** The vendor hotspot interface of network.hotspot (ro.vendor.wifi.sap.interface of the stock dumps: ap0). */
    private static final java.util.regex.Pattern IFACE = java.util.regex.Pattern.compile("[a-z]+[0-9]+");
    private static final Set<String> GAME_KEYS = set("enabled", "items", "themes", "cec_detect", "note", "gated", "sound",
            "vrr_top_speed", "cs", "ar_wide");

    private final String device, model, adapter;
    private final LinkedHashMap<FeatureId, Map<String, Object>> features = new LinkedHashMap<>();
    private final LinkedHashMap<FeatureId, String> hints = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> keys = new LinkedHashMap<>();
    /** sections: per section the model's stock order, {header-or-null, rows...} groups as MenuSpec's table, and its source. */
    private final LinkedHashMap<String, Object[][]> orders = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> orderSources = new LinkedHashMap<>();
    /** The profile's own sub pages (sections.&lt;s&gt; {"page": ...} rows): their groups in MenuSpec's table form. */
    private final LinkedHashMap<FeatureId, Object[][]> pageOrders = new LinkedHashMap<>();
    private final List<String> gameItems = new ArrayList<>(), gameThemes = new ArrayList<>(), pending = new ArrayList<>(),
            gameGated = new ArrayList<>(), gameSounds = new ArrayList<>(GAME_SOUNDS.subList(0, 3)),
            gameCs = new ArrayList<>(GAME_CS);
    private final List<Integer> gameArWide = new ArrayList<>();
    private boolean gameSoundNamed;
    private boolean gameMenu, cecDetect, vrrTopSpeed;
    private final List<String> problems = new ArrayList<>();

    private MenuProfile(Map<String, Object> j) {
        for (String k : j.keySet()) if (!TOP.contains(k)) problem("unknown key \"" + k + "\"");
        Object sc = j.get("schema");
        if (!(sc instanceof Long) || (Long) sc != SCHEMA) problem("schema must be " + SCHEMA);
        device = str(j.get("device"), "device");
        model = str(j.get("model"), "model");
        adapter = str(j.get("adapter"), "adapter");
        readFeatures(j.get("features"));
        readHints(j.get("hints"));
        readKeys(j.get("keys"));
        readGame(j.get("game_menu"));
        readSections(j.get("sections"));
        Object pe = j.get("pending");
        if (pe != null) {
            for (Object o : list(pe, "pending")) {
                if (!(o instanceof String)) problem("pending: not a feature id");
                else if (FeatureId.known((String) o) != null) problem("pending: " + o + " is a known feature, list it under features");
                else pending.add((String) o);
            }
        }
    }

    /** Parses and checks a profile; never throws for content problems (see {@link #problems()}). */
    public static MenuProfile parse(String json) {
        Object v;
        try {
            v = MenuJson.parse(json);
        } catch (IllegalArgumentException e) {
            MenuProfile p = new MenuProfile(new LinkedHashMap<>());
            p.problems.clear();
            p.problem(e.getMessage());
            return p;
        }
        if (!(v instanceof Map)) {
            MenuProfile p = new MenuProfile(new LinkedHashMap<>());
            p.problems.clear();
            p.problem("the profile is not a JSON object");
            return p;
        }
        @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) v;
        return new MenuProfile(m);
    }

    // ------------------------------------------------------------------ reading

    private void readFeatures(Object o) {
        if (!(o instanceof Map)) { problem("features must be an object"); return; }
        @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) o;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            FeatureId id = FeatureId.known(e.getKey());
            if (id == null) { problem("unknown feature \"" + e.getKey() + "\" (add it under pending and ask the lumen-menu lead)"); continue; }
            if (!(e.getValue() instanceof Map)) { problem(id + ": must be an object ({} when it has no parameters)"); continue; }
            @SuppressWarnings("unchecked") Map<String, Object> par = (Map<String, Object>) e.getValue();
            for (String k : par.keySet()) if (!PARAMS.contains(k)) problem(id + ": unknown parameter \"" + k + "\"");
            if (id.kind == FeatureKind.CHOICE) {
                List<String> opts = tokens(par.get("options"), id + ".options");
                if (opts.isEmpty()) problem(id + ": a choice needs a non-empty options list");
                if (FeatureId.EYE == id && !opts.isEmpty() && !"off".equals(opts.get(0))) problem("eye: options start with off");
                if (par.containsKey("support")) {
                    par = new LinkedHashMap<>(par);
                    par.put("support", readSupport(id, opts, par.get("support")));
                }
            } else if (par.containsKey("options") || par.containsKey("support")) {
                problem(id + ": only a choice has options and support");
            }
            if (par.containsKey("chart") && (FeatureId.FOCUS_MANUAL != id || !FocusChart.SHORT.equals(par.get("chart"))
                    && !FocusChart.OFF.equals(par.get("chart")))) {
                problem(id + ": chart is focus.manual's, \"short\" or \"off\" (FocusChart)");
            }
            if (par.containsKey("jack") && (FeatureId.SOUND_OUTPUT != id
                    || !SoundJack.SENSED.equals(par.get("jack")) && !SoundJack.MANUAL.equals(par.get("jack")))) {
                problem(id + ": jack is only for sound.output, \"" + SoundJack.SENSED + "\" or \"" + SoundJack.MANUAL + "\"");
            }
            if (FeatureId.LAMP == id) {
                long mn = num(par.get("min"), id + ".min"), mx = num(par.get("max"), id + ".max");
                if (mn >= mx) problem("lamp: min must be below max");
            }
            if (FeatureId.BOOST == id) {
                num(par.get("level"), "boost.level");
                Object sm = par.get("summary");
                if (sm != null && !BOOST_SUMMARIES.contains(sm)) problem("boost.summary must be one of " + BOOST_SUMMARIES);
            } else if (par.containsKey("summary")) {
                problem(id + ": only boost has a summary");
            }
            if (FeatureId.PICTURE_PQ == id) {
                List<String> items = tokens(par.get("items"), "picture.pq.items");
                if (items.isEmpty()) problem("picture.pq: items needed");
                for (String it : items) if (!PQ_ITEMS.contains(it)) problem("picture.pq: unknown item " + it);
                long mn = num(par.get("min"), "picture.pq.min"), mx = num(par.get("max"), "picture.pq.max");
                if (mn >= mx) problem("picture.pq: min must be below max");
            }
            if (FeatureId.NETWORK_HOTSPOT == id) {
                Object nm = par.get("name"), ifc = par.get("iface");
                if (!(nm instanceof String) || ((String) nm).trim().isEmpty()
                        || ((String) nm).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 32) {
                    problem("network.hotspot: name must be the default hotspot name, 1 to 32 bytes");
                }
                if (!(ifc instanceof String) || !IFACE.matcher((String) ifc).matches()) {
                    problem("network.hotspot: iface must be the vendor hotspot interface (ap0)");
                }
            } else if (par.containsKey("name") || par.containsKey("iface")) {
                problem(id + ": only network.hotspot has a name and an iface");
            }
            features.put(id, Collections.unmodifiableMap(new LinkedHashMap<>(par)));
        }
        int hotspot = 0;
        for (FeatureId f : MenuSpec.HOTSPOT_ROWS) if (features.containsKey(f)) hotspot++;
        if (hotspot != 0 && hotspot != MenuSpec.HOTSPOT_ROWS.size()) {
            problem("the Network popup rows come together: " + MenuSpec.HOTSPOT_ROWS);
        }
        if (hotspot != 0 && !features.containsKey(FeatureId.NETWORK)) problem("the Network popup rows need the network tile");
        for (FeatureId[] r : MenuSpec.REQUIRES) {
            if (features.containsKey(r[0]) && !features.containsKey(r[1])) problem(r[0] + " needs " + r[1] + " (its tile)");
        }
        if (features.containsKey(FeatureId.BOOST)) {
            if (!features.containsKey(FeatureId.LAMP)) problem("boost needs lamp");
            else if (num(features.get(FeatureId.BOOST).get("level"), "boost.level")
                    != num(features.get(FeatureId.LAMP).get("max"), "lamp.max") + 1) {
                problem("boost.level must be lamp.max + 1 (the step after the top light level)");
            }
        }
        for (FeatureId f : features.keySet()) {
            FeatureId page = MenuSpec.pageOf(f);
            if (page != null && !features.containsKey(page)) problem(f + " needs " + page + " (the page it is a row of)");
        }
    }

    private void readHints(Object o) {
        if (o == null) return;
        if (!(o instanceof Map)) { problem("hints must be an object"); return; }
        @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) o;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            FeatureId f = FeatureId.known(e.getKey());
            if (f == null || !features.containsKey(f)) problem("hint for " + e.getKey() + ", which the profile does not have");
            if (!(e.getValue() instanceof String) || !HINT_PICTURES.contains(e.getValue())) {
                problem("hint " + e.getKey() + ": unknown picture " + e.getValue() + " (known: " + HINT_PICTURES + ")");
                continue;
            }
            if (f != null) hints.put(f, (String) e.getValue());
        }
    }

    private void readKeys(Object o) {
        if (!(o instanceof Map)) { problem("keys must be an object"); return; }
        @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) o;
        for (String k : m.keySet()) {
            if (!"gear_short".equals(k) && !"gear_long".equals(k) && !"eye_gear_short".equals(k) && !"power_short".equals(k)) {
                problem("keys: unknown " + k);
            }
        }
        String s = str(m.get("gear_short"), "keys.gear_short"), l = str(m.get("gear_long"), "keys.gear_long");
        if (!GEAR_SHORT.contains(s)) problem("keys.gear_short: " + s + " (allowed " + GEAR_SHORT + ")");
        if (!GEAR_LONG.contains(l)) problem("keys.gear_long: " + l + " (allowed " + GEAR_LONG + ")");
        keys.put("gear_short", s);
        keys.put("gear_long", l);
        if (m.containsKey("eye_gear_short")) {
            String e = str(m.get("eye_gear_short"), "keys.eye_gear_short");
            if (!EYE_GEAR_SHORT.contains(e)) problem("keys.eye_gear_short: " + e + " (allowed " + EYE_GEAR_SHORT + ")");
            keys.put("eye_gear_short", e);
        }
        if (m.containsKey("power_short")) {
            String p = str(m.get("power_short"), "keys.power_short");
            if (PowerKeyChoice.of(p) == null) problem("keys.power_short: " + p + " (allowed power_menu, sleep)");
            keys.put("power_short", p);
        }
    }

    private void readGame(Object o) {
        if (!(o instanceof Map)) { problem("game_menu must be an object ({\"enabled\": false} when there is none)"); return; }
        @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) o;
        for (String k : m.keySet()) if (!GAME_KEYS.contains(k)) problem("game_menu: unknown key " + k);
        Object en = m.get("enabled");
        if (!(en instanceof Boolean)) { problem("game_menu.enabled must be true or false"); return; }
        gameMenu = (Boolean) en;
        if (!gameMenu) {
            if (m.containsKey("items") || m.containsKey("themes")) problem("game_menu: items/themes without enabled");
            if ("game_menu".equals(keys.get("gear_long"))) problem("keys.gear_long is game_menu but the game menu is off");
            return;
        }
        int last = -1;
        for (String it : tokens(m.get("items"), "game_menu.items")) {
            int at = GAME_ITEMS.indexOf(it);
            if (at < 0) problem("game_menu.items: unknown " + it);
            else if (at <= last) problem("game_menu.items: " + it + " out of the fixed order " + GAME_ITEMS);
            else { last = at; gameItems.add(it); }
        }
        if (gameItems.isEmpty()) problem("game_menu.items: empty");
        if (m.containsKey("gated")) {                             // items shown only once their getter answered in range
            for (String g : tokens(m.get("gated"), "game_menu.gated")) {
                if (!gameItems.contains(g)) problem("game_menu.gated: " + g + " is not one of the items");
                else gameGated.add(g);
            }
        }
        if (m.containsKey("sound")) {                             // the stock game sound values of this model
            List<String> snd = tokens(m.get("sound"), "game_menu.sound");
            if (!gameItems.contains("snd")) problem("game_menu.sound without the snd item");
            else if (snd.size() < 3 || snd.size() > GAME_SOUNDS.size() || !snd.equals(GAME_SOUNDS.subList(0, snd.size())))
                problem("game_menu.sound: " + snd + " is not " + GAME_SOUNDS.subList(0, 3) + " plus the next of " + GAME_SOUNDS);
            else { gameSounds.clear(); gameSounds.addAll(snd); gameSoundNamed = true; }
        }
        if (m.containsKey("cs")) {                                // the stock colour space list of this model
            List<String> cs = tokens(m.get("cs"), "game_menu.cs");
            int at = -1;
            boolean order = !cs.isEmpty();
            for (String c : cs) {
                int i = GAME_CS.indexOf(c);
                if (i <= at) order = false;
                at = Math.max(at, i);
            }
            if (!gameItems.contains("cs")) problem("game_menu.cs without the cs item");
            else if (!order) problem("game_menu.cs: " + cs + " is not a subset of " + GAME_CS + " in that order");
            else { gameCs.clear(); gameCs.addAll(cs); }
        }
        if (m.containsKey("ar_wide")) {                           // wire values of 21:9 and 32:9 (two ints)
            Object w = m.get("ar_wide");
            if (!gameItems.contains("ar")) problem("game_menu.ar_wide without the ar item");
            else if (!(w instanceof List) || ((List<?>) w).size() != 2) problem("game_menu.ar_wide must be two numbers");
            else for (Object x : (List<?>) w) {
                if (!(x instanceof Long) || (Long) x < 0 || (Long) x > 15) problem("game_menu.ar_wide: " + x + " is not a wire value 0..15");
                else gameArWide.add(((Long) x).intValue());
            }
        }
        for (String th : tokens(m.get("themes"), "game_menu.themes")) {
            if (!GAME_THEMES.contains(th)) problem("game_menu.themes: unknown " + th);
            else gameThemes.add(th);
        }
        Object cd = m.get("cec_detect");
        if (!(cd instanceof Boolean)) problem("game_menu.cec_detect must be true or false");
        else cecDetect = (Boolean) cd;
        if (m.containsKey("vrr_top_speed")) {                     // stock support_vrr_only_at_top_speed
            Object v = m.get("vrr_top_speed");
            if (!(v instanceof Boolean)) problem("game_menu.vrr_top_speed must be true or false");
            else if (!gameItems.contains("vrr") || !gameItems.contains("lat")) problem("game_menu.vrr_top_speed without the vrr and lat items");
            else vrrTopSpeed = (Boolean) v;
        }
    }

    /**
     * sections (optional): a section's rows in the model's stock order, {"stock": where the order comes from,
     * "groups": [{"header": h, "rows": [...]}, ...]}. A group may have no header, h is one of MenuSpec.HEADERS. Rows are
     * rows of that section (MenuSpec's table, or a row MenuSpec.alsoIn lets the section show too) once each, rows the
     * profile lacks are skipped, and every row of the section the profile has must be there (else it would vanish). A
     * row may be the model's own stock sub page, {"page": id, "groups": [...]} with groups of the same form and rows of
     * the same section: the page row stands there, its rows open behind it (a page of this profile only, never one of
     * MenuSpec's fixed pages, and no page inside it).
     */
    private void readSections(Object o) {
        if (o != null) readOrders(o);
        for (FeatureId f : features.keySet()) {
            if (f.kind == FeatureKind.PAGE && !MenuSpec.isTablePage(f) && !pageOrders.containsKey(f)) {
                problem(f + ": a page of this profile needs its rows in sections (a {\"page\": \"" + f + "\", \"groups\": ...} row)");
            }
        }
    }

    /** The section orders of {@link #readSections} (sections present). */
    private void readOrders(Object o) {
        if (!(o instanceof Map)) { problem("sections must be an object"); return; }
        @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) o;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            String sec = e.getKey(), at = "sections." + sec;
            if (!MenuSpec.isSection(sec)) { problem("sections: unknown section " + sec); continue; }
            if (!(e.getValue() instanceof Map)) { problem(at + " must be an object"); continue; }
            @SuppressWarnings("unchecked") Map<String, Object> s = (Map<String, Object>) e.getValue();
            for (String k : s.keySet()) if (!"stock".equals(k) && !"groups".equals(k)) problem(at + ": unknown key " + k);
            String stock = str(s.get("stock"), at + ".stock");
            Set<FeatureId> named = new HashSet<>();
            Object[][] groups = readGroups(sec, at, s.get("groups"), named);
            for (FeatureId f : features.keySet()) {
                if (sec.equals(MenuSpec.sectionOf(f)) && !named.contains(f)) problem(at + ": " + f + " has no place in the order");
            }
            orders.put(sec, groups);
            orderSources.put(sec, stock);
        }
    }

    /** The groups of a section order or of a page in it, in MenuSpec's table form; {@code named} collects every row. */
    private Object[][] readGroups(String sec, String at, Object o, Set<FeatureId> named) {
        List<Object[]> groups = new ArrayList<>();
        for (Object g : list(o, at + ".groups")) {
            if (!(g instanceof Map)) { problem(at + ".groups: a group must be an object"); continue; }
            @SuppressWarnings("unchecked") Map<String, Object> gm = (Map<String, Object>) g;
            for (String k : gm.keySet()) if (!"header".equals(k) && !"rows".equals(k)) problem(at + ": unknown group key " + k);
            String header = gm.containsKey("header") ? str(gm.get("header"), at + " header") : null;
            if (header != null && !MenuSpec.HEADERS.contains(header)) problem(at + ": unknown header " + header);
            if (groups.isEmpty() && MenuSpec.LINE.equals(header)) problem(at + ": a line cannot open the page");
            List<Object> rows = new ArrayList<>();
            rows.add(header);
            for (Object r : list(gm.get("rows"), at + " rows")) {
                FeatureId f = r instanceof Map ? readPage(sec, at, r, named) : r instanceof String ? FeatureId.known((String) r) : null;
                if (f == null) { if (!(r instanceof Map)) problem(at + ": unknown row " + r); continue; }
                if (!sec.equals(MenuSpec.sectionOf(f)) && !MenuSpec.alsoIn(sec, f)) problem(at + ": " + f + " is no row of " + sec);
                else if (!named.add(f)) problem(at + ": " + f + " twice");
                else rows.add(f);
            }
            if (rows.size() == 1) problem(at + ": a group without rows");
            groups.add(rows.toArray());
        }
        if (groups.isEmpty()) problem(at + ": no groups");
        return groups.toArray(new Object[0][]);
    }

    /** A {"page": id, "groups": [...]} row: its groups go to pageOrders, the page id stands as the row. */
    private FeatureId readPage(String sec, String at, Object r, Set<FeatureId> named) {
        @SuppressWarnings("unchecked") Map<String, Object> pm = (Map<String, Object>) r;
        for (String k : pm.keySet()) if (!"page".equals(k) && !"groups".equals(k)) problem(at + ": unknown page key " + k);
        FeatureId f = pm.get("page") instanceof String ? FeatureId.known((String) pm.get("page")) : null;
        if (f == null || f.kind != FeatureKind.PAGE || MenuSpec.isTablePage(f)) {
            problem(at + ": " + pm.get("page") + " is no page of a profile (a PAGE feature outside MenuSpec's fixed pages)");
            return null;
        }
        pageOrders.put(f, readGroups(sec, at + "." + f, pm.get("groups"), named));
        if (!features.containsKey(f)) problem(at + ": page " + f + " is not a feature of the profile (its rows would vanish)");
        return f;
    }

    // ------------------------------------------------------------------ helpers

    private void problem(String p) { problems.add(p); }

    /**
     * A choice's {@code support}: option -&gt; the kinds of content it is offered for (stock tree {@code support} lists);
     * an option left out is offered for every kind.
     */
    private Map<String, List<Signal>> readSupport(FeatureId id, List<String> opts, Object o) {
        Map<String, List<Signal>> out = new LinkedHashMap<>();
        if (!(o instanceof Map) || ((Map<?, ?>) o).isEmpty()) { problem(id + ".support must be a non-empty object"); return out; }
        for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
            String opt = String.valueOf(e.getKey());
            if (!opts.contains(opt)) problem(id + ".support: " + opt + " is not one of the options");
            List<Signal> on = new ArrayList<>();
            for (String t : tokens(e.getValue(), id + ".support." + opt)) {
                Signal s = Signal.of(t);
                if (s == null) problem(id + ".support." + opt + ": unknown content " + t);
                else on.add(s);
            }
            if (on.isEmpty()) problem(id + ".support." + opt + ": empty (leave the option out to offer it always)");
            out.put(opt, Collections.unmodifiableList(on));
        }
        return Collections.unmodifiableMap(out);
    }

    private String str(Object o, String what) {
        if (o instanceof String && !((String) o).isEmpty()) return (String) o;
        problem(what + " must be a non-empty string");
        return "";
    }

    private long num(Object o, String what) {
        if (o instanceof Long) return (Long) o;
        problem(what + " must be a whole number");
        return 0;
    }

    private List<Object> list(Object o, String what) {
        if (o instanceof List) {
            @SuppressWarnings("unchecked") List<Object> l = (List<Object>) o;
            return l;
        }
        problem(what + " must be a list");
        return Collections.emptyList();
    }

    private List<String> tokens(Object o, String what) {
        List<String> out = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (Object x : list(o, what)) {
            if (!(x instanceof String) || !((String) x).matches("[a-z0-9_]+")) { problem(what + ": bad token " + x); continue; }
            if (!seen.add((String) x)) problem(what + ": " + x + " twice");
            out.add((String) x);
        }
        return out;
    }

    private static Set<String> set(String... a) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(a)));
    }

    // ------------------------------------------------------------------ queries

    /** Empty when the profile is valid. */
    public List<String> problems() { return Collections.unmodifiableList(problems); }

    public boolean ok() { return problems.isEmpty(); }

    public String device() { return device; }

    public String model() { return model; }

    /** The adapter's source file (relative to gsi/), read by the contract test. */
    public String adapter() { return adapter; }

    /** THE gate: a feature exists in the menu only when this is true (never a disabled row). */
    public boolean has(FeatureId feature) { return ok() && features.containsKey(feature); }

    public Set<FeatureId> features() { return ok() ? Collections.unmodifiableSet(features.keySet()) : Collections.emptySet(); }

    @SuppressWarnings("unchecked")
    public List<String> options(FeatureId feature) {
        if (!has(feature)) return Collections.emptyList();
        Object o = features.get(feature).get("options");
        return o == null ? Collections.emptyList() : Collections.unmodifiableList((List<String>) (List<?>) o);
    }

    @SuppressWarnings("unchecked")
    public List<String> items(FeatureId feature) {
        if (!has(feature)) return Collections.emptyList();
        Object o = features.get(feature).get("items");
        return o == null ? Collections.emptyList() : Collections.unmodifiableList((List<String>) (List<?>) o);
    }

    /** True when some option of the choice is offered only for some kinds of content ({@link SignalRule}). */
    public boolean hasSupport(FeatureId feature) { return !supportOf(feature).isEmpty(); }

    /** The kinds of content {@code option} is offered for; empty = every kind. */
    public List<Signal> support(FeatureId feature, String option) {
        List<Signal> l = supportOf(feature).get(option);
        return l == null ? Collections.emptyList() : l;
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<Signal>> supportOf(FeatureId feature) {
        Object o = has(feature) ? features.get(feature).get("support") : null;
        return o instanceof Map ? (Map<String, List<Signal>>) o : Collections.emptyMap();
    }

    public int number(FeatureId feature, String key, int fallback) {
        if (!has(feature)) return fallback;
        Object o = features.get(feature).get(key);
        return o instanceof Long ? (int) (long) (Long) o : fallback;
    }

    /**
     * The kernel reports a plug of the 3.5 mm jack (sound.output "jack", default sensed). "manual" (HB): the headphones
     * row is always listed and chosen by hand, Auto never moves to it.
     */
    public boolean jackSensed() {
        return !has(FeatureId.SOUND_OUTPUT) || !SoundJack.MANUAL.equals(features.get(FeatureId.SOUND_OUTPUT).get("jack"));
    }

    /** A text parameter of a feature (network.hotspot name and iface), or null. */
    public String text(FeatureId feature, String key) {
        Object v = has(feature) ? features.get(feature).get(key) : null;
        return v instanceof String ? (String) v : null;
    }

    /** The hint picture of a row, or null: a picture is drawn only where the profile names one. */
    public String hint(FeatureId feature) { return has(feature) ? hints.get(feature) : null; }

    public Map<FeatureId, String> hints() { return ok() ? Collections.unmodifiableMap(hints) : Collections.emptyMap(); }

    public String gearShort() { return keys.get("gear_short"); }

    public String gearLong() { return keys.get("gear_long"); }

    /** keys.eye_gear_short as named, or null (the default restore). */
    public String eyeGearShort() { return keys.get("eye_gear_short"); }

    /**
     * keys.power_short: what a short press of the remote power key does until the user picks otherwise (General, Short
     * press of the power key); the stock power menu when the profile names none.
     */
    public PowerKeyChoice powerShort() {
        PowerKeyChoice c = PowerKeyChoice.of(keys.get("power_short"));
        return c == null ? PowerKeyChoice.POWER_MENU : c;
    }

    /** A short gear press on the eye-protection mask also turns eye protection off (restore_off; a broken profile: no). */
    public boolean eyeGearShortOff() { return ok() && "restore_off".equals(keys.get("eye_gear_short")); }

    public boolean gameMenu() { return ok() && gameMenu; }

    public List<String> gameItems() { return gameMenu() ? Collections.unmodifiableList(gameItems) : Collections.emptyList(); }

    /** game_menu.gated: items hidden until their getter answered in range on this unit (X50 super resolution). */
    public List<String> gameGated() { return gameMenu() ? Collections.unmodifiableList(gameGated) : Collections.emptyList(); }

    /** game_menu.sound: the game sound values this model's stock offers (default off, surround, story). */
    public List<String> gameSounds() { return Collections.unmodifiableList(gameSounds); }

    /** The game menu colour spaces of this model (game_menu.cs, default all of GAME_CS). */
    public List<String> gameCs() { return Collections.unmodifiableList(gameCs); }

    /** The game menu's 21:9 / 32:9 wire values (game_menu.ar_wide), empty when the model offers none. */
    public List<Integer> gameArWide() { return gameMenu() ? Collections.unmodifiableList(gameArWide) : Collections.emptyList(); }

    /** The profile names its game sound values (game_menu.sound). */
    public boolean gameSoundNamed() { return gameMenu() && gameSoundNamed; }

    public List<String> gameThemes() { return gameMenu() ? Collections.unmodifiableList(gameThemes) : Collections.emptyList(); }

    public boolean cecDetect() { return gameMenu() && cecDetect; }

    /** game_menu.vrr_top_speed: VRR may go on only at top speed (stock feature support_vrr_only_at_top_speed). */
    public boolean gameVrrTopSpeed() { return gameMenu() && vrrTopSpeed; }

    public List<String> pending() { return Collections.unmodifiableList(pending); }

    /** The section's stock order ({header-or-null, rows...} groups, MenuSpec's table form), null for the table's own. */
    Object[][] order(String section) { return ok() ? orders.get(section) : null; }

    /** The groups of the profile's own pages ({"page": ...} rows of its sections), empty for an invalid profile. */
    Map<FeatureId, Object[][]> pageOrders() { return ok() ? Collections.unmodifiableMap(pageOrders) : Collections.emptyMap(); }

    /** Where the section's stock order comes from (sections.&lt;s&gt;.stock), null without one. */
    public String orderSource(String section) { return ok() ? orderSources.get(section) : null; }

    /** The Boost hint of this model (features.boost.summary): "paler" (Z9X stock, default) or "brighter" (HB stock). */
    public String boostSummary() {
        Map<String, Object> b = features.get(FeatureId.BOOST);
        Object s = b == null ? null : b.get("summary");
        return s instanceof String ? (String) s : "paler";
    }
}
