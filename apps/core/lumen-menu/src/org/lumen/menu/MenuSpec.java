// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the shared menu shows for one profile: the quick panel D tiles, the rows of every
 * section page and popup, the All settings categories, the Boost step and the game menu items. The order and the
 * places are FIXED for every projector (never laid out per device); a feature the profile lacks is simply left out,
 * a header whose rows are all left out goes too, a section tile without rows goes too, and an empty tile row goes.
 * This class is the only place that decides presence; the views build exactly this list.
 */
public final class MenuSpec {

    // ------------------------------------------------------------------ fixed layout (every device)

    /** Section ids (QuickPanel.SECTION_* use the same words). */
    public static final String KEYSTONE = "keystone", FOCUS = "focus", PICTURE = "picture", SOUND = "sound",
            GAME = "game", PROJECTION = "projection", EYE = "eye", GENERAL = "general", LAMP = "lamp";

    /** Tile sizes of layout D. */
    public static final String ROUND = "round", WIDE = "wide", SMALL = "small";

    /** A tile: {@code feature} is a feature id or a section id (a section tile exists when its section has rows). */
    public static final class Tile {
        public final String size, id;
        /** For the last small slot: the second candidate (mirroring, else USB files), or null. */
        public final String orElse;

        Tile(String size, String id, String orElse) {
            this.size = size;
            this.id = id;
            this.orElse = orElse;
        }

        @Override public String toString() { return size + " " + id + (orElse == null ? "" : "|" + orElse); }
    }

    /** A group of rows under an optional header (header null: no header). */
    public static final class Group {
        public final String header;
        public final List<String> rows;

        Group(String header, List<String> rows) {
            this.header = header;
            this.rows = Collections.unmodifiableList(rows);
        }
    }

    private static final String[][] ROUND_TILES = {{EYE, Feature.EYE}, {GENERAL, null}};
    /** Rows of the card: one wide tile and two small ones each (stock structure), {size, id, orElse}. */
    private static final String[][][] TILE_ROWS = {
            {{WIDE, Feature.KEYSTONE_AK_NOW}, {SMALL, KEYSTONE}, {SMALL, Feature.ZOOM, Feature.ZOOM_DIGITAL}},
            {{WIDE, PICTURE}, {SMALL, SOUND}, {SMALL, FOCUS}},
            {{WIDE, Feature.INPUT}, {SMALL, Feature.BLUETOOTH}, {SMALL, Feature.NETWORK}},
            {{WIDE, GAME}, {SMALL, PROJECTION}, {SMALL, Feature.MIRRORING, Feature.USB_FILES}},
    };
    /** Section pages: {section, header-or-null, rows...} groups in order. */
    private static final Object[][] SECTIONS = {
            {KEYSTONE, new Object[][]{
                    {null, Feature.KEYSTONE_AK_NOW, Feature.KEYSTONE_FIT_NOW, Feature.KEYSTONE_MANUAL, Feature.ZOOM,
                            Feature.ZOOM_DIGITAL,
                            Feature.KEYSTONE_FIT_SCREEN, Feature.KEYSTONE_ROTATION, Feature.KEYSTONE_ANGLE_H,
                            Feature.KEYSTONE_ANGLE_V},
                    {"image_correction", Feature.KEYSTONE_REALTIME, Feature.KEYSTONE_ON_MOVE, Feature.KEYSTONE_ON_POWER, Feature.KEYSTONE_CURTAIN,
                            Feature.KEYSTONE_OBSTACLE},
                    {"more", Feature.KEYSTONE_RESTORE}}},
            {FOCUS, new Object[][]{
                    {null, Feature.FOCUS_AF_NOW, Feature.FOCUS_MANUAL, Feature.FOCUS_SMART, Feature.FOCUS_ON_MOVE,
                            Feature.FOCUS_ON_POWER}}},
            {LAMP, new Object[][]{{null, Feature.LAMP, Feature.BOOST}}},
            {PICTURE, new Object[][]{
                    {null, Feature.PICTURE_MODE, Feature.PICTURE_PQ, Feature.PICTURE_COLOR_TEMP, Feature.PICTURE_MEMC,
                            Feature.PICTURE_AI_CONTRAST, Feature.PICTURE_DYNAMIC_CONTRAST,
                            Feature.PICTURE_DYNAMIC_BLACK, Feature.PICTURE_SUPER_RES, Feature.PICTURE_ULTRA120,
                            Feature.PICTURE_PRO, Feature.PICTURE_RESET, Feature.PICTURE_RESET_ALL},
                    {"hdmi", Feature.HDMI_RANGE, Feature.HDMI_EDID, Feature.PICTURE_ASPECT}}},
            {SOUND, new Object[][]{
                    {null, Feature.SOUND_ENHANCEMENT, Feature.SOUND_MODE, Feature.SOUND_EQ, Feature.SOUND_LEVELING,
                            Feature.SOUND_EARC, Feature.SOUND_OUTPUT, Feature.SOUND_FORMAT}}},
            {PROJECTION, new Object[][]{{null, Feature.PROJECTION_MODE, Feature.PROJECTION_REAR}}},
            {GAME, new Object[][]{{null, Feature.GAME_MODE, Feature.GAME_SPEED, Feature.GAME_ALLM}}},
            {EYE, new Object[][]{{null, Feature.EYE}}},
            {GENERAL, new Object[][]{
                    {null, Feature.SETTINGS_SYSTEM, Feature.SETTINGS_PROJECTOR, Feature.SETTINGS_CEC,
                            Feature.SETTINGS_RECENTS, Feature.SETTINGS_AMBIENT_SPEAKER, Feature.SETTINGS_SLEEP_TIMER,
                            Feature.SETTINGS_REMOTE, Feature.LAMP_ECO, Feature.SETTINGS_LAUNCHER}}},
    };
    /** All settings: the round category buttons, in this order. */
    private static final String[] ALL_SETTINGS = {KEYSTONE, FOCUS, PICTURE, PROJECTION, SOUND, EYE, GENERAL};

    // ------------------------------------------------------------------ result for one profile

    public final MenuProfile profile;
    public final List<Tile> round;
    public final List<List<Tile>> rows;
    public final Map<String, List<Group>> sections;
    public final List<String> allSettings;

    private MenuSpec(MenuProfile p) {
        profile = p;
        LinkedHashMap<String, List<Group>> secs = new LinkedHashMap<>();
        for (Object[] s : SECTIONS) {
            List<Group> groups = new ArrayList<>();
            for (Object[] g : (Object[][]) s[1]) {
                List<String> rs = new ArrayList<>();
                for (int i = 1; i < g.length; i++) if (p.has((String) g[i])) rs.add((String) g[i]);
                if (!rs.isEmpty()) groups.add(new Group((String) g[0], rs));
            }
            if (!groups.isEmpty()) secs.put((String) s[0], Collections.unmodifiableList(groups));
        }
        sections = Collections.unmodifiableMap(secs);
        List<Tile> rt = new ArrayList<>();
        for (String[] t : ROUND_TILES) {
            if (t[1] == null || p.has(t[1])) rt.add(new Tile(ROUND, t[0], null));
        }
        round = Collections.unmodifiableList(rt);
        List<List<Tile>> rws = new ArrayList<>();
        for (String[][] row : TILE_ROWS) {
            List<Tile> r = new ArrayList<>();
            for (String[] t : row) {
                String id = t[1], alt = t.length > 2 ? t[2] : null;
                boolean a = present(id), b = alt != null && present(alt);
                if (a) r.add(new Tile(t[0], id, b ? alt : null));
                else if (b) r.add(new Tile(t[0], alt, null));
            }
            if (!r.isEmpty()) rws.add(Collections.unmodifiableList(r));
        }
        rows = Collections.unmodifiableList(rws);
        List<String> all = new ArrayList<>();
        for (String c : ALL_SETTINGS) if (secs.containsKey(c) || GENERAL.equals(c)) all.add(c);
        allSettings = Collections.unmodifiableList(all);
    }

    private boolean present(String id) {
        return Feature.known(id) ? profile.has(id) : sections.containsKey(id);
    }

    /** The menu of a profile; an invalid profile gives an empty menu (only the gear, nothing to set). */
    public static MenuSpec of(MenuProfile p) { return new MenuSpec(p); }

    /** True when the section has at least one row (its tile, popup and All settings category exist). */
    public boolean hasSection(String section) { return sections.containsKey(section); }

    /** Rows of a section in order (headers left out), empty when absent. */
    public List<String> rowsOf(String section) {
        List<String> out = new ArrayList<>();
        List<Group> gs = sections.get(section);
        if (gs != null) for (Group g : gs) out.addAll(g.rows);
        return out;
    }

    /** True when the header has at least one row in this profile. */
    public boolean hasHeader(String section, String header) {
        List<Group> gs = sections.get(section);
        if (gs != null) for (Group g : gs) if (header.equals(g.header)) return true;
        return false;
    }

    /** The Boost step of the light slider, or -1 when the model has no Boost. */
    public int boostLevel() { return profile.has(Feature.BOOST) ? profile.number(Feature.BOOST, "level", -1) : -1; }

    /** True when the tile with this id (feature or section) is on the card. */
    public boolean hasTile(String id) {
        for (Tile t : round) if (t.id.equals(id)) return true;
        for (List<Tile> r : rows) for (Tile t : r) if (t.id.equals(id) || id.equals(t.orElse)) return true;
        return false;
    }
}
