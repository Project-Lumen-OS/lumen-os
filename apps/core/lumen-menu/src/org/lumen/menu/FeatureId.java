// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

import java.util.HashMap;
import java.util.Map;

/**
 * Every feature id the shared menu knows, the one declared id set: a device profile may only
 * name these ids (apps/build_apk.sh checks the app's menu.json against this file with tools/menu_ids.py), the menu
 * shows a row or tile only for an id the profile names, and adapters switch on these constants, so a typo fails the
 * build. The picture sliders are ids of their own ({@link #PQ_BRIGHTNESS} ...): an
 * item of {@link #PICTURE_PQ}, never named by a profile. {@link #toString()} is the id text.
 */
public enum FeatureId {
    /** eye protection; options off/on (Z9X) or off/follow/rest (HB, X50) */
    EYE("eye", FeatureKind.CHOICE),
    /** Auto keystone now (wide Autocorrect tile + keystone row) */
    KEYSTONE_AK_NOW("keystone.ak_now", FeatureKind.ACTION),
    /** fit to the screen now */
    KEYSTONE_FIT_NOW("keystone.fit_now", FeatureKind.ACTION),
    /** manual keystone corners */
    KEYSTONE_MANUAL("keystone.manual", FeatureKind.SCREEN),
    /** fit to a screen with a frame (curtain fit page) */
    KEYSTONE_FIT_SCREEN("keystone.fit_screen", FeatureKind.SCREEN),
    /** picture rotation */
    KEYSTONE_ROTATION("keystone.rotation", FeatureKind.SCREEN),
    /** real-time keystone (Auto keystone when moved) */
    KEYSTONE_REALTIME("keystone.realtime", FeatureKind.TOGGLE),
    /** keystone at power on */
    KEYSTONE_ON_POWER("keystone.on_power", FeatureKind.TOGGLE),
    /** fit the picture to the screen (curtain) when correcting */
    KEYSTONE_CURTAIN("keystone.curtain", FeatureKind.TOGGLE),
    /** avoid obstacles when correcting */
    KEYSTONE_OBSTACLE("keystone.obstacle", FeatureKind.TOGGLE),
    /** restore the full picture (corners reset) */
    KEYSTONE_RESTORE("keystone.restore", FeatureKind.ACTION),
    /** zoom screen (small Zoom tile + keystone row with its summary) */
    ZOOM("zoom", FeatureKind.SCREEN),
    /** Autofocus now */
    FOCUS_AF_NOW("focus.af_now", FeatureKind.ACTION),
    /** manual focus */
    FOCUS_MANUAL("focus.manual", FeatureKind.SCREEN),
    /** smart autofocus */
    FOCUS_SMART("focus.smart", FeatureKind.TOGGLE),
    /** autofocus when moved */
    FOCUS_ON_MOVE("focus.on_move", FeatureKind.TOGGLE),
    /** autofocus at power on */
    FOCUS_ON_POWER("focus.on_power", FeatureKind.TOGGLE),
    /** brightness of the light (bottom slider); min, max */
    LAMP("lamp", FeatureKind.SLIDER),
    /** Boost above the top light level (yellow); level = the slider step that means Boost */
    BOOST("boost", FeatureKind.TOGGLE),
    /** picture mode; options */
    PICTURE_MODE("picture.mode", FeatureKind.CHOICE),
    /** brightness, contrast, saturation, sharpness sliders; items, min, max */
    PICTURE_PQ("picture.pq", FeatureKind.SLIDER),
    /** colour temperature; options */
    PICTURE_COLOR_TEMP("picture.color_temp", FeatureKind.CHOICE),
    /** motion smoothing; options */
    PICTURE_MEMC("picture.memc", FeatureKind.CHOICE),
    /** AI contrast */
    PICTURE_AI_CONTRAST("picture.ai_contrast", FeatureKind.TOGGLE),
    /** super resolution */
    PICTURE_SUPER_RES("picture.super_res", FeatureKind.TOGGLE),
    /** 120 Hz output */
    PICTURE_ULTRA120("picture.ultra120", FeatureKind.TOGGLE),
    /** expert picture page */
    PICTURE_PRO("picture.pro", FeatureKind.SCREEN),
    /** reset this picture mode */
    PICTURE_RESET("picture.reset", FeatureKind.ACTION),
    /** reset every picture setting */
    PICTURE_RESET_ALL("picture.reset_all", FeatureKind.ACTION),
    /** aspect ratio; options */
    PICTURE_ASPECT("picture.aspect", FeatureKind.CHOICE),
    /** HDMI colour range; options */
    HDMI_RANGE("hdmi.range", FeatureKind.CHOICE),
    /** HDMI mode (EDID version); options */
    HDMI_EDID("hdmi.edid", FeatureKind.CHOICE),
    /** sound processing (DTS Virtual:X and the rest); options */
    SOUND_ENHANCEMENT("sound.enhancement", FeatureKind.CHOICE),
    /** sound mode; options */
    SOUND_MODE("sound.mode", FeatureKind.CHOICE),
    /** equalizer page (shared rules: speaker only, not with DTS) */
    SOUND_EQ("sound.eq", FeatureKind.SCREEN),
    /** volume leveling */
    SOUND_LEVELING("sound.leveling", FeatureKind.TOGGLE),
    /** eARC */
    SOUND_EARC("sound.earc", FeatureKind.TOGGLE),
    /** sound output; options */
    SOUND_OUTPUT("sound.output", FeatureKind.CHOICE),
    /** digital sound format (shared DTS rules); options */
    SOUND_FORMAT("sound.format", FeatureKind.CHOICE),
    /** inputs (wide tile, popup of the sources) */
    INPUT("input", FeatureKind.TILE),
    /** Bluetooth settings */
    BLUETOOTH("bluetooth", FeatureKind.TILE),
    /** network settings */
    NETWORK("network", FeatureKind.TILE),
    /** screen mirroring receiver (shown only when the receiver app is installed) */
    MIRRORING("mirroring", FeatureKind.TILE),
    /** USB files (last small tile where mirroring is absent; needs Lumen Home Files) */
    USB_FILES("usb_files", FeatureKind.TILE),
    /** game mode; options */
    GAME_MODE("game.mode", FeatureKind.CHOICE),
    /** game speed (input lag) option; options */
    GAME_SPEED("game.speed", FeatureKind.CHOICE),
    /** ALLM handling; options */
    GAME_ALLM("game.allm", FeatureKind.CHOICE),
    /** projection: options auto, table, ceiling */
    PROJECTION_MODE("projection.mode", FeatureKind.CHOICE),
    /** rear projection */
    PROJECTION_REAR("projection.rear", FeatureKind.TOGGLE),
    /** Android settings */
    SETTINGS_SYSTEM("settings.system", FeatureKind.SCREEN),
    /** Projector settings (two-pane SettingsActivity) */
    SETTINGS_PROJECTOR("settings.projector", FeatureKind.SCREEN),
    /** HDMI-CEC page */
    SETTINGS_CEC("settings.cec", FeatureKind.SCREEN),
    /** recent apps */
    SETTINGS_RECENTS("settings.recents", FeatureKind.SCREEN),
    /** home screen choice; options lumen, classic */
    SETTINGS_LAUNCHER("settings.launcher", FeatureKind.CHOICE),
    /** horizontal tilt of the projector, as text */
    KEYSTONE_ANGLE_H("keystone.angle_h", FeatureKind.INFO),
    /** vertical tilt of the projector, as text */
    KEYSTONE_ANGLE_V("keystone.angle_v", FeatureKind.INFO),
    /** dynamic contrast */
    PICTURE_DYNAMIC_CONTRAST("picture.dynamic_contrast", FeatureKind.TOGGLE),
    /** dynamic black level enhancement */
    PICTURE_DYNAMIC_BLACK("picture.dynamic_black", FeatureKind.TOGGLE),
    /** sleep timer */
    SETTINGS_SLEEP_TIMER("settings.sleep_timer", FeatureKind.SCREEN),
    /** pair the remote */
    SETTINGS_REMOTE("settings.remote", FeatureKind.SCREEN),
    /** auto keystone after the projector was moved (a row of its own where the stock lists it apart from focus) */
    KEYSTONE_ON_MOVE("keystone.on_move", FeatureKind.TOGGLE),
    /** digital zoom and shift (Play 6 zoom_and_displacement): the tile slot and keystone row of zoom, no optical claim */
    ZOOM_DIGITAL("zoom.digital", FeatureKind.SCREEN),
    /** power saving (stock eco_mode_switch, battery models); the vendor changes the light level with it */
    LAMP_ECO("lamp.eco", FeatureKind.TOGGLE),
    /** ambient speaker page (Bluetooth speaker mode, ambient light, what closing the projector does) */
    SETTINGS_AMBIENT_SPEAKER("settings.ambient_speaker", FeatureKind.SCREEN),
    /**
     * the shared Licences screen (GmLicences: the CC BY credits of the game menu's controller models, the fonts) in
     * General, for a device app with no Projector settings screen of its own (the X50); the Z9X, HB and Play 6 show
     * the same screen from their Projector settings, Help
     */
    SETTINGS_LICENCES("settings.licences", FeatureKind.SCREEN),
    /**
     * the stock power menu's "Turn off screen" (关屏): the light goes off, sound and apps go on, the power key gives
     * it back ({@link LampOff})
     */
    LAMP_OFF("lamp.off", FeatureKind.ACTION),
    /** the brightness slider of {@link #PICTURE_PQ} */
    PQ_BRIGHTNESS(PICTURE_PQ, "brightness"),
    /** the contrast slider of {@link #PICTURE_PQ} */
    PQ_CONTRAST(PICTURE_PQ, "contrast"),
    /** the saturation slider of {@link #PICTURE_PQ} */
    PQ_SATURATION(PICTURE_PQ, "saturation"),
    /** the sharpness slider of {@link #PICTURE_PQ} */
    PQ_SHARPNESS(PICTURE_PQ, "sharpness");

    /** The id text of the profile and of the menu state. */
    public final String id;
    public final FeatureKind kind;
    /** {@link #PICTURE_PQ} for a picture slider, else null. */
    public final FeatureId parent;
    /** The slider item of a picture slider ("contrast"), else null. */
    public final String item;

    FeatureId(String id, FeatureKind kind) {
        this.id = id;
        this.kind = kind;
        this.parent = null;
        this.item = null;
    }

    FeatureId(FeatureId parent, String item) {
        this.id = parent.id + "." + item;
        this.kind = FeatureKind.SLIDER;
        this.parent = parent;
        this.item = item;
    }

    private static final Map<String, FeatureId> BY_ID = new HashMap<>();

    static {
        for (FeatureId f : values()) BY_ID.put(f.id, f);
    }

    /** The id with this text (a catalogue id or a picture slider), or null. */
    public static FeatureId of(String id) { return id == null ? null : BY_ID.get(id); }

    /** The catalogue id a profile may name, or null (a picture slider is not one). */
    public static FeatureId known(String id) {
        FeatureId f = of(id);
        return f == null || f.parent != null ? null : f;
    }

    /** The picture slider of {@code item} ("contrast"), or null. */
    public static FeatureId pq(String item) { return item == null ? null : of(PICTURE_PQ.id + "." + item); }

    @Override public String toString() { return id; }
}
