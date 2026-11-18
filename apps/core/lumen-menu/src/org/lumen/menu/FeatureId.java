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
    /**
     * 3D: the stock 3D modes of the model ({@link ThreeDMode} tokens, in its stock order); the small 3D tile in the
     * network slot of the card (network becomes a round button on top), its popup and the last Picture row
     */
    PICTURE_3D("picture.3d", FeatureKind.CHOICE),
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
    /**
     * the lens iris (stock picture_iris_ring_adjustment, first row of the picture page); options dynamic, f2, f3, f4,
     * f5_5, f7 in the stock order of the model. Dynamic, f5.5 and f7 hold the light level ({@link IrisRule})
     */
    PICTURE_IRIS("picture.iris", FeatureKind.CHOICE),
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
     * "Send a problem report": the shared report screen of apps/core/lumen-report, the same on every projector. A
     * module screen ({@link MenuSpec#MODULE_SCREENS}): the menu opens it itself, the device adapter has no part in it
     */
    SETTINGS_REPORT("settings.report", FeatureKind.SCREEN),
    /**
     * the signal panel of apps/core/lumen-signal (2026-11-18, variant A): a round button on the card's top row
     * and a General row; each press goes card, full panel, hidden. A module screen ({@link MenuSpec#MODULE_SCREENS}):
     * the menu opens it itself, the device's part is its SignalReader and devices/&lt;c&gt;/signal.tsv
     */
    SIGNAL_PANEL("signal.panel", FeatureKind.SCREEN),
    /**
     * "Keep the signal card on screen" (2026-11-18, for people who film the screen): the small card stays over
     * every HDMI input until the option goes off or the panel's key closes it. Off by default. A module setting
     * ({@link MenuSpec#MODULE_SETTINGS}): lumen-signal stores it, no device adapter
     */
    SIGNAL_PIN("signal.pin", FeatureKind.TOGGLE),
    /**
     * How often the visible panel reads the HDMI receiver: normal (a change and every 5 s, the default) or fast (every
     * second, rare frame stutter possible). Only where the model reads the receiver (lumen-signal HDMI_FORMAT, Z9X). A
     * module setting ({@link MenuSpec#MODULE_SETTINGS})
     */
    SIGNAL_REFRESH("signal.refresh", FeatureKind.CHOICE),
    /**
     * The sound volume (the music stream, the level the remote's volume keys move): an item the quick menu editor
     * offers on every model (2026-11-18), never in the default menu. A module setting of every model
     * ({@link MenuSpec#EVERY_MODEL}): no profile names it, no device adapter
     */
    SOUND_VOLUME("sound.volume", FeatureKind.SLIDER),
    /**
     * the stock power menu's "Turn off screen" (关屏): the light goes off, sound and apps go on, the power key gives
     * it back ({@link LampOff})
     */
    LAMP_OFF("lamp.off", FeatureKind.ACTION),
    /** colour gamut (native, BT.2020, DCI-P3, BT.709, Adobe RGB: the RS30 stock picture_gamut) */
    PICTURE_GAMUT("picture.gamut", FeatureKind.CHOICE),
    /** rainbow reduction (the DLP colour wheel's rainbow effect, the RS30 stock picture_anti_rbe) */
    PICTURE_ANTI_RBE("picture.anti_rbe", FeatureKind.TOGGLE),
    /** gamma curve (X50 family stock image_mode_gamma: BT.1886, ST 2084, HLG by content, or manual); options */
    PICTURE_GAMMA("picture.gamma", FeatureKind.CHOICE),
    /** manual gamma value 1.8 .. 2.6 (stock image_mode_custom_gamma, only with the manual gamma); options */
    PICTURE_GAMMA_INDEX("picture.gamma_index", FeatureKind.CHOICE),
    /** noise reduction (stock image_mode_noise_reduct); options */
    PICTURE_NOISE("picture.noise", FeatureKind.CHOICE),
    /** local contrast (stock image_mode_part_constrast_ratio); options */
    PICTURE_LOCAL_CONTRAST("picture.local_contrast", FeatureKind.CHOICE),
    /** colour optimisation (stock image_mode_custom_color_optimization) */
    PICTURE_COLOR_OPT("picture.color_opt", FeatureKind.TOGGLE),
    /** HDR dynamic tone mapping, HDR10 content only (stock picture_enhanced_hdr_mapping) */
    PICTURE_HDR_MAPPING("picture.hdr_mapping", FeatureKind.TOGGLE),
    /** the HDR enhancement page (stock picture_hdr_enhancement: tone mapping, screen size and gain), HDR content only */
    PICTURE_HDR("picture.hdr", FeatureKind.PAGE),
    /** the screen size the HDR tone mapping assumes, in inches (stock picture_hdr_curtain_size); options */
    PICTURE_HDR_SCREEN_SIZE("picture.hdr.screen_size", FeatureKind.CHOICE),
    /** the screen gain the HDR tone mapping assumes, x10 (stock picture_hdr_curtain_gain); options */
    PICTURE_HDR_SCREEN_GAIN("picture.hdr.screen_gain", FeatureKind.CHOICE),
    /** the Dolby Vision screen page (stock picture_dolby_curtain at the end of the picture modes), Dolby Vision only */
    PICTURE_DV_SCREEN("picture.dv_screen", FeatureKind.PAGE),
    /** the screen size Dolby Vision maps for, in inches (stock picture_dolby_curtain_size); options */
    PICTURE_DV_SCREEN_SIZE("picture.dv_screen.size", FeatureKind.CHOICE),
    /** the screen gain Dolby Vision maps for, x10 (stock picture_dolby_curtain_gain); options */
    PICTURE_DV_SCREEN_GAIN("picture.dv_screen.gain", FeatureKind.CHOICE),
    /** the white balance page (stock image_mode_prof_setting_whiteBalance: the 2-point and the 11-point pages) */
    PICTURE_WB("picture.wb", FeatureKind.PAGE),
    /** the 2-point white balance page (stock image_2_point_white_balance): three gains, three offsets, reset */
    PICTURE_WB2("picture.wb2", FeatureKind.PAGE),
    /** red gain of the 2-point white balance; min, max */
    PICTURE_WB2_RED_GAIN("picture.wb2.red_gain", FeatureKind.SLIDER),
    /** green gain of the 2-point white balance; min, max */
    PICTURE_WB2_GREEN_GAIN("picture.wb2.green_gain", FeatureKind.SLIDER),
    /** blue gain of the 2-point white balance; min, max */
    PICTURE_WB2_BLUE_GAIN("picture.wb2.blue_gain", FeatureKind.SLIDER),
    /** red offset of the 2-point white balance; min, max */
    PICTURE_WB2_RED_OFFSET("picture.wb2.red_offset", FeatureKind.SLIDER),
    /** green offset of the 2-point white balance; min, max */
    PICTURE_WB2_GREEN_OFFSET("picture.wb2.green_offset", FeatureKind.SLIDER),
    /** blue offset of the 2-point white balance; min, max */
    PICTURE_WB2_BLUE_OFFSET("picture.wb2.blue_offset", FeatureKind.SLIDER),
    /** the 2-point white balance back to its defaults (stock two_point_white_balance_reset_btn) */
    PICTURE_WB2_RESET("picture.wb2.reset", FeatureKind.ACTION),
    /** the 11-point white balance page (stock image_11_point_white_balance): grey level, red, green, blue, reset */
    PICTURE_WB11("picture.wb11", FeatureKind.PAGE),
    /** the grey level the 11-point sliders edit, in percent (stock image_segmentation); options */
    PICTURE_WB11_LEVEL("picture.wb11.level", FeatureKind.CHOICE),
    /** red of the grey level shown; min, max */
    PICTURE_WB11_RED("picture.wb11.red", FeatureKind.SLIDER),
    /** green of the grey level shown; min, max */
    PICTURE_WB11_GREEN("picture.wb11.green", FeatureKind.SLIDER),
    /** blue of the grey level shown; min, max */
    PICTURE_WB11_BLUE("picture.wb11.blue", FeatureKind.SLIDER),
    /** the 11-point white balance back to its defaults (stock eleven_point_white_balance_reset_btn) */
    PICTURE_WB11_RESET("picture.wb11.reset", FeatureKind.ACTION),
    /** the colour correction page (stock image_mode_custom_color_correction): colour, hue, saturation, lightness */
    PICTURE_CC("picture.cc", FeatureKind.PAGE),
    /** the colour the three sliders edit (stock image_mode_color_correction_switch); options */
    PICTURE_CC_COLOR("picture.cc.color", FeatureKind.CHOICE),
    /** hue of the colour shown; min, max */
    PICTURE_CC_HUE("picture.cc.hue", FeatureKind.SLIDER),
    /** saturation of the colour shown; min, max */
    PICTURE_CC_SATURATION("picture.cc.saturation", FeatureKind.SLIDER),
    /** lightness of the colour shown; min, max */
    PICTURE_CC_LIGHTNESS("picture.cc.lightness", FeatureKind.SLIDER),
    /** colour correction back to its defaults (stock image_mode_custom_color_correction_reset) */
    PICTURE_CC_RESET("picture.cc.reset", FeatureKind.ACTION),
    /** switch to the matching picture mode for IMAX Enhanced, HDR Vivid and filmmaker content (stock image_set_mode_auto_switch) */
    PICTURE_AUTO_SWITCH("picture.auto_switch", FeatureKind.TOGGLE),
    /** the style filter (stock picture_3d_lut_bt, a 3D LUT): original, or a filter set on stock; options */
    PICTURE_STYLE_FILTER("picture.style_filter", FeatureKind.CHOICE),
    /**
     * colour enhancement for older viewers (stock more_elderly_color_enhancement: off or a strength); a row of the
     * general page, where stock lists it (General, Display group, with the eye protection); options
     */
    EYE_ELDER_COLOR("eye.elder_color", FeatureKind.CHOICE),
    /**
     * screen displacement: the arrows shift the picture digitally, past its edge a pan-tilt head turns the projector
     * (the RS30 stock move_only page)
     */
    ZOOM_MOVE("zoom.move", FeatureKind.SCREEN),
    /** key sounds: Android's sound_effects_enabled (the stock sound page's voice_key_tone switch) */
    SOUND_KEY_TONE("sound.key_tone", FeatureKind.TOGGLE),
    /**
     * the HDMI app's own settings page (org.lumen.tvinput: switch to a newly connected device, back home when it goes,
     * the startup source), the stock signal page's signal_source_setting_auto and signal_boot_source_info, for a device
     * app with no HDMI-CEC page that links it ({@link #SETTINGS_CEC} does on the Z9X and the HB)
     */
    SETTINGS_HDMI("settings.hdmi", FeatureKind.SCREEN),
    /**
     * Android's colour correction (lumen-system ColourVision), the stock General page's colour vision row; options off and
     * the stock filters gray, red_green, green_red, blue_yellow
     */
    SETTINGS_COLOUR_VISION("settings.colour_vision", FeatureKind.CHOICE),
    /** the system temperature the vendor reports, as text (the stock About page's sys_tempe row) */
    SETTINGS_TEMPERATURE("settings.temperature", FeatureKind.INFO),
    /**
     * lens memory: stored zoom and lens shift positions to go back to, save, rename and delete (the X50 stock
     * lens_memory list)
     */
    ZOOM_MEMORY("zoom.memory", FeatureKind.SCREEN),
    /** how the remote focuses (the stock remote_focus_img_guide row: a picture and one sentence, nothing to set) */
    FOCUS_GUIDE("focus.guide", FeatureKind.INFO),
    /**
     * the stock picture settings page (newsettings image_mode_setting, Professional settings): a page whose rows and
     * groups the profile gives (sections.picture, a {"page": ..., "groups": ...} row), on the models whose stock has it
     */
    PICTURE_SETTINGS("picture.settings", FeatureKind.PAGE),
    /**
     * dynamic black level tuning (stock image_mode_vibrant_correct, right under the dynamic black switch and shown only
     * while it is on): a two-way slider, min and max in the profile (stock -40..40, 0 in the middle)
     */
    PICTURE_BLACK_TUNING("picture.black_tuning", FeatureKind.SLIDER),
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
