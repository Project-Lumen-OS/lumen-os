package org.lumen.projector.panel;

import org.lumen.menu.FeatureId;
import org.lumen.menu.MenuSpec;
import org.lumen.menu.MenuState;
import org.lumen.menu.ProjectorFeatures.Confirm;
import org.lumen.menu.ProjectorFeatures.Note;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * XGIMI Play 6 (dragon_healthy): the plain-Java rules of the Play 6 adapter of the shared projector menu
 * (apps/core/lumen-menu the design notes, profile devices/dragon_healthy/menu.json, facts
 * the design notes). No Android and no HAL here, so host tests (test/menu) check every
 * table and gate: the profile's option tokens to the Play 6 HIDL wire values (IGmpf / IGmpf2 by name, codes of
 * research/play6/gmpf_codes_play6.tsv), which values the menu may show in the current picture mode and input (the
 * stock dayao tree rules the old Play 6 panel kept), and which screens a device screen returns to.
 *
 * Lumen rule on the Play 6: a row the current mode or input does not have is never shown (MenuState.hide, never a
 * disabled row, never a value of another mode), a row whose getter failed neither, and a write to it is refused
 * without any call. The v3 hooks of the module come from here too: the stock refusals before
 * a press (blocked), the stock questions (confirm) and what this unit has (available).
 */
public final class Play6MenuGates {
    private Play6MenuGates() {}

    // ------------------------------------------------------------------ wire values (Play6Gmpf, test/menu checks them)

    /** IGmpf 666 / 667 picture modes (stock dayao tree). */
    static final int PM_AI = 10, PM_MOVIE = 1, PM_SPORT = 9, PM_TV = 7, PM_CUSTOM = 3, PM_OFFICE = 25,
            PM_PERFORMANCE = 5, PM_GAME = 4;
    /** 666 values 10..17: the AI scene sub-modes, shown as AI. */
    static final int AI_SCENE_FIRST = 10, AI_SCENE_LAST = 17;
    /** IGmpf 696 current source: the one HDMI port. */
    public static final int SOURCE_HDMI1 = 1;
    /** IGmpf 664 / 665 aspect ratio; 3 (point to point) is a read-only value shown as Original. */
    static final int ASPECT_4_3 = 0, ASPECT_16_9 = 1, ASPECT_AUTO = 2, ASPECT_POINT = 3, ASPECT_ORIGINAL = 4;
    /** IGmpf 661 / 660 EDID version. */
    static final int EDID_14 = 1, EDID_20 = 2;
    /** IGmpf 172 / 173 put mode. */
    static final int PUT_FRONT_TABLE = 0, PUT_FRONT_CEILING = 1, PUT_REAR_TABLE = 2, PUT_REAR_CEILING = 3;
    /** game/GameMode modes (IGmpf2 33 read through GameMode.modeOf). */
    static final int GAME_OFF = 0, GAME_ON = 1, GAME_AUTO = 2;
    /** IGmpf 684 / 685: cool 0, standard 1, warm 2, custom 3. */
    static final int CT_CUSTOM = 3;

    // ------------------------------------------------------------------ token tables

    /** One option list of the profile: token i has wire value wires[i]. */
    static final class Table {
        final String[] tokens;
        final int[] wires;

        Table(String[] tokens, int[] wires) {
            if (tokens.length != wires.length) throw new IllegalArgumentException("table size");
            this.tokens = tokens;
            this.wires = wires;
        }

        /** The token of a wire value, or null (a value outside the stock list is never shown). */
        String token(Integer wire) {
            if (wire == null) return null;
            for (int i = 0; i < wires.length; i++) if (wires[i] == wire) return tokens[i];
            return null;
        }

        /** The wire value of a token, or null (not an option of this list). */
        Integer wire(String token) {
            if (token == null) return null;
            for (int i = 0; i < tokens.length; i++) if (tokens[i].equals(token)) return wires[i];
            return null;
        }
    }

    /** Picture modes in the stock dayao order (picture.mode, until the module labels movie / tv / custom: pending). */
    static final Table PICTURE_MODE = new Table(
            new String[]{"ai", "movie", "sport", "tv", "custom", "office", "performance"},
            new int[]{PM_AI, PM_MOVIE, PM_SPORT, PM_TV, PM_CUSTOM, PM_OFFICE, PM_PERFORMANCE});
    /** IGmpf 685 colour temperature (Custom only). */
    static final Table COLOR_TEMP = new Table(new String[]{"cool", "standard", "warm", "custom"}, new int[]{0, 1, 2, 3});
    /** IGmpf 669 motion compensation. */
    static final Table MEMC = new Table(new String[]{"off", "low", "medium", "high"}, new int[]{0, 1, 2, 3});
    /** IGmpf 635 HDMI colour range. */
    static final Table HDMI_RANGE = new Table(new String[]{"auto", "full", "limited"}, new int[]{0, 1, 2});
    /** IGmpf 660 HDMI mode: compatible = HDMI 1.4, enhanced = HDMI 2.0. */
    static final Table EDID = new Table(new String[]{"compat", "enhanced"}, new int[]{EDID_14, EDID_20});
    /** IGmpf 665 aspect ratio in the stock order (image_ratio_auto, _4_3, _16_9, _none). */
    static final Table ASPECT = new Table(new String[]{"auto", "r4_3", "r16_9", "original"},
            new int[]{ASPECT_AUTO, ASPECT_4_3, ASPECT_16_9, ASPECT_ORIGINAL});
    /** IGmpf 61 sound modes (stock sound_effect order). */
    static final Table SOUND_MODE = new Table(new String[]{"ai", "music", "movie", "karaoke", "sports", "standard"},
            new int[]{3, 2, 1, 4, 12, 20});
    /** game/GameMode. */
    static final Table GAME_MODE = new Table(new String[]{"off", "on", "auto"}, new int[]{GAME_OFF, GAME_ON, GAME_AUTO});
    /** Home screen choice (LauncherSwitcher): 0 Lumen, 1 classic. */
    static final Table LAUNCHER = new Table(new String[]{"lumen", "classic"}, new int[]{0, 1});
    /** sound.output tokens -> audio/AudioOutputKind names (no wired headphones on the Play 6). */
    private static final String[][] OUTPUTS = {{"auto", "AUTO"}, {"speaker", "SPEAKER"}, {"arc", "ARC"}, {"bt", "BT"},
            {"usb", "USB"}};

    static String outputToken(String kind) {
        for (String[] o : OUTPUTS) if (o[1].equals(kind)) return o[0];
        return null;
    }

    static String outputKind(String token) {
        for (String[] o : OUTPUTS) if (o[0].equals(token)) return o[1];
        return null;
    }

    // ------------------------------------------------------------------ the stock mode rules

    /** The picture mode the menu shows for a 666 read: the AI scene sub-modes (10..17) as AI, Game (4) as nothing. */
    static Integer shownPictureMode(Integer raw) {
        if (raw == null) return null;
        return raw >= AI_SCENE_FIRST && raw <= AI_SCENE_LAST ? Integer.valueOf(PM_AI) : raw;
    }

    /** 666 reads 4: the MStar Game picture mode that game mode sets (picture mode then locked). */
    static boolean gamePicture(Integer raw) { return raw != null && raw == PM_GAME; }

    /** Custom picture mode: the picture items, colour temperature, dynamic contrast, picture reset exist only here. */
    static boolean custom(Integer raw) { return raw != null && raw == PM_CUSTOM; }

    /** Motion compensation and local contrast: Movie, TV and Custom (stock dayao tree). */
    static boolean memcMode(Integer raw) {
        Integer pm = shownPictureMode(raw);
        return pm != null && (pm == PM_MOVIE || pm == PM_TV || pm == PM_CUSTOM);
    }

    /** HDR: AI, Movie, Sport, TV, Custom and Office (not Performance, not Game). */
    static boolean hdrMode(Integer raw) {
        Integer pm = shownPictureMode(raw);
        return pm != null && (pm == PM_AI || pm == PM_MOVIE || pm == PM_SPORT || pm == PM_TV || pm == PM_CUSTOM
                || pm == PM_OFFICE);
    }

    /** "Original" changes the picture only for a signal smaller than the 1920 x 1080 panel (vendor needs >= 600 x 448). */
    static boolean originalFits(int[] hdmiInfo) {
        if (hdmiInfo == null || hdmiInfo.length < 2) return false;
        int w = hdmiInfo[0], h = hdmiInfo[1];
        return w >= 600 && h >= 448 && w < 1920 && h < 1080;
    }

    /** The aspect token of a 664 read: point-to-point as Original, Original as Auto where it does not fit, above 4 as Auto. */
    static String aspectToken(Integer v, boolean originalFits) {
        if (v == null) return null;
        int shown = v == ASPECT_POINT ? ASPECT_ORIGINAL : v;
        if (shown == ASPECT_ORIGINAL && !originalFits) shown = ASPECT_AUTO;
        String t = ASPECT.token(shown);
        return t != null ? t : "auto";
    }

    /** Projection: Auto while auto flip is on, else table (0 / 2) or ceiling (1 / 3). */
    static String projectionToken(Integer putMode, Boolean autoReverse) {
        if (putMode == null || autoReverse == null) return null;
        if (autoReverse) return "auto";
        if (putMode == PUT_FRONT_TABLE || putMode == PUT_REAR_TABLE) return "table";
        if (putMode == PUT_FRONT_CEILING || putMode == PUT_REAR_CEILING) return "ceiling";
        return null;
    }

    /** Table / ceiling keep the rear side of the current put mode (stock setProjectorPutModeV5). -1 = not a put mode. */
    static int putModeFor(String token, int cur) {
        boolean front = cur == PUT_FRONT_TABLE || cur == PUT_FRONT_CEILING;
        if ("table".equals(token)) return front ? PUT_FRONT_TABLE : PUT_REAR_TABLE;
        if ("ceiling".equals(token)) return front ? PUT_FRONT_CEILING : PUT_REAR_CEILING;
        return -1;
    }

    /** Rear switch: 0 / 2 -> 2 on, 0 off; 1 / 3 -> 3 on, 1 off (stock setRearProjection). */
    static int putModeForRear(boolean on, int cur) {
        boolean table = cur == PUT_FRONT_TABLE || cur == PUT_REAR_TABLE;
        return on ? (table ? PUT_REAR_TABLE : PUT_REAR_CEILING) : (table ? PUT_FRONT_TABLE : PUT_FRONT_CEILING);
    }

    /** Light level 1..10 (stock brightness_adjustment_progress). */
    static boolean lampValue(int v) { return v >= 1 && v <= 10; }

    // ------------------------------------------------------------------ presence

    /** Ids still waiting for the module (menu.json pending); the adapter already handles them. */

    private static final FeatureId[] ALWAYS = {
            FeatureId.KEYSTONE_AK_NOW, FeatureId.KEYSTONE_MANUAL, FeatureId.KEYSTONE_ROTATION, FeatureId.KEYSTONE_REALTIME,
            FeatureId.KEYSTONE_ON_POWER, FeatureId.KEYSTONE_RESTORE, FeatureId.ZOOM_DIGITAL,
            FeatureId.FOCUS_AF_NOW, FeatureId.FOCUS_MANUAL, FeatureId.FOCUS_SMART,
            FeatureId.LAMP, FeatureId.LAMP_ECO, FeatureId.LAMP_OFF,
            FeatureId.PICTURE_MODE, FeatureId.PICTURE_PQ, FeatureId.PICTURE_COLOR_TEMP, FeatureId.PICTURE_MEMC,
            FeatureId.PICTURE_DYNAMIC_CONTRAST, FeatureId.PICTURE_PRO, FeatureId.PICTURE_RESET,
            FeatureId.HDMI_RANGE, FeatureId.HDMI_EDID, FeatureId.PICTURE_ASPECT,
            FeatureId.SOUND_MODE, FeatureId.SOUND_EQ, FeatureId.SOUND_EARC, FeatureId.SOUND_OUTPUT,
            FeatureId.INPUT, FeatureId.BLUETOOTH, FeatureId.NETWORK, FeatureId.MIRRORING, FeatureId.USB_FILES,
            FeatureId.GAME_MODE, FeatureId.PROJECTION_MODE, FeatureId.PROJECTION_REAR,
            FeatureId.SETTINGS_SYSTEM, FeatureId.SETTINGS_PROJECTOR, FeatureId.SETTINGS_RECENTS, FeatureId.SETTINGS_LAUNCHER,
            FeatureId.SETTINGS_AMBIENT_SPEAKER,
    };

    /**
     * What the Play 6 adapter implements: every row of the old Play 6 panel (no optical zoom: the Play 6 zoom is the
     * digital zoom and shift of zoom.digital) and the screen mirroring tile (the menu builds it only where the receiver
     * found Wi-Fi Direct on this unit). What this unit has is {@link #available}.
     */
    static Set<FeatureId> implemented() {
        LinkedHashSet<FeatureId> s = new LinkedHashSet<>();
        Collections.addAll(s, ALWAYS);
        s.add(FeatureId.EYE);
        return Collections.unmodifiableSet(s);
    }

    /**
     * What this unit has, asked when the menu is built (false: no row, no tile): eye protection only while the
     * image's NUI fifo reader is up (Device.hasEyeProtection: IGmpf 152 is sent only with it; Play6Menu builds the
     * menu again when that changes), the home screen choice only where LauncherSwitcher can switch, power saving on
     * the battery model only (stock eco_mode_switch).
     */
    static boolean available(FeatureId feature, boolean eyeReady, boolean launcherChoice, boolean battery) {
        switch (feature) {
            case EYE: return eyeReady;
            case SETTINGS_LAUNCHER: return launcherChoice;
            case LAMP_ECO: return battery;
            default: return true;
        }
    }

    // ------------------------------------------------------------------ the state

    /** One read of the Play 6 values (Play6Snapshot fields, copied by the adapter; null = not read / failed). */
    static final class Read {
        Integer source, pictureMode, lamp, soundEffect, putMode, memc, colorTemp, aspect, hdmiRange, edid;
        Boolean eye, eco, powerOnAk, moveAk, autoReverse, smartAf, dynContrast, earc;
        final Integer[] pq = new Integer[4];
        int[] hdmiInfo;
        /** IGmpf2 identity guard open at the read. */
        boolean gmpf2;
        /** game/GameMode.modeOf of IGmpf2 33, -1 = not read. */
        int gameMode = -1;
        /** AudioOutputChoice.kind name, or null. */
        String outputKind;
        /** LauncherSwitcher token (lumen / classic), null when the choice is not available. */
        String launcher;
        /** audio/P6SoundEq can work now (the Play 6 speaker, DTS off); null = not read. */
        Boolean eqOk;
        /** AudioOutputChoice kind names of the outputs connected now (AudioOutputChoice.options), null = not read. */
        java.util.List<String> outputKinds;
        /** Kind name -> the row text stock shows (the device name of a Bluetooth / USB output, ARC or eARC). */
        final java.util.Map<String, String> outputLabels = new java.util.HashMap<>();
    }

    /** The sound.output tokens of the outputs connected now, in the profile order (stock: only those are offered). */
    static java.util.List<String> outputTokens(java.util.List<String> kinds) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (kinds == null) return null;
        for (String[] o : OUTPUTS) if (kinds.contains(o[1])) out.add(o[0]);
        return out;
    }

    /** HDMI is the current source (696 == 1): the HDMI rows and game mode belong to it. */
    static boolean hdmi(Read r) { return r.source != null && r.source == SOURCE_HDMI1; }

    /** The Custom picture items (Play6Snapshot.pq order: IGmpf 692 / 694 / 690 / 688). */
    static final String[] PQ_ITEMS = {"brightness", "contrast", "saturation", "sharpness"};

    /** Rows that carry a value: shown only with one (a row whose getter failed is hidden, never empty). */
    static final FeatureId[] VALUE_ROWS = {FeatureId.EYE, FeatureId.KEYSTONE_REALTIME, FeatureId.KEYSTONE_ON_POWER,
            FeatureId.FOCUS_SMART, FeatureId.LAMP, FeatureId.LAMP_ECO, FeatureId.PICTURE_MODE, FeatureId.PICTURE_COLOR_TEMP,
            FeatureId.PICTURE_MEMC, FeatureId.PICTURE_DYNAMIC_CONTRAST, FeatureId.HDMI_RANGE, FeatureId.HDMI_EDID,
            FeatureId.PICTURE_ASPECT, FeatureId.SOUND_MODE, FeatureId.SOUND_EARC, FeatureId.SOUND_OUTPUT, FeatureId.GAME_MODE,
            FeatureId.PROJECTION_MODE, FeatureId.PROJECTION_REAR, FeatureId.SETTINGS_LAUNCHER};

    /**
     * The menu state of one read: the values the current mode and input have, every other row hidden (rule).
     * {@code text} turns a string resource name into its text (the adapter's context; tests pass the name back).
     */
    static MenuState state(Read r, java.util.function.Function<String, String> text) {
        MenuState.Builder b = MenuState.builder();
        if (r == null) return b.build();                       // nothing read: the menu says "not ready"
        b.on(FeatureId.EYE, r.eye);
        b.on(FeatureId.KEYSTONE_REALTIME, r.moveAk);
        b.on(FeatureId.KEYSTONE_ON_POWER, r.powerOnAk);
        if (r.gmpf2) b.on(FeatureId.FOCUS_SMART, r.smartAf);
        if (r.lamp != null && lampValue(r.lamp)) b.number(FeatureId.LAMP, r.lamp);
        b.on(FeatureId.LAMP_ECO, r.eco);
        // picture: Game (4) shows no mode (locked, the stock hint on top of the page); AI scenes as AI
        if (gamePicture(r.pictureMode)) b.note(MenuSpec.PICTURE, text.apply("p6_pic_game_hint"));
        else b.put(FeatureId.PICTURE_MODE, PICTURE_MODE.token(shownPictureMode(r.pictureMode)));
        boolean custom = custom(r.pictureMode);
        for (int i = 0; i < PQ_ITEMS.length; i++) {
            Integer v = r.pq[i];
            FeatureId f = FeatureId.pq(PQ_ITEMS[i]);
            if (custom && v != null && v >= 0 && v <= 100) b.number(f, v);
            else b.hide(f);
        }
        if (custom) {
            b.put(FeatureId.PICTURE_COLOR_TEMP, COLOR_TEMP.token(r.colorTemp));
            if (r.gmpf2) b.on(FeatureId.PICTURE_DYNAMIC_CONTRAST, r.dynContrast);
        } else {
            b.hide(FeatureId.PICTURE_RESET);
        }
        if (memcMode(r.pictureMode)) b.put(FeatureId.PICTURE_MEMC, MEMC.token(r.memc));
        // HDMI rows: only while the HDMI input is the current source
        if (hdmi(r)) {
            b.put(FeatureId.HDMI_RANGE, HDMI_RANGE.token(r.hdmiRange));
            b.put(FeatureId.HDMI_EDID, EDID.token(r.edid));
            b.put(FeatureId.PICTURE_ASPECT, aspectToken(r.aspect, originalFits(r.hdmiInfo)));
            // Original only for a signal smaller than the panel (it changes nothing otherwise, stock image_ratio_none)
            if (!originalFits(r.hdmiInfo)) b.options(FeatureId.PICTURE_ASPECT, java.util.Arrays.asList("auto", "r4_3", "r16_9"));
            if (r.gmpf2 && r.gameMode >= 0) b.put(FeatureId.GAME_MODE, GAME_MODE.token(r.gameMode));
        }
        b.put(FeatureId.SOUND_MODE, SOUND_MODE.token(r.soundEffect));
        b.on(FeatureId.SOUND_EARC, r.earc);
        // sound output: Auto, Speaker and the outputs connected now; a stored output that is gone shows as Auto (stock)
        String out = outputToken(r.outputKind);
        java.util.List<String> outs = outputTokens(r.outputKinds);
        if (outs != null) {
            if (out != null && !outs.contains(out)) out = "auto";
            b.options(FeatureId.SOUND_OUTPUT, outs);
            for (String[] o : OUTPUTS) {
                String l = o[0].equals("arc") || o[0].equals("bt") || o[0].equals("usb") ? r.outputLabels.get(o[1]) : null;
                if (outs.contains(o[0])) b.label(FeatureId.SOUND_OUTPUT, o[0], l);
            }
        }
        b.put(FeatureId.SOUND_OUTPUT, out);
        // the Lumen equalizer on the Play 6 speaker: no entry while it cannot work (DTS on, another output)
        if (Boolean.FALSE.equals(r.eqOk)) b.hide(FeatureId.SOUND_EQ);
        b.put(FeatureId.PROJECTION_MODE, projectionToken(r.putMode, r.autoReverse));
        if (r.putMode != null && r.putMode >= PUT_FRONT_TABLE && r.putMode <= PUT_REAR_CEILING) {
            b.on(FeatureId.PROJECTION_REAR, r.putMode == PUT_REAR_TABLE || r.putMode == PUT_REAR_CEILING);
        }
        b.put(FeatureId.SETTINGS_LAUNCHER, r.launcher);
        // every value row without a value now: hidden (another mode or input, or its getter failed)
        MenuState probe = b.build();
        for (FeatureId f : VALUE_ROWS) if (!probe.has(f)) b.hide(f);
        return b.build();
    }

    /** The stock refusal of the game page: the game mode lives on the HDMI input only. Null = allowed. */
    static Note gamePage(Read r) {
        return r == null || hdmi(r) ? null : Note.of("panel_game_hdmi_only");
    }

    /**
     * The stock refusal of a press before anything moves (the module's blocked hook), from the last read: the game
     * page off HDMI (stock game mode lives on the HDMI input only), the picture mode in Game (locked, the stock hint),
     * a write the read already refuses with a note. Null = allowed (the write still checks on p6-hal).
     */
    static Note blocked(FeatureId feature, String value, Read r) {
        if (r == null) return null;                            // not read yet: the menu's not ready path
        if (value == null) return null;                        // the picture mode in Game: refusal's stock hint below
        String no = refusal(feature, r);
        return no == null || no.isEmpty() ? null : Note.of(no);
    }

    /**
     * The stock questions (the module's confirm hook; title, message, yes button as string names), from the last read:
     * the Performance heat warning, a projection change that flips the picture, the HDMI mode (the source reconnects),
     * keystone restore, picture reset, the home screen switch, power saving the first time each way. Null = no question.
     */
    static Confirm confirm(FeatureId feature, String value, Read r, boolean ecoOnAsked, boolean ecoOffAsked) {
        switch (feature) {
            case KEYSTONE_RESTORE:
                return new Confirm("panel_reset_keystone", "panel_reset_keystone_msg", "panel_reset");
            case PICTURE_RESET:
                return new Confirm("panel_picture_reset", "p6_pic_reset_msg", "panel_reset");
            default:
                break;
        }
        if (r == null || value == null) return null;
        switch (feature) {
            case PICTURE_MODE: {
                Integer m = PICTURE_MODE.wire(value);
                boolean change = m != null && !m.equals(shownPictureMode(r.pictureMode));
                return change && m == PM_PERFORMANCE
                        ? new Confirm("panel_pm_performance", "panel_pm_performance_msg", "panel_switch") : null;
            }
            case PROJECTION_MODE: {
                if (r.putMode == null || r.autoReverse == null) return null;
                boolean flip = "auto".equals(value) ? !r.autoReverse : putModeFor(value, r.putMode) >= 0
                        && putModeFor(value, r.putMode) != r.putMode;
                return flip ? new Confirm("panel_projection_mode", "panel_proj_confirm_flip", "panel_switch") : null;
            }
            case PROJECTION_REAR: {
                if (r.putMode == null || !("on".equals(value) || "off".equals(value))) return null;
                return putModeForRear("on".equals(value), r.putMode) != r.putMode
                        ? new Confirm("panel_projection_mode", "panel_proj_confirm_flip", "panel_switch") : null;
            }
            case HDMI_EDID: {
                Integer v = EDID.wire(value);
                return v != null && !v.equals(r.edid) ? new Confirm("hdmi_mode", "hdmi_mode_note", "panel_switch") : null;
            }
            case SETTINGS_LAUNCHER:
                if (value.equals(r.launcher)) return null;
                if ("classic".equals(value)) return new Confirm("launcher_row", "launcher_confirm_classic", "launcher_switch");
                if ("lumen".equals(value)) return new Confirm("launcher_row", "launcher_confirm_lumen", "launcher_switch");
                return null;
            case LAMP_ECO: {
                boolean on = "on".equals(value);
                if (on == Boolean.TRUE.equals(r.eco) || (on ? ecoOnAsked : ecoOffAsked)) return null;
                return on ? new Confirm("p6_eco_on_title", "p6_eco_on_msg", "p6_eco_turn_on")
                        : new Confirm("p6_eco_off_title", "p6_eco_off_msg", "p6_eco_turn_off");
            }
            default:
                return null;
        }
    }

    /**
     * A write the current mode or input refuses before any call: null = may go to the device, "" = refused without
     * a note, else the string resource name of the note. {@code r} is the read right before the write.
     */
    static String refusal(FeatureId feature, Read r) {
        if (feature.parent == FeatureId.PICTURE_PQ || FeatureId.PICTURE_COLOR_TEMP == feature
                || FeatureId.PICTURE_RESET == feature) {
            return custom(r.pictureMode) ? null : "";
        }
        switch (feature) {
            case PICTURE_MODE:
                if (gamePicture(r.pictureMode)) return "p6_pic_game_hint";
                // a mode outside the stock list (or none read) is not shown, so it is not changed either
                return PICTURE_MODE.token(shownPictureMode(r.pictureMode)) == null ? "" : null;
            case PICTURE_DYNAMIC_CONTRAST:
                return custom(r.pictureMode) && r.gmpf2 ? null : "";
            case PICTURE_MEMC:
                return memcMode(r.pictureMode) ? null : "";
            case HDMI_RANGE:
            case HDMI_EDID:
            case PICTURE_ASPECT:
                return hdmi(r) ? null : "panel_game_hdmi_only";
            case GAME_MODE:
                if (!hdmi(r)) return "panel_game_hdmi_only";
                return r.gmpf2 && GAME_MODE.token(r.gameMode) != null ? null : "toast_not_ready";
            case FOCUS_SMART:
                return r.gmpf2 ? null : "toast_not_ready";
            case PROJECTION_MODE:
            case PROJECTION_REAR:
                return r.putMode == null || r.autoReverse == null ? "toast_not_ready" : null;
            default:
                return null;
        }
    }
}
