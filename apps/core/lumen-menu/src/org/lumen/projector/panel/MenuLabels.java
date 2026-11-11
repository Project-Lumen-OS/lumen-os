// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

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
    static int title(String id) {
        switch (id) {
            case "keystone.angle_h": return R.string.lumen_m_angle_h;
            case "zoom.digital": return R.string.lumen_m_zs_title;
            case "lamp.eco": return R.string.lumen_m_eco;
            case "settings.ambient_speaker": return R.string.lumen_m_ambient_speaker;
            case "keystone.angle_v": return R.string.lumen_m_angle_v;
            case "picture.dynamic_contrast": return R.string.lumen_m_dynamic_contrast;
            case "picture.dynamic_black": return R.string.lumen_m_dynamic_black;
            case "settings.sleep_timer": return R.string.lumen_m_sleep_timer;
            case "settings.remote": return R.string.lumen_m_remote;
            case "sound.earc": return R.string.lumen_m_earc;
            case "boost": return R.string.lumen_m_panel_boost;
            case "eye": return R.string.lumen_m_panel_eye;
            case "focus.af_now": return R.string.lumen_m_panel_af_now;
            case "focus.manual": return R.string.lumen_m_panel_manual_focus;
            case "focus.on_move": return R.string.lumen_m_panel_move_af;
            case "focus.on_power": return R.string.lumen_m_panel_power_on_af;
            case "focus.smart": return R.string.lumen_m_panel_smart_af;
            case "game.allm": return R.string.lumen_m_panel_allm;
            case "game.mode": return R.string.lumen_m_panel_game_mode;
            case "game.speed": return R.string.lumen_m_panel_game_speed;
            case "hdmi.edid": return R.string.lumen_m_hdmi_mode;
            case "hdmi.range": return R.string.lumen_m_hdmi_range;
            case "keystone.ak_now": return R.string.lumen_m_panel_ak_now;
            case "keystone.curtain": return R.string.lumen_m_panel_fit_screen;
            case "keystone.fit_now": return R.string.lumen_m_panel_fit_now;
            case "keystone.fit_screen": return R.string.lumen_m_fit_row;
            case "keystone.manual": return R.string.lumen_m_panel_manual_keystone;
            case "keystone.obstacle": return R.string.lumen_m_panel_obstacle;
            case "keystone.on_power": return R.string.lumen_m_panel_power_on_keystone;
            case "keystone.realtime": return R.string.lumen_m_panel_rt_keystone;
            case "keystone.on_move": return R.string.lumen_m_panel_move_ak;
            case "keystone.restore": return R.string.lumen_m_kst_restore;
            case "keystone.rotation": return R.string.lumen_m_rot_title;
            case "lamp": return R.string.lumen_m_panel_lamp;
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
            case "settings.launcher": return R.string.lumen_m_launcher_row;
            case "settings.projector": return R.string.lumen_m_panel_projector_settings;
            case "settings.recents": return R.string.lumen_m_recents_title;
            case "settings.system": return R.string.lumen_m_panel_system_settings;
            case "sound.enhancement": return R.string.lumen_m_panel_sound_enhancement;
            case "sound.eq": return R.string.lumen_m_eq_title;
            case "sound.format": return R.string.lumen_m_sound_format;
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
            case "eye": return R.string.lumen_m_panel_eye_desc;
            case "keystone.fit_screen": return R.string.lumen_m_fit_row_summary;
            case "lamp": return R.string.lumen_m_panel_lamp_hint;
            case "zoom": return R.string.lumen_m_panel_optical_zoom_summary;
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

    /** header: a string resource id, 0 when none. */
    static int header(String id) {
        switch (id) {
            case "image_correction": return R.string.lumen_m_panel_image_correction_settings;
            case "more": return R.string.lumen_m_kst_more;
            default: return 0;
        }
    }

    /** section: a string resource id, 0 when none. */
    static int section(String id) {
        switch (id) {
            case "eye": return R.string.lumen_m_panel_eye;
            case "focus": return R.string.lumen_m_panel_tile_focus;
            case "game": return R.string.lumen_m_panel_sec_game;
            case "general": return R.string.lumen_m_panel_sec_general;
            case "keystone": return R.string.lumen_m_panel_tile_keystone;
            case "lamp": return R.string.lumen_m_panel_lamp;
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
            case "usb_files": return R.string.lumen_qpd_usb;
            case "zoom": return R.string.lumen_qpd_zoom;
            case "zoom.digital": return R.string.lumen_qpd_zoom;
            default: return 0;
        }
    }

    /** icon: the animated tile icon, 0 when none. */
    static int icon(String id) {
        switch (id) {
            case "bluetooth": return R.drawable.lumen_qpd_ic_bt;
            case "eye": return R.drawable.lumen_qpd_ic_eye;
            case "focus": return R.drawable.lumen_qpd_ic_focus;
            case "game": return R.drawable.lumen_qpd_ic_game;
            case "general": return R.drawable.lumen_qpd_ic_gear;
            case "input": return R.drawable.lumen_qpd_ic_src;
            case "keystone": return R.drawable.lumen_qpd_ic_kst;
            case "keystone.ak_now": return R.drawable.lumen_qpd_ic_ak;
            case "mirroring": return R.drawable.lumen_qpd_ic_mirror;
            case "network": return R.drawable.lumen_qpd_ic_wifi;
            case "picture": return R.drawable.lumen_qpd_ic_pic;
            case "projection": return R.drawable.lumen_qpd_ic_proj;
            case "sound": return R.drawable.lumen_qpd_ic_snd;
            case "usb_files": return R.drawable.lumen_qpd_ic_usb;
            case "zoom": return R.drawable.lumen_qpd_ic_zoom;
            case "zoom.digital": return R.drawable.lumen_qpd_ic_zoom;
            default: return 0;
        }
    }

    /** option: the label of one option token of a choice feature, 0 when none. */
    static int option(String feature, String token) {
        switch (feature + "|" + token) {
            case "picture.color_temp|standard": return R.string.lumen_m_ct_standard;
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
            case "picture.mode|isf_day": return R.string.lumen_m_pm_isf_day;
            case "picture.mode|isf_night": return R.string.lumen_m_pm_isf_night;
            case "picture.mode|soft": return R.string.lumen_m_pm_soft;
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
            case "hdmi.range|auto": return R.string.lumen_m_panel_auto;
            case "hdmi.range|full": return R.string.lumen_m_hdmi_range_full;
            case "hdmi.range|limited": return R.string.lumen_m_hdmi_range_limited;
            case "picture.aspect|auto": return R.string.lumen_m_panel_auto;
            case "picture.aspect|original": return R.string.lumen_m_panel_aspect_original;
            case "picture.aspect|r16_9": return R.string.lumen_m_panel_aspect_16_9;
            case "picture.aspect|r4_3": return R.string.lumen_m_panel_aspect_4_3;
            case "picture.color_temp|color_temp_1": return R.string.lumen_m_panel_ct_1;
            case "picture.color_temp|d65": return R.string.lumen_m_panel_ct_d65;
            case "picture.memc|high": return R.string.lumen_m_panel_high;
            case "picture.memc|low": return R.string.lumen_m_panel_low;
            case "picture.memc|medium": return R.string.lumen_m_panel_medium;
            case "picture.memc|off": return R.string.lumen_m_panel_off;
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
            default: return 0;
        }
    }

    /** The Boost hint of the profile's boost.summary token (what Boost means on that model's stock). */
    static int boostSummary(String token) {
        return "brighter".equals(token) ? R.string.lumen_m_boost_effect_brighter : R.string.lumen_m_boost_effect;
    }

    /** The text under a hint picture. */
    static int tipText(String hint) {
        switch (hint) {
            case "picture_mode": return R.string.lumen_qpd_tip_picture;
            case "rear_projection": return R.string.lumen_qpd_tip_rear;
            default: return 0;
        }
    }

    /** Short tile value of an option where the full name does not fit a square tile, 0 = the option label. */
    static int tileValue(String feature, String token) {
        switch (feature + "|" + token) {
            case "picture.mode|ai": return R.string.lumen_m_panel_tileval_pm_ai;
            case "picture.mode|color_accuracy": return R.string.lumen_m_panel_tileval_pm_color_accuracy;
            case "picture.mode|performance": return R.string.lumen_m_panel_tileval_pm_performance;
            default: return 0;
        }
    }
}
