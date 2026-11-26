// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
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
    /** picture rotation */
    KEYSTONE_ROTATION("keystone.rotation", FeatureKind.SCREEN),
    /**
     * Wall colour adaptation (stock wall_color_calibration, 2026-11-19): the card of apps/core/lumen-wallcolor on
     * every model whose stock has it. A module screen ({@link MenuSpec#MODULE_SCREENS}): the menu opens it itself, the
     * device's part is its WallColorHal and devices/&lt;c&gt;/wallcolor.conf
     */
    KEYSTONE_WALL_COLOR("keystone.wall_color", FeatureKind.SCREEN),
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
    /** User picture modes (stock image_mode_custom_base_*, CustomizeDataManager): the mode list's "Custom" entry. */
    PICTURE_CUSTOM_NEW("picture.custom_new", FeatureKind.ACTION),
    /** The stock mode a user mode is based on (Switch / GetCustomImageModeMapping), shown in a user mode only. */
    PICTURE_CUSTOM_BASE("picture.custom_base", FeatureKind.CHOICE),
    /** Delete the user mode in use (stock delete_image_mode_user_setting), shown in a user mode only. */
    PICTURE_CUSTOM_DELETE("picture.custom_delete", FeatureKind.ACTION),
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
    /**
     * The Network tile's popup (owner variant A, 2026-11-19), stock Settings > Network order: the Wi-Fi row (the
     * network the projector is on, opens Android's Wi-Fi page), a line, the stock Wireless Hotspot rows. Module rows
     * (lumen-system Hotspot), every model whose stock tree has the hotspot names all five.
     */
    NETWORK_WIFI("network.wifi", FeatureKind.SCREEN),
    /** the stock hotspot switch (network_ap): start or stop Android's hotspot */
    NETWORK_HOTSPOT("network.hotspot", FeatureKind.TOGGLE),
    /** the stock hotspot name (network_ap_ssid): the text edit screen, greyed while the hotspot is off */
    NETWORK_HOTSPOT_NAME("network.hotspot_name", FeatureKind.SCREEN),
    /** the stock hotspot password (network_ap_password): the text edit screen, greyed while the hotspot is off */
    NETWORK_HOTSPOT_PASSWORD("network.hotspot_password", FeatureKind.SCREEN),
    /** the stock restore default (network_ap_reset): the first name and password again, hidden while the hotspot is off */
    NETWORK_HOTSPOT_RESET("network.hotspot_reset", FeatureKind.ACTION),
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
    /**
     * Power On Auto Boot (stock 通电自动开机, General, Advanced, Power on/off): the projector starts by itself when the
     * mains comes on. The vendor stores it for its own boot loader; the adapter reads it with the menu and writes it
     * only when the user flips the row, never at boot (2026-11-19)
     */
    POWER_ON_AC("power.on_ac", FeatureKind.TOGGLE),
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
     * The sensor card of apps/core/lumen-sensors (2026-11-19: the old LumenSensors app as an overlay): a row of
     * the For developers page of All settings; each press goes card, full sheet, hidden. A module screen ({@link MenuSpec#MODULE_SCREENS})
     */
    SENSORS_PANEL("sensors.panel", FeatureKind.SCREEN),
    /** "Remote buttons": give a remote key an app or an action (apps/core/lumen-remote keys, KeyBindActivity) */
    SETTINGS_REMOTE_KEYS("settings.remote_keys", FeatureKind.SCREEN),
    /** the stock power-on melody (IGmpf 420 / 419 on Z9X, HB and Play 6), from the old Projector settings page */
    POWER_ON_MUSIC("power.on_music", FeatureKind.TOGGLE),
    /** what opens at power-on: options home, hdmi (one input) or home, hdmi1, hdmi2 (source.BootSource) */
    POWER_BOOT_SOURCE("power.boot_source", FeatureKind.CHOICE),
    /** the voice assistant's speech language (mic key), only where a speech recogniser exists; options system, ru, en ... */
    VOICE_LANGUAGE("voice.language", FeatureKind.CHOICE),
    /** the voice assistant speaks its answers */
    VOICE_SPEAK("voice.speak", FeatureKind.TOGGLE),
    /** the optional voice key of the assistant: a text field on its own small screen */
    VOICE_KEY("voice.key", FeatureKind.SCREEN),
    /** the interface resolution chooser (4K / 1080p, display.UiResolution), only where the image allows it */
    SETTINGS_UI_RESOLUTION("settings.ui_resolution", FeatureKind.SCREEN),
    /** the anonymous statistics switch (rules 2.19: the only one), hidden where nobody owns it on this unit */
    SETTINGS_STATS("settings.stats", FeatureKind.TOGGLE),
    /** the app's diagnostic lines (HAL state, versions) in the developer sheet of apps/core/lumen-sensors */
    SETTINGS_DIAGNOSTICS("settings.diagnostics", FeatureKind.SCREEN),
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
     * the stock screensaver page's start now row (screen_saver_simple enter_screen_saver_text): Android's screensaver
     * (lumen-system ScreensaverStart), a module screen
     */
    SETTINGS_SCREENSAVER_NOW("settings.screensaver_now", FeatureKind.SCREEN),
    /**
     * the stock screensaver page's start after row (screen_waiting_time_close: never, m1, m5, m10, m30, m60): Android's
     * idle time (lumen-system ScreensaverDelay), a module setting
     */
    SETTINGS_SCREENSAVER_DELAY("settings.screensaver_delay", FeatureKind.CHOICE),
    /**
     * high altitude mode: the fans run faster for thin air (the stock General advanced page high_altitude_mode switch,
     * MoreRepository.setHighAltitudeMode: SystemManager setHighAltitudeMode / getHighAltitudeMode)
     */
    SETTINGS_HIGH_ALTITUDE("settings.high_altitude", FeatureKind.TOGGLE),
    /**
     * the stock General page serial port control (serial_port_control_state_diana, X50 Ultra, Ultra Max, TITAN): a USB
     * to RS-232 adapter drives the projector; the vendor service owns the port, the rows are its stock switches
     */
    SETTINGS_SERIAL("settings.serial", FeatureKind.PAGE),
    /** serial_port_control.switch: SystemManager setConsoleEnable / getConsoleEnable */
    SERIAL_SWITCH("serial.switch", FeatureKind.TOGGLE),
    /** serial_port_control.id_group A..Z: SystemManager setConsoleID(group, number) / getConsoleID "group,number" */
    SERIAL_ID_GROUP("serial.id_group", FeatureKind.CHOICE),
    /** serial_port_control.id_number 0..20: the same call as the group */
    SERIAL_ID_NUMBER("serial.id_number", FeatureKind.CHOICE),
    /** serial_port_control.response_group: SystemManager setConsoleGroupAckEnable / getConsoleGroupAckEnable */
    SERIAL_ACK_GROUP("serial.ack_group", FeatureKind.TOGGLE),
    /** serial_port_control.response_all: SystemManager setConsoleAllAckEnable / getConsoleAllAckEnable */
    SERIAL_ACK_ALL("serial.ack_all", FeatureKind.TOGGLE),
    /** serial_port_control.master_mode: SystemManager setConsoleSeriesHost / getConsoleSeriesHost */
    SERIAL_MASTER("serial.master", FeatureKind.TOGGLE),
    /**
     * the stock General row parental settings (parental_settings, data.backup.parental_settings: XGIMI Home's parent
     * page): the shared parental controls of apps/core/lumen-parental, hosted by Lumen Home. A module screen
     */
    SETTINGS_PARENTAL("settings.parental", FeatureKind.SCREEN),
    /**
     * lens memory: stored zoom and lens shift positions to go back to, save, rename and delete (the X50 stock
     * lens_memory list)
     */
    ZOOM_MEMORY("zoom.memory", FeatureKind.SCREEN),
    /** how the remote focuses (the stock remote_focus_img_guide row: a picture and one sentence, nothing to set) */
    FOCUS_GUIDE("focus.guide", FeatureKind.INFO),
    /**
     * the focus rows of the Image correction page (TITAN testers 2026-11-19, mirroring the keystone rows): auto focus
     * now and manual focus on the page itself, smart auto focus and the remote picture on this sub page of the profile
     */
    FOCUS_SETTINGS("focus.settings", FeatureKind.PAGE),
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
    /** which side of the screen the menu sits on (General, MenuLook: left / right), a module setting of every model */
    SETTINGS_MENU_SIDE("settings.menu_side", FeatureKind.CHOICE),
    /** the menu's size (General, MenuLook: normal / compact, more rows on a page), a module setting of every model */
    SETTINGS_MENU_DENSITY("settings.menu_density", FeatureKind.CHOICE),
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
