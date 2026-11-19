// SPDX-License-Identifier: Apache-2.0
package org.lumen.signal;

/**
 * The text keys of the panel: each is the string resource lumen_signal_&lt;key&gt; in res/values (English) and
 * res/values-ru (Russian). The host test checks that every key here exists in both and nothing else does.
 */
public final class SignalText {
    private SignalText() {}

    public static final String TITLE = "title", PORT_HDMI = "port_hdmi", PORT_APPS = "port_apps", NO_SIGNAL = "no_signal",
            SEC_INPUT = "sec_input", SEC_PROJECTOR = "sec_projector", SEC_SOUND = "sec_sound",
            R_INPUT = "r_input", PROGRESSIVE = "progressive", INTERLACED = "interlaced", HZ = "hz",
            R_LINK = "r_link", LINK_HDMI = "link_hdmi", LINK_DVI = "link_dvi",
            R_HDR = "r_hdr", HDR_SDR = "hdr_sdr", HDR_HDR10 = "hdr_hdr10", HDR_DOLBY = "hdr_dolby", HDR_HLG = "hdr_hlg",
            HDR_HDR10_PLUS = "hdr_hdr10_plus", HDR_VIVID = "hdr_vivid", HDR_OTHER = "hdr_other",
            R_IMAX = "r_imax", R_FILMMAKER = "r_filmmaker", R_ASPECT = "r_aspect", ASPECT_SETTING = "aspect_setting",
            ASPECT_AUTO = "aspect_auto", ASPECT_ORIGINAL = "aspect_original",
            R_RANGE = "r_range", RANGE_AUTO = "range_auto", RANGE_FULL = "range_full", RANGE_LIMITED = "range_limited",
            R_EDID = "r_edid", EDID_14 = "edid_14", EDID_20 = "edid_20", EDID_21 = "edid_21", EDID_AUTO = "edid_auto",
            R_PORT = "r_port", PORT_CEC = "port_cec", PORT_ARC = "port_arc", PORT_PLAIN = "port_plain",
            R_ALLM = "r_allm", R_VRR = "r_vrr", R_FREESYNC = "r_freesync", YES = "yes", NO = "no",
            R_SOURCE = "r_source", SOURCE_APPS = "source_apps", R_SCREEN = "r_screen",
            R_CHIP = "r_chip", R_MOTION = "r_motion", MOTION_SMOOTH = "motion_smooth", MOTION_JUDDER = "motion_judder",
            VERDICT_SMOOTH = "verdict_smooth", VERDICT_JUDDER = "verdict_judder", R_GAME_MODE = "r_game_mode",
            R_HGIG = "r_hgig", R_TONE_MAP = "r_tone_map", R_DV_MODE = "r_dv_mode", DV_BRIGHT = "dv_bright",
            DV_DARK = "dv_dark", R_TEMP_LIGHT = "r_temp_light", R_TEMP_SYSTEM = "r_temp_system", DEGREES = "degrees",
            ON = "on", OFF = "off", R_OUT = "r_out", OUT_SPEAKER = "out_speaker", OUT_ARC = "out_arc",
            OUT_EARC = "out_earc", OUT_BT = "out_bt", OUT_USB = "out_usb", OUT_WIRED = "out_wired",
            OUT_HDMI = "out_hdmi", OUT_OTHER = "out_other", R_SOUND_FORMAT = "r_sound_format", FMT_AUTO = "fmt_auto",
            FMT_PCM = "fmt_pcm", FMT_PASS = "fmt_pass", FMT_MANUAL = "fmt_manual", SOUND_LINE = "sound_line",
            POPUP_ROW = "popup_row", HINT_HOLD = "hint_hold", HINT_FOLD = "hint_fold", HINT_HIDE = "hint_hide",
            HINT_CLOSE = "hint_close",
            R_COLOR_FORMAT = "r_color_format", R_COLORIMETRY = "r_colorimetry", COLOR_DEFAULT = "color_default",
            R_DEPTH = "r_depth", DEPTH_BITS = "depth_bits", RANGE_AUTO_FULL = "range_auto_full",
            RANGE_AUTO_LIMITED = "range_auto_limited",
            R_DEVICE = "r_device", KIND_STB = "kind_stb", KIND_DVD = "kind_dvd", KIND_GAME = "kind_game",
            KIND_PC = "kind_pc", KIND_BLURAY = "kind_bluray", KIND_PLAYER = "kind_player", LINK_FRL = "link_frl",
            LINK_TMDS = "link_tmds", LINK_TMDS20 = "link_tmds20", R_MASTERING = "r_mastering", NITS = "nits",
            MASTER_MIN = "master_min", R_LIGHT = "r_light", LIGHT_BOTH = "light_both", LIGHT_CLL = "light_cll",
            LIGHT_FALL = "light_fall";

    /** Every key, for the resource check. */
    public static final String[] ALL = {TITLE, PORT_HDMI, PORT_APPS, NO_SIGNAL, SEC_INPUT, SEC_PROJECTOR, SEC_SOUND,
            R_INPUT, PROGRESSIVE, INTERLACED, HZ, R_LINK, LINK_HDMI, LINK_DVI, R_HDR, HDR_SDR, HDR_HDR10, HDR_DOLBY,
            HDR_HLG, HDR_HDR10_PLUS, HDR_VIVID, HDR_OTHER, R_IMAX, R_FILMMAKER, R_ASPECT, ASPECT_SETTING, ASPECT_AUTO,
            ASPECT_ORIGINAL, R_RANGE, RANGE_AUTO, RANGE_FULL,
            RANGE_LIMITED, R_EDID, EDID_14, EDID_20, EDID_21, EDID_AUTO, R_PORT, PORT_CEC, PORT_ARC, PORT_PLAIN, R_ALLM,
            R_VRR, R_FREESYNC, YES, NO, R_SOURCE, SOURCE_APPS, R_SCREEN, R_CHIP, R_MOTION, MOTION_SMOOTH, MOTION_JUDDER,
            VERDICT_SMOOTH, VERDICT_JUDDER, R_GAME_MODE, R_HGIG, R_TONE_MAP, R_DV_MODE, DV_BRIGHT, DV_DARK, R_TEMP_LIGHT,
            R_TEMP_SYSTEM, DEGREES, ON, OFF, R_OUT, OUT_SPEAKER, OUT_ARC, OUT_EARC, OUT_BT, OUT_USB, OUT_WIRED, OUT_HDMI,
            OUT_OTHER, R_SOUND_FORMAT, FMT_AUTO, FMT_PCM, FMT_PASS, FMT_MANUAL, SOUND_LINE, POPUP_ROW, HINT_HOLD,
            HINT_FOLD, HINT_HIDE, HINT_CLOSE, R_COLOR_FORMAT, R_COLORIMETRY, COLOR_DEFAULT, R_DEPTH, DEPTH_BITS, RANGE_AUTO_FULL,
            RANGE_AUTO_LIMITED, R_DEVICE, KIND_STB, KIND_DVD, KIND_GAME, KIND_PC, KIND_BLURAY, KIND_PLAYER, LINK_FRL,
            LINK_TMDS, LINK_TMDS20, R_MASTERING, NITS, MASTER_MIN, R_LIGHT, LIGHT_BOTH, LIGHT_CLL, LIGHT_FALL};

    /** The texts of the current language. */
    public interface Words {
        /** The text of {@code key}, its %1$s.. filled with {@code args}. */
        String get(String key, Object... args);

        /** The decimal separator of the current language ('.' English, ',' Russian). */
        char decimal();

        /** The menu's own name of a menu row ("picture.mode": Picture mode), null when the app gives none. */
        default String menuTitle(String feature) { return null; }

        /** The menu's own name of one option of that row ("picture.mode", "standard": Standard), null = none. */
        default String menuOption(String feature, String token) { return null; }
    }

}
