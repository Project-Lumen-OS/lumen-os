// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import org.lumen.menu.FeatureId;
import org.lumen.projector.R;

/**
 * Labels and icons of the shared menu (apps/core/lumen-menu): feature titles, option
 * tokens, section and tile names, tile icons. Every text is the Z9X app's own text with its 17 translations
 * (lumen_strings_menu.xml in res/values and the 17 locale folders, names lumen_m_); a device never brings menu texts of its own. A token without
 * a label here cannot be shown: request it from the lumen-menu lead.
 */
final class MenuLabels {
    private MenuLabels() {}

    /** title: a string resource id, 0 when none. */
    static int title(FeatureId f) { return title(f.id); }

    static int summary(FeatureId f) { return summary(f.id); }

    static int option(FeatureId f, String token) { return option(f.id, token); }

    static int tileValue(FeatureId f, String token) { return tileValue(f.id, token); }

    static int title(String id) {
        switch (id) {
            case "network.wifi": return R.string.lumen_m_hotspot_wifi;
            case "source.list": return R.string.lumen_m_source_list;
            case "bluetooth.add": return R.string.lumen_m_bt_add;
            case "network.hotspot": return R.string.lumen_m_hotspot_hotspot;
            case "network.hotspot_name": return R.string.lumen_m_hotspot_name;
            case "network.hotspot_password": return R.string.lumen_m_hotspot_password;
            case "network.hotspot_reset": return R.string.lumen_m_hotspot_reset;
            case "keystone.angle_h": return R.string.lumen_m_angle_h;
            case "settings.menu_side": return R.string.lumen_m_menu_side;
            case "focus.settings": return R.string.lumen_m_focus_settings;
            case "settings.menu_density": return R.string.lumen_m_menu_density;
            case "settings.power_key": return R.string.lumen_m_power_key_short;
            case "settings.language": return R.string.lumen_m_language;
            case "settings.date_time": return R.string.lumen_m_date_time;
            case "settings.storage": return R.string.lumen_m_storage;
            case "settings.update": return R.string.lumen_m_update;
            case "settings.about": return R.string.lumen_m_about;
            case "dev.system_info": return R.string.lumen_m_system_info;
            case "dev.network_adb": return R.string.lumen_m_network_adb;
            case "dev.android": return R.string.lumen_m_android_dev;
            case "signal.panel": return R.string.lumen_m_signal;
            case "sensors.panel": return R.string.lumen_m_sensors;
            case "dev.mirror_diag": return R.string.lumen_m_mirror_diag;
            case "power.on_music": return R.string.lumen_m_power_music;
            case "power.boot_source": return R.string.lumen_m_boot_source;
            case "voice.language": return R.string.lumen_m_voice_language;
            case "voice.speak": return R.string.lumen_m_voice_speak;
            case "voice.key": return R.string.lumen_m_voice_key;
            case "settings.ui_resolution": return R.string.lumen_m_uires;
            case "settings.stats": return R.string.lumen_m_stats;
            case "settings.diagnostics": return R.string.lumen_m_diag;
            case "signal.pin": return R.string.lumen_m_signal_pin;
            case "sound.volume": return R.string.lumen_m_volume;
            case "signal.refresh": return R.string.lumen_m_signal_refresh;
            case "zoom.digital": return R.string.lumen_m_zs_title;
            case "lamp.eco": return R.string.lumen_m_eco;
            case "power.on_ac": return R.string.lumen_m_power_on_ac;
            case "picture.custom_new": return R.string.lumen_m_pm_custom;
            case "picture.custom_base": return R.string.lumen_m_custom_base;
            case "picture.custom_delete": return R.string.lumen_m_custom_delete;
            case "settings.serial": return R.string.lumen_m_serial;
            case "serial.switch": return R.string.lumen_m_serial_switch;
            case "serial.id_group": return R.string.lumen_m_serial_id_group;
            case "serial.id_number": return R.string.lumen_m_serial_id_number;
            case "serial.ack_group": return R.string.lumen_m_serial_ack_group;
            case "serial.ack_all": return R.string.lumen_m_serial_ack_all;
            case "serial.master": return R.string.lumen_m_serial_master;
            case "settings.parental": return R.string.lumen_m_parental;
            case "settings.casting": return R.string.lumen_m_casting;
            case "lamp.off": return R.string.lumen_m_lamp_off;
            case "picture.gamut": return R.string.lumen_m_gamut;
            case "picture.anti_rbe": return R.string.lumen_m_anti_rbe;
            case "picture.gamma": return R.string.lumen_m_gamma;
            case "picture.gamma_index": return R.string.lumen_m_gamma_index;
            case "picture.noise": return R.string.lumen_m_noise;
            case "picture.local_contrast": return R.string.lumen_m_local_contrast;
            case "picture.color_opt": return R.string.lumen_m_color_opt;
            case "picture.hdr_mapping": return R.string.lumen_m_hdr_mapping;
            case "picture.hdr": return R.string.lumen_m_hdr_enh;
            case "picture.hdr.screen_size": return R.string.lumen_m_screen_size;
            case "picture.hdr.screen_gain": return R.string.lumen_m_screen_gain;
            case "picture.dv_screen": return R.string.lumen_m_dv_screen;
            case "picture.dv_screen.size": return R.string.lumen_m_screen_size;
            case "picture.dv_screen.gain": return R.string.lumen_m_screen_gain;
            case "picture.wb": return R.string.lumen_m_wb;
            case "picture.wb2": return R.string.lumen_m_wb_2;
            case "picture.wb2.red_gain": return R.string.lumen_m_red_gain;
            case "picture.wb2.green_gain": return R.string.lumen_m_green_gain;
            case "picture.wb2.blue_gain": return R.string.lumen_m_blue_gain;
            case "picture.wb2.red_offset": return R.string.lumen_m_red_offset;
            case "picture.wb2.green_offset": return R.string.lumen_m_green_offset;
            case "picture.wb2.blue_offset": return R.string.lumen_m_blue_offset;
            case "picture.wb2.reset": return R.string.lumen_m_reset_defaults;
            case "picture.wb11": return R.string.lumen_m_wb_11;
            case "picture.wb11.level": return R.string.lumen_m_gray_level;
            case "picture.wb11.red": return R.string.lumen_m_red;
            case "picture.wb11.green": return R.string.lumen_m_green;
            case "picture.wb11.blue": return R.string.lumen_m_blue;
            case "picture.wb11.reset": return R.string.lumen_m_reset_defaults;
            case "picture.cc": return R.string.lumen_m_cc;
            case "picture.cc.color": return R.string.lumen_m_cc_color;
            case "picture.cc.hue": return R.string.lumen_m_hue;
            case "picture.cc.saturation": return R.string.lumen_m_panel_pq_saturation;
            case "picture.cc.lightness": return R.string.lumen_m_panel_pq_brightness;
            case "picture.cc.reset": return R.string.lumen_m_reset_defaults;
            case "picture.auto_switch": return R.string.lumen_m_auto_switch;
            case "picture.style_filter": return R.string.lumen_m_style_filter;
            case "eye.elder_color": return R.string.lumen_m_elder_color;
            case "zoom.move": return R.string.lumen_m_zoom_move;
            case "zoom.memory": return R.string.lumen_m_lens_memory;
            case "focus.guide": return R.string.lumen_m_focus_guide;
            case "settings.ambient_speaker": return R.string.lumen_m_ambient_speaker;
            case "keystone.angle_v": return R.string.lumen_m_angle_v;
            case "picture.dynamic_contrast": return R.string.lumen_m_dynamic_contrast;
            case "picture.dynamic_black": return R.string.lumen_m_dynamic_black;
            case "picture.db_page": return R.string.lumen_m_db_page;
            case "picture.db_page.switch": return R.string.lumen_m_dynamic_black;
            case "picture.db_page.level": return R.string.lumen_m_db_level;
            case "picture.db_page.subtitle_top": return R.string.lumen_m_db_subtitle_top;
            case "picture.db_page.subtitle_bottom": return R.string.lumen_m_db_subtitle_bottom;
            case "picture.db_page.reset": return R.string.lumen_m_db_reset;
            case "picture.black_tuning": return R.string.lumen_m_black_tuning;
            case "picture.settings": return R.string.lumen_m_picture_settings;
            case "picture.iris": return R.string.lumen_m_iris;
            case "settings.sleep_timer": return R.string.lumen_m_sleep_timer;
            case "settings.remote": return R.string.lumen_m_remote;
            case "settings.remote_keys": return R.string.lumen_remote_keys_title;
            case "settings.licences": return R.string.lumen_gm_licences;
            case "settings.report": return R.string.lumen_report_title;
            case "sound.earc": return R.string.lumen_m_earc;
            case "boost": return R.string.lumen_m_panel_boost;
            case "eye": return R.string.lumen_m_panel_eye;
            case "focus.af_now": return R.string.lumen_m_panel_af_now;
            case "focus.manual": return R.string.lumen_m_panel_manual_focus;
            case "focus.smart": return R.string.lumen_m_panel_smart_af;
            case "game.allm": return R.string.lumen_m_panel_allm;
            case "game.mode": return R.string.lumen_m_panel_game_mode;
            case "game.speed": return R.string.lumen_m_panel_game_speed;
            case "hdmi.edid": return R.string.lumen_m_hdmi_mode;
            case "hdmi.range": return R.string.lumen_m_hdmi_range;
            case "keystone.ak_now": return R.string.lumen_m_panel_ak_now;
            case "keystone.curtain": return R.string.lumen_m_panel_fit_screen;
            case "keystone.fit_now": return R.string.lumen_m_panel_fit_now;
            case "keystone.manual": return R.string.lumen_m_panel_manual_keystone;
            case "keystone.obstacle": return R.string.lumen_m_panel_obstacle;
            case "keystone.on_power": return R.string.lumen_m_panel_power_on_keystone;
            case "keystone.realtime": return R.string.lumen_m_panel_rt_keystone;
            case "keystone.on_move": return R.string.lumen_m_panel_move_ak;
            case "keystone.restore": return R.string.lumen_m_kst_restore;
            case "keystone.rotation": return R.string.lumen_m_rot_title;
            case "keystone.wall_color": return R.string.lumen_wallcolor_title;
            case "keystone.color_uniformity": return R.string.lumen_m_color_uniformity;
            case "lamp": return R.string.lumen_m_panel_lamp;
            case "picture.3d": return R.string.lumen_m_3d_title;
            case "picture.ai_contrast": return R.string.lumen_m_panel_ai_contrast;
            case "picture.aspect": return R.string.lumen_m_panel_aspect;
            case "picture.color_temp": return R.string.lumen_m_panel_color_temp;
            case "picture.memc": return R.string.lumen_m_panel_memc;
            case "picture.mode": return R.string.lumen_m_panel_picture_mode;
            case "picture.pro": return R.string.lumen_m_pro_title;
            case "picture.reset": return R.string.lumen_m_panel_picture_reset;
            case "picture.reset_all": return R.string.lumen_m_picture_reset_all;
            case "picture.super_res": return R.string.lumen_m_panel_super_resolution;
            case "picture.ultra120": return R.string.lumen_m_panel_ultra120;
            case "projection.mode": return R.string.lumen_m_panel_projection_mode;
            case "projection.rear": return R.string.lumen_m_panel_proj_rear;
            case "settings.cec": return R.string.lumen_m_cec_entry;
            case "settings.hdmi": return R.string.lumen_m_hdmi_options;
            case "settings.colour_vision": return R.string.lumen_m_colour_vision;
            case "settings.temperature": return R.string.lumen_m_temperature;
            case "settings.screensaver_now": return R.string.lumen_m_dream_now;
            case "settings.screensaver_delay": return R.string.lumen_m_dream_delay;
            case "settings.high_altitude": return R.string.lumen_m_high_altitude;
            case "settings.launcher": return R.string.lumen_m_launcher_row;
            case "settings.recents": return R.string.lumen_m_recents_title;
            case "settings.system": return R.string.lumen_m_panel_system_settings;
            case "sound.enhancement": return R.string.lumen_m_panel_sound_enhancement;
            case "sound.eq": return R.string.lumen_m_eq_title;
            case "sound.format": return R.string.lumen_m_sound_format;
            case "sound.key_tone": return R.string.lumen_m_sound_key_tone;
            case "sound.leveling": return R.string.lumen_m_sound_volume_leveling;
            case "sound.mode": return R.string.lumen_m_panel_sound_mode;
            case "sound.output": return R.string.lumen_m_sound_output;
            case "zoom": return R.string.lumen_m_panel_optical_zoom;
            default: return 0;
        }
    }

    /** summary: a string resource id, 0 when none. */
    static int summary(String id) {
        switch (id) {
            case "boost": return R.string.lumen_m_boost_effect;
            case "lamp": return R.string.lumen_m_panel_lamp_hint;
            case "signal.refresh": return R.string.lumen_m_signal_refresh_note;
            default: return 0;
        }
    }

    /** pq: a string resource id, 0 when none. */
    static int pq(String id) {
        switch (id) {
            case "brightness": return R.string.lumen_m_panel_pq_brightness;
            case "contrast": return R.string.lumen_m_panel_pq_contrast;
            case "saturation": return R.string.lumen_m_panel_pq_saturation;
            case "sharpness": return R.string.lumen_m_panel_pq_sharpness;
            default: return 0;
        }
    }

    /** header: a string resource id, 0 when none (a line has no text). */
    static int header(String id) {
        switch (id) {
            case "auto_correction": return R.string.lumen_qpd_autocorrect;
            case "size_position": return R.string.lumen_m_kst_size_position;
            case "smart": return R.string.lumen_m_kst_smart;
            case "more": return R.string.lumen_m_kst_more;
            case "projector_brightness": return R.string.lumen_m_hdr_projector_brightness;
            case "image": return R.string.lumen_m_hdr_image;
            case "basic": return R.string.lumen_m_hdr_basic;
            case "colour": return R.string.lumen_m_hdr_colour;
            case "contrast": return R.string.lumen_m_hdr_contrast;
            case "clarity": return R.string.lumen_m_hdr_clarity;
            case "image_more": return R.string.lumen_m_hdr_image_more;
            case "screensaver": return R.string.lumen_m_hdr_screensaver;
            case "power": return R.string.lumen_m_hdr_power;
            case "voice": return R.string.lumen_m_hdr_voice;
            case "menu": return R.string.lumen_m_hdr_menu;
            case "language_time": return R.string.lumen_m_hdr_language_time;
            case "display_menu": return R.string.lumen_m_hdr_display_menu;
            case "remote_voice": return R.string.lumen_m_hdr_remote_voice;
            case "apps_devices": return R.string.lumen_m_hdr_apps_devices;
            case "storage_updates": return R.string.lumen_m_hdr_storage_updates;
            case "help": return R.string.lumen_m_hdr_help;
            case "system": return R.string.lumen_m_hdr_system;
            default: return 0;
        }
    }

    /** section: a string resource id, 0 when none. */
    static int section(String id) {
        switch (id) {
            case "eye": return R.string.lumen_m_panel_eye;
            case "focus": return R.string.lumen_m_panel_tile_focus;
            case "game": return R.string.lumen_m_panel_sec_game;
            case "developer": return R.string.lumen_m_hdr_developer;
            case "general": return R.string.lumen_m_panel_sec_general;
            case "keystone": return R.string.lumen_m_panel_tile_keystone;
            case "lamp": return R.string.lumen_m_panel_lamp;
            case "network": return R.string.lumen_qpd_network;
            case "source": return R.string.lumen_m_sec_source;
            case "bluetooth": return R.string.lumen_qpd_bluetooth;
            case "picture": return R.string.lumen_m_panel_sec_picture;
            case "projection": return R.string.lumen_m_panel_projection_mode;
            case "sound": return R.string.lumen_m_panel_sec_sound;
            default: return 0;
        }
    }

    /** The title of a section page: the section name, except General, whose page is All settings (QPC:1455). */
    static int page(String id) {
        switch (id) {
            case "general": return R.string.lumen_m_panel_all_settings;
            default: return section(id);
        }
    }

    /** The name of a section in the All settings list (QPC:cat): the section name, Projection for its page. */
    static int category(String id) {
        switch (id) {
            case "projection": return R.string.lumen_m_panel_tile_projection;
            default: return section(id);
        }
    }

    /** tile: the tile label, 0 when none. */
    static int tile(String id) {
        switch (id) {
            case "eye": return R.string.lumen_m_panel_tile_eye;
            case "focus": return R.string.lumen_m_panel_tile_focus;
            case "game": return R.string.lumen_m_panel_tile_game;
            case "general": return R.string.lumen_m_panel_all_settings;
            case "input": return R.string.lumen_m_panel_tile_input;
            case "keystone": return R.string.lumen_m_panel_tile_keystone;
            case "picture": return R.string.lumen_m_panel_sec_picture;
            case "projection": return R.string.lumen_m_panel_tile_projection;
            case "sound": return R.string.lumen_m_panel_sec_sound;
            case "bluetooth": return R.string.lumen_qpd_bluetooth;
            case "keystone.ak_now": return R.string.lumen_qpd_autocorrect;
            case "mirroring": return R.string.lumen_qpd_mirroring;
            case "network": return R.string.lumen_qpd_network;
            case "picture.3d": return R.string.lumen_m_3d;
            case "theme": return R.string.lumen_qpd_theme;
            case "signal.panel": return R.string.lumen_m_signal;
            case "sensors.panel": return R.string.lumen_m_sensors;
            case "dev.mirror_diag": return R.string.lumen_m_mirror_diag;
            case "power.on_music": return R.string.lumen_m_power_music;
            case "power.boot_source": return R.string.lumen_m_boot_source;
            case "voice.language": return R.string.lumen_m_voice_language;
            case "voice.speak": return R.string.lumen_m_voice_speak;
            case "voice.key": return R.string.lumen_m_voice_key;
            case "settings.ui_resolution": return R.string.lumen_m_uires;
            case "settings.stats": return R.string.lumen_m_stats;
            case "settings.diagnostics": return R.string.lumen_m_diag;
            case "zoom": return R.string.lumen_qpd_zoom;
            case "zoom.digital": return R.string.lumen_qpd_zoom;
            default: return 0;
        }
    }

    /** icon: the animated tile icon, 0 when none. */
    static int icon(String id) {
        switch (id) {
            case "bluetooth": return R.drawable.lumen_qpd_ic_bt;
            case "developer": return R.drawable.lumen_qpd_ic_dev;
            case "eye": return R.drawable.lumen_qpd_ic_eye;
            case "focus": return R.drawable.lumen_qpd_ic_focus;
            case "game": return R.drawable.lumen_qpd_ic_game;
            case "general": return R.drawable.lumen_qpd_ic_gear;
            case "input": return R.drawable.lumen_qpd_ic_src;
            case "source": return R.drawable.lumen_qpd_ic_src;
            case "keystone": return R.drawable.lumen_qpd_ic_kst;
            case "keystone.ak_now": return R.drawable.lumen_qpd_ic_ak;
            case "mirroring": return R.drawable.lumen_qpd_ic_mirror;
            case "network": return R.drawable.lumen_qpd_ic_wifi;
            case "picture": return R.drawable.lumen_qpd_ic_pic;
            case "picture.3d": return R.drawable.lumen_qpd_ic_3d;
            case "picture.iris": return R.drawable.lumen_qpd_ic_iris;
            case "projection": return R.drawable.lumen_qpd_ic_proj;
            case "sound": return R.drawable.lumen_qpd_ic_snd;
            case "theme": return R.drawable.lumen_qpd_ic_theme_light;
            case "signal.panel": return R.drawable.lumen_gm_ic_info;
            case "zoom": return R.drawable.lumen_qpd_ic_zoom;
            case "zoom.digital": return R.drawable.lumen_qpd_ic_zoom;
            // the pencil editor's items beyond layout D's tiles
            case "edit": return R.drawable.lumen_ic_edit;
            case "lamp": return R.drawable.lumen_qpd_ic_sun;
            case "sound.volume": return R.drawable.lumen_ic_volume;
            case "sound.mode": return R.drawable.lumen_ic_playing;
            case "sound.output": return R.drawable.lumen_ic_speaker;
            case "picture.mode": return R.drawable.lumen_ic_picture_mode;
            case "picture.aspect": return R.drawable.lumen_ic_aspect;
            case "focus.af_now": return R.drawable.lumen_ic_focus;
            case "picture.pq.brightness": return R.drawable.lumen_ic_brightness;
            case "picture.pq.contrast": return R.drawable.lumen_ic_contrast;
            case "picture.pq.saturation": return R.drawable.lumen_ic_palette;
            case "picture.pq.sharpness": return R.drawable.lumen_ic_sharpness;
            // the full menu's rows as quick tiles (2026-11-24, Lucide, tools/qpd_icons.py): a row of a page takes
            // its page's icon (MenuController.quickIcon)
            case "picture.memc": return R.drawable.lumen_qpd_ic_memc;
            case "picture.ai_contrast": return R.drawable.lumen_qpd_ic_ai;
            case "picture.dynamic_black":
            case "picture.db_page":
            case "picture.black_tuning": return R.drawable.lumen_qpd_ic_black;
            case "picture.dynamic_contrast":
            case "picture.local_contrast": return R.drawable.lumen_qpd_ic_dcontrast;
            case "picture.color_temp": return R.drawable.lumen_qpd_ic_ctemp;
            case "picture.gamma":
            case "picture.gamma_index": return R.drawable.lumen_qpd_ic_gamma;
            case "picture.hdr":
            case "picture.hdr_mapping": return R.drawable.lumen_qpd_ic_hdr;
            case "picture.noise": return R.drawable.lumen_qpd_ic_noise;
            case "picture.super_res": return R.drawable.lumen_qpd_ic_sres;
            case "picture.wb":
            case "picture.wb2":
            case "picture.wb11": return R.drawable.lumen_qpd_ic_wb;
            case "picture.cc": return R.drawable.lumen_qpd_ic_cc;
            case "picture.gamut": return R.drawable.lumen_qpd_ic_gamut;
            case "picture.anti_rbe": return R.drawable.lumen_qpd_ic_rbe;
            default: return 0;
        }
    }

    /** The theme button's icon for a mode: sun, moon, half disc. */
    static int themeIcon(org.lumen.ui.ThemeMode m) {
        switch (m) {
            case DARK: return R.drawable.lumen_qpd_ic_theme_dark;
            case AUTO: return R.drawable.lumen_qpd_ic_theme_auto;
            default: return R.drawable.lumen_qpd_ic_theme_light;
        }
    }

    /** The name of a theme mode: Светлая, Тёмная, Авто. */
    static int themeLabel(org.lumen.ui.ThemeMode m) {
        switch (m) {
            case DARK: return R.string.lumen_qpd_theme_dark;
            case AUTO: return R.string.lumen_qpd_theme_auto;
            default: return R.string.lumen_qpd_theme_light;
        }
    }

    /** option: the label of one option token of a choice feature, 0 when none. */
    static int option(String feature, String token) {
        switch (feature + "|" + token) {
            case "picture.color_temp|standard": return R.string.lumen_m_ct_standard;
            case "settings.menu_side|left": return R.string.lumen_m_menu_left;
            case "settings.menu_side|right": return R.string.lumen_m_menu_right;
            case "settings.menu_density|normal": return R.string.lumen_m_menu_normal;
            case "settings.menu_density|compact": return R.string.lumen_m_menu_compact;
            case "settings.power_key|power_menu": return R.string.lumen_m_power_key_menu;
            case "settings.power_key|sleep": return R.string.lumen_m_power_key_sleep;
            case "picture.color_temp|cool": return R.string.lumen_m_ct_cool;
            case "picture.color_temp|warm": return R.string.lumen_m_ct_warm;
            case "picture.color_temp|custom": return R.string.lumen_m_ct_custom;
            case "picture.mode|movie": return R.string.lumen_m_pm_movie;
            case "picture.mode|tv": return R.string.lumen_m_pm_tv;
            case "picture.mode|custom": return R.string.lumen_m_pm_custom;
            case "picture.color_temp|movie": return R.string.lumen_m_ct_movie;
            case "eye|follow": return R.string.lumen_m_eye_follow;
            case "eye|rest": return R.string.lumen_m_eye_rest;
            case "hdmi.edid|hdmi_1_4": return R.string.lumen_m_edid_hdmi_1_4;
            case "hdmi.edid|hdmi_2_0": return R.string.lumen_m_edid_hdmi_2_0;
            case "picture.mode|filmmaker": return R.string.lumen_m_pm_filmmaker;
            case "picture.mode|imax": return R.string.lumen_m_pm_imax;
            case "picture.mode|isf_day": return R.string.lumen_m_pm_isf_day;
            case "picture.mode|isf_night": return R.string.lumen_m_pm_isf_night;
            case "picture.mode|bright": return R.string.lumen_m_pm_bright;
            case "picture.mode|soft": return R.string.lumen_m_pm_soft;
            case "picture.mode|cinema": return R.string.lumen_m_pm_cinema;
            case "picture.mode|gorgeous": return R.string.lumen_m_pm_gorgeous;
            case "picture.mode|eye_care": return R.string.lumen_m_pm_eye_care;
            case "picture.color_temp|fresh": return R.string.lumen_m_ct_fresh;
            case "picture.mode|dv_bright": return R.string.lumen_m_dv_bright;
            case "picture.mode|dv_dark": return R.string.lumen_m_dv_dark;
            case "picture.custom_base|standard": return R.string.lumen_m_panel_pm_standard;
            case "picture.custom_base|sport": return R.string.lumen_m_panel_pm_sport;
            case "picture.custom_base|color_accuracy": return R.string.lumen_m_panel_pm_color_accuracy;
            case "picture.custom_base|soft": return R.string.lumen_m_pm_soft;
            case "picture.custom_base|filmmaker": return R.string.lumen_m_pm_filmmaker;
            case "picture.custom_base|isf_day": return R.string.lumen_m_pm_isf_day;
            case "picture.custom_base|isf_night": return R.string.lumen_m_pm_isf_night;
            case "picture.custom_base|movie": return R.string.lumen_m_pm_movie;
            case "picture.custom_base|cinema": return R.string.lumen_m_pm_cinema;
            case "picture.custom_base|gorgeous": return R.string.lumen_m_pm_gorgeous;
            case "picture.custom_base|eye_care": return R.string.lumen_m_pm_eye_care;
            case "sound.output|arc": return R.string.lumen_m_out_arc;
            case "sound.output|bt": return R.string.lumen_m_out_bt;
            case "sound.output|usb": return R.string.lumen_m_out_usb;
            case "sound.output|wired": return R.string.lumen_m_out_wired;
            case "eye|off": return R.string.lumen_m_panel_off;
            case "eye|on": return R.string.lumen_m_panel_on;
            case "game.allm|device": return R.string.lumen_m_panel_allm_device;
            case "game.allm|signal": return R.string.lumen_m_panel_allm_signal;
            case "game.mode|auto": return R.string.lumen_m_panel_auto;
            case "game.mode|off": return R.string.lumen_m_panel_off;
            case "game.mode|on": return R.string.lumen_m_panel_on;
            case "game.speed|basic": return R.string.lumen_m_panel_speed_standard;
            case "game.speed|basic_hfr": return R.string.lumen_m_panel_speed_standard_hfr;
            case "game.speed|top_speed": return R.string.lumen_m_panel_speed_top;
            case "game.speed|top_speed_hfr": return R.string.lumen_m_panel_speed_top_hfr;
            case "hdmi.edid|compat": return R.string.lumen_m_hdmi_mode_compat;
            case "hdmi.edid|enhanced": return R.string.lumen_m_hdmi_mode_enhanced;
            case "power.boot_source|home": return R.string.lumen_m_boot_home;
            case "power.boot_source|hdmi": return R.string.lumen_m_boot_hdmi;
            case "power.boot_source|hdmi1": return R.string.lumen_m_boot_hdmi1;
            case "power.boot_source|hdmi2": return R.string.lumen_m_boot_hdmi2;
            case "voice.language|system": return R.string.lumen_m_voice_lang_system;
            case "voice.language|ru": return R.string.lumen_m_lang_ru;
            case "voice.language|en": return R.string.lumen_m_lang_en;
            case "voice.language|uk": return R.string.lumen_m_lang_uk;
            case "voice.language|kk": return R.string.lumen_m_lang_kk;
            case "voice.language|de": return R.string.lumen_m_lang_de;
            case "voice.language|fr": return R.string.lumen_m_lang_fr;
            case "voice.language|es": return R.string.lumen_m_lang_es;
            case "voice.language|it": return R.string.lumen_m_lang_it;
            case "voice.language|pt": return R.string.lumen_m_lang_pt;
            case "voice.language|pl": return R.string.lumen_m_lang_pl;
            case "voice.language|tr": return R.string.lumen_m_lang_tr;
            case "voice.language|zh": return R.string.lumen_m_lang_zh;
            case "voice.language|ja": return R.string.lumen_m_lang_ja;
            case "voice.language|ko": return R.string.lumen_m_lang_ko;
            case "voice.language|ar": return R.string.lumen_m_lang_ar;
            case "signal.refresh|normal": return R.string.lumen_m_signal_refresh_normal;
            case "signal.refresh|fast": return R.string.lumen_m_signal_refresh_fast;
            case "hdmi.range|auto": return R.string.lumen_m_panel_auto;
            case "hdmi.range|auto_full": return R.string.lumen_m_hdmi_range_auto_full;
            case "hdmi.range|auto_limited": return R.string.lumen_m_hdmi_range_auto_limited;
            case "hdmi.range|full": return R.string.lumen_m_hdmi_range_full;
            case "hdmi.range|limited": return R.string.lumen_m_hdmi_range_limited;
            case "picture.3d|off": return R.string.lumen_m_3d_off;
            case "picture.3d|sbs": return R.string.lumen_m_3d_sbs;
            case "picture.3d|tb": return R.string.lumen_m_3d_tb;
            case "picture.3d|sbs_2d": return R.string.lumen_m_3d_sbs_2d;
            case "picture.3d|tb_2d": return R.string.lumen_m_3d_tb_2d;
            case "picture.3d|frame_packing": return R.string.lumen_m_3d_frame_packing;
            case "picture.aspect|auto": return R.string.lumen_m_panel_auto;
            case "picture.aspect|original": return R.string.lumen_m_panel_aspect_original;
            case "picture.aspect|r16_9": return R.string.lumen_m_panel_aspect_16_9;
            case "picture.aspect|r4_3": return R.string.lumen_m_panel_aspect_4_3;
            case "picture.color_temp|color_temp_1": return R.string.lumen_m_panel_ct_1;
            case "picture.color_temp|d65": return R.string.lumen_m_panel_ct_d65;
            case "picture.gamut|adobe": return R.string.lumen_m_gamut_adobe;
            case "picture.gamut|bt2020": return R.string.lumen_m_gamut_bt2020;
            case "picture.gamut|bt709": return R.string.lumen_m_gamut_bt709;
            case "picture.gamut|native": return R.string.lumen_m_gamut_native;
            case "picture.gamut|p3": return R.string.lumen_m_gamut_p3;
            case "picture.memc|high": return R.string.lumen_m_panel_high;
            case "picture.memc|low": return R.string.lumen_m_panel_low;
            case "picture.memc|medium": return R.string.lumen_m_panel_medium;
            case "picture.memc|off": return R.string.lumen_m_panel_off;
            case "picture.memc|original": return R.string.lumen_m_memc_original;
            case "picture.cc.color|red": return R.string.lumen_m_red;
            case "picture.cc.color|green": return R.string.lumen_m_green;
            case "picture.cc.color|blue": return R.string.lumen_m_blue;
            case "picture.cc.color|cyan": return R.string.lumen_m_cyan;
            case "picture.cc.color|magenta": return R.string.lumen_m_magenta;
            case "picture.cc.color|yellow": return R.string.lumen_m_yellow;
            case "picture.cc.color|skin": return R.string.lumen_m_skin;
            case "picture.hdr.screen_gain|5": return R.string.lumen_m_gain_0_5;
            case "picture.hdr.screen_gain|6": return R.string.lumen_m_gain_0_6;
            case "picture.hdr.screen_gain|7": return R.string.lumen_m_gain_0_7;
            case "picture.hdr.screen_gain|8": return R.string.lumen_m_gain_0_8;
            case "picture.hdr.screen_gain|9": return R.string.lumen_m_gain_0_9;
            case "picture.hdr.screen_gain|10": return R.string.lumen_m_gain_1_0;
            case "picture.hdr.screen_gain|11": return R.string.lumen_m_gain_1_1;
            case "picture.hdr.screen_gain|12": return R.string.lumen_m_gain_1_2;
            case "picture.hdr.screen_gain|13": return R.string.lumen_m_gain_1_3;
            case "picture.hdr.screen_gain|14": return R.string.lumen_m_gain_1_4;
            case "picture.hdr.screen_gain|15": return R.string.lumen_m_gain_1_5;
            case "picture.hdr.screen_gain|16": return R.string.lumen_m_gain_1_6;
            case "picture.hdr.screen_gain|17": return R.string.lumen_m_gain_1_7;
            case "picture.hdr.screen_gain|18": return R.string.lumen_m_gain_1_8;
            case "picture.hdr.screen_gain|19": return R.string.lumen_m_gain_1_9;
            case "picture.hdr.screen_gain|20": return R.string.lumen_m_gain_2_0;
            case "picture.dv_screen.gain|5": return R.string.lumen_m_gain_0_5;
            case "picture.dv_screen.gain|6": return R.string.lumen_m_gain_0_6;
            case "picture.dv_screen.gain|7": return R.string.lumen_m_gain_0_7;
            case "picture.dv_screen.gain|8": return R.string.lumen_m_gain_0_8;
            case "picture.dv_screen.gain|9": return R.string.lumen_m_gain_0_9;
            case "picture.dv_screen.gain|10": return R.string.lumen_m_gain_1_0;
            case "picture.dv_screen.gain|11": return R.string.lumen_m_gain_1_1;
            case "picture.dv_screen.gain|12": return R.string.lumen_m_gain_1_2;
            case "picture.dv_screen.gain|13": return R.string.lumen_m_gain_1_3;
            case "picture.dv_screen.gain|14": return R.string.lumen_m_gain_1_4;
            case "picture.dv_screen.gain|15": return R.string.lumen_m_gain_1_5;
            case "picture.dv_screen.gain|16": return R.string.lumen_m_gain_1_6;
            case "picture.dv_screen.gain|17": return R.string.lumen_m_gain_1_7;
            case "picture.dv_screen.gain|18": return R.string.lumen_m_gain_1_8;
            case "picture.dv_screen.gain|19": return R.string.lumen_m_gain_1_9;
            case "picture.dv_screen.gain|20": return R.string.lumen_m_gain_2_0;
            case "picture.style_filter|original": return R.string.lumen_m_original;
            case "picture.style_filter|custom": return R.string.lumen_m_filter_custom;
            case "picture.noise|off": return R.string.lumen_m_panel_off;
            case "picture.noise|low": return R.string.lumen_m_level_low;
            case "picture.noise|medium": return R.string.lumen_m_level_medium;
            case "picture.noise|high": return R.string.lumen_m_level_high;
            case "picture.local_contrast|off": return R.string.lumen_m_panel_off;
            case "picture.local_contrast|low": return R.string.lumen_m_level_low;
            case "picture.db_page.level|low": return R.string.lumen_m_level_low;
            case "picture.db_page.level|high": return R.string.lumen_m_level_high;
            case "picture.local_contrast|medium": return R.string.lumen_m_level_medium;
            case "picture.local_contrast|high": return R.string.lumen_m_level_high;
            case "eye.elder_color|off": return R.string.lumen_m_panel_off;
            case "eye.elder_color|low": return R.string.lumen_m_level_low;
            case "eye.elder_color|medium": return R.string.lumen_m_level_medium;
            case "eye.elder_color|high": return R.string.lumen_m_level_high;
            case "picture.noise|auto": return R.string.lumen_m_panel_auto;
            case "picture.color_temp|balanced": return R.string.lumen_m_ct_balanced;
            case "picture.gamma|bt1886": return R.string.lumen_m_gamma_bt1886;
            case "picture.gamma|st2084": return R.string.lumen_m_gamma_st2084;
            case "picture.gamma|hlg": return R.string.lumen_m_gamma_hlg;
            case "picture.gamma|manual": return R.string.lumen_m_gamma_manual;
            case "picture.gamma_index|g1_8": return R.string.lumen_m_gamma_1_8;
            case "picture.gamma_index|g1_9": return R.string.lumen_m_gamma_1_9;
            case "picture.gamma_index|g2_0": return R.string.lumen_m_gamma_2_0;
            case "picture.gamma_index|g2_1": return R.string.lumen_m_gamma_2_1;
            case "picture.gamma_index|g2_2": return R.string.lumen_m_gamma_2_2;
            case "picture.gamma_index|g2_3": return R.string.lumen_m_gamma_2_3;
            case "picture.gamma_index|g2_4": return R.string.lumen_m_gamma_2_4;
            case "picture.gamma_index|g2_5": return R.string.lumen_m_gamma_2_5;
            case "picture.gamma_index|g2_6": return R.string.lumen_m_gamma_2_6;
            case "picture.mode|ai": return R.string.lumen_m_panel_pm_ai;
            case "picture.mode|color_accuracy": return R.string.lumen_m_panel_pm_color_accuracy;
            case "picture.mode|hdr_vivid": return R.string.lumen_m_panel_pm_hdr_vivid;
            case "picture.mode|office": return R.string.lumen_m_panel_pm_office;
            case "picture.mode|performance": return R.string.lumen_m_panel_pm_performance;
            case "picture.mode|sport": return R.string.lumen_m_panel_pm_sport;
            case "picture.mode|standard": return R.string.lumen_m_panel_pm_standard;
            case "projection.mode|auto": return R.string.lumen_m_panel_proj_auto;
            case "projection.mode|ceiling": return R.string.lumen_m_panel_proj_ceiling;
            case "projection.mode|table": return R.string.lumen_m_panel_proj_table;
            case "settings.screensaver_delay|never": return R.string.lumen_m_dream_never;
            case "settings.screensaver_delay|m1": return R.string.lumen_m_dream_m1;
            case "settings.screensaver_delay|m5": return R.string.lumen_m_dream_m5;
            case "settings.screensaver_delay|m10": return R.string.lumen_m_dream_m10;
            case "settings.screensaver_delay|m30": return R.string.lumen_m_dream_m30;
            case "settings.screensaver_delay|m60": return R.string.lumen_m_dream_m60;
            case "settings.colour_vision|off": return R.string.lumen_m_panel_off;
            case "settings.colour_vision|gray": return R.string.lumen_m_colour_vision_gray;
            case "settings.colour_vision|red_green": return R.string.lumen_m_colour_vision_red_green;
            case "settings.colour_vision|green_red": return R.string.lumen_m_colour_vision_green_red;
            case "settings.colour_vision|blue_yellow": return R.string.lumen_m_colour_vision_blue_yellow;
            case "settings.launcher|classic": return R.string.lumen_m_launcher_classic;
            case "settings.launcher|lumen": return R.string.lumen_m_launcher_lumen;
            case "sound.enhancement|dts_vx": return R.string.lumen_m_panel_dts_vx;
            case "sound.enhancement|harman": return R.string.lumen_m_panel_harman;
            case "sound.format|auto": return R.string.lumen_m_sound_format_auto;
            case "sound.format|passthrough": return R.string.lumen_m_sound_format_passthrough;
            case "sound.format|pcm": return R.string.lumen_m_sound_format_pcm;
            case "sound.mode|ai": return R.string.lumen_m_panel_sound_ai;
            case "sound.mode|karaoke": return R.string.lumen_m_panel_sound_karaoke;
            case "sound.mode|movie": return R.string.lumen_m_panel_sound_movie;
            case "sound.mode|music": return R.string.lumen_m_panel_sound_music;
            case "sound.mode|sports": return R.string.lumen_m_panel_sound_sports;
            case "sound.mode|standard": return R.string.lumen_m_panel_sound_standard;
            case "sound.output|auto": return R.string.lumen_m_sound_output_auto;
            case "sound.output|speaker": return R.string.lumen_m_sound_output_speaker;
            case "picture.iris|dynamic": return R.string.lumen_m_iris_dynamic;
            case "picture.iris|f2": return R.string.lumen_m_iris_f2;
            case "picture.iris|f3": return R.string.lumen_m_iris_f3;
            case "picture.iris|f4": return R.string.lumen_m_iris_f4;
            case "picture.iris|f5_5": return R.string.lumen_m_iris_f5_5;
            case "picture.iris|f7": return R.string.lumen_m_iris_f7;
            case "picture.iris|f3_5": return R.string.lumen_m_iris_f3_5;
            case "picture.iris|f5": return R.string.lumen_m_iris_f5;
            default: return 0;
        }
    }

    /**
     * optionFormat: the label of every number token of a choice feature, a format with the number as %1$d (the screen
     * sizes in inches, the white balance grey levels in percent), 0 when its tokens have labels of their own.
     */
    /** Rows whose option tokens are their own label: the serial ID's letter a..z (shown as A..Z) and number 0..20. */
    static boolean ownLabels(String feature) {
        switch (feature) {
            case "serial.id_group": return true;
            case "serial.id_number": return true;
            default: return false;
        }
    }

    static int optionFormat(String feature) {
        switch (feature) {
            case "picture.hdr.screen_size": return R.string.lumen_m_inches;
            case "picture.dv_screen.size": return R.string.lumen_m_inches;
            case "picture.wb11.level": return R.string.lumen_m_wb_level;
            default: return 0;
        }
    }

    /** The Boost hint of the profile's boost.summary token (what Boost means on that model's stock). */
    static int boostSummary(String token) {
        return "brighter".equals(token) ? R.string.lumen_m_boost_effect_brighter : R.string.lumen_m_boost_effect;
    }

    /** The text under a hint picture that does not change (the projection picture has tipMount + tipSide). */
    static int tipText(String hint) {
        switch (hint) {
            case "focus_guide_front": return R.string.lumen_qpd_tip_focus_guide_front;
            case "focus_guide_side": return R.string.lumen_qpd_tip_focus_guide;
            default: return 0;
        }
    }

    /** The first sentence under the projection picture: how the projector is placed (projection.mode token). */
    static int tipMount(String mode) {
        switch (mode) {
            case "table": return R.string.lumen_qpd_tip_table;
            case "ceiling": return R.string.lumen_qpd_tip_ceiling;
            case "auto": return R.string.lumen_qpd_tip_auto;
            default: return 0;
        }
    }

    /** The second sentence under the projection picture: the viewer's side of the screen (ProjectionSetup.side). */
    static int tipSide(String side) {
        switch (side) {
            case "front": return R.string.lumen_qpd_tip_front;
            case "rear": return R.string.lumen_qpd_tip_rear;
            default: return 0;
        }
    }

    /** Short tile value of an option where the full name does not fit a square tile, 0 = the option label. */
    static int tileValue(String feature, String token) {
        switch (feature + "|" + token) {
            case "picture.mode|ai": return R.string.lumen_m_panel_tileval_pm_ai;
            case "picture.mode|color_accuracy": return R.string.lumen_m_panel_tileval_pm_color_accuracy;
            case "picture.mode|performance": return R.string.lumen_m_panel_tileval_pm_performance;
            case "picture.mode|dv_bright": case "picture.mode|dv_dark": return R.string.lumen_m_fmt_dolby;   // stock: Dolby Vision
            default: return 0;
        }
    }
}
