// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import org.lumen.menu.Feature;
import org.lumen.menu.MenuSpec;
import org.lumen.menu.MenuState;
import org.lumen.menu.ProjectorFeatures.Confirm;
import org.lumen.menu.ProjectorFeatures.Note;

import java.util.ArrayList;
import java.util.List;

/**
 * The Z9X stock rules of the shared menu, plain Java (no Android, no HAL), so the JVM render harness and the host
 * tests run exactly what the projector runs (apps/core/lumen-menu/test/render, test/z9x). Every rule is the one of
 * the Z9X 1.0.10 quick panel (QuickPanelController of release/z9x-1.0.10, cited as QPC:line): what the rows show
 * ({@link #state}), the notes that refuse a press before anything moves ({@link #blocked}) and the stock questions
 * ({@link #confirm}). Z9xFeatures fills {@link In} from one HAL read (Snapshot) plus the app's own stores.
 */
final class Z9xMenuRules {
    private Z9xMenuRules() {}

    /** Resolves a string resource of the app by name (the projector: Context; the harness: the res/ XML of a locale). */
    interface Text {
        String get(String name, Object... args);
    }

    /** The text line at the end of the General page (what a short POWER press does, QPC:1484). */
    static final String FOOTER_GENERAL = "power_sleep_summary";

    /** Wire values (GmpfClient enums), kept here so the rules stay plain Java. */
    static final int SOURCE_MEDIA = 0, SOURCE_HDMI1 = 1, SOURCE_HDMI2 = 2;
    static final int HDR_SDR = 0, HDR_HDR10 = 1, HDR_DOLBY_VISION = 2, HDR_HLG = 4, HDR_HDR10_PLUS = 5, HDR_VIVID = 6;
    static final int PICTURE_MODE_GAME = 4;
    static final int SPEAKER_PATH = 0;
    /** picture.mode tokens and 667 wires in stock order (QPC:105 PM_ORDER); 666 10..17 all read as AI. */
    static final String[] PM = {"ai", "standard", "sport", "hdr_vivid", "color_accuracy", "office", "performance"};
    static final int[] PM_WIRE = {10, 1, 9, 26, 29, 25, 5};
    /** sound.mode tokens and 61 wires (SoundProfiles.MODES: AI, then the four amp tables; no Standard). */
    static final String[] SOUND = {"ai", "movie", "music", "sports", "karaoke"};
    static final int[] SOUND_WIRE = {3, 1, 2, 12, 4};
    static final String[] MEMC = {"off", "low", "medium", "high"};
    static final String[] CT = {"d65", "color_temp_1"};               // ColorTempRule.WIRES 16, 17
    static final String[] RANGE = {"auto", "full", "limited"};
    static final String[] ASPECT = {"auto", "r4_3", "r16_9", "original"};
    static final int[] ASPECT_WIRE = {2, 0, 1, 4};                     // QPC:120 ASPECT_ORDER
    static final String[] SPEED = {"basic", "basic_hfr", "top_speed", "top_speed_hfr"};
    static final int[] SPEED_WIRE = {0, 1, 3, 2};                      // QPC:116 SPEED_ORDER
    static final String[] GAME = {"off", "on", "auto"};
    static final String[] ENH = {"harman", "dts_vx"};                  // SoundProfiles.ORDER
    static final String[] FORMAT = {"auto", "pcm", "passthrough"};     // AudioOutputChoice.Format order

    /** One read of the projector and the app stores the rules look at (null fields = that read failed). */
    static final class In {
        Integer source, hdr, pictureMode, lamp, lumensMode, memc, fmt3d, gameOption, aspect, soundEffect, soundProcess,
                putMode, hdmiRange, edid, gameMode55;
        final Integer[] pq = new Integer[4];
        Boolean is3dTo2d, dtsOn, eye, curtain, obstacle, powerOnAk, moveAk, moveAf, powerOnAf, smartAf, autoReverse,
                boost, aiContrast, superRes, ultra120, nativeFps, volumeBalance, earc, allm, gameActive55, tifHdmi;
        /** 662 plausible: its frame rate in 1/100 Hz, else null. */
        Integer hdmiFpsCenti;
        boolean soundPathActive;
        int soundPath = -1;
        // ---- the app's own stores (not HAL reads)
        /** QuickPanel.KEY_GAME_MODE: the game mode set last (-1 none). */
        int storedGameMode = -1;
        /** KEY_ULTRA_120: the Ultra 120 Hz value set last (157 failed). */
        boolean storedUltra120;
        /** Game mode / speed just set and not yet read back (shown until the read agrees or the wait is over). */
        int pendingGame = -1;
        Integer pendingSpeed;
        /** The connected sound outputs (AudioOutputChoice.options): tokens, labels, the selected index. */
        List<String> outTokens = new ArrayList<>();
        List<String> outLabels = new ArrayList<>();
        int outSelected = -1;
        boolean arcConnected;
        int formatIndex = -1;
        /** LauncherSwitcher.currentMode is classic. */
        boolean launcherClassic;
        /** AiSoundEngine.shownSubMode: the 61 wire the AI sound label names. */
        int aiSubWire = 1;
        /** The equalizer of the current sound mode: usable, its preset label (resource name) when usable. */
        String eqPresetLabel;
        /** The HAL read failed (QPC apply(null)): only the app's own stores above are known. */
        boolean failed;

        boolean isHdmiSource() { return source != null && (source == SOURCE_HDMI1 || source == SOURCE_HDMI2); }

        /** Snapshot.hdmiConfirmed: HDMI by 696 or an open org.lumen.tvinput HDMI session. */
        boolean hdmiConfirmed() { return isHdmiSource() || Boolean.TRUE.equals(tifHdmi); }

        /** Snapshot.noHdmiConfirmed: 696 == 0 and tvinput confirms no HDMI session. */
        boolean noHdmiConfirmed() { return source != null && source == SOURCE_MEDIA && Boolean.FALSE.equals(tifHdmi); }

        /** Snapshot.isTopSpeedGame. */
        boolean isTopSpeedGame() {
            return gameOption != null && (gameOption == 2 || gameOption == 3) && !Boolean.FALSE.equals(gameActive55);
        }

        boolean hdmiBelow62Hz() { return hdmiFpsCenti != null && hdmiFpsCenti < 6200; }

        PanelRules.In rules() {                                       // QPC:713 rin
            PanelRules.In in = new PanelRules.In();
            in.pic666 = pictureMode;
            in.hdr620 = hdr;
            in.fmt192 = fmt3d;
            in.to2d193 = is3dTo2d;
            in.lumens175 = lumensMode;
            in.opt647 = gameOption;
            in.game55 = gameActive55;
            in.boost51 = boost;
            in.nativeFps283 = nativeFps;
            in.ultra120 = ultra120;
            in.hdmi = hdmiConfirmed();
            in.hdmiFpsCenti = hdmiFpsCenti;
            return in;
        }

        /** QPC:1667 gameModeOn: the vendor game picture, else 55 when known, else "On" set by us, on HDMI. */
        boolean gameModeOn() {
            if (pictureMode != null && pictureMode == PICTURE_MODE_GAME) return true;
            if (gameActive55 != null) return gameActive55 && hdmiConfirmed();
            return storedGameMode == 1 && hdmiConfirmed();
        }
    }

    static String pmToken(Integer wire) {
        if (wire == null) return null;
        if (wire >= 10 && wire <= 17) return "ai";
        for (int i = 0; i < PM_WIRE.length; i++) if (PM_WIRE[i] == wire) return PM[i];
        return null;
    }

    static int pmWire(String token) { return wireOf(PM, PM_WIRE, token); }

    static String soundToken(Integer wire) { return tokenOf(SOUND, SOUND_WIRE, wire); }

    static int soundWire(String token) { return wireOf(SOUND, SOUND_WIRE, token); }

    static String tokenOf(String[] tokens, int[] wires, Integer wire) {
        if (wire == null) return null;
        for (int i = 0; i < wires.length; i++) if (wires[i] == wire) return tokens[i];
        return null;
    }

    static int wireOf(String[] tokens, int[] wires, String token) {
        for (int i = 0; i < tokens.length; i++) if (tokens[i].equals(token)) return wires[i];
        return -1;
    }

    static int indexOf(String[] tokens, String token) {
        for (int i = 0; i < tokens.length; i++) if (tokens[i].equals(token)) return i;
        return -1;
    }

    /** QPC:1605 visibleModes: HDR Vivid only for HDR Vivid content, Colour accuracy not for DV / HDR Vivid; the current mode always. */
    static List<String> visibleModes(In in) {
        int hdr = in == null || in.hdr == null ? HDR_SDR : in.hdr;
        String cur = in == null ? null : pmToken(in.pictureMode);
        List<String> l = new ArrayList<>();
        for (String m : PM) {
            boolean show = true;
            if ("hdr_vivid".equals(m)) show = hdr == HDR_VIVID;
            if ("color_accuracy".equals(m)) show = hdr != HDR_VIVID && hdr != HDR_DOLBY_VISION;
            if (show || m.equals(cur)) l.add(m);
        }
        return l;
    }

    /** The game mode the rows show (QPC:2143): 55 when it is known on HDMI, the change just made while it is pending. */
    static int shownGameMode(In in) {
        int gm = in.storedGameMode;
        if (in.hdmiConfirmed() && in.gameMode55 != null) {
            int pg = in.pendingGame;
            gm = pg >= 0 && pg != in.gameMode55 ? pg : in.gameMode55;
        }
        return gm;
    }

    /** What every row shows (QPC:2099 apply). {@code in} null or {@code failed} = the read failed (apply(null)). */
    static MenuState state(In in, Text t) {
        MenuState.Builder b = MenuState.builder();
        if (in == null) {
            in = new In();
            in.failed = true;
        }
        boolean ok = !in.failed;
        // ---- picture mode (options depend on the content's HDR type)
        b.options(Feature.PICTURE_MODE, visibleModes(in));
        b.put(Feature.PICTURE_MODE, pmToken(in.pictureMode));
        // ---- lamp + Boost (the menu puts the slider on the Boost step while Boost reads on)
        b.number(Feature.LAMP, in.lamp).on(Feature.BOOST, in.boost);
        // ---- Ultra 120 Hz: 157, else the value set last; never grey (QPC:2124)
        b.on(Feature.PICTURE_ULTRA120, !ok ? null : in.ultra120 != null ? in.ultra120 : in.storedUltra120);
        // ---- game (QPC:2139): off HDMI only the hint; Speed only in On, Auto switch only in Auto
        boolean hdmi = in.hdmiConfirmed();
        if (ok && !hdmi) b.note(MenuSpec.GAME, t.get("panel_game_hdmi_only"));
        int gm = shownGameMode(in);
        b.put(Feature.GAME_MODE, gm >= 0 && gm <= 2 ? GAME[gm] : "");
        b.hideIf(Feature.GAME_MODE, !(hdmi && in.boost != null));
        if (in.gameOption != null) {
            int shown = in.gameOption;
            if (in.pendingSpeed != null) shown = in.pendingSpeed;
            String sp = tokenOf(SPEED, SPEED_WIRE, shown);
            b.put(Feature.GAME_SPEED, sp != null ? sp : "");
        }
        b.hideIf(Feature.GAME_SPEED, !(hdmi && in.gameOption != null && gm == 1));
        if (in.allm != null) b.put(Feature.GAME_ALLM, in.allm ? "signal" : "device");
        b.hideIf(Feature.GAME_ALLM, !(hdmi && in.allm != null && gm == 2));
        // ---- sound enhancement: the STORED profile as stock shows it, "—" while unknown, the DTS hint
        if (in.soundProcess != null) {
            int row = SoundState.enhancementRow(in.soundProcess, in.dtsOn);
            b.put(Feature.SOUND_ENHANCEMENT, row >= 0 && row < ENH.length ? ENH[row] : "");
        }
        String dts = t.get("panel_dts_vx");
        int hk = ok ? SoundState.hint(in.soundProcess, in.dtsOn, in.soundPathActive, in.soundPath, SPEAKER_PATH) : SoundState.HINT_NONE;
        String sh = hk == SoundState.HINT_NOT_READY ? t.get("sound_output_not_ready", dts)
                : hk == SoundState.HINT_SPEAKER_ONLY ? t.get("sound_speaker_only", dts)
                : hk == SoundState.HINT_UNKNOWN ? t.get("sound_unknown_hint") : null;
        b.note(Feature.SOUND_ENHANCEMENT, sh);
        // ---- sound mode: "AI · <sub-mode>" follows the app (QPC:1366)
        b.label(Feature.SOUND_MODE, "ai", t.get("sound_ai_sub", t.get(soundLabelRes(in.aiSubWire))));
        if (in.soundEffect != null) {
            String sm = soundToken(in.soundEffect);
            b.put(Feature.SOUND_MODE, sm != null ? sm : "");
        }
        // ---- equalizer entry: the preset of this mode, "off with DTS", greyed while nothing could be read
        // (QPC:1354: eqPresetLabel is set by the app only when the equalizer is usable: AMP mode and a valid sound mode)
        boolean dtsStored = SoundState.dtsStored(in.soundProcess, in.dtsOn);
        b.put(Feature.SOUND_EQ, in.eqPresetLabel != null ? t.get(in.eqPresetLabel) : dtsStored ? t.get("eq_off_dts") : null);
        if (!ok) b.disable(Feature.SOUND_EQ);                         // QPC:1356 setRowEnabled(ok)
        // ---- eye protection, home screen
        b.on(Feature.EYE, in.eye);
        b.put(Feature.SETTINGS_LAUNCHER, in.launcherClassic ? "classic" : "lumen");
        // ---- focus + keystone
        b.on(Feature.KEYSTONE_REALTIME, in.moveAk).on(Feature.KEYSTONE_ON_POWER, in.powerOnAk)
                .on(Feature.KEYSTONE_CURTAIN, in.curtain).on(Feature.KEYSTONE_OBSTACLE, in.obstacle)
                .on(Feature.FOCUS_ON_MOVE, in.moveAf).on(Feature.FOCUS_ON_POWER, in.powerOnAf)
                .on(Feature.FOCUS_SMART, in.smartAf);
        // ---- projection
        if (in.putMode != null && in.autoReverse != null) {
            int p = in.putMode;
            b.put(Feature.PROJECTION_MODE, in.autoReverse ? "auto" : (p == 1 || p == 3) ? "ceiling" : "table");
            b.on(Feature.PROJECTION_REAR, p == 2 || p == 3);
        }
        // ---- picture rows: a row whose getter failed is hidden (rule: hide, never disable)
        String[] pqItems = {"brightness", "contrast", "saturation", "sharpness"};
        for (int i = 0; i < pqItems.length; i++) {
            String id = Feature.PICTURE_PQ + "." + pqItems[i];
            b.number(id, in.pq[i]);
            b.hideIf(id, ok && in.pq[i] == null);
        }
        Integer lm = in.lumensMode;
        if (ColorTempRule.isOffice(lm)) b.placeholder(Feature.PICTURE_COLOR_TEMP, t.get("panel_ct_3"));
        if (ColorTempRule.known(ok, lm)) {
            int ci = ColorTempRule.index(lm);
            b.put(Feature.PICTURE_COLOR_TEMP, ci >= 0 ? CT[ci] : "");
        }
        b.hideIf(Feature.PICTURE_COLOR_TEMP, ColorTempRule.hidden(ok, lm));
        boolean memcKnown = in.memc != null && in.memc >= 0 && in.memc <= 3;
        if (memcKnown) b.put(Feature.PICTURE_MEMC, MEMC[in.memc]);
        b.hideIf(Feature.PICTURE_MEMC, ok && !memcKnown);
        b.on(Feature.PICTURE_AI_CONTRAST, in.aiContrast).hideIf(Feature.PICTURE_AI_CONTRAST, ok && in.aiContrast == null);
        b.on(Feature.PICTURE_SUPER_RES, in.superRes).hideIf(Feature.PICTURE_SUPER_RES, ok && in.superRes == null);
        // HDMI settings: only while HDMI is on screen (they apply to that input)
        boolean showRange = hdmi && in.hdmiRange != null && in.hdmiRange >= 0 && in.hdmiRange <= 2;
        boolean showEdid = hdmi && in.edid != null;
        String as = tokenOf(ASPECT, ASPECT_WIRE, in.aspect);
        boolean showAspect = hdmi && as != null;
        if (showRange) b.put(Feature.HDMI_RANGE, RANGE[in.hdmiRange]);
        if (showEdid) b.put(Feature.HDMI_EDID, PanelRules.edidIndex(in.edid) == 1 ? "enhanced" : "compat");
        if (showAspect) b.put(Feature.PICTURE_ASPECT, as);
        b.hideIf(Feature.HDMI_RANGE, !showRange).hideIf(Feature.HDMI_EDID, !showEdid).hideIf(Feature.PICTURE_ASPECT, !showAspect);
        b.on(Feature.SOUND_LEVELING, in.volumeBalance).hideIf(Feature.SOUND_LEVELING, in.volumeBalance == null);
        b.on(Feature.SOUND_EARC, in.earc).hideIf(Feature.SOUND_EARC, in.earc == null);
        // sound output: the connected outputs (Auto and Speaker always), labels from the audio module
        if (!in.outTokens.isEmpty()) {
            b.options(Feature.SOUND_OUTPUT, in.outTokens);
            for (int i = 0; i < in.outTokens.size(); i++) b.label(Feature.SOUND_OUTPUT, in.outTokens.get(i), in.outLabels.get(i));
            if (in.outSelected >= 0 && in.outSelected < in.outTokens.size()) b.put(Feature.SOUND_OUTPUT, in.outTokens.get(in.outSelected));
            else b.put(Feature.SOUND_OUTPUT, "");
        }
        if (in.arcConnected && in.formatIndex >= 0 && in.formatIndex < FORMAT.length) b.put(Feature.SOUND_FORMAT, FORMAT[in.formatIndex]);
        else if (in.arcConnected) b.put(Feature.SOUND_FORMAT, "");
        b.hideIf(Feature.SOUND_FORMAT, !in.arcConnected);
        // ---- tiles: input and HDR type; the game tile says HDMI only off HDMI, else the mode
        b.tile(Feature.INPUT, ok ? infoText(in, t) : null);
        b.tile(MenuSpec.GAME, !ok ? null : !hdmi ? t.get("panel_tile_hdmi_only") : gm >= 0 && gm <= 2 ? t.get(gameLabel(gm)) : null);
        return b.build();
    }

    static String gameLabel(int gm) { return gm == 0 ? "lumen_m_panel_off" : gm == 1 ? "lumen_m_panel_on" : "lumen_m_panel_auto"; }

    static String soundLabelRes(int wire) {
        switch (wire) {
            case 3: return "panel_sound_ai";
            case 1: return "panel_sound_movie";
            case 2: return "panel_sound_music";
            case 12: return "panel_sound_sports";
            case 4: return "panel_sound_karaoke";
            default: return "panel_sound_standard";
        }
    }

    /** QPC:2310 infoText: "HDMI 1  ·  HDR10" (never Dolby Vision by name). */
    static String infoText(In in, Text t) {
        String src = in.source == null ? null : in.source == SOURCE_HDMI1 ? t.get("panel_src_hdmi1")
                : in.source == SOURCE_HDMI2 ? t.get("panel_src_hdmi2") : t.get("panel_src_android");
        String hdr = null;
        if (in.hdr != null) {
            switch (in.hdr) {
                case HDR_SDR: hdr = "SDR"; break;
                case HDR_HDR10: hdr = "HDR10"; break;
                case HDR_HLG: hdr = "HLG"; break;
                case HDR_HDR10_PLUS: hdr = "HDR10+"; break;
                case HDR_VIVID: hdr = "HDR Vivid"; break;
                default: hdr = "HDR"; break;
            }
        }
        if (src == null && hdr == null) return "";
        if (src == null) return hdr;
        if (hdr == null) return src;
        return src + "  \u00b7  " + hdr;
    }

    /** RuleNotes.note: the string resource name of a stock refusal reason. */
    static String note(PanelRules.Why w, boolean turnOff3d) {
        switch (w) {
            case GAME: return "blocked_game";
            case TOP_SPEED: return "rules_not_top_speed";
            case AI: return "rules_not_ai";
            case PERFORMANCE: return "rules_not_performance";
            case OFFICE: return "rules_not_office";
            case THREE_D: return turnOff3d ? "panel_boost_3d" : "panel_ultra120_3d";
            case BOOST: return "rules_not_boost";
            case NATIVE_FPS: return "rules_not_native_fps";
            case ULTRA_120: return "rules_not_ultra120";
            case DOLBY_VISION: return "rules_dv_active";
            case HDR: return "rules_not_hdr";
            case NO_VIDEO: return "panel_3d_need_video";
            case HIGH_FRAME_RATE: return "rules_not_hfr";
            case CLOSE_HFR: return "rules_close_hfr";
            case NOT_HDMI: return "panel_game_hdmi_only";
            default: return null;
        }
    }

    /** RuleNotes.show under the pressed row's title: the note, or null when the rule allows it. */
    private static Note rule(PanelRules.Why w, boolean turnOff3d) {
        if (w == null || w == PanelRules.Why.NONE) return null;
        String res = note(w, turnOff3d);
        return res == null ? null : Note.titled(res);
    }

    /** A press that does nothing (the read is missing: the rows only re-read, QPC "refresh(); return"). */
    static final Note SILENT = Note.text(null, false);

    /**
     * The stock refusal of a press before anything moves (QPC handlers, in their order), or null. {@code value}
     * null = a slider step, a section page or a screen; for the lamp also the Boost presses.
     */
    static Note blocked(String f, String value, In in) {
        if (f.startsWith(Feature.PICTURE_PQ + ".")) {                 // QPC:428 / 1045 basic picture sliders
            return in == null ? null : rule(PanelRules.basicOrPro(in.rules()), true);
        }
        switch (f) {
            case Feature.LAMP:
            case Feature.BOOST: {                                     // QPC:421 + 584 lampRefusal (3D, a locked lumens mode)
                if (in == null) return Feature.BOOST.equals(f) ? SILENT : null;
                if (Feature.BOOST.equals(f) && in.boost == null) return SILENT;
                if (!(Lamp3d.blocks(in.fmt3d, in.is3dTo2d) || Lamp3d.modeLocks(in.lumensMode))) return null;
                int lb = PanelRules.brightness(in.rules());
                if (lb == PanelRules.LAMP_3D || lb == PanelRules.LAMP_FREE) return Note.titledBy("panel_lamp", "panel_boost_3d");
                String name = lb == 0 ? "@rules_custom" : lb == 1 ? "@panel_pm_office" : "@panel_pm_performance";
                return Note.titledBy("panel_lamp", "panel_lamp_mode_locked", name);
            }
            case MenuSpec.GAME:                                       // QPC:518 the game page: DV, 3D, HDMI
                return in == null ? null : rule(PanelRules.gamePage(in.rules()), true);
            case Feature.PICTURE_MODE:                                // QPC:1639
                if (in == null || in.pictureMode == null) return SILENT;
                return rule(PanelRules.pictureMode(in.rules(), in.gameModeOn()), true);
            case Feature.PICTURE_COLOR_TEMP: {                        // QPC:1053
                if (in == null || in.lumensMode == null) return SILENT;
                Note n = rule(PanelRules.basicOrPro(in.rules()), true);
                return n != null ? n : rule(PanelRules.colorTemp(in.rules()), true);
            }
            case Feature.PICTURE_MEMC: {                              // QPC:1065
                if (in == null || in.memc == null) return SILENT;
                Note n = rule(PanelRules.basicOrPro(in.rules()), true);
                if (n != null) return n;
                return Boolean.TRUE.equals(in.nativeFps) ? Note.titled("pro_memc_blocked") : null;
            }
            case Feature.PICTURE_AI_CONTRAST:
            case Feature.PICTURE_SUPER_RES:
            case Feature.PICTURE_PRO:
            case Feature.PICTURE_RESET:                               // QPC:1079 / 1084 / 1101 / 1105
                return in == null ? null : rule(PanelRules.basicOrPro(in.rules()), true);
            case Feature.PICTURE_ULTRA120: {                          // QPC:1089 + 1725
                if (in == null) return null;
                Note n = rule(PanelRules.basicOrPro(in.rules()), true);
                if (n != null || !"on".equals(value)) return n;
                n = rule(PanelRules.ultra120On(in.rules()), true);
                if (n != null) return n;
                if (!in.noHdmiConfirmed() && !in.hdmiBelow62Hz()) {
                    return Note.titled(in.hdmiFpsCenti != null && in.hdmiFpsCenti >= 6200 ? "panel_ultra120_refused" : "panel_ultra120_hdmi");
                }
                return null;
            }
            case Feature.HDMI_EDID:                                   // QPC:1139
                return in == null ? null : rule(PanelRules.hdmiMode(in.rules()), false);
            case Feature.PICTURE_ASPECT:                              // QPC:1153
                if (in == null || in.aspect == null) return SILENT;
                return rule(PanelRules.aspect(in.rules(), in.gameModeOn()), false);
            case Feature.SOUND_ENHANCEMENT:                           // QPC:1180
                return in == null || in.soundProcess == null ? SILENT : null;
            case Feature.SOUND_MODE:                                  // QPC:1387
                return in == null || in.soundEffect == null ? SILENT : null;
            case Feature.GAME_SPEED:                                  // QPC:1848
                if (in == null || in.gameOption == null) return SILENT;
                return rule(PanelRules.gamePage(in.rules()), true);
            case Feature.GAME_ALLM:                                   // QPC:1430
                return in == null || in.allm == null ? SILENT : null;
            case Feature.KEYSTONE_REALTIME:                           // QPC:979
                return in == null ? null : rule(PanelRules.realtimeKeystone(in.rules()), false);
            case Feature.KEYSTONE_ROTATION:                           // QPC:973
                return in == null ? null : rule(PanelRules.keystone(in.rules()), false);
            case Feature.KEYSTONE_FIT_SCREEN:                         // QPC:965
                return in != null && in.isTopSpeedGame() ? Note.of("panel_kst_game_mode") : null;
            case Feature.PROJECTION_MODE:                             // QPC:2023
                return in == null || in.putMode == null || in.autoReverse == null ? SILENT : null;
            case Feature.PROJECTION_REAR:                             // QPC:2043
                return in == null || in.putMode == null ? SILENT : null;
            default:
                return null;
        }
    }

    /** The stock question before a change (QPC confirm calls), or null. */
    static Confirm confirm(String f, String value, In in) {
        switch (f) {
            case Feature.EYE:                                         // QPC:1942
                boolean on = "on".equals(value);
                return new Confirm("panel_eye", on ? "panel_eye_on_msg" : "panel_eye_off_msg", on ? "panel_turn_on" : "panel_turn_off");
            case Feature.PICTURE_MODE:                                // QPC:1653
                return "performance".equals(value) ? new Confirm("panel_pm_performance", "panel_pm_performance_msg", "panel_switch") : null;
            case Feature.PICTURE_ULTRA120:                            // QPC:1744
                return "on".equals(value) ? new Confirm("panel_ultra120", "panel_ultra120_msg", "panel_switch") : null;
            case Feature.PICTURE_RESET:                               // QPC:1106
                return new Confirm("panel_picture_reset", "panel_picture_reset_msg", "panel_reset");
            case Feature.PICTURE_RESET_ALL:                           // QPC:1114
                return new Confirm("picture_reset_all", "picture_reset_all_msg", "panel_reset");
            case Feature.SETTINGS_LAUNCHER:                           // QPC:1496
                return new Confirm("launcher_row", "classic".equals(value) ? "launcher_confirm_classic" : "launcher_confirm_lumen", "launcher_switch");
            case Feature.PROJECTION_MODE: {                           // QPC:2022
                if (in == null || in.putMode == null || in.autoReverse == null) return null;
                if ("auto".equals(value)) return in.autoReverse ? null : new Confirm("panel_projection_mode", "panel_proj_confirm_flip", "panel_switch");
                int target = projectionTarget(in.putMode, value);
                if (target == in.putMode) return null;                // same put mode: only auto flip goes off, no question
                return putModeConfirm(in.putMode, target);
            }
            case Feature.PROJECTION_REAR: {                           // QPC:2042
                if (in == null || in.putMode == null) return null;
                int target = rearTarget(in.putMode, "on".equals(value));
                return target == in.putMode ? null : putModeConfirm(in.putMode, target);
            }
            default:
                return null;
        }
    }

    /** Table / ceiling from the current put mode (front / rear kept). */
    static int projectionTarget(int cur, String value) {
        boolean front = cur == 0 || cur == 1;
        return "table".equals(value) ? (front ? 0 : 2) : (front ? 1 : 3);
    }

    /** Rear switch: from 0/2 on -> 2, off -> 0; from 1/3 on -> 3, off -> 1. */
    static int rearTarget(int cur, boolean rear) {
        boolean table = cur == 0 || cur == 2;
        return rear ? (table ? 2 : 3) : (table ? 0 : 1);
    }

    static boolean toCeiling(int cur, int target) { return (target == 1 || target == 3) && (cur == 0 || cur == 2); }

    private static Confirm putModeConfirm(int cur, int target) {             // QPC:2051 askPutMode
        return new Confirm("panel_projection_mode", toCeiling(cur, target) ? "panel_proj_confirm_ceiling" : "panel_proj_confirm_flip",
                "panel_switch");
    }
}
