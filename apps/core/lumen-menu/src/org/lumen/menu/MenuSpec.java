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

    /** A tile: {@code id} is a feature id or a section id (a section tile exists when its section has rows). */
    public static final class Tile {
        public final String size, id;
        /** The feature of a feature tile, null for a section tile. */
        public final FeatureId feature;
        /** For the last small slot: the second candidate (mirroring, else USB files), or null. */
        public final String orElse;

        Tile(String size, Object id, Object orElse) {
            this.size = size;
            this.id = String.valueOf(id);
            this.feature = id instanceof FeatureId ? (FeatureId) id : null;
            this.orElse = orElse == null ? null : String.valueOf(orElse);
        }

        @Override public String toString() { return size + " " + id + (orElse == null ? "" : "|" + orElse); }
    }

    /** A group of rows under an optional header (header null: no header). */
    public static final class Group {
        public final String header;
        public final List<FeatureId> rows;

        Group(String header, List<FeatureId> rows) {
            this.header = header;
            this.rows = Collections.unmodifiableList(rows);
        }
    }

    private static final Object[][] ROUND_TILES = {{EYE, FeatureId.EYE}, {GENERAL, null}};
    /** Rows of the card: one wide tile and two small ones each (stock structure), {size, feature or section, orElse}. */
    private static final Object[][][] TILE_ROWS = {
            {{WIDE, FeatureId.KEYSTONE_AK_NOW}, {SMALL, KEYSTONE}, {SMALL, FeatureId.ZOOM, FeatureId.ZOOM_DIGITAL}},
            {{WIDE, PICTURE}, {SMALL, SOUND}, {SMALL, FOCUS}},
            {{WIDE, FeatureId.INPUT}, {SMALL, FeatureId.BLUETOOTH}, {SMALL, FeatureId.NETWORK}},
            {{WIDE, GAME}, {SMALL, PROJECTION}, {SMALL, FeatureId.MIRRORING, FeatureId.USB_FILES}},
    };
    /** Section pages: {section, header-or-null, rows...} groups in order. */
    private static final Object[][] SECTIONS = {
            {KEYSTONE, new Object[][]{
                    {null, FeatureId.KEYSTONE_AK_NOW, FeatureId.KEYSTONE_FIT_NOW, FeatureId.KEYSTONE_MANUAL, FeatureId.ZOOM,
                            FeatureId.ZOOM_DIGITAL,
                            FeatureId.KEYSTONE_FIT_SCREEN, FeatureId.KEYSTONE_ROTATION, FeatureId.KEYSTONE_ANGLE_H,
                            FeatureId.KEYSTONE_ANGLE_V},
                    {"image_correction", FeatureId.KEYSTONE_REALTIME, FeatureId.KEYSTONE_ON_MOVE, FeatureId.KEYSTONE_ON_POWER, FeatureId.KEYSTONE_CURTAIN,
                            FeatureId.KEYSTONE_OBSTACLE},
                    {"more", FeatureId.KEYSTONE_RESTORE}}},
            {FOCUS, new Object[][]{
                    {null, FeatureId.FOCUS_AF_NOW, FeatureId.FOCUS_MANUAL, FeatureId.FOCUS_SMART, FeatureId.FOCUS_ON_MOVE,
                            FeatureId.FOCUS_ON_POWER}}},
            {LAMP, new Object[][]{{null, FeatureId.LAMP, FeatureId.BOOST}}},
            {PICTURE, new Object[][]{
                    {null, FeatureId.PICTURE_MODE, FeatureId.PICTURE_PQ, FeatureId.PICTURE_COLOR_TEMP, FeatureId.PICTURE_MEMC,
                            FeatureId.PICTURE_AI_CONTRAST, FeatureId.PICTURE_DYNAMIC_CONTRAST,
                            FeatureId.PICTURE_DYNAMIC_BLACK, FeatureId.PICTURE_SUPER_RES, FeatureId.PICTURE_ULTRA120,
                            FeatureId.PICTURE_PRO, FeatureId.PICTURE_RESET, FeatureId.PICTURE_RESET_ALL},
                    {"hdmi", FeatureId.HDMI_RANGE, FeatureId.HDMI_EDID, FeatureId.PICTURE_ASPECT}}},
            {SOUND, new Object[][]{
                    {null, FeatureId.SOUND_ENHANCEMENT, FeatureId.SOUND_MODE, FeatureId.SOUND_EQ, FeatureId.SOUND_LEVELING,
                            FeatureId.SOUND_EARC, FeatureId.SOUND_OUTPUT, FeatureId.SOUND_FORMAT}}},
            {PROJECTION, new Object[][]{{null, FeatureId.PROJECTION_MODE, FeatureId.PROJECTION_REAR}}},
            {GAME, new Object[][]{{null, FeatureId.GAME_MODE, FeatureId.GAME_SPEED, FeatureId.GAME_ALLM}}},
            {EYE, new Object[][]{{null, FeatureId.EYE}}},
            {GENERAL, new Object[][]{
                    {null, FeatureId.LAMP_OFF, FeatureId.SETTINGS_SYSTEM, FeatureId.SETTINGS_PROJECTOR, FeatureId.SETTINGS_CEC,
                            FeatureId.SETTINGS_RECENTS, FeatureId.SETTINGS_AMBIENT_SPEAKER, FeatureId.SETTINGS_SLEEP_TIMER,
                            FeatureId.SETTINGS_REMOTE, FeatureId.LAMP_ECO, FeatureId.SETTINGS_LAUNCHER,
                            FeatureId.SETTINGS_LICENCES}}},
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
                List<FeatureId> rs = new ArrayList<>();
                for (int i = 1; i < g.length; i++) if (p.has((FeatureId) g[i])) rs.add((FeatureId) g[i]);
                if (!rs.isEmpty()) groups.add(new Group((String) g[0], rs));
            }
            if (!groups.isEmpty()) secs.put((String) s[0], Collections.unmodifiableList(groups));
        }
        sections = Collections.unmodifiableMap(secs);
        List<Tile> rt = new ArrayList<>();
        for (Object[] t : ROUND_TILES) {
            if (t[1] == null || p.has((FeatureId) t[1])) rt.add(new Tile(ROUND, t[0], null));
        }
        round = Collections.unmodifiableList(rt);
        List<List<Tile>> rws = new ArrayList<>();
        for (Object[][] row : TILE_ROWS) {
            List<Tile> r = new ArrayList<>();
            for (Object[] t : row) {
                Object id = t[1], alt = t.length > 2 ? t[2] : null;
                boolean a = present(id), b = alt != null && present(alt);
                if (a) r.add(new Tile((String) t[0], id, b ? alt : null));
                else if (b) r.add(new Tile((String) t[0], alt, null));
            }
            if (!r.isEmpty()) rws.add(Collections.unmodifiableList(r));
        }
        rows = Collections.unmodifiableList(rws);
        List<String> all = new ArrayList<>();
        for (String c : ALL_SETTINGS) if (secs.containsKey(c) || GENERAL.equals(c)) all.add(c);
        allSettings = Collections.unmodifiableList(all);
    }

    private boolean present(Object id) {
        return id instanceof FeatureId ? profile.has((FeatureId) id) : sections.containsKey(id);
    }

    /** The menu of a profile; an invalid profile gives an empty menu (only the gear, nothing to set). */
    public static MenuSpec of(MenuProfile p) { return new MenuSpec(p); }

    /** True when the section has at least one row (its tile, popup and All settings category exist). */
    public boolean hasSection(String section) { return sections.containsKey(section); }

    /** Rows of a section in order (headers left out), empty when absent. */
    public List<FeatureId> rowsOf(String section) {
        List<FeatureId> out = new ArrayList<>();
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
    public int boostLevel() { return profile.has(FeatureId.BOOST) ? profile.number(FeatureId.BOOST, "level", -1) : -1; }

    /** True when the tile with this id (feature or section) is on the card. */
    public boolean hasTile(String id) {
        for (Tile t : round) if (t.id.equals(id)) return true;
        for (List<Tile> r : rows) for (Tile t : r) if (t.id.equals(id) || id.equals(t.orElse)) return true;
        return false;
    }
}
