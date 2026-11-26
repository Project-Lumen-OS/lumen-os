// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

import org.lumen.menu.ProjectorFeatures.Note;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The stock picture settings page refusal of every XGIMI newsettings (SettingMutexManager.mutex; dexdump of the HB
 * G0148 v6.17.12, the Z9X G0082 v6.15.62 and the X50 v1.2.64 newsettings, the same bytecode in the RS30 and Titan
 * ones). Stock checks it when a page opens, so none of its rows moves while it refuses: the game mode on
 * (DisplayManager getGameModeProp state 0, toast image_mode_mutex_game_run), then the picture mode AI (GmTvManager
 * getPictureMode reads exactly 10, ai_image_mode_mutex_*_setting), then Performance (reads 5,
 * performance_image_mode_mutex_*_setting); AI and Performance not while Dolby Vision plays (getHdrType 2, read only in
 * those two modes). The vendor reads 10 while any AI scene runs (film 16 included), so the page refuses for the whole
 * AI mode on every model, as on stock. The rule before them (picture sync of several projectors) has no Lumen row.
 * <p>
 * Models differ only as data ({@link Layout}): which stock page holds a row and the texts of that page. Stock never
 * shows such a row while the page refuses: OK on the page entry gives a plain toast, the entry is not greyed and
 * nothing opens (picture mode, light, reset all stay free). Lumen shows the rows on its own picture page, so every
 * step or press of one of them is refused before the row moves, with the same plain toast, and nothing is sent. A
 * value that could not be read never refuses. Plain Java, the adapter hands in its raw reads.
 */
public final class PictureSettingsRule {
    private PictureSettingsRule() {}

    /** Raw stock values the rule tests. */
    public static final int GAME_STATE_ON = 0, MODE_AI = 10, MODE_PERFORMANCE = 5, HDR_DOLBY_VISION = 2;
    /**
     * The stock texts (lumen-menu string resources): game mode (every page), AI and Performance on a page of
     * professional settings (ai_ / performance_image_mode_mutex_prof_setting) and on the basic page of picture mode 3.0
     * (ai_ / performance_image_mode_mutex_basic_setting).
     */
    public static final String NOTE_GAME = "lumen_m_ps_game", NOTE_AI = "lumen_m_ps_ai",
            NOTE_PERFORMANCE = "lumen_m_ps_performance", NOTE_AI_BASIC = "lumen_m_ps_ai_basic",
            NOTE_PERFORMANCE_BASIC = "lumen_m_ps_performance_basic";

    /** One stock page: the Lumen rows it holds and its AI and Performance texts. */
    public static final class Page {
        final String ai, performance;
        final FeatureId[] rows;

        Page(String ai, String performance, FeatureId... rows) {
            this.ai = ai;
            this.performance = performance;
            this.rows = rows;
        }

        boolean has(FeatureId row) {
            for (FeatureId r : rows) if (r == row) return true;
            return false;
        }
    }

    /** The stock picture settings pages of one settings tree (a row sits on one page at most). */
    public static final class Layout {
        final Page[] pages;

        Layout(Page... pages) { this.pages = pages; }

        /** The page that holds {@code f} (a slider by its picture.pq row), or null. */
        Page pageOf(FeatureId f) {
            if (f == null) return null;
            FeatureId row = f.parent != null ? f.parent : f;
            for (Page p : pages) if (p.has(row)) return p;
            return null;
        }
    }

    /**
     * Node image_mode_setting, one page with the professional texts (image_mode_prof_setting shares its block): the
     * HB dapengse / dapenghm / dapengfhm trees and the X50 family dianapro / dianamax / dianahy / athena35ultramax
     * trees, the same children. Brightness / contrast / saturation / sharpness, motion compensation, colour
     * temperature, white balance and its pages, gamut, colour optimisation, colour correction and its rows, gamma and
     * its value, HDR tone mapping, local and dynamic contrast, AI contrast, dynamic black and its level tuning
     * (image_mode_vibrant_correct, X50 family), noise reduction, super resolution, the auto mode switch, reset this picture mode; and the professional page that holds such rows on
     * the HB. Not on it: the picture mode, the light, Boost, reset all, aspect ratio, the HDMI rows.
     */
    public static final Layout ONE_PAGE = new Layout(new Page(NOTE_AI, NOTE_PERFORMANCE,
            FeatureId.PICTURE_PQ, FeatureId.PICTURE_MEMC, FeatureId.PICTURE_COLOR_TEMP,
            FeatureId.PICTURE_PRO, FeatureId.PICTURE_GAMUT, FeatureId.PICTURE_COLOR_OPT, FeatureId.PICTURE_GAMMA,
            FeatureId.PICTURE_GAMMA_INDEX, FeatureId.PICTURE_HDR_MAPPING, FeatureId.PICTURE_LOCAL_CONTRAST,
            FeatureId.PICTURE_DYNAMIC_CONTRAST, FeatureId.PICTURE_AI_CONTRAST, FeatureId.PICTURE_DYNAMIC_BLACK,
            FeatureId.PICTURE_BLACK_TUNING, FeatureId.PICTURE_NOISE, FeatureId.PICTURE_SUPER_RES, FeatureId.PICTURE_RESET,
            FeatureId.PICTURE_WB, FeatureId.PICTURE_WB2, FeatureId.PICTURE_WB2_RED_GAIN, FeatureId.PICTURE_WB2_GREEN_GAIN,
            FeatureId.PICTURE_WB2_BLUE_GAIN, FeatureId.PICTURE_WB2_RED_OFFSET, FeatureId.PICTURE_WB2_GREEN_OFFSET,
            FeatureId.PICTURE_WB2_BLUE_OFFSET, FeatureId.PICTURE_WB2_RESET,
            FeatureId.PICTURE_WB11, FeatureId.PICTURE_WB11_LEVEL, FeatureId.PICTURE_WB11_RED, FeatureId.PICTURE_WB11_GREEN,
            FeatureId.PICTURE_WB11_BLUE, FeatureId.PICTURE_WB11_RESET,
            FeatureId.PICTURE_CC, FeatureId.PICTURE_CC_COLOR, FeatureId.PICTURE_CC_HUE, FeatureId.PICTURE_CC_SATURATION,
            FeatureId.PICTURE_CC_LIGHTNESS, FeatureId.PICTURE_CC_RESET,
            FeatureId.PICTURE_HDR, FeatureId.PICTURE_HDR_SCREEN_SIZE, FeatureId.PICTURE_HDR_SCREEN_GAIN,
            FeatureId.PICTURE_AUTO_SWITCH, FeatureId.PICTURE_CUSTOM_BASE, FeatureId.PICTURE_CUSTOM_DELETE));

    /**
     * Picture mode 3.0, two pages with their own texts (the Z9X dapeng tree). image_mode_basic_setting: the auto mode
     * switch, brightness / contrast / saturation / sharpness, colour temperature, motion compensation, reset the basic
     * settings. image_mode_prof_setting: white balance and its pages, gamut, colour optimisation, colour correction and
     * its rows, gamma and its value, HDR tone mapping, local and dynamic contrast, AI contrast, dynamic black, Ultra
     * 120 Hz, noise reduction, super resolution; and the professional page itself.
     */
    public static final Layout BASIC_AND_PRO = new Layout(
            new Page(NOTE_AI_BASIC, NOTE_PERFORMANCE_BASIC, FeatureId.PICTURE_AUTO_SWITCH, FeatureId.PICTURE_PQ,
                    FeatureId.PICTURE_COLOR_TEMP, FeatureId.PICTURE_MEMC, FeatureId.PICTURE_RESET),
            new Page(NOTE_AI, NOTE_PERFORMANCE, FeatureId.PICTURE_PRO,
                    FeatureId.PICTURE_WB, FeatureId.PICTURE_WB2, FeatureId.PICTURE_WB2_RED_GAIN, FeatureId.PICTURE_WB2_GREEN_GAIN,
                    FeatureId.PICTURE_WB2_BLUE_GAIN, FeatureId.PICTURE_WB2_RED_OFFSET, FeatureId.PICTURE_WB2_GREEN_OFFSET,
                    FeatureId.PICTURE_WB2_BLUE_OFFSET, FeatureId.PICTURE_WB2_RESET,
                    FeatureId.PICTURE_WB11, FeatureId.PICTURE_WB11_LEVEL, FeatureId.PICTURE_WB11_RED, FeatureId.PICTURE_WB11_GREEN,
                    FeatureId.PICTURE_WB11_BLUE, FeatureId.PICTURE_WB11_RESET, FeatureId.PICTURE_GAMUT, FeatureId.PICTURE_COLOR_OPT,
                    FeatureId.PICTURE_CC, FeatureId.PICTURE_CC_COLOR, FeatureId.PICTURE_CC_HUE, FeatureId.PICTURE_CC_SATURATION,
                    FeatureId.PICTURE_CC_LIGHTNESS, FeatureId.PICTURE_CC_RESET, FeatureId.PICTURE_GAMMA,
                    FeatureId.PICTURE_GAMMA_INDEX, FeatureId.PICTURE_HDR_MAPPING, FeatureId.PICTURE_LOCAL_CONTRAST,
                    FeatureId.PICTURE_DYNAMIC_CONTRAST, FeatureId.PICTURE_AI_CONTRAST, FeatureId.PICTURE_DYNAMIC_BLACK,
                    FeatureId.PICTURE_ULTRA120, FeatureId.PICTURE_NOISE, FeatureId.PICTURE_SUPER_RES));

    /** One raw read of the adapter (a HAL call or its last snapshot); null = not read. */
    public interface Read<T, E extends Exception> {
        T get() throws E;
    }

    /**
     * The rows that stay closed while the game mode runs (TITAN testers 2026-11-19: the picture settings open in game
     * mode): the ones that add frames of delay, motion compensation, noise reduction, super resolution and Ultra 120 Hz.
     * Every other row of the page is written in game mode like any time and read back, so a value the vendor does not
     * take shows as not applied. Stock refuses the whole page (image_mode_mutex_game_run).
     */
    public static final Set<FeatureId> GAME_LATENCY = Collections.unmodifiableSet(EnumSet.of(FeatureId.PICTURE_MEMC,
            FeatureId.PICTURE_NOISE, FeatureId.PICTURE_SUPER_RES, FeatureId.PICTURE_ULTRA120));

    /** True for a row (or a slider of a row) that stays closed in game mode, {@link #GAME_LATENCY}. */
    public static boolean gameLatency(FeatureId f) {
        return f != null && GAME_LATENCY.contains(f.parent != null ? f.parent : f);
    }

    /** Is {@code f} (or the picture.pq row of a slider) on a stock picture settings page of {@code layout}? */
    public static boolean onPage(Layout layout, FeatureId f) {
        return layout.pageOf(f) != null;
    }

    /**
     * The stock text of the refusal of {@code f}, or null when its page opens, reading as stock does: the game state
     * first, then the picture mode, and the content kind only in AI and Performance. {@code gameOn}: the game mode prop
     * reads state 0.
     */
    public static <E extends Exception> String check(Layout layout, FeatureId f, Read<Boolean, E> gameOn,
            Read<Integer, E> pictureMode, Read<Integer, E> hdrType) throws E {
        Page page = layout.pageOf(f);
        if (page == null) return null;
        if (gameLatency(f) && Boolean.TRUE.equals(gameOn.get())) return NOTE_GAME;
        Integer mode = pictureMode.get();
        if (mode == null || mode != MODE_AI && mode != MODE_PERFORMANCE) return null;
        Integer hdr = hdrType.get();
        if (hdr != null && hdr == HDR_DOLBY_VISION) return null;
        return mode == MODE_AI ? page.ai : page.performance;
    }

    /** {@link #check} on values already read (null = not read). */
    public static String why(Layout layout, FeatureId f, Boolean gameOn, Integer pictureMode, Integer hdrType) {
        return check(layout, f, () -> gameOn, () -> pictureMode, () -> hdrType);
    }

    /** The refusal of a step or press of {@code f}: the stock toast (its text only, as stock), or null when allowed. */
    public static Note blocked(Layout layout, FeatureId f, Boolean gameOn, Integer pictureMode, Integer hdrType) {
        String why = why(layout, f, gameOn, pictureMode, hdrType);
        return why == null ? null : Note.toast(why);
    }

    /** {@link #blocked} on a kept {@link Reads}, no reads never refuse. */
    public static Note blocked(Layout layout, FeatureId f, Reads r) {
        return r == null ? null : blocked(layout, f, r.gameOn, r.pictureMode, r.hdrType);
    }

    /** The raw values of one state read, for an adapter that keeps them for the press before anything moves. */
    public static final class Reads {
        public final Boolean gameOn;
        public final Integer pictureMode, hdrType;

        Reads(Boolean gameOn, Integer pictureMode, Integer hdrType) {
            this.gameOn = gameOn;
            this.pictureMode = pictureMode;
            this.hdrType = hdrType;
        }

        /**
         * Reads as stock does, the game state first, then the picture mode (also in game mode now, the page opens there
         * except the {@link #GAME_LATENCY} rows), the content only in AI and Performance.
         */
        public static <E extends Exception> Reads read(Read<Boolean, E> gameOn, Read<Integer, E> pictureMode,
                Read<Integer, E> hdrType) throws E {
            Boolean game = gameOn.get();
            Integer mode = pictureMode.get();
            if (mode == null || mode != MODE_AI && mode != MODE_PERFORMANCE) return new Reads(game, mode, null);
            return new Reads(game, mode, hdrType.get());
        }
    }
}
