package org.lumen.projector.panel;

import org.lumen.menu.MenuJson;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Render walk of the Z9X 1.0.10 quick panel (QuickPanelController of release/z9x-1.0.10, transcribed: its build
 * order, labels, apply() rules and handler checks, QPC:line), compiled against the 1.0.10 PanelRules,
 * ColorTempRule, SoundState and Lamp3d and resolved with the 1.0.10 res/. Prints what NewWalk prints.
 * java OldWalk RES_DIR SCENARIOS PROBES
 */
public final class OldWalk {
    static WalkLib.Res res;
    static Map<String, Object> env;
    static O sn;                         // null = the read failed (apply(null))

    /** The 1.0.10 Snapshot fields plus the app stores apply() read. */
    static final class O {
        Integer source, hdr, pictureMode, lamp, lumensMode, memc, fmt3d, gameOption, aspect, soundEffect, soundProcess,
                putMode, hdmiRange, edid, gameMode55, hdmiFpsCenti;
        Integer[] pq = new Integer[4];
        Boolean is3dTo2d, dtsOn, eye, curtain, obstacle, powerOnAk, moveAk, moveAf, powerOnAf, smartAf, autoReverse,
                boost, aiContrast, superRes, ultra120, nativeFps, volumeBalance, earc, allm, gameActive55, tifHdmi;
        boolean soundPathActive;
        int soundPath = -1;
        int storedGameMode = -1;
        boolean storedUltra120;
        int pendingGame = -1;
        Integer pendingSpeed;
        List<String> outTokens = new ArrayList<>(), outLabels = new ArrayList<>();
        int outSelected = -1, formatIndex = -1;
        boolean arcConnected, launcherClassic;
        int aiSubWire = 1;
        String eqPresetLabel;

        boolean hdmiConfirmed() { return source != null && (source == 1 || source == 2) || Boolean.TRUE.equals(tifHdmi); }
        boolean noHdmiConfirmed() { return source != null && source == 0 && Boolean.FALSE.equals(tifHdmi); }
        boolean isTopSpeedGame() { return gameOption != null && (gameOption == 2 || gameOption == 3) && !Boolean.FALSE.equals(gameActive55); }
        boolean hdmiBelow62Hz() { return hdmiFpsCenti != null && hdmiFpsCenti < 6200; }
    }

    static String s(String n) { return res.get(n); }

    static final int[] PM_WIRE = {10, 1, 9, 26, 29, 25, 5};
    static final String[] PM_LABEL = {"panel_pm_ai", "panel_pm_standard", "panel_pm_sport", "panel_pm_hdr_vivid",
            "panel_pm_color_accuracy", "panel_pm_office", "panel_pm_performance"};
    static final int[] SOUND_WIRE = {3, 1, 2, 12, 4};                  // SoundProfiles.MODES
    static final int[] SPEED_WIRE = {0, 1, 3, 2};
    static final int[] ASPECT_WIRE = {2, 0, 1, 4};

    static int pmIndexOfWire(Integer w) {
        if (w == null) return -1;
        if (w >= 10 && w <= 17) return 0;
        for (int i = 0; i < PM_WIRE.length; i++) if (PM_WIRE[i] == w) return i;
        return -1;
    }

    /** QPC:1605 visibleModes. */
    static List<Integer> visibleModes() {
        int hdr = sn == null || sn.hdr == null ? 0 : sn.hdr;
        int cur = sn == null ? -1 : pmIndexOfWire(sn.pictureMode);
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < PM_WIRE.length; i++) {
            boolean show = true;
            if (i == 3) show = hdr == 6;
            if (i == 4) show = hdr != 6 && hdr != 2;
            if (show || i == cur) l.add(i);
        }
        return l;
    }

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

    static String soundLabel(int wire) {                                  // QPC:1366
        if (wire == 3) return res.get("sound_ai_sub", s(soundLabelRes(sn == null ? 1 : sn.aiSubWire)));
        return s(soundLabelRes(wire));
    }

    static int index(int[] wires, Integer v) {
        if (v == null) return -1;
        for (int i = 0; i < wires.length; i++) if (wires[i] == v) return i;
        return -1;
    }

    static WalkLib.Row toggle(String titleRes, Boolean v) {               // QPC:2078 setToggle
        WalkLib.Row r = new WalkLib.Row("toggle", s(titleRes));
        r.value = v == null ? null : v ? "on" : "off";
        r.enabled = v != null;
        return r;
    }

    static WalkLib.Row choice(String title, String[] optRes, int index, boolean known) {   // QPC:2083 setChoice
        WalkLib.Row r = new WalkLib.Row("choice", title);
        r.opts = new ArrayList<>();
        for (String o : optRes) r.opts.add(s(o));
        int i = known ? index : -1;
        r.value = i >= 0 && i < r.opts.size() ? r.opts.get(i) : null;
        r.enabled = known;
        return r;
    }

    static WalkLib.Row text(String t) { return new WalkLib.Row("text", t); }

    static WalkLib.Row nav(String t) { return new WalkLib.Row("nav", t); }

    static WalkLib.Row action(String t) { return new WalkLib.Row("action", t); }

    /** QPC:2143 the game mode shown. */
    static int gm() {
        boolean ok = sn != null;
        int gm = ok ? sn.storedGameMode : -1;
        if (ok && sn.hdmiConfirmed() && sn.gameMode55 != null) {
            int pg = sn.pendingGame;
            gm = pg >= 0 && pg != sn.gameMode55 ? pg : sn.gameMode55;
        }
        return gm;
    }

    static PanelRules.In rin() {                                          // QPC:713
        PanelRules.In in = new PanelRules.In();
        if (sn == null) return in;
        in.pic666 = sn.pictureMode; in.hdr620 = sn.hdr; in.fmt192 = sn.fmt3d; in.to2d193 = sn.is3dTo2d;
        in.lumens175 = sn.lumensMode; in.opt647 = sn.gameOption; in.game55 = sn.gameActive55; in.boost51 = sn.boost;
        in.nativeFps283 = sn.nativeFps; in.ultra120 = sn.ultra120; in.hdmi = sn.hdmiConfirmed(); in.hdmiFpsCenti = sn.hdmiFpsCenti;
        return in;
    }

    static boolean gameModeOn() {                                         // QPC:1667
        if (sn == null) return false;
        if (sn.pictureMode != null && sn.pictureMode == 4) return true;
        if (sn.gameActive55 != null) return sn.gameActive55 && sn.hdmiConfirmed();
        return sn.storedGameMode == 1 && sn.hdmiConfirmed();
    }

    // ---- pages (QPC build* order, then apply())
    static List<WalkLib.Row> focusPage() {
        boolean ok = sn != null;
        return new ArrayList<>(Arrays.asList(action(s("panel_af_now")), nav(s("panel_manual_focus")),
                toggle("panel_smart_af", ok ? sn.smartAf : null), toggle("panel_move_af", ok ? sn.moveAf : null),
                toggle("panel_power_on_af", ok ? sn.powerOnAf : null)));
    }

    static List<WalkLib.Row> keystonePage() {
        boolean ok = sn != null;
        List<WalkLib.Row> p = new ArrayList<>();
        p.add(action(s("panel_ak_now")));
        p.add(action(s("panel_fit_now")));
        p.add(nav(s("panel_manual_keystone")));
        p.add(nav(s("panel_optical_zoom")));
        p.add(text(s("panel_optical_zoom_summary")));
        if (Boolean.TRUE.equals(env.get("fit"))) { p.add(nav(s("fit_row"))); p.add(text(s("fit_row_summary"))); }
        p.add(nav(s("rot_title")));
        p.add(new WalkLib.Row("header", s("panel_image_correction_settings")));
        p.add(toggle("panel_rt_keystone", ok ? sn.moveAk : null));
        p.add(toggle("panel_power_on_keystone", ok ? sn.powerOnAk : null));
        p.add(toggle("panel_fit_screen", ok ? sn.curtain : null));
        p.add(toggle("panel_obstacle", ok ? sn.obstacle : null));
        p.add(new WalkLib.Row("header", s("kst_more")));
        p.add(action(s("kst_restore")));
        return p;
    }

    static List<WalkLib.Row> picturePage() {
        boolean ok = sn != null;
        List<WalkLib.Row> p = new ArrayList<>();
        List<Integer> modes = visibleModes();
        String[] ml = new String[modes.size()];
        for (int i = 0; i < ml.length; i++) ml[i] = PM_LABEL[modes.get(i)];
        int pm = ok ? pmIndexOfWire(sn.pictureMode) : -1;
        p.add(choice(s("panel_picture_mode"), ml, modes.indexOf(pm), pm >= 0));
        p.add(new WalkLib.Row("hint", s("qpd_tip_picture")));
        String[] pqt = {"panel_pq_brightness", "panel_pq_contrast", "panel_pq_saturation", "panel_pq_sharpness"};
        for (int i = 0; i < 4; i++) {
            Integer v = ok ? sn.pq[i] : null;
            WalkLib.Row r = new WalkLib.Row("slider", s(pqt[i]));
            r.value = v == null ? null : Integer.toString(v);
            r.enabled = v != null;
            r.visible = !(ok && v == null);
            p.add(r);
        }
        Integer lm = ok ? sn.lumensMode : null;
        WalkLib.Row ct = choice(s("panel_color_temp"), new String[]{"panel_ct_d65", "panel_ct_1"}, ColorTempRule.index(lm), ColorTempRule.known(ok, lm));
        ct.placeholder = ColorTempRule.isOffice(lm) ? s("panel_ct_3") : null;
        ct.visible = !ColorTempRule.hidden(ok, lm);
        p.add(ct);
        boolean memcKnown = ok && sn.memc != null && sn.memc >= 0 && sn.memc <= 3;
        WalkLib.Row memc = choice(s("panel_memc"), new String[]{"panel_off", "panel_low", "panel_medium", "panel_high"}, memcKnown ? sn.memc : -1, memcKnown);
        memc.visible = !(ok && !memcKnown);
        p.add(memc);
        WalkLib.Row ai = toggle("panel_ai_contrast", ok ? sn.aiContrast : null);
        ai.visible = !(ok && sn.aiContrast == null);
        p.add(ai);
        WalkLib.Row sr = toggle("panel_super_resolution", ok ? sn.superRes : null);
        sr.visible = !(ok && sn.superRes == null);
        p.add(sr);
        boolean u120 = ok && sn.storedUltra120;
        if (ok && sn.ultra120 != null) u120 = sn.ultra120;
        WalkLib.Row ur = new WalkLib.Row("toggle", s("panel_ultra120"));
        ur.value = ok ? (u120 ? "on" : "off") : null;
        ur.enabled = ok;
        p.add(ur);
        p.add(nav(s("pro_title")));
        p.add(action(s("panel_picture_reset")));
        p.add(action(s("picture_reset_all")));
        boolean onHdmi = ok && sn.hdmiConfirmed();
        boolean showRange = onHdmi && sn.hdmiRange != null && sn.hdmiRange >= 0 && sn.hdmiRange <= 2;
        boolean showEdid = onHdmi && sn.edid != null;
        int as = ok ? index(ASPECT_WIRE, sn.aspect) : -1;
        boolean showAspect = onHdmi && as >= 0;
        WalkLib.Row hh = new WalkLib.Row("header", "HDMI");
        hh.visible = showRange || showEdid || showAspect;            // QPC:2249
        p.add(hh);
        WalkLib.Row hr = choice(s("hdmi_range"), new String[]{"panel_auto", "hdmi_range_full", "hdmi_range_limited"}, showRange ? sn.hdmiRange : -1, true);
        hr.enabled = true;
        hr.visible = showRange;                                       // QPC:2251
        p.add(hr);
        WalkLib.Row hm = choice(s("hdmi_mode"), new String[]{"hdmi_mode_compat", "hdmi_mode_enhanced"}, showEdid ? PanelRules.edidIndex(sn.edid) : -1, true);
        hm.visible = showEdid;                                        // QPC:2254
        p.add(hm);
        WalkLib.Row ar = choice(s("panel_aspect"), new String[]{"panel_auto", "panel_aspect_4_3", "panel_aspect_16_9", "panel_aspect_original"}, showAspect ? as : -1, true);
        ar.visible = showAspect;                                          // GONE from the start (QPC:1165)
        p.add(ar);
        return p;
    }

    static List<WalkLib.Row> soundPage() {
        boolean ok = sn != null;
        List<WalkLib.Row> p = new ArrayList<>();
        int enh = ok ? SoundState.enhancementRow(sn.soundProcess, sn.dtsOn) : -1;
        p.add(choice(s("panel_sound_enhancement"), new String[]{"panel_harman", "panel_dts_vx"}, enh, ok && sn.soundProcess != null));
        String dtsLabel = s("panel_dts_vx");
        int hk = ok ? SoundState.hint(sn.soundProcess, sn.dtsOn, sn.soundPathActive, sn.soundPath, 0) : SoundState.HINT_NONE;
        String sh = hk == SoundState.HINT_NOT_READY ? res.get("sound_output_not_ready", dtsLabel)
                : hk == SoundState.HINT_SPEAKER_ONLY ? res.get("sound_speaker_only", dtsLabel)
                : hk == SoundState.HINT_UNKNOWN ? s("sound_unknown_hint") : null;
        if (sh != null) p.add(text(sh));
        WalkLib.Row sm = new WalkLib.Row("choice", s("panel_sound_mode"));
        sm.opts = new ArrayList<>();
        for (int w : SOUND_WIRE) sm.opts.add(soundLabel(w));
        int si = ok ? index(SOUND_WIRE, sn.soundEffect) : -1;
        boolean known = ok && sn.soundEffect != null;
        sm.value = known && si >= 0 ? sm.opts.get(si) : null;
        sm.enabled = known;
        p.add(sm);
        WalkLib.Row eq = nav(s("eq_title"));
        boolean dts = ok && SoundState.dtsStored(sn.soundProcess, sn.dtsOn);
        eq.value = ok && sn.eqPresetLabel != null ? s(sn.eqPresetLabel) : dts ? s("eq_off_dts") : null;
        eq.enabled = ok;                                                  // QPC:1356 setRowEnabled(ok)
        p.add(eq);
        WalkLib.Row vl = toggle("sound_volume_leveling", ok ? sn.volumeBalance : null);
        vl.visible = ok && sn.volumeBalance != null;                     // QPC:2256
        p.add(vl);
        WalkLib.Row ea = new WalkLib.Row("toggle", "eARC");
        ea.value = ok && sn.earc != null ? (sn.earc ? "on" : "off") : null;
        ea.enabled = ok && sn.earc != null;
        ea.visible = ok && sn.earc != null;                              // QPC:2258
        p.add(ea);
        WalkLib.Row so = new WalkLib.Row("choice", s("sound_output"));
        so.opts = new ArrayList<>();
        if (ok) so.opts.addAll(sn.outLabels);
        else { so.opts.add(s("sound_output_auto")); so.opts.add(s("sound_output_speaker")); }
        so.value = ok && sn.outSelected >= 0 && sn.outSelected < so.opts.size() ? so.opts.get(sn.outSelected) : null;
        so.enabled = ok;
        p.add(so);
        boolean arc = ok && sn.arcConnected;
        WalkLib.Row df = choice(s("sound_format"), new String[]{"sound_format_auto", "sound_format_pcm", "sound_format_passthrough"},
                arc ? sn.formatIndex : -1, arc);
        df.visible = arc;
        p.add(df);
        return p;
    }

    static List<WalkLib.Row> projectionPage() {
        boolean ok = sn != null;
        boolean auto = ok && Boolean.TRUE.equals(sn.autoReverse);
        boolean known = ok && sn.putMode != null && sn.autoReverse != null;
        List<WalkLib.Row> p = new ArrayList<>();
        String[] t = {"panel_proj_auto", "panel_proj_table", "panel_proj_ceiling"};
        boolean[] c = {known && auto, known && !auto && (sn.putMode == 0 || sn.putMode == 2), known && !auto && (sn.putMode == 1 || sn.putMode == 3)};
        for (int i = 0; i < 3; i++) {
            WalkLib.Row r = new WalkLib.Row("radio", s(t[i]));
            r.value = c[i] ? "[x]" : "[ ]";
            r.enabled = known;
            p.add(r);
        }
        p.add(toggle("panel_proj_rear", known ? (Boolean) (sn.putMode == 2 || sn.putMode == 3) : null));
        p.add(new WalkLib.Row("hint", s("qpd_tip_rear")));
        return p;
    }

    static List<WalkLib.Row> gamePage() {
        boolean ok = sn != null;
        boolean hdmi = ok && sn.hdmiConfirmed();
        List<WalkLib.Row> p = new ArrayList<>();
        WalkLib.Row hint = text(s("panel_game_hdmi_only"));
        hint.visible = ok && !hdmi;
        p.add(hint);
        int gm = gm();
        WalkLib.Row mode = choice(s("panel_game_mode"), new String[]{"panel_off", "panel_on", "panel_auto"}, gm, true);
        mode.visible = hdmi && sn.boost != null;
        p.add(mode);
        int sp = -1;
        if (ok && sn.gameOption != null) sp = index(SPEED_WIRE, sn.pendingSpeed != null ? sn.pendingSpeed : sn.gameOption);
        WalkLib.Row speed = choice(s("panel_game_speed"), new String[]{"panel_speed_standard", "panel_speed_standard_hfr", "panel_speed_top", "panel_speed_top_hfr"},
                sp, ok && sn.gameOption != null);
        speed.visible = hdmi && sn.gameOption != null && gm == 1;
        p.add(speed);
        WalkLib.Row allm = choice(s("panel_allm"), new String[]{"panel_allm_signal", "panel_allm_device"},
                ok && sn.allm != null ? (sn.allm ? 0 : 1) : -1, ok && sn.allm != null);
        allm.visible = hdmi && sn.allm != null && gm == 2;
        p.add(allm);
        return p;
    }

    static List<WalkLib.Row> eyePage() {
        boolean ok = sn != null;
        return new ArrayList<>(Arrays.asList(toggle("panel_eye", ok ? sn.eye : null), text(s("panel_eye_desc"))));
    }

    static List<WalkLib.Row> generalPage() {
        List<WalkLib.Row> p = new ArrayList<>(Arrays.asList(nav(s("panel_system_settings")), nav(s("panel_projector_settings")),
                nav(s("cec_entry")), nav(s("recents_title"))));
        if (Boolean.TRUE.equals(env.get("launcher"))) {
            boolean classic = sn != null ? sn.launcherClassic : false;
            p.add(choice(s("launcher_row"), new String[]{"launcher_lumen", "launcher_classic"}, classic ? 1 : 0, true));
        }
        p.add(text(s("power_sleep_summary")));
        return p;
    }

    static String infoText() {                                            // QPC:2310
        String src = sn.source == null ? null : sn.source == 1 ? s("panel_src_hdmi1") : sn.source == 2 ? s("panel_src_hdmi2") : s("panel_src_android");
        String hdr = null;
        if (sn.hdr != null) {
            switch (sn.hdr) {
                case 0: hdr = "SDR"; break;
                case 1: hdr = "HDR10"; break;
                case 4: hdr = "HLG"; break;
                case 5: hdr = "HDR10+"; break;
                case 6: hdr = "HDR Vivid"; break;
                default: hdr = "HDR"; break;
            }
        }
        if (src == null && hdr == null) return "";
        if (src == null) return hdr;
        if (hdr == null) return src;
        return src + "  \u00b7  " + hdr;
    }

    static void walk(StringBuilder out) {
        boolean ok = sn != null;
        out.append("card\n");
        out.append("  round ").append(s("panel_tile_eye")).append(ok && Boolean.FALSE.equals(sn.eye) ? " (icon eye off)" : "").append('\n');
        out.append("  round ").append(s("panel_all_settings")).append('\n');
        out.append("  wide ").append(s("qpd_autocorrect")).append(" = ").append(s("qpd_autocorrect_value")).append('\n');
        out.append("  small ").append(s("panel_tile_keystone")).append('\n');
        out.append("  small ").append(s("qpd_zoom")).append('\n');
        int pm = ok ? pmIndexOfWire(sn.pictureMode) : -1;
        String pmTile = pm < 0 ? null : pm == 0 ? s("panel_tileval_pm_ai") : pm == 4 ? s("panel_tileval_pm_color_accuracy")
                : pm == 6 ? s("panel_tileval_pm_performance") : s(PM_LABEL[pm]);
        out.append("  wide ").append(s("panel_sec_picture")).append(pmTile == null ? "" : " = " + pmTile).append('\n');
        out.append("  small ").append(s("panel_sec_sound")).append('\n');
        out.append("  small ").append(s("panel_tile_focus")).append('\n');
        String info = ok ? infoText() : null;
        out.append("  wide ").append(s("panel_tile_input")).append(info == null ? "" : " = " + info).append('\n');
        out.append("  small ").append(s("qpd_bluetooth")).append('\n');
        out.append("  small ").append(s("qpd_network")).append('\n');
        boolean hdmi = ok && sn.hdmiConfirmed();
        int gm = gm();
        String gv = !ok ? null : !hdmi ? s("panel_tile_hdmi_only") : gm >= 0 && gm <= 2 ? s(new String[]{"panel_off", "panel_on", "panel_auto"}[gm]) : null;
        out.append("  wide ").append(s("panel_tile_game")).append(gv == null ? "" : " = " + gv).append('\n');
        out.append("  small ").append(s("panel_tile_projection")).append('\n');
        boolean mir = Boolean.TRUE.equals(env.get("mirroring")), usb = Boolean.TRUE.equals(env.get("usb"));
        if (mir) out.append("  small ").append(s("qpd_mirroring")).append('\n');
        else if (usb) out.append("  small ").append(s("qpd_usb")).append('\n');
        boolean boostOn = ok && Boolean.TRUE.equals(sn.boost);
        out.append("  slider ").append(s("panel_lamp")).append(" = ").append(boostOn ? s("panel_boost") : ok && sn.lamp != null ? sn.lamp.toString() : "-").append('\n');
        // popups (QPC:843)
        List<WalkLib.Row> pic = new ArrayList<>();
        for (int i : visibleModes()) {
            WalkLib.Row r = new WalkLib.Row("radio", s(PM_LABEL[i]));
            r.value = pm == i ? "[x]" : "[ ]";
            pic.add(r);
        }
        pic.add(nav(s("qpd_picture_more")));
        pic.add(new WalkLib.Row("hint", s("qpd_tip_picture")));
        WalkLib.page(out, "popup " + s("panel_picture_mode"), pic);
        List<WalkLib.Row> snd = new ArrayList<>();
        int si = ok ? index(SOUND_WIRE, sn.soundEffect) : -1;
        for (int i = 0; i < SOUND_WIRE.length; i++) {
            WalkLib.Row r = new WalkLib.Row("radio", soundLabel(SOUND_WIRE[i]));
            r.value = i == si ? "[x]" : "[ ]";
            snd.add(r);
        }
        snd.add(nav(s("qpd_sound_more")));
        WalkLib.page(out, "popup " + s("panel_sound_mode"), snd);
        List<WalkLib.Row> game = gamePage(), gd = new ArrayList<>();
        for (int k = 0; k < game.size(); k++) {
            if (k == 1) {
                String[] gl = {"panel_off", "panel_on", "panel_auto"};
                for (int i = 0; i < 3; i++) {
                    WalkLib.Row r = new WalkLib.Row("radio", s(gl[i]));
                    r.value = i == gm ? "[x]" : "[ ]";
                    r.visible = game.get(1).visible;
                    gd.add(r);
                }
            } else gd.add(game.get(k));
        }
        WalkLib.page(out, "popup " + s("panel_sec_game"), gd);
        WalkLib.page(out, "page " + s("panel_tile_keystone"), keystonePage());
        WalkLib.page(out, "page " + s("panel_tile_focus"), focusPage());
        WalkLib.page(out, "page " + s("panel_sec_picture"), picturePage());
        WalkLib.page(out, "page " + s("panel_sec_sound"), soundPage());
        WalkLib.page(out, "page " + s("panel_projection_mode"), projectionPage());
        WalkLib.page(out, "page " + s("panel_sec_game"), gamePage());
        WalkLib.page(out, "page " + s("panel_eye"), eyePage());
        WalkLib.page(out, "page " + s("panel_all_settings"), generalPage());
        out.append("all settings: ").append(s("panel_tile_keystone")).append(", ").append(s("panel_tile_focus")).append(", ")
                .append(s("panel_sec_picture")).append(", ").append(s("panel_tile_projection")).append(", ").append(s("panel_sec_sound"))
                .append(", ").append(s("panel_eye")).append(", ").append(s("panel_sec_general")).append(",\n");
    }

    // ---- handler checks before a change (probes)
    static String note(PanelRules.Why w, boolean off3d) {
        switch (w) {
            case GAME: return "blocked_game";
            case TOP_SPEED: return "rules_not_top_speed";
            case AI: return "rules_not_ai";
            case PERFORMANCE: return "rules_not_performance";
            case OFFICE: return "rules_not_office";
            case THREE_D: return off3d ? "panel_boost_3d" : "panel_ultra120_3d";
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

    static String rule(String title, PanelRules.Why w, boolean off3d) {
        if (w == null || w == PanelRules.Why.NONE) return null;
        String r = note(w, off3d);
        return r == null ? null : "note " + title + " | " + s(r);
    }

    static String confirm(String t, String m, String ok) { return "confirm " + s(t) + " | " + s(m) + " | " + s(ok); }

    static final String SILENT = "(silent)";

    static String probe(String f, String v) {
        boolean ok = sn != null;
        String r;
        switch (f) {
            case "eye": return confirm("panel_eye", "on".equals(v) ? "panel_eye_on_msg" : "panel_eye_off_msg", "on".equals(v) ? "panel_turn_on" : "panel_turn_off");
            case "picture.mode": {
                if (!ok || sn.pictureMode == null) return SILENT;
                r = rule(s("panel_picture_mode"), PanelRules.pictureMode(rin(), gameModeOn()), true);
                if (r != null) return r;
                return "performance".equals(v) ? confirm("panel_pm_performance", "panel_pm_performance_msg", "panel_switch") : "go";
            }
            case "picture.ultra120": {
                r = rule(s("panel_ultra120"), PanelRules.basicOrPro(rin()), true);
                if (r != null || !"on".equals(v)) return r != null ? r : "go";
                r = rule(s("panel_ultra120"), PanelRules.ultra120On(rin()), true);
                if (r != null) return r;
                if (ok && !sn.noHdmiConfirmed() && !sn.hdmiBelow62Hz()) {   // QPC:1738 snap != null
                    return "note " + s("panel_ultra120") + " | " + s(sn.hdmiFpsCenti != null && sn.hdmiFpsCenti >= 6200 ? "panel_ultra120_refused" : "panel_ultra120_hdmi");
                }
                return confirm("panel_ultra120", "panel_ultra120_msg", "panel_switch");
            }
            case "picture.reset":
                if (ok) { r = rule(s("panel_picture_reset"), PanelRules.basicOrPro(rin()), true); if (r != null) return r; }
                return confirm("panel_picture_reset", "panel_picture_reset_msg", "panel_reset");
            case "picture.reset_all": return confirm("picture_reset_all", "picture_reset_all_msg", "panel_reset");
            case "settings.launcher": return confirm("launcher_row", "classic".equals(v) ? "launcher_confirm_classic" : "launcher_confirm_lumen", "launcher_switch");
            case "projection.mode": {
                if (!ok || sn.putMode == null || sn.autoReverse == null) return SILENT;
                int cur = sn.putMode;
                if ("auto".equals(v)) return sn.autoReverse ? "go" : confirm("panel_projection_mode", "panel_proj_confirm_flip", "panel_switch");
                boolean front = cur == 0 || cur == 1;
                int target = "table".equals(v) ? (front ? 0 : 2) : (front ? 1 : 3);
                if (target == cur) return "go";
                boolean toCeiling = (target == 1 || target == 3) && (cur == 0 || cur == 2);
                return confirm("panel_projection_mode", toCeiling ? "panel_proj_confirm_ceiling" : "panel_proj_confirm_flip", "panel_switch");
            }
            case "projection.rear": {
                if (!ok || sn.putMode == null) return SILENT;
                int cur = sn.putMode;
                boolean table = cur == 0 || cur == 2, on = "on".equals(v);
                int target = on ? (table ? 2 : 3) : (table ? 0 : 1);
                if (target == cur) return "go";
                boolean toCeiling = (target == 1 || target == 3) && (cur == 0 || cur == 2);
                return confirm("panel_projection_mode", toCeiling ? "panel_proj_confirm_ceiling" : "panel_proj_confirm_flip", "panel_switch");
            }
            case "picture.memc": {
                if (!ok || sn.memc == null) return SILENT;
                r = rule(s("panel_memc"), PanelRules.basicOrPro(rin()), true);
                if (r != null) return r;
                return Boolean.TRUE.equals(sn.nativeFps) ? "note " + s("panel_memc") + " | " + s("pro_memc_blocked") : "go";
            }
            case "keystone.fit_screen": return ok && sn.isTopSpeedGame() ? "note " + s("panel_kst_game_mode") : "go";
            case "lamp": {                                                // QPC:421 + 584 lampRefusal
                if (!ok || !(Lamp3d.blocks(sn.fmt3d, sn.is3dTo2d) || Lamp3d.modeLocks(sn.lumensMode))) return "go";
                int b = PanelRules.brightness(rin());
                if (b == PanelRules.LAMP_3D || b == PanelRules.LAMP_FREE) return "note " + s("panel_lamp") + " | " + s("panel_boost_3d");
                String name = b == 0 ? "rules_custom" : b == 1 ? "panel_pm_office" : "panel_pm_performance";
                return "note " + s("panel_lamp") + " | " + res.get("panel_lamp_mode_locked", s(name));
            }
            case "picture.pq.brightness": {                               // QPC:428
                if (!ok) return "go";
                r = rule(s("panel_pq_brightness"), PanelRules.basicOrPro(rin()), true);
                return r != null ? r : "go";
            }
            case "picture.color_temp": {                                  // QPC:1053
                if (!ok || sn.lumensMode == null) return SILENT;
                r = rule(s("panel_color_temp"), PanelRules.basicOrPro(rin()), true);
                if (r == null) r = rule(s("panel_color_temp"), PanelRules.colorTemp(rin()), true);
                return r != null ? r : "go";
            }
            case "picture.ai_contrast": {                                 // QPC:1079 pictureRowRefused
                r = rule(s("panel_ai_contrast"), PanelRules.basicOrPro(rin()), true);
                return r != null ? r : "go";
            }
            case "hdmi.edid": {                                           // QPC:1139
                r = rule(s("hdmi_mode"), PanelRules.hdmiMode(rin()), false);
                return r != null ? r : "go";
            }
            case "game.speed": {                                          // QPC:1848
                if (!ok || sn.gameOption == null) return SILENT;
                r = rule(s("panel_game_speed"), PanelRules.gamePage(rin()), true);
                return r != null ? r : "go";
            }
            case "keystone.realtime": {                                   // QPC:979
                r = rule(s("panel_rt_keystone"), PanelRules.realtimeKeystone(rin()), false);
                return r != null ? r : "go";
            }
            case "keystone.rotation": {                                   // QPC:973
                r = rule(s("rot_title"), PanelRules.keystone(rin()), false);
                return r != null ? r : "go";
            }
            case "game": {                                                // QPC:518 the game page
                if (!ok) return "go";
                r = rule(s("panel_sec_game"), PanelRules.gamePage(rin()), true);
                return r != null ? r : "go";
            }
            case "sound.mode": return !ok || sn.soundEffect == null ? SILENT : "go";
            case "sound.enhancement": return !ok || sn.soundProcess == null ? SILENT : "go";
            case "game.allm": return !ok || sn.allm == null ? SILENT : "go";
            case "picture.aspect": {
                if (!ok || sn.aspect == null) return SILENT;
                r = rule(s("panel_aspect"), PanelRules.aspect(rin(), gameModeOn()), false);
                return r != null ? r : "go";
            }
            default: return "?";
        }
    }

    public static void main(String[] a) throws Exception {
        res = new WalkLib.Res(new File(a[0]));
        @SuppressWarnings("unchecked") List<Object> scen = (List<Object>) MenuJson.parse(new String(Files.readAllBytes(new File(a[1]).toPath()), StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked") List<Object> probes = (List<Object>) MenuJson.parse(new String(Files.readAllBytes(new File(a[2]).toPath()), StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (String loc : res.locales()) {
            res.locale = loc;
            for (Object so : scen) {
                @SuppressWarnings("unchecked") Map<String, Object> sc = (Map<String, Object>) so;
                @SuppressWarnings("unchecked") Map<String, Object> e = (Map<String, Object>) sc.get("env");
                env = e;
                sn = toO(sc.get("in"));
                out.append("### ").append(loc).append(' ').append(sc.get("name")).append('\n');
                walk(out);
                for (Object po : probes) {
                    @SuppressWarnings("unchecked") List<Object> p = (List<Object>) po;
                    String f = (String) p.get(0), v = (String) p.get(1);
                    if ("settings.launcher".equals(f) && !Boolean.TRUE.equals(env.get("launcher"))) continue;
                    if ("keystone.fit_screen".equals(f) && !Boolean.TRUE.equals(env.get("fit"))) continue;
                    out.append("probe ").append(f).append('=').append(v).append(": ").append(probe(f, v)).append('\n');
                }
            }
        }
        System.out.print(out);
    }

    static O toO(Object o) throws Exception {
        if (o == null) return null;
        @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) o;
        O in = new O();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            Field f = O.class.getDeclaredField(e.getKey());
            f.setAccessible(true);
            Object v = e.getValue();
            Class<?> t = f.getType();
            if (e.getKey().equals("pq")) {
                @SuppressWarnings("unchecked") List<Object> l = (List<Object>) v;
                for (int i = 0; i < l.size(); i++) in.pq[i] = l.get(i) == null ? null : ((Long) l.get(i)).intValue();
            } else if (e.getKey().equals("outTokens") || e.getKey().equals("outLabels")) {
                @SuppressWarnings("unchecked") List<Object> l = (List<Object>) v;
                @SuppressWarnings("unchecked") List<String> dst = (List<String>) f.get(in);
                for (Object x : l) { String sx = (String) x; dst.add(sx.startsWith("@") ? s(sx.substring(1)) : sx); }
            } else if (t == Integer.class || t == int.class) {
                f.set(in, v == null ? null : ((Long) v).intValue());
            } else {
                f.set(in, v);
            }
        }
        return in;
    }
}
