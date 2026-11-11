// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every feature id the shared menu knows. A device profile may only name ids of this list;
 * the menu shows a row or tile for an id only when the profile names it. A device that needs an id this list lacks
 * puts it under "pending" in its menu.json and asks the lead of lumen-menu: device branches never edit this file.
 */
public final class Feature {
    private Feature() {}

    /** What the menu builds for a feature. */
    public enum Kind {
        /** a tile that acts by itself (opens a system page, the source popup) */
        TILE,
        /** a switch row, value "on" / "off" */
        TOGGLE,
        /** a radio list or choice row, value = one option token of the profile */
        CHOICE,
        /** a slider (or a group of sliders), value = a number inside min..max */
        SLIDER,
        /** a row that runs something once */
        ACTION,
        /** a row that opens a device screen in place of the menu */
        SCREEN,
        /** a row that only shows a value the device reads (text) */
        INFO
    }

    /** eye protection; options off/on (Z9X) or off/follow/rest (HB, X50) */
    public static final String EYE = "eye";
    /** Auto keystone now (wide Autocorrect tile + keystone row) */
    public static final String KEYSTONE_AK_NOW = "keystone.ak_now";
    /** fit to the screen now */
    public static final String KEYSTONE_FIT_NOW = "keystone.fit_now";
    /** manual keystone corners */
    public static final String KEYSTONE_MANUAL = "keystone.manual";
    /** fit to a screen with a frame (curtain fit page) */
    public static final String KEYSTONE_FIT_SCREEN = "keystone.fit_screen";
    /** picture rotation */
    public static final String KEYSTONE_ROTATION = "keystone.rotation";
    /** real-time keystone (Auto keystone when moved) */
    public static final String KEYSTONE_REALTIME = "keystone.realtime";
    /** keystone at power on */
    public static final String KEYSTONE_ON_POWER = "keystone.on_power";
    /** fit the picture to the screen (curtain) when correcting */
    public static final String KEYSTONE_CURTAIN = "keystone.curtain";
    /** avoid obstacles when correcting */
    public static final String KEYSTONE_OBSTACLE = "keystone.obstacle";
    /** restore the full picture (corners reset) */
    public static final String KEYSTONE_RESTORE = "keystone.restore";
    /** zoom screen (small Zoom tile + keystone row with its summary) */
    public static final String ZOOM = "zoom";
    /** Autofocus now */
    public static final String FOCUS_AF_NOW = "focus.af_now";
    /** manual focus */
    public static final String FOCUS_MANUAL = "focus.manual";
    /** smart autofocus */
    public static final String FOCUS_SMART = "focus.smart";
    /** autofocus when moved */
    public static final String FOCUS_ON_MOVE = "focus.on_move";
    /** autofocus at power on */
    public static final String FOCUS_ON_POWER = "focus.on_power";
    /** brightness of the light (bottom slider); min, max */
    public static final String LAMP = "lamp";
    /** Boost above the top light level (yellow); level = the slider step that means Boost */
    public static final String BOOST = "boost";
    /** picture mode; options */
    public static final String PICTURE_MODE = "picture.mode";
    /** brightness, contrast, saturation, sharpness sliders; items, min, max */
    public static final String PICTURE_PQ = "picture.pq";
    /** colour temperature; options */
    public static final String PICTURE_COLOR_TEMP = "picture.color_temp";
    /** motion smoothing; options */
    public static final String PICTURE_MEMC = "picture.memc";
    /** AI contrast */
    public static final String PICTURE_AI_CONTRAST = "picture.ai_contrast";
    /** super resolution */
    public static final String PICTURE_SUPER_RES = "picture.super_res";
    /** 120 Hz output */
    public static final String PICTURE_ULTRA120 = "picture.ultra120";
    /** expert picture page */
    public static final String PICTURE_PRO = "picture.pro";
    /** reset this picture mode */
    public static final String PICTURE_RESET = "picture.reset";
    /** reset every picture setting */
    public static final String PICTURE_RESET_ALL = "picture.reset_all";
    /** aspect ratio; options */
    public static final String PICTURE_ASPECT = "picture.aspect";
    /** HDMI colour range; options */
    public static final String HDMI_RANGE = "hdmi.range";
    /** HDMI mode (EDID version); options */
    public static final String HDMI_EDID = "hdmi.edid";
    /** sound processing (DTS Virtual:X and the rest); options */
    public static final String SOUND_ENHANCEMENT = "sound.enhancement";
    /** sound mode; options */
    public static final String SOUND_MODE = "sound.mode";
    /** equalizer page (shared rules: speaker only, not with DTS) */
    public static final String SOUND_EQ = "sound.eq";
    /** volume leveling */
    public static final String SOUND_LEVELING = "sound.leveling";
    /** eARC */
    public static final String SOUND_EARC = "sound.earc";
    /** sound output; options */
    public static final String SOUND_OUTPUT = "sound.output";
    /** digital sound format (shared DTS rules); options */
    public static final String SOUND_FORMAT = "sound.format";
    /** inputs (wide tile, popup of the sources) */
    public static final String INPUT = "input";
    /** Bluetooth settings */
    public static final String BLUETOOTH = "bluetooth";
    /** network settings */
    public static final String NETWORK = "network";
    /** screen mirroring receiver (shown only when the receiver app is installed) */
    public static final String MIRRORING = "mirroring";
    /** USB files (last small tile where mirroring is absent; needs Lumen Home Files) */
    public static final String USB_FILES = "usb_files";
    /** game mode; options */
    public static final String GAME_MODE = "game.mode";
    /** game speed (input lag) option; options */
    public static final String GAME_SPEED = "game.speed";
    /** ALLM handling; options */
    public static final String GAME_ALLM = "game.allm";
    /** projection: options auto, table, ceiling */
    public static final String PROJECTION_MODE = "projection.mode";
    /** rear projection */
    public static final String PROJECTION_REAR = "projection.rear";
    /** Android settings */
    public static final String SETTINGS_SYSTEM = "settings.system";
    /** Projector settings (two-pane SettingsActivity) */
    public static final String SETTINGS_PROJECTOR = "settings.projector";
    /** HDMI-CEC page */
    public static final String SETTINGS_CEC = "settings.cec";
    /** recent apps */
    public static final String SETTINGS_RECENTS = "settings.recents";
    /** home screen choice; options lumen, classic */
    public static final String SETTINGS_LAUNCHER = "settings.launcher";

    /** horizontal tilt of the projector, as text */
    public static final String KEYSTONE_ANGLE_H = "keystone.angle_h";
    /** vertical tilt of the projector, as text */
    public static final String KEYSTONE_ANGLE_V = "keystone.angle_v";
    /** dynamic contrast */
    public static final String PICTURE_DYNAMIC_CONTRAST = "picture.dynamic_contrast";
    /** dynamic black level enhancement */
    public static final String PICTURE_DYNAMIC_BLACK = "picture.dynamic_black";
    /** sleep timer */
    public static final String SETTINGS_SLEEP_TIMER = "settings.sleep_timer";
    /** pair the remote */
    public static final String SETTINGS_REMOTE = "settings.remote";
    /** auto keystone after the projector was moved (a row of its own where the stock lists it apart from focus) */
    public static final String KEYSTONE_ON_MOVE = "keystone.on_move";
    /** digital zoom and shift (Play 6 zoom_and_displacement): the tile slot and keystone row of zoom, no optical claim */
    public static final String ZOOM_DIGITAL = "zoom.digital";
    /** power saving (stock eco_mode_switch, battery models); the vendor changes the light level with it */
    public static final String LAMP_ECO = "lamp.eco";
    /** ambient speaker page (Bluetooth speaker mode, ambient light, what closing the projector does) */
    public static final String SETTINGS_AMBIENT_SPEAKER = "settings.ambient_speaker";

    /** Catalogue entry. */
    public static final class Def {
        public final String id;
        public final Kind kind;

        Def(String id, Kind kind) {
            this.id = id;
            this.kind = kind;
        }
    }

    /** id -> definition, in catalogue order. */
    public static final Map<String, Def> ALL;

    static {
        LinkedHashMap<String, Def> m = new LinkedHashMap<>();
        for (Def d : new Def[]{
            new Def(EYE, Kind.CHOICE),
            new Def(KEYSTONE_AK_NOW, Kind.ACTION),
            new Def(KEYSTONE_FIT_NOW, Kind.ACTION),
            new Def(KEYSTONE_MANUAL, Kind.SCREEN),
            new Def(KEYSTONE_FIT_SCREEN, Kind.SCREEN),
            new Def(KEYSTONE_ROTATION, Kind.SCREEN),
            new Def(KEYSTONE_REALTIME, Kind.TOGGLE),
            new Def(KEYSTONE_ON_POWER, Kind.TOGGLE),
            new Def(KEYSTONE_CURTAIN, Kind.TOGGLE),
            new Def(KEYSTONE_OBSTACLE, Kind.TOGGLE),
            new Def(KEYSTONE_RESTORE, Kind.ACTION),
            new Def(ZOOM, Kind.SCREEN),
            new Def(FOCUS_AF_NOW, Kind.ACTION),
            new Def(FOCUS_MANUAL, Kind.SCREEN),
            new Def(FOCUS_SMART, Kind.TOGGLE),
            new Def(FOCUS_ON_MOVE, Kind.TOGGLE),
            new Def(FOCUS_ON_POWER, Kind.TOGGLE),
            new Def(LAMP, Kind.SLIDER),
            new Def(BOOST, Kind.TOGGLE),
            new Def(PICTURE_MODE, Kind.CHOICE),
            new Def(PICTURE_PQ, Kind.SLIDER),
            new Def(PICTURE_COLOR_TEMP, Kind.CHOICE),
            new Def(PICTURE_MEMC, Kind.CHOICE),
            new Def(PICTURE_AI_CONTRAST, Kind.TOGGLE),
            new Def(PICTURE_SUPER_RES, Kind.TOGGLE),
            new Def(PICTURE_ULTRA120, Kind.TOGGLE),
            new Def(PICTURE_PRO, Kind.SCREEN),
            new Def(PICTURE_RESET, Kind.ACTION),
            new Def(PICTURE_RESET_ALL, Kind.ACTION),
            new Def(PICTURE_ASPECT, Kind.CHOICE),
            new Def(HDMI_RANGE, Kind.CHOICE),
            new Def(HDMI_EDID, Kind.CHOICE),
            new Def(SOUND_ENHANCEMENT, Kind.CHOICE),
            new Def(SOUND_MODE, Kind.CHOICE),
            new Def(SOUND_EQ, Kind.SCREEN),
            new Def(SOUND_LEVELING, Kind.TOGGLE),
            new Def(SOUND_EARC, Kind.TOGGLE),
            new Def(SOUND_OUTPUT, Kind.CHOICE),
            new Def(SOUND_FORMAT, Kind.CHOICE),
            new Def(INPUT, Kind.TILE),
            new Def(BLUETOOTH, Kind.TILE),
            new Def(NETWORK, Kind.TILE),
            new Def(MIRRORING, Kind.TILE),
            new Def(USB_FILES, Kind.TILE),
            new Def(GAME_MODE, Kind.CHOICE),
            new Def(GAME_SPEED, Kind.CHOICE),
            new Def(GAME_ALLM, Kind.CHOICE),
            new Def(PROJECTION_MODE, Kind.CHOICE),
            new Def(PROJECTION_REAR, Kind.TOGGLE),
            new Def(SETTINGS_SYSTEM, Kind.SCREEN),
            new Def(SETTINGS_PROJECTOR, Kind.SCREEN),
            new Def(SETTINGS_CEC, Kind.SCREEN),
            new Def(SETTINGS_RECENTS, Kind.SCREEN),
            new Def(SETTINGS_LAUNCHER, Kind.CHOICE),
            new Def(KEYSTONE_ANGLE_H, Kind.INFO),
            new Def(KEYSTONE_ANGLE_V, Kind.INFO),
            new Def(PICTURE_DYNAMIC_CONTRAST, Kind.TOGGLE),
            new Def(PICTURE_DYNAMIC_BLACK, Kind.TOGGLE),
            new Def(SETTINGS_SLEEP_TIMER, Kind.SCREEN),
            new Def(SETTINGS_REMOTE, Kind.SCREEN),
            new Def(KEYSTONE_ON_MOVE, Kind.TOGGLE),
            new Def(ZOOM_DIGITAL, Kind.SCREEN),
            new Def(LAMP_ECO, Kind.TOGGLE),
            new Def(SETTINGS_AMBIENT_SPEAKER, Kind.SCREEN),
        }) {
            if (m.put(d.id, d) != null) throw new IllegalStateException("duplicate feature " + d.id);
        }
        ALL = Collections.unmodifiableMap(m);
    }

    public static Def def(String id) { return ALL.get(id); }

    public static boolean known(String id) { return ALL.containsKey(id); }
}
