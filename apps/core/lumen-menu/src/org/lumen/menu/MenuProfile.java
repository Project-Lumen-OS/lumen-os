// SPDX-License-Identifier: Apache-2.0
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

    /** Hint pictures lumen-menu can draw (QpdTip). A profile names one only where the stock menu has that picture. */
    public static final Set<String> HINT_PICTURES = set("picture_mode", "rear_projection", "eye_follow", "eye_rest",
            "game_mode", "curtain_fit", "dynamic_black");
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
            "lat", "vrr", "hfr", "pic", "cs", "dark", "sr", "ar", "hdr", "snd", "aim", "fps"));
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
            "game_menu", "pending", "note");
    private static final Set<String> PARAMS = set("options", "min", "max", "level", "items", "note", "summary");
    private static final Set<String> GAME_KEYS = set("enabled", "items", "themes", "cec_detect", "note", "gated", "sound");

    private final String device, model, adapter;
    private final LinkedHashMap<FeatureId, Map<String, Object>> features = new LinkedHashMap<>();
    private final LinkedHashMap<FeatureId, String> hints = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> keys = new LinkedHashMap<>();
    private final List<String> gameItems = new ArrayList<>(), gameThemes = new ArrayList<>(), pending = new ArrayList<>(),
            gameGated = new ArrayList<>(), gameSounds = new ArrayList<>(GAME_SOUNDS.subList(0, 3));
    private boolean gameSoundNamed;
    private boolean gameMenu, cecDetect;
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
            } else if (par.containsKey("options")) {
                problem(id + ": only a choice has options");
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
            features.put(id, Collections.unmodifiableMap(new LinkedHashMap<>(par)));
        }
        if (features.containsKey(FeatureId.BOOST)) {
            if (!features.containsKey(FeatureId.LAMP)) problem("boost needs lamp");
            else if (num(features.get(FeatureId.BOOST).get("level"), "boost.level")
                    != num(features.get(FeatureId.LAMP).get("max"), "lamp.max") + 1) {
                problem("boost.level must be lamp.max + 1 (the step after the top light level)");
            }
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
            if (!"gear_short".equals(k) && !"gear_long".equals(k) && !"eye_gear_short".equals(k)) problem("keys: unknown " + k);
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
        for (String th : tokens(m.get("themes"), "game_menu.themes")) {
            if (!GAME_THEMES.contains(th)) problem("game_menu.themes: unknown " + th);
            else gameThemes.add(th);
        }
        Object cd = m.get("cec_detect");
        if (!(cd instanceof Boolean)) problem("game_menu.cec_detect must be true or false");
        else cecDetect = (Boolean) cd;
    }

    // ------------------------------------------------------------------ helpers

    private void problem(String p) { problems.add(p); }

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

    public int number(FeatureId feature, String key, int fallback) {
        if (!has(feature)) return fallback;
        Object o = features.get(feature).get(key);
        return o instanceof Long ? (int) (long) (Long) o : fallback;
    }

    /** The hint picture of a row, or null: a picture is drawn only where the profile names one. */
    public String hint(FeatureId feature) { return has(feature) ? hints.get(feature) : null; }

    public Map<FeatureId, String> hints() { return ok() ? Collections.unmodifiableMap(hints) : Collections.emptyMap(); }

    public String gearShort() { return keys.get("gear_short"); }

    public String gearLong() { return keys.get("gear_long"); }

    /** keys.eye_gear_short as named, or null (the default restore). */
    public String eyeGearShort() { return keys.get("eye_gear_short"); }

    /** A short gear press on the eye-protection mask also turns eye protection off (restore_off; a broken profile: no). */
    public boolean eyeGearShortOff() { return ok() && "restore_off".equals(keys.get("eye_gear_short")); }

    public boolean gameMenu() { return ok() && gameMenu; }

    public List<String> gameItems() { return gameMenu() ? Collections.unmodifiableList(gameItems) : Collections.emptyList(); }

    /** game_menu.gated: items hidden until their getter answered in range on this unit (X50 super resolution). */
    public List<String> gameGated() { return gameMenu() ? Collections.unmodifiableList(gameGated) : Collections.emptyList(); }

    /** game_menu.sound: the game sound values this model's stock offers (default off, surround, story). */
    public List<String> gameSounds() { return Collections.unmodifiableList(gameSounds); }

    /** The profile names its game sound values (game_menu.sound). */
    public boolean gameSoundNamed() { return gameMenu() && gameSoundNamed; }

    public List<String> gameThemes() { return gameMenu() ? Collections.unmodifiableList(gameThemes) : Collections.emptyList(); }

    public boolean cecDetect() { return gameMenu() && cecDetect; }

    public List<String> pending() { return Collections.unmodifiableList(pending); }

    /** The Boost hint of this model (features.boost.summary): "paler" (Z9X stock, default) or "brighter" (HB stock). */
    public String boostSummary() {
        Map<String, Object> b = features.get(FeatureId.BOOST);
        Object s = b == null ? null : b.get("summary");
        return s instanceof String ? (String) s : "paler";
    }
}
