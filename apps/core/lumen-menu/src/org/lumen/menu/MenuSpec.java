// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What the shared menu shows for one profile: the quick panel D tiles, the rows of every
 * section page and popup, the All settings categories, the Boost step and the game menu items. The card and the
 * pages are FIXED for every projector; a section's row order and groups are this table's unless the profile gives
 * its model's stock order ({@code sections} in menu.json, the Keystone page of each stock tree). A feature the profile
 * lacks is simply left out, a header whose rows are all left out goes too, a section tile without popup rows goes
 * too, and an empty tile row goes. A section popup leaves out the rows that have a tile of their own on the card;
 * All settings keeps them. This class is the only place that decides presence; the views build exactly this list
 * ({@link #cardRows} adds the runtime part of the card).
 */
public final class MenuSpec {

    // ------------------------------------------------------------------ fixed layout (every device)

    /** Section ids (QuickPanel.SECTION_* use the same words). */
    public static final String KEYSTONE = "keystone", FOCUS = "focus", PICTURE = "picture", SOUND = "sound",
            GAME = "game", PROJECTION = "projection", EYE = "eye", GENERAL = "general", LAMP = "lamp",
            DEVELOPER = "developer";
    /**
     * The Network tile's popup (owner variant A, 2026-11-19): a section with the tile's own id, so the tile and its
     * round button open it like any popup. No section tile, no All settings category.
     */
    public static final String NETWORK = "network";
    /**
     * Stock Input source (root/signal of 1.3.38): the source list, the source at power-on, HDMI-CEC and the HDMI input
     * options. A page of All settings only (the Input tile keeps opening the sources at once).
     */
    public static final String SOURCE = "source";
    /**
     * Stock Bluetooth and accessories (root/bluetooth): the Bluetooth tile's popup and its All settings page, with the
     * tile's own id as Network: Add more devices, then the paired devices (BtPage).
     */
    public static final String BLUETOOTH = "bluetooth";
    /** The theme button of the top row (2026-11-18, in place of voice search): Светлая, Тёмная, Авто. */
    public static final String THEME = "theme";

    /** Tile sizes of layout D; a slider tile spans the card under the tile rows. */
    public static final String ROUND = "round", WIDE = "wide", SMALL = "small", SLIDER = "slider";
    /** The group header that is a thin line, no text (stock setting_common_line between two groups). */
    public static final String LINE = "line";
    /**
     * General's groups the code names: rows other modules add to General (the screensaver) go right after Display and
     * menu and always above Help, so Send a problem report stays at the bottom (owner option B 2026-11-26).
     */
    public static final String LANGUAGE_TIME = "language_time", DISPLAY_MENU = "display_menu", HELP = "help";
    /** The header id of the picture page's first group (stock image_title_v4, "Picture"). */
    public static final String PICTURE_HEADER = "image";
    /**
     * Group headers a section or a profile page may have: the stock small titles of the Keystone page (size_position,
     * smart, more), its correction settings sub page shown in place (auto_correction), the stock small titles of the
     * image page (projector_brightness, image, image_more: its More is another stock text than the keystone one) and
     * of its picture settings page (basic, colour, contrast, clarity, image_more), the Picture HDMI group, the line.
     */
    public static final Set<String> HEADERS = Collections.unmodifiableSet(new HashSet<>(java.util.Arrays.asList(
            "auto_correction", "size_position", "smart", "more", "hdmi", "projector_brightness", "image", "basic",
            "colour", "contrast", "clarity", "image_more", LINE)));

    /** A tile: {@code id} is a feature id or a section id (a section tile exists when its section has rows). */
    public static final class Tile {
        public final String size, id;
        /** The feature of a feature tile, null for a section tile. */
        public final FeatureId feature;
        /** The second candidate of the slot (zoom, else digital zoom), or null. */
        public final String orElse;
        /** True when the tile gives its place to a later tile of its row once the row has more tiles than places. */
        public final boolean yields;

        Tile(String size, Object id, Object orElse, boolean yields) {
            this.size = size;
            this.id = String.valueOf(id);
            this.feature = id instanceof FeatureId ? (FeatureId) id : null;
            this.orElse = orElse == null ? null : String.valueOf(orElse);
            this.yields = yields;
        }

        @Override public String toString() {
            return size + " " + id + (orElse == null ? "" : "|" + orElse) + (yields ? " (yields)" : "");
        }
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

    /**
     * The round buttons of the top row, left to right: a tile pushed off the card (below), the theme button (Светлая,
     * Тёмная, Авто), the gear (All settings). Network is a round button only on a model whose 3D tile took its card place. The signal panel is a
     * developer feature (2026-11-18): no round button, one row of the For developers page of All settings.
     */
    private static final Object[] ROUND_TILES = {FeatureId.NETWORK, THEME, GENERAL};
    /**
     * {tile, the tile it pushes off the card}: on a model with stock 3D the 3D tile takes the network place on the card
     * and network becomes a round button on top (a Z9X user, 2026-11-14: 3D for every film without the settings).
     */
    private static final FeatureId[][] PUSHES_UP = {{FeatureId.PICTURE_3D, FeatureId.NETWORK}};
    /** Places of one card row: one wide tile and two small ones (stock structure). */
    public static final int ROW_PLACES = 3;
    private static final String YIELDS = "yields";
    /**
     * Rows of the card, {size, feature or section, orElse, YIELDS}. Row 4: projection gives its place to screen
     * mirroring where both are there, so the row stays one wide and two small tiles. 2026-11-13: eye protection
     * is a tile and the USB tile is gone (USB files stay in the Input popup and in Lumen Home).
     */
    private static final Object[][][] TILE_ROWS = {
            {{WIDE, FeatureId.KEYSTONE_AK_NOW}, {SMALL, KEYSTONE}, {SMALL, FeatureId.ZOOM, FeatureId.ZOOM_DIGITAL}},
            {{WIDE, PICTURE}, {SMALL, SOUND}, {SMALL, FOCUS}},
            {{WIDE, FeatureId.INPUT}, {SMALL, FeatureId.BLUETOOTH}, {SMALL, FeatureId.PICTURE_3D, FeatureId.NETWORK}},
            {{WIDE, GAME}, {SMALL, PROJECTION, null, YIELDS}, {SMALL, FeatureId.MIRRORING}, {SMALL, FeatureId.EYE}},
    };
    /**
     * The slider tiles under the tile rows, top to bottom, each where the profile has its feature: the light level
     * (brightness), then the lens iris right under it (X50 family tester request 2026-11-18: the stock iris row of the
     * picture page, one step per stock option). A number slider spans the profile's range, a choice slider steps
     * through the profile's options in their stock order.
     */
    private static final FeatureId[] SLIDER_TILES = {FeatureId.LAMP, FeatureId.PICTURE_IRIS};

    /** True for a feature that can be a slider tile of the card. */
    public static boolean isSliderTile(String id) {
        for (FeatureId f : SLIDER_TILES) if (f.id.equals(id)) return true;
        return false;
    }
    /**
     * Section pages: {section, header-or-null, rows...} groups in order. A section's rows are the rows listed here (a
     * profile's stock order may only name these); the order and groups here count where the profile gives none.
     */
    private static final Object[][] SECTIONS = {
            {KEYSTONE, new Object[][]{                  // each profile names its stock order (sections.keystone)
                    {null, FeatureId.KEYSTONE_AK_NOW, FeatureId.KEYSTONE_FIT_NOW, FeatureId.KEYSTONE_MANUAL,
                            FeatureId.FOCUS_SETTINGS, FeatureId.ZOOM,
                            FeatureId.ZOOM_DIGITAL, FeatureId.ZOOM_MOVE, FeatureId.KEYSTONE_ROTATION,
                            FeatureId.KEYSTONE_WALL_COLOR, FeatureId.KEYSTONE_COLOR_UNIFORMITY, FeatureId.ZOOM_MEMORY},
                    {"auto_correction", FeatureId.KEYSTONE_REALTIME, FeatureId.KEYSTONE_ON_POWER, FeatureId.KEYSTONE_ON_MOVE,
                            FeatureId.KEYSTONE_OBSTACLE, FeatureId.KEYSTONE_CURTAIN},
                    {"more", FeatureId.KEYSTONE_RESTORE, FeatureId.KEYSTONE_ANGLE_H, FeatureId.KEYSTONE_ANGLE_V}}},
            {FOCUS, new Object[][]{
                    {null, FeatureId.FOCUS_AF_NOW, FeatureId.FOCUS_MANUAL, FeatureId.FOCUS_SMART,
                            FeatureId.FOCUS_GUIDE}}},
            {LAMP, new Object[][]{{null, FeatureId.LAMP, FeatureId.BOOST}}},
            {PICTURE, new Object[][]{                   // stock image page: the iris under the light level, then the mode
                    {null, FeatureId.PICTURE_IRIS, FeatureId.PICTURE_MODE, FeatureId.PICTURE_DV_SCREEN,
                            FeatureId.PICTURE_CUSTOM_NEW,
                            FeatureId.PICTURE_SETTINGS, FeatureId.PICTURE_CUSTOM_BASE, FeatureId.PICTURE_AUTO_SWITCH, FeatureId.PICTURE_PQ, FeatureId.PICTURE_COLOR_TEMP,
                            FeatureId.PICTURE_MEMC, FeatureId.PICTURE_WB, FeatureId.PICTURE_GAMUT, FeatureId.PICTURE_COLOR_OPT,
                            FeatureId.PICTURE_CC, FeatureId.PICTURE_GAMMA, FeatureId.PICTURE_GAMMA_INDEX, FeatureId.PICTURE_HDR,
                            FeatureId.PICTURE_LOCAL_CONTRAST, FeatureId.PICTURE_DYNAMIC_CONTRAST, FeatureId.PICTURE_AI_CONTRAST,
                            FeatureId.PICTURE_DB_PAGE, FeatureId.PICTURE_DYNAMIC_BLACK, FeatureId.PICTURE_BLACK_TUNING,
                            FeatureId.PICTURE_NOISE, FeatureId.PICTURE_SUPER_RES,
                            FeatureId.PICTURE_ULTRA120, FeatureId.PICTURE_PRO, FeatureId.PICTURE_RESET, FeatureId.PICTURE_CUSTOM_DELETE,
                            FeatureId.PICTURE_RESET_ALL, FeatureId.PICTURE_STYLE_FILTER,
                            FeatureId.PICTURE_ANTI_RBE, FeatureId.PICTURE_3D},
                    {"hdmi", FeatureId.HDMI_RANGE, FeatureId.HDMI_EDID, FeatureId.PICTURE_ASPECT}}},
            {SOUND, new Object[][]{
                    {null, FeatureId.SOUND_ENHANCEMENT, FeatureId.SOUND_MODE, FeatureId.SOUND_EQ, FeatureId.SOUND_LEVELING,
                            FeatureId.SOUND_EARC, FeatureId.SOUND_OUTPUT, FeatureId.SOUND_FORMAT,
                            FeatureId.SOUND_KEY_TONE}}},
            {PROJECTION, new Object[][]{{null, FeatureId.PROJECTION_MODE, FeatureId.PROJECTION_REAR}}},
            {GAME, new Object[][]{{null, FeatureId.GAME_MODE, FeatureId.GAME_SPEED, FeatureId.GAME_ALLM}}},
            {EYE, new Object[][]{{null, FeatureId.EYE}}},
            {NETWORK, new Object[][]{                   // stock Settings > Network: Wi-Fi, a line, the hotspot rows
                    {null, FeatureId.NETWORK_WIFI},
                    {LINE, FeatureId.NETWORK_HOTSPOT, FeatureId.NETWORK_HOTSPOT_NAME, FeatureId.NETWORK_HOTSPOT_PASSWORD,
                            FeatureId.NETWORK_HOTSPOT_RESET}}},
            {SOURCE, new Object[][]{                    // stock root/signal: the list, then its global settings
                    {null, FeatureId.SOURCE_LIST, FeatureId.SETTINGS_CEC, FeatureId.SETTINGS_HDMI}}},
            {BLUETOOTH, new Object[][]{{null, FeatureId.BLUETOOTH_ADD,     // stock root/bluetooth (BtPage adds the devices)
                    FeatureId.BLUETOOTH_ABS_VOLUME}}},                  // and its other group: absolute volume
            // owner option B 2026-11-26: General grouped under headers, Send a problem report at its bottom under Help
            // (user reports are the main bug source), picture off only in the power menu and the quick menu
            {GENERAL, new Object[][]{
                    {LANGUAGE_TIME, FeatureId.SETTINGS_LANGUAGE, FeatureId.SETTINGS_DATE_TIME, FeatureId.VOICE_LANGUAGE},
                    {"power", FeatureId.SETTINGS_POWER_KEY, FeatureId.SETTINGS_SLEEP_TIMER, FeatureId.LAMP_ECO},
                    {DISPLAY_MENU, FeatureId.EYE, FeatureId.SETTINGS_SCREENSAVER_NOW, FeatureId.SETTINGS_SCREENSAVER_DELAY,
                            FeatureId.EYE_ELDER_COLOR, FeatureId.SETTINGS_COLOUR_VISION, FeatureId.SETTINGS_MENU_SIDE,
                            FeatureId.SETTINGS_MENU_DENSITY},
                    {"remote_voice", FeatureId.SETTINGS_REMOTE, FeatureId.SETTINGS_REMOTE_KEYS, FeatureId.VOICE_SPEAK,
                            FeatureId.VOICE_KEY},
                    {"apps_devices", FeatureId.SETTINGS_LAUNCHER, FeatureId.SETTINGS_RECENTS, FeatureId.SETTINGS_CASTING,
                            FeatureId.SETTINGS_AMBIENT_SPEAKER, FeatureId.SETTINGS_PARENTAL, FeatureId.SETTINGS_SERIAL},
                    // stock small_title_more: Advanced settings (more_setting), its power on and off page behind it
                    {"more", FeatureId.SETTINGS_ADVANCED},
                    {"storage_updates", FeatureId.SETTINGS_STORAGE, FeatureId.SETTINGS_UPDATE, FeatureId.SETTINGS_ABOUT},
                    {HELP, FeatureId.SETTINGS_REPORT, FeatureId.SETTINGS_STATS, FeatureId.SETTINGS_LICENCES,
                            FeatureId.SETTINGS_SYSTEM}}},
            // 2026-11-19: the developer tools are one page of their own, the last of All settings (as on Android),
            // never rows of General, a section tile or a round button; option B 2026-11-26: the technical rows of General
            // (interface resolution) and the Android developer screens in its System group
            // the temperature is a readout (X50 live audit 2026-11-24: the Sensors card shows the same value)
            {DEVELOPER, new Object[][]{{null, FeatureId.SIGNAL_PANEL, FeatureId.SENSORS_PANEL, FeatureId.SIGNAL_PIN,
                    FeatureId.SIGNAL_REFRESH, FeatureId.SETTINGS_TEMPERATURE, FeatureId.SETTINGS_DIAGNOSTICS,
                    FeatureId.MIRROR_DIAG},
                    {"system", FeatureId.SETTINGS_UI_RESOLUTION, FeatureId.DEV_SYSTEM_INFO, FeatureId.DEV_NETWORK_ADB,
                            FeatureId.DEV_ANDROID}}},
    };
    /**
     * {section, row of another section}: a row a profile's stock order may also show on this section's page. The stock
     * image page starts with the light level (brightness_adjustment), while the card keeps its brightness slider.
     */
    private static final Object[][] ALSO_IN = {{PICTURE, FeatureId.LAMP}, {KEYSTONE, FeatureId.FOCUS_AF_NOW},
            {KEYSTONE, FeatureId.FOCUS_MANUAL}, {KEYSTONE, FeatureId.FOCUS_SMART}, {KEYSTONE, FeatureId.FOCUS_GUIDE},
            {KEYSTONE, FeatureId.PROJECTION_MODE}, {KEYSTONE, FeatureId.PROJECTION_REAR}};
    /**
     * Sections whose rows a profile may move onto another section's page (TITAN testers 2026-11-19: auto and manual
     * focus on the Image correction page with a smart focus sub page, as the keystone rows). The section keeps its card
     * tile and popup, the other popup leaves the moved rows out (the card reaches each row once), and All settings
     * drops the section's own category once every row of it stands on the other page.
     */
    private static final String[] MOVABLE = {FOCUS, PROJECTION, EYE};

    /** True when a profile's stock order of {@code section} may also show {@code f}, a row of another section. */
    public static boolean alsoIn(String section, FeatureId f) {
        for (Object[] a : ALSO_IN) if (a[0].equals(section) && a[1] == f) return true;
        return false;
    }

    /**
     * Screens of shared modules that the menu opens itself (no adapter code, no per-device copy): a profile names
     * them like any feature, so a model without the row has none, and the adapter does not implement them. Pair
     * remote and Remote buttons (apps/core/lumen-remote) are two rows of General on every model (2026-11-24: the X50
     * family had its own pairing page with Remote buttons inside it).
     */
    public static final Set<FeatureId> MODULE_SCREENS = Collections.unmodifiableSet(EnumSet.of(FeatureId.SETTINGS_REPORT,
            FeatureId.SETTINGS_REMOTE, FeatureId.SETTINGS_REMOTE_KEYS,
            FeatureId.SIGNAL_PANEL, FeatureId.SENSORS_PANEL, FeatureId.SETTINGS_SCREENSAVER_NOW,
            FeatureId.KEYSTONE_WALL_COLOR, FeatureId.SETTINGS_PARENTAL, FeatureId.SETTINGS_CASTING, FeatureId.NETWORK_WIFI,
            FeatureId.NETWORK_HOTSPOT_NAME,
            FeatureId.NETWORK_HOTSPOT_PASSWORD, FeatureId.NETWORK_HOTSPOT_RESET, FeatureId.SOURCE_LIST,
            FeatureId.BLUETOOTH_ADD, FeatureId.SETTINGS_HDMI, FeatureId.MIRROR_DIAG, FeatureId.SETTINGS_LICENCES,
            FeatureId.SETTINGS_LANGUAGE, FeatureId.SETTINGS_DATE_TIME, FeatureId.SETTINGS_STORAGE, FeatureId.SETTINGS_UPDATE,
            FeatureId.SETTINGS_ABOUT, FeatureId.DEV_SYSTEM_INFO, FeatureId.DEV_ANDROID));

    /**
     * Settings of shared modules the menu reads and writes itself (ModuleScreens, no device adapter): a profile names
     * them like any feature, the module decides whether the row is there on this model. The key sounds and colour
     * vision are Android settings (lumen-system), the same on every projector whose stock tree has the row, and so is
     * the hotspot switch (lumen-system Hotspot). The power-on melody is Lumen's own sound (lumen-audio BootSound), the
     * same on every projector.
     */
    public static final Set<FeatureId> MODULE_SETTINGS = Collections.unmodifiableSet(EnumSet.of(FeatureId.SIGNAL_PIN,
            FeatureId.SIGNAL_REFRESH, FeatureId.SOUND_KEY_TONE, FeatureId.SETTINGS_COLOUR_VISION,
            FeatureId.SETTINGS_SCREENSAVER_DELAY, FeatureId.NETWORK_HOTSPOT, FeatureId.POWER_ON_MUSIC,
            FeatureId.SETTINGS_MENU_SIDE, FeatureId.SETTINGS_MENU_DENSITY, FeatureId.SETTINGS_POWER_KEY,
            FeatureId.DEV_NETWORK_ADB, FeatureId.BLUETOOTH_ABS_VOLUME));

    /**
     * Rows with no place in All settings that the quick menu editor still offers: picture off (option B 2026-11-26: it
     * left General, the power menu and the quick menu keep it).
     */
    public static final Set<FeatureId> QUICK_ONLY = Collections.unmodifiableSet(EnumSet.of(FeatureId.LAMP_OFF));

    /**
     * {row, the tile it belongs to}: a profile with the row names the tile too (MenuProfile), as boost needs lamp: the
     * Bluetooth page is the Bluetooth tile's, the Input source list opens what the Input tile opens.
     */
    static final FeatureId[][] REQUIRES = {{FeatureId.BLUETOOTH_ADD, FeatureId.BLUETOOTH},
            {FeatureId.SOURCE_LIST, FeatureId.INPUT}};

    /** The Network popup's rows: a profile names all of them or none (MenuProfile), with the Network tile. */
    public static final Set<FeatureId> HOTSPOT_ROWS = Collections.unmodifiableSet(EnumSet.of(FeatureId.NETWORK_WIFI,
            FeatureId.NETWORK_HOTSPOT, FeatureId.NETWORK_HOTSPOT_NAME, FeatureId.NETWORK_HOTSPOT_PASSWORD,
            FeatureId.NETWORK_HOTSPOT_RESET));

    /**
     * Rows that only show the profile's own data (the focus page's remote picture: the profile hint), so no device
     * adapter has a part in them.
     */
    public static final Set<FeatureId> MODULE_INFO = Collections.unmodifiableSet(EnumSet.of(FeatureId.FOCUS_GUIDE));

    /**
     * Pages of the menu's own grouping (the smart focus page of the Image correction page): only rows of other features
     * open behind them, so no device adapter has a part in the page row itself.
     */
    public static final Set<FeatureId> MODULE_PAGES = Collections.unmodifiableSet(EnumSet.of(FeatureId.FOCUS_SETTINGS,
            FeatureId.SETTINGS_ADVANCED, FeatureId.SETTINGS_POWER_ON_OFF));

    /**
     * Module settings every projector has, so no profile names them: the quick menu editor offers them (the volume,
     * 2026-11-18: an optional item, never in the default menu). Nothing in the section pages, the snapshots or
     * the device adapters.
     */
    public static final Set<FeatureId> EVERY_MODEL = Collections.unmodifiableSet(EnumSet.of(FeatureId.SOUND_VOLUME));

    /** True for a module screen or setting: the menu handles it, the device adapter has no part in it. */
    public static boolean module(FeatureId f) {
        return MODULE_SCREENS.contains(f) || MODULE_SETTINGS.contains(f) || MODULE_INFO.contains(f)
                || MODULE_PAGES.contains(f) || EVERY_MODEL.contains(f);
    }

    /**
     * All settings, the category rail in this order: General first (owner option B 2026-11-26), then the stock 1.3.38
     * rail (X50 live audit 2026-11-24): Picture correction (Keystone with focus and the projection rows where the
     * profile's stock order has them), Brightness and display (Picture), Network, Input source, Bluetooth and
     * accessories, Sound, then For developers last. A {@link #MOVABLE} section whose rows all stand on another page has
     * no category of its own.
     */
    private static final String[] ALL_SETTINGS = {GENERAL, KEYSTONE, FOCUS, PICTURE, PROJECTION, NETWORK, SOURCE, BLUETOOTH,
            SOUND, EYE, DEVELOPER};

    /** True for a section that is only a page of All settings (General, Input source, For developers): no popup. */
    public static boolean allSettingsOnly(String section) {
        return GENERAL.equals(section) || DEVELOPER.equals(section) || SOURCE.equals(section);
    }
    /**
     * Pages of the menu's own rows ({@link FeatureKind#PAGE}): {page, header-or-null, rows...} groups, in the stock
     * order of each page (newsettings image_mode_setting). A page is there when the profile names it and at least
     * one of its rows (a page row counts when its own page is there); a row of a page needs that page in the profile
     * (MenuProfile). A page that holds pages is listed after them.
     */
    private static final Object[][] PAGES = {
            {FeatureId.SETTINGS_SERIAL, new Object[][]{{null, FeatureId.SERIAL_SWITCH, FeatureId.SERIAL_ID_GROUP,
                    FeatureId.SERIAL_ID_NUMBER, FeatureId.SERIAL_ACK_GROUP, FeatureId.SERIAL_ACK_ALL, FeatureId.SERIAL_MASTER}}},
            {FeatureId.PICTURE_WB2, new Object[][]{{null, FeatureId.PICTURE_WB2_RED_GAIN, FeatureId.PICTURE_WB2_GREEN_GAIN,
                    FeatureId.PICTURE_WB2_BLUE_GAIN, FeatureId.PICTURE_WB2_RED_OFFSET, FeatureId.PICTURE_WB2_GREEN_OFFSET,
                    FeatureId.PICTURE_WB2_BLUE_OFFSET, FeatureId.PICTURE_WB2_RESET}}},
            {FeatureId.PICTURE_WB11, new Object[][]{{null, FeatureId.PICTURE_WB11_LEVEL, FeatureId.PICTURE_WB11_RED,
                    FeatureId.PICTURE_WB11_GREEN, FeatureId.PICTURE_WB11_BLUE, FeatureId.PICTURE_WB11_RESET}}},
            {FeatureId.PICTURE_WB, new Object[][]{{null, FeatureId.PICTURE_WB2, FeatureId.PICTURE_WB11}}},
            {FeatureId.PICTURE_CC, new Object[][]{{null, FeatureId.PICTURE_CC_COLOR, FeatureId.PICTURE_CC_HUE,
                    FeatureId.PICTURE_CC_SATURATION, FeatureId.PICTURE_CC_LIGHTNESS, FeatureId.PICTURE_CC_RESET}}},
            {FeatureId.PICTURE_HDR, new Object[][]{{null, FeatureId.PICTURE_HDR_MAPPING, FeatureId.PICTURE_HDR_SCREEN_SIZE,
                    FeatureId.PICTURE_HDR_SCREEN_GAIN}}},
            {FeatureId.PICTURE_DV_SCREEN, new Object[][]{{null, FeatureId.PICTURE_DV_SCREEN_SIZE,
                    FeatureId.PICTURE_DV_SCREEN_GAIN}}},
            {FeatureId.PICTURE_DB_PAGE, new Object[][]{{null, FeatureId.PICTURE_DB_SWITCH, FeatureId.PICTURE_DB_LEVEL,
                    FeatureId.PICTURE_DB_SUBTITLE_TOP, FeatureId.PICTURE_DB_SUBTITLE_BOTTOM, FeatureId.PICTURE_DB_RESET}}},
            // stock General > Advanced settings (more_setting) > Power on and off (boot_and_power_off_settings), in the
            // stock order: signal_boot_source_info, voice_boot_music, power_on; then the advanced page itself
            {FeatureId.SETTINGS_POWER_ON_OFF, new Object[][]{{null, FeatureId.POWER_BOOT_SOURCE, FeatureId.POWER_ON_MUSIC,
                    FeatureId.POWER_ON_AC}}},
            {FeatureId.SETTINGS_ADVANCED, new Object[][]{{null, FeatureId.SETTINGS_POWER_ON_OFF,
                    FeatureId.SETTINGS_HIGH_ALTITUDE}}},
    };

    /** True when {@code f} is one of the fixed pages of {@link #PAGES} (not a page a profile gives itself). */
    public static boolean isTablePage(FeatureId f) {
        for (Object[] pg : PAGES) if (pg[0] == f) return true;
        return false;
    }

    /** The page whose rows list {@code f}, or null for a row of a section. */
    public static FeatureId pageOf(FeatureId f) {
        for (Object[] pg : PAGES) {
            for (Object[] g : (Object[][]) pg[1]) {
                for (int i = 1; i < g.length; i++) if (g[i] == f) return (FeatureId) pg[0];
            }
        }
        return null;
    }

    /** The section whose rows list {@code f}, or null for a feature that is no section row (a tile, a page row). */
    public static String sectionOf(FeatureId f) {
        for (Object[] s : SECTIONS) {
            for (Object[] g : (Object[][]) s[1]) {
                for (int i = 1; i < g.length; i++) if (g[i] == f) return (String) s[0];
            }
        }
        return null;
    }

    /** True when {@code id} is a section of the section table. */
    public static boolean isSection(String id) {
        for (Object[] s : SECTIONS) if (s[0].equals(id)) return true;
        return false;
    }

    // ------------------------------------------------------------------ result for one profile

    public final MenuProfile profile;
    public final List<Tile> round;
    public final List<List<Tile>> rows;
    /** The slider tiles of the card, top to bottom ({@link #SLIDER_TILES} the profile has). */
    public final List<Tile> sliders;
    /** Section pages in full: All settings shows these. */
    public final Map<String, List<Group>> sections;
    /**
     * The popup of a section tile: the section without the rows that are tiles of their own on the card (keystone
     * without Auto keystone now and zoom), so the card reaches every row once.
     */
    public final Map<String, List<Group>> popups;
    public final List<String> allSettings;
    /** The pages of the menu's own rows the profile has ({@link FeatureKind#PAGE} rows), in table order. */
    public final Map<FeatureId, List<Group>> pages;
    /**
     * Rows a profile shows on another section's page ({@link #MOVABLE}: the focus rows on the Image correction page),
     * each with its own section: only that section's popup has it, the other page's popup leaves it out.
     */
    public final Map<FeatureId, String> moved;

    private MenuSpec(MenuProfile p) {
        profile = p;
        pages = pages(p);
        sections = sections(p, sec -> Collections.emptySet(), pages);
        Set<FeatureId> card = cardFeatures(p);
        moved = Collections.unmodifiableMap(moved(p));
        popups = sections(p, sec -> {                          // a moved row stays only in its own section's popup
            Set<FeatureId> leave = new HashSet<>(card);
            for (Map.Entry<FeatureId, String> e : moved.entrySet()) if (!sec.equals(e.getValue())) leave.add(e.getKey());
            return leave;
        }, pages);
        List<Tile> rt = new ArrayList<>();
        for (Object t : ROUND_TILES) {
            if (!(t instanceof FeatureId) || roundShown((FeatureId) t)) rt.add(new Tile(ROUND, t, null, false));
        }
        round = Collections.unmodifiableList(rt);
        List<List<Tile>> rws = new ArrayList<>();
        for (Object[][] row : TILE_ROWS) {
            List<Tile> r = new ArrayList<>();
            for (Object[] t : row) {
                Object id = t[1], alt = t.length > 2 ? t[2] : null;
                boolean a = present(id), b = alt != null && present(alt), yields = t.length > 3 && YIELDS.equals(t[3]);
                if (a) r.add(new Tile((String) t[0], id, b ? alt : null, yields));
                else if (b) r.add(new Tile((String) t[0], alt, null, yields));
            }
            if (!r.isEmpty()) rws.add(Collections.unmodifiableList(r));
        }
        rows = Collections.unmodifiableList(rws);
        List<Tile> sl = new ArrayList<>();
        for (FeatureId f : SLIDER_TILES) if (present(f) && sliderRange(f) != null) sl.add(new Tile(SLIDER, f, null, false));
        sliders = Collections.unmodifiableList(sl);
        List<String> all = new ArrayList<>();
        for (String c : ALL_SETTINGS) {
            if ((sections.containsKey(c) || GENERAL.equals(c)) && !allMoved(c, sections, pages)) all.add(c);
        }
        allSettings = Collections.unmodifiableList(all);
    }

    /**
     * The section groups with the rows the profile has, minus {@code leaveOut}, in the profile's stock order where it
     * gives one; empty groups and sections go.
     */
    /**
     * The rows of a {@link #MOVABLE} section the profile shows on another section's page, and a page of the profile
     * whose rows all moved (focus.settings), each with the section it came from: only that section's popup keeps it.
     */
    private static Map<FeatureId, String> moved(MenuProfile p) {
        Map<FeatureId, String> out = new java.util.HashMap<>();
        for (Object[] s : SECTIONS) {                         // a profile's stock order, else the shared table
            Object[][] order = p.order((String) s[0]);
            if (order == null) order = (Object[][]) s[1];
            for (Object[] g : order) {
                for (int i = 1; i < g.length; i++) {
                    FeatureId f = (FeatureId) g[i];
                    if (movable(sectionOf(f)) && !s[0].equals(sectionOf(f))) out.put(f, sectionOf(f));
                }
            }
        }
        for (Map.Entry<FeatureId, Object[][]> e : p.pageOrders().entrySet()) {
            String home = null;
            boolean all = true;
            for (Object[] g : e.getValue()) {
                for (int i = 1; i < g.length; i++) {
                    String sec = sectionOf((FeatureId) g[i]);
                    if (!movable(sec) || home != null && !home.equals(sec)) all = false;
                    else home = sec;
                }
            }
            if (all && home != null) {
                for (Object[] g : e.getValue()) for (int i = 1; i < g.length; i++) out.put((FeatureId) g[i], home);
                out.put(e.getKey(), home);
            }
        }
        return out;
    }

    private static boolean movable(String section) {
        for (String m : MOVABLE) if (m.equals(section)) return true;
        return false;
    }

    /** True when every row of {@link #MOVABLE} section {@code c} stands on another section's page (no own category). */
    private static boolean allMoved(String c, Map<String, List<Group>> sections, Map<FeatureId, List<Group>> pages) {
        if (!movable(c) || !sections.containsKey(c)) return false;
        Set<FeatureId> elsewhere = new HashSet<>();
        for (Map.Entry<String, List<Group>> e : sections.entrySet()) {
            if (e.getKey().equals(c)) continue;
            for (Group g : e.getValue()) {
                for (FeatureId f : g.rows) {
                    elsewhere.add(f);
                    List<Group> sub = pages.get(f);
                    if (sub != null) for (Group sg : sub) elsewhere.addAll(sg.rows);
                }
            }
        }
        for (Group g : sections.get(c)) for (FeatureId f : g.rows) if (!elsewhere.contains(f)) return false;
        return true;
    }

    private static Map<String, List<Group>> sections(MenuProfile p, java.util.function.Function<String, Set<FeatureId>> leaveOut,
                                                     Map<FeatureId, List<Group>> pages) {
        LinkedHashMap<String, List<Group>> secs = new LinkedHashMap<>();
        for (Object[] s : SECTIONS) {
            Object[][] order = p.order((String) s[0]);
            List<Group> groups = groups(order != null ? order : (Object[][]) s[1], p, leaveOut.apply((String) s[0]), pages);
            if (!groups.isEmpty()) secs.put((String) s[0], Collections.unmodifiableList(groups));
        }
        return Collections.unmodifiableMap(secs);
    }

    /**
     * The pages with at least one row in the profile (pages holding pages come after them in PAGES); MenuProfile
     * refuses a row whose page is missing, so a page with a row is always named.
     */
    private static Map<FeatureId, List<Group>> pages(MenuProfile p) {
        LinkedHashMap<FeatureId, List<Group>> out = new LinkedHashMap<>();
        for (Object[] pg : PAGES) {
            FeatureId f = (FeatureId) pg[0];
            List<Group> groups = groups((Object[][]) pg[1], p, Collections.emptySet(), out);
            if (!groups.isEmpty()) out.put(f, Collections.unmodifiableList(groups));
        }
        for (FeatureId f : p.pageOrders().keySet()) {            // the profile's own pages (picture.settings) hold table pages
            List<Group> groups = groups(p.pageOrders().get(f), p, Collections.emptySet(), out);
            if (!groups.isEmpty()) out.put(f, Collections.unmodifiableList(groups));
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * Groups with the rows the profile has (a page row only with its page), minus {@code leaveOut}; empty ones go, and
     * a line that would open the page is no line (a line only stands between two groups).
     */
    private static List<Group> groups(Object[][] table, MenuProfile p, Set<FeatureId> leaveOut, Map<FeatureId, List<Group>> pages) {
        List<Group> groups = new ArrayList<>();
        for (Object[] g : table) {
            List<FeatureId> rs = new ArrayList<>();
            for (int i = 1; i < g.length; i++) {
                FeatureId f = (FeatureId) g[i];
                if (p.has(f) && !leaveOut.contains(f) && (f.kind != FeatureKind.PAGE || pages.containsKey(f))) rs.add(f);
            }
            if (!rs.isEmpty()) groups.add(new Group(groups.isEmpty() && LINE.equals(g[0]) ? null : (String) g[0], rs));
        }
        return groups;
    }

    /** The features with a feature tile on the card: per slot the first candidate the profile has, else the second. */
    private static Set<FeatureId> cardFeatures(MenuProfile p) {
        Set<FeatureId> out = new HashSet<>();
        for (Object[][] row : TILE_ROWS) {
            for (Object[] t : row) {
                for (int i = 1; i < t.length; i++) {
                    if (t[i] instanceof FeatureId && p.has((FeatureId) t[i])) {
                        out.add((FeatureId) t[i]);
                        break;
                    }
                }
            }
        }
        return out;
    }

    private static Object candidate(String id) {
        FeatureId f = FeatureId.known(id);
        return f != null ? f : id;
    }

    /** The feature whose card place {@code f} takes (it goes up to the round buttons then), else null. */
    public static FeatureId pushesOff(FeatureId f) {
        for (FeatureId[] p : PUSHES_UP) if (p[0] == f) return p[1];
        return null;
    }

    /** True when {@code f} is there and a tile that pushes it off the card is there too. */
    private static boolean pushedUp(FeatureId f, Predicate<Object> there) {
        for (FeatureId[] p : PUSHES_UP) if (p[1] == f && there.test(p[0]) && there.test(f)) return true;
        return false;
    }

    /** A feature tile needs its feature, a section tile a popup with rows. */
    /** A feature's round button: a tile pushed off the card only when pushed, any other one when the profile has it. */
    private boolean roundShown(FeatureId f) {
        return pushTarget(f) ? pushedUp(f, this::present) : present(f);
    }

    /** True for a card tile that becomes a round button only when another tile takes its card place (network). */
    private static boolean pushTarget(FeatureId f) {
        for (FeatureId[] p : PUSHES_UP) if (p[1] == f) return true;
        return false;
    }

    private boolean present(Object id) {
        return id instanceof FeatureId ? profile.has((FeatureId) id) : popups.containsKey(id);
    }

    /** The menu of a profile; an invalid profile gives an empty menu (only the gear, nothing to set). */
    public static MenuSpec of(MenuProfile p) { return new MenuSpec(p); }

    /**
     * The round buttons shown now: of {@link #round} the ones {@code usable} accepts, a pushed up tile only while the
     * tile that took its card place is usable (else it stays on the card, {@link #cardRows}).
     */
    public List<Tile> roundRow(Predicate<String> usable) {
        List<Tile> out = new ArrayList<>();
        for (Tile t : round) {
            if (!usable.test(t.id)) continue;
            if (t.feature != null && pushTarget(t.feature) && !pushedUp(t.feature, o -> usable.test(String.valueOf(o)))) continue;
            out.add(t);
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * The card rows shown now: of each slot the first candidate {@code usable} accepts (the runtime part: the
     * receiver app, a usable feature), a row with more tiles than {@link #ROW_PLACES} drops its yielding tiles
     * first, an empty row goes.
     */
    public List<List<Tile>> cardRows(Predicate<String> usable) {
        List<List<Tile>> out = new ArrayList<>();
        for (List<Tile> row : rows) {
            List<Tile> r = new ArrayList<>();
            for (Tile t : row) {
                String id = usable.test(t.id) ? t.id : t.orElse != null && usable.test(t.orElse) ? t.orElse : null;
                if (id != null) r.add(t.orElse == null ? t : new Tile(t.size, candidate(id), null, t.yields));
            }
            for (int i = 0; r.size() > ROW_PLACES && i < r.size(); ) {
                if (r.get(i).yields) r.remove(i);
                else i++;
            }
            if (!r.isEmpty()) out.add(Collections.unmodifiableList(r));
        }
        return Collections.unmodifiableList(out);
    }

    /** True when the section has at least one row (its tile, popup and All settings category exist). */
    public boolean hasSection(String section) { return sections.containsKey(section); }

    /** Rows of a section in order (headers left out), empty when absent. */
    public List<FeatureId> rowsOf(String section) { return flat(sections.get(section)); }

    /** Rows of a section's popup in order (headers left out), empty when absent. */
    public List<FeatureId> popupRowsOf(String section) { return flat(popups.get(section)); }

    /** Rows of a page in order (headers left out), empty when the profile has no such page. */
    public List<FeatureId> pageRowsOf(FeatureId page) { return flat(pages.get(page)); }

    private static List<FeatureId> flat(List<Group> gs) {
        List<FeatureId> out = new ArrayList<>();
        if (gs != null) for (Group g : gs) out.addAll(g.rows);
        return out;
    }

    /** True when the header has at least one row in this profile. */
    public boolean hasHeader(String section, String header) {
        List<Group> gs = sections.get(section);
        if (gs != null) for (Group g : gs) if (header.equals(g.header)) return true;
        return false;
    }

    /** A header is on screen only while one of its rows is: never a header without rows. */
    public boolean headerShown(List<FeatureId> rows, MenuState st) {
        for (FeatureId f : rows) if (rowShown(f, st)) return true;
        return false;
    }

    /** A line ({@link #LINE}) is on screen only with a shown row above it and one below: never at the top of a page. */
    public boolean lineShown(List<FeatureId> above, List<FeatureId> rows, MenuState st) {
        return headerShown(above, st) && headerShown(rows, st);
    }

    /** The row of {@code f} is on screen in {@code st}; the picture sliders count while one of them is. */
    public boolean rowShown(FeatureId f, MenuState st) {
        if (st == null) return true;
        if (st.hidden(f)) return false;
        if (FeatureId.PICTURE_PQ != f) return true;
        for (String item : profile.items(f)) if (!st.hidden(FeatureId.pq(item))) return true;
        return false;
    }

    /** The Boost step of the light slider, or -1 when the model has no Boost. */
    public int boostLevel() { return profile.has(FeatureId.BOOST) ? profile.number(FeatureId.BOOST, "level", -1) : -1; }

    /** True when the tile with this id (feature or section) is on the card. */
    public boolean hasTile(String id) {
        for (Tile t : round) if (t.id.equals(id)) return true;
        for (List<Tile> r : rows) for (Tile t : r) if (t.id.equals(id) || id.equals(t.orElse)) return true;
        for (Tile t : sliders) if (t.id.equals(id)) return true;
        return false;
    }

    /**
     * The steps of a slider tile: a number slider's profile range {min, max}, a choice slider's option indexes
     * {0, options - 1}; null when the profile cannot draw it as a slider (a choice with fewer than two options).
     */
    public int[] sliderRange(FeatureId f) {
        if (f.kind == FeatureKind.CHOICE) {
            int n = profile.options(f).size();
            return n < 2 ? null : new int[]{0, n - 1};
        }
        return new int[]{profile.number(f, "min", 0), profile.number(f, "max", 0)};
    }

    /**
     * The step a choice slider shows for {@code token} in {@code options} (the options listed now), or null when the
     * value is unknown or not one of them (the knob then shows nothing, never an invented step).
     */
    public static Integer sliderStep(List<String> options, String token) {
        int i = token == null || options == null ? -1 : options.indexOf(token);
        return i < 0 ? null : i;
    }

    /** The option token of step {@code step} of a choice slider, or null outside the options. */
    public static String sliderToken(List<String> options, int step) {
        return options == null || step < 0 || step >= options.size() ? null : options.get(step);
    }

    /**
     * The audio format row sits at the bottom of the sound output page (owner variant C, 2026-11-18) wherever the profile
     * has that page; without it, it stays a row of the sound section.
     */
    public static boolean onOutputPage(FeatureId f, MenuProfile p) {
        return FeatureId.SOUND_FORMAT == f && p != null && p.has(FeatureId.SOUND_OUTPUT);
    }
}
