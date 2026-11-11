package org.lumen.projector.panel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Z9X HB (goat_elegant): the plain-Java half of the HB adapter of the shared menu (apps/core/lumen-menu
 * section 4), host tested by test/menu/run.sh. No Android class, no HAL call.
 *
 * (1) GATES: every feature id of devices/goat_elegant/menu.json and the HbFeatures switch that must be ON for it on THIS
 * unit (null = no switch: the stock HB has it on every model code). HbMenuFeatures.implemented() is this table read
 * against HbFeatures at run time, so one profile serves G0130 and G0148: G0148 (and an unknown model, and a model whose
 * feature.xml cannot be read) loses boost and picture.color_temp (HbFeatures.modelOff), a feature.xml key that says
 * off removes its rows. Lumen rule: hide, never a disabled row.
 *
 * (2) the option tokens of the profile and the HB wire value of each (stock dapengse tree order, the values the HB
 * vendor takes, GmpfClient enums): the menu only ever sees the tokens.
 */
public final class HbMenuGates {
    private HbMenuGates() {}

    /**
     * {feature id, HbFeatures.F name or null}. The order is the profile's; a row whose feature is in the profile's
     * "pending" list (picture.color_temp: the module has no labels for the HB tokens yet) is ready for the day it moves
     * into "features" and shows nothing meanwhile (the menu builds only profile features).
     */
    static final String[][] GATES = {
            {"eye", "EYE_PROTECTION"},
            {"keystone.ak_now", "AUTO_KEYSTONE"},
            {"keystone.fit_now", "CURTAIN_FIT"},
            {"keystone.manual", "MANUAL_KEYSTONE"},
            {"zoom", "OPTICAL_ZOOM"},
            {"keystone.on_power", "AUTO_KEYSTONE"},
            {"keystone.curtain", "CURTAIN_FIT"},
            {"keystone.obstacle", "OBSTACLE_AVOID"},
            {"keystone.restore", "KEYSTONE_RESET"},
            {"focus.af_now", null},
            {"focus.manual", "MANUAL_FOCUS"},
            {"focus.smart", null},
            {"lamp", null},
            {"boost", "BOOST"},
            {"picture.mode", "PICTURE"},
            {"picture.pq", "PICTURE"},
            {"picture.color_temp", "COLOR_TEMP"},
            {"picture.memc", "PICTURE"},
            {"picture.pro", "PICTURE"},
            {"picture.reset", "PICTURE"},
            {"picture.reset_all", "PICTURE"},
            {"hdmi.range", "HDMI"},
            {"hdmi.edid", "HDMI"},
            {"picture.aspect", null},
            {"sound.mode", "SOUND_MODES"},
            {"sound.earc", "HDMI"},
            {"sound.output", null},
            {"sound.format", null},
            {"input", null},
            {"bluetooth", null},
            {"network", null},
            {"usb_files", null},
            {"game.mode", "GAME_MODE"},
            {"game.speed", "GAME_MODE"},
            {"game.allm", "GAME_MODE"},
            {"projection.mode", null},
            {"projection.rear", null},
            {"settings.system", null},
            {"settings.projector", null},
            {"settings.cec", null},
            {"settings.recents", null},
            {"settings.launcher", null},
    };

    /** Game menu items (profile game_menu.items) and their HbFeatures switch; sr and snd are not on the HB stock. */
    static final String[][] GAME_GATES = {
            {"lat", "GAME_MODE"},
            {"vrr", "GAME_VRR"},
            {"hfr", "GAME_MODE"},
            {"pic", "GAME_PICTURE"},
            {"dark", "GAME_DARK_BOOST"},
            {"ar", "GAME_MODE"},
            {"aim", "GAME_CROSSHAIR"},
            {"fps", "GAME_FPS"},
    };

    /** Feature ids the HB adapter implements right now: every GATES row whose switch is ON ({@code on} = HbFeatures.on). */
    static Set<String> implemented(Predicate<String> on) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String[] g : GATES) if (g[1] == null || on.test(g[1])) out.add(g[0]);
        return Collections.unmodifiableSet(out);
    }

    /** Game menu items shown on this unit (before the module's own read-back capability check). */
    static Set<String> gameItems(Predicate<String> on) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (!on.test("GAME_MODE") || !on.test("GAME_BAR")) return Collections.unmodifiableSet(out);
        for (String[] g : GAME_GATES) if (on.test(g[1])) out.add(g[0]);
        return Collections.unmodifiableSet(out);
    }

    // ------------------------------------------------------------------ tokens <-> HB wire values

    /** A fixed token table: profile order, token -> wire. */
    static final class Table {
        final String feature;
        final Map<String, Integer> wire;

        Table(String feature, Object... pairs) {
            this.feature = feature;
            LinkedHashMap<String, Integer> m = new LinkedHashMap<>();
            for (int i = 0; i < pairs.length; i += 2) {
                if (m.put((String) pairs[i], (Integer) pairs[i + 1]) != null) throw new IllegalStateException(feature + " twice " + pairs[i]);
            }
            wire = Collections.unmodifiableMap(m);
        }

        /** The wire of a token, null when it is not an option of this feature. */
        Integer wire(String token) { return token == null ? null : wire.get(token); }

        /** The token of a read-back wire, null when the HB menu lists no such value (the row then shows none). */
        String token(Integer w) {
            if (w == null) return null;
            for (Map.Entry<String, Integer> e : wire.entrySet()) if (e.getValue().intValue() == w) return e.getKey();
            return null;
        }
    }

    /** TV 33 / 34 picture mode (GmpfClient.PictureMode): stock image_mode_4_state order; no Office, no Dolby Vision. */
    static final Table PICTURE_MODE = new Table("picture.mode",
            "ai", 10, "standard", 1, "sport", 9, "color_accuracy", 29, "hdr_vivid", 26, "performance", 5);
    /** DISPLAY 4 / 5 lumens mode, the dapengse list (image_mode_4_color_temp_dapengse map 0 / 16). */
    static final Table COLOR_TEMP = new Table("picture.color_temp", "standard", 0, "movie", 16);
    /** TV 31 / 32 MEMC. */
    static final Table MEMC = new Table("picture.memc", "off", 0, "low", 1, "medium", 2, "high", 3);
    /** TV 35 / 36 aspect ratio of the quick menu (21:9 / 32:9 = 5 / 6 belong to the game menu only). */
    static final Table ASPECT = new Table("picture.aspect", "auto", 2, "r16_9", 1, "r4_3", 0, "original", 4);
    /** TV 63 / 64 HDMI colour range. */
    static final Table HDMI_RANGE = new Table("hdmi.range", "auto", 0, "full", 1, "limited", 2);
    /** TV 39 / 40 EDID, HB raw wire (stock SignalEnum 1 / 2 minus 1): 0 = 1.4, 1 = 2.0; 2 / 3 are never sent. */
    static final Table EDID = new Table("hdmi.edid", "compat", 0, "enhanced", 1);
    /** AUDIO 6 / 7 sound mode (stock SoundEnum wires); Standard (20) is never sent, as on the Z9X. */
    static final Table SOUND_MODE = new Table("sound.mode", "ai", 3, "music", 2, "movie", 1, "karaoke", 4, "sports", 12);
    /** DISPLAY 76 / 77 game option: the two the stock HB sends (1 standard, 2 top speed). */
    static final Table GAME_SPEED = new Table("game.speed", "basic_hfr", 1, "top_speed_hfr", 2);
    /** Game mode row: 0 off, 1 on, 2 auto (stock gmuiapi setGameMode level, read from DISPLAY 148). */
    static final Table GAME_MODE = new Table("game.mode", "off", 0, "on", 1, "auto", 2);
    /** ALLM: TV 129 true = follow the signal, false = follow the device (stock allm row). */
    static final Table ALLM = new Table("game.allm", "signal", 1, "device", 0);
    /** Projection: auto = MOTION 8 on; table / ceiling = put mode family (DISPLAY 2 / 3, front or rear kept). */
    static final Table PROJECTION = new Table("projection.mode", "auto", 0, "table", 1, "ceiling", 2);
    /** Sound output kinds: the AudioOutputChoice.Kind names in lower case (HbOutputRows order). */
    static final Table OUTPUT = new Table("sound.output", "auto", 0, "speaker", 1, "arc", 2, "bt", 3, "wired", 4, "usb", 5);
    /** digital_output_format, stock order PCM, Passthrough, Auto (AudioOutputChoice.Format ordinals PCM 1, PASSTHROUGH 2, AUTO 0). */
    static final Table FORMAT = new Table("sound.format", "pcm", 1, "passthrough", 2, "auto", 0);
    /** Home screen choice. */
    static final Table LAUNCHER = new Table("settings.launcher", "lumen", 0, "classic", 1);

    static final Table[] TABLES = {PICTURE_MODE, COLOR_TEMP, MEMC, ASPECT, HDMI_RANGE, EDID, SOUND_MODE, GAME_SPEED,
            GAME_MODE, ALLM, PROJECTION, OUTPUT, FORMAT, LAUNCHER};

    static Table table(String feature) {
        for (Table t : TABLES) if (t.feature.equals(feature)) return t;
        return null;
    }

    /** AudioOutputChoice.Kind name of an output token (AUTO, SPEAKER, ARC, BT, WIRED, USB), null for an unknown one. */
    static String outputKind(String token) {
        Integer w = OUTPUT.wire(token);
        if (w == null) return null;
        switch (w) {
            case 0: return "AUTO";
            case 1: return "SPEAKER";
            case 2: return "ARC";
            case 3: return "BT";
            case 4: return "WIRED";
            default: return "USB";
        }
    }

    /** Output token of an AudioOutputChoice.Kind name, null when unknown. */
    static String outputToken(String kind) {
        if (kind == null) return null;
        switch (kind) {
            case "AUTO": return "auto";
            case "SPEAKER": return "speaker";
            case "ARC": return "arc";
            case "BT": return "bt";
            case "WIRED": return "wired";
            case "USB": return "usb";
            default: return null;
        }
    }

    // ------------------------------------------------------------------ light and Boost

    static final int LAMP_MIN = 1, LAMP_MAX = 10, BOOST_LEVEL = 11;

    /** A light slider value the vendor takes (DISPLAY 6): 1..10, never the Boost step. */
    static boolean lampValue(int v) { return v >= LAMP_MIN && v <= LAMP_MAX; }

    /** Projection token from the read-back: auto flip on = auto, else the put mode family. Null when unknown. */
    static String projectionToken(Integer putMode, Boolean autoReverse) {
        if (putMode == null || autoReverse == null) return null;
        if (autoReverse) return "auto";
        if (putMode == 0 || putMode == 2) return "table";
        if (putMode == 1 || putMode == 3) return "ceiling";
        return null;
    }

    /** The put mode for table / ceiling keeping front or rear (stock ProjectionRepository), -1 for anything else. */
    static int putModeFor(String token, int current) {
        boolean front = current == 0 || current == 1;
        if ("table".equals(token)) return front ? 0 : 2;
        if ("ceiling".equals(token)) return front ? 1 : 3;
        return -1;
    }

    /** Rear switch: from 0/2 on -> 2, off -> 0; from 1/3 on -> 3, off -> 1 (stock setRearProjection). */
    static int putModeForRear(boolean rear, int current) {
        boolean table = current == 0 || current == 2;
        return rear ? (table ? 2 : 3) : (table ? 0 : 1);
    }

    /** Game speed token of a 77 read: 0 / 3 (no HFR, left by older Lumen builds) show as their speed. */
    static String gameSpeedToken(Integer option) {
        if (option == null) return null;
        int shown = option == 0 ? 1 : option == 3 ? 2 : option;
        return GAME_SPEED.token(shown);
    }
}
