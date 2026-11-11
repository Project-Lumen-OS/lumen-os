/*
 * HbFeatures: the compile-time feature table of the Z9X HB.
 * HB-only file, no HAL access.
 *
 * Sources: the stock switches /mnt/vendor/xgimiconfig/<model>/feature/feature.xml (the model code of this unit,
 * ro.boot.xgimi.modelname: one stock build serves G0118, G0130 and G0148, each with its own folder), the stock
 * settings tree settings_tree_product_dapengse.json, devices/goat_elegant/stock analysis, and the S0 values in
 * stock analysis States:
 *   ON       present and proven: rows built.
 *   OFF      absent on the HB: rows never built, the HAL methods send nothing (NotOnHbException).
 *   PENDING  present on the HB but a value or a struct is not proven: rows not built until the check named in the
 *            table passes; then one line here changes to ON (a reviewed fork change).
 * Lumen rule: never show a disabled row; fail toward hiding.
 *
 * At start init() reads the feature.xml of this unit's model once when it is readable and logs every key that
 * disagrees with the table; a key that says "off" where the table says ON or PENDING turns that feature OFF for the
 * process. On top of the file, MODEL_SENSITIVE lists the features whose stock rows differ between the HB model codes
 * (gsi-stock analysis): the menu was ported from the G0130 (dapengse) tree only, so
 * G0148 (dapengfhm: support_brightness_boost=0, support_custom_picture_mode=false, colour temperature list 16 / 17
 * instead of 0 / 16) hides them until its own menu is ported, and so does any model code this table does not know
 * or a feature.xml that cannot be read (fail toward hiding). Everything else keeps the table.
 */
package org.lumen.projector.hal;

import android.os.SystemProperties;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HbFeatures {
    private static final String TAG = "Z9xFeatures";
    /** The unit's model code (G0130, G0148, ...), the name of its stock config folder. */
    static final String MODEL_PROP = "ro.boot.xgimi.modelname";
    static final String CONFIG_ROOT = "/mnt/vendor/xgimiconfig/";

    public enum State { ON, OFF, PENDING }

    /**
     * One feature: its state and, where the stock switches name it, the feature.xml key and the values that mean
     * "off" for that key (each key has its own meaning, e.g. support_screen_zoom 0 = supported).
     */
    public enum F {
        /** 3D modes. support_3D=0. */
        THREE_D(State.OFF, "support_3D", "0"),
        /** Dolby Vision picture modes: no DV in the tree (already parked on the Z9X). */
        DOLBY_VISION(State.OFF, null),
        /** Office picture mode: not in image_mode_4_state. */
        OFFICE_MODE(State.OFF, null),
        /** AI contrast: not in the HB tree. */
        AI_CONTRAST(State.OFF, null),
        /** Super resolution: not in the HB tree. */
        SUPER_RESOLUTION(State.OFF, null),
        /** Original frame rate: not in the HB tree. */
        NATIVE_FRAME_RATE(State.OFF, null),
        /** Ultra 120 Hz (Perfect 120): not in the HB tree. */
        ULTRA_120(State.OFF, null),
        /** Volume levelling: not in the HB tree. */
        VOLUME_BALANCE(State.OFF, null),
        /** Harman / DTS Virtual:X / DTS effect. support_dts_effect=false, support_dts_vx_effect=false. */
        SOUND_ENHANCEMENT(State.OFF, "support_dts_effect", "false"),
        /** Real-time keystone: the Z9X keystone_non_inductive_real_time is not in the HB tree. */
        REALTIME_KEYSTONE(State.OFF, null),
        /** Auto keystone after a move (MOTION 6 / 7): displacement_autoKeystone_v5; stock ResetAkUtils, KeyStoneManager. */
        MOVE_AK(State.ON, "support_kst_adjust_type", "0", "1", "3"),
        /** One-press auto keystone (FOCUS 27 autoKst): support_kst_adjust_type=2. */
        AUTO_KEYSTONE(State.ON, "support_kst_adjust_type", "0", "1", "3"),
        /**
         * Obstacle avoidance (FOCUS 33 / 34). G0130 feature.xml support_Adaptived_AK=5: stock SystemUI FeatureUtils
         * reads a value of "4" or more as the AK4 family (supportAk4), stock newsettings refuses the obstacle switch
         * only for "2" (KeyStoneRepository.setAutoAdjustAreaAdp, curtain only), and the dapengse settings tree shows
         * projection_area_self_adapt with no feature condition. ON with fit to screen (9.1, 2026-10-29):
         * fit ON also turns this ON, as the stock setAutoAdjustScreenAdp does.
         */
        OBSTACLE_AVOID(State.ON, "support_Adaptived_AK", "0"),
        /**
         * Fit to screen (curtain, FOCUS 35 / 36) and "Fit to screen now" (FOCUS 53 newAutoKst(11), approved 9.1 on
         * 2026-10-29). Stock G0130: the dapengse tree shows curtain_projection_self_adapt with no feature
         * condition, and SystemUI CurtainAdaptManager.autoKeystone sends newAutoKst(11).
         */
        CURTAIN_FIT(State.ON, "support_Adaptived_AK", "0"),
        /**
         * Manual keystone (16 read / 64 check / 15 apply). ON since hb-t8c (2026-10-30): the 5.2 rule 4 read check
         * passed on the device (81 points, r * 9 + c, corners TL [0][0] TR [0][1] BR [1][1] BL [1][0] inside the
         * panel, vendor full frame 0,0 / 3839,2159) and one corner moved one step and back with 64 + 15 (HW warp,
         * no setKstPrepare). 9.2 (158) and 9.3 type 0 stay refused: the panel sends neither (device tests).
         */
        MANUAL_KEYSTONE(State.ON, null),
        /** Digital zoom and shift: support_screen_zoom=0 (0 = supported); 9.2 / 9.3 and a device check. */
        ZOOM_SHIFT(State.PENDING, "support_screen_zoom", "1"),
        /** Picture rotation: picture_screen_rotation; struct and sign check on the device, 9.2. */
        ROTATION(State.PENDING, null),
        /**
         * Optical zoom: support_optical_zoom=true, zoom.ini ZoomControlEn=1. hb-t5 (the device test of hb-t4, 2026-10-29: no
         * zoom row anywhere): ON with DISPLAY 185 setKstPrepare(enter, 2) (9.3, zoom case), 93 / 94, 97 / 98, 95 (+-1
         * from the last 98, inside the last 97 range) and 63 zoomControl(5, 10) only; stock newsettings ZoomStateView.
         */
        OPTICAL_ZOOM(State.ON, "support_optical_zoom", "false"),
        /**
         * Keystone reset (hb-t5: the only way back after "Fit to screen"): the stock HB V3DisplayManager.setIChipReset
         * sequence, DISPLAY 38(0), 84, 85(0, 0) only when 84 returned 6 (9.7), plus 63(5, 10) with OPTICAL_ZOOM.
         */
        KEYSTONE_RESET(State.ON, null),
        /** Manual focus: S0 (d) shows the stock HB sends 17 / 0 / 1 / 2 as the Z9X (15 is the Hall calibration). */
        MANUAL_FOCUS(State.ON, null),
        /** Picture modes, MEMC, basic picture, gamma, WB 2 / 11 point, HSY, gamut, dynamic contrast / black, local
         *  contrast, noise reduction, HDR tone mapping, reset: tree image_*. */
        PICTURE(State.ON, null),
        /**
         * Colour temperature: image_mode_4_color_temp_dapengse, lumens mode 0 Standard / 16 Movie (picture.json map;
         * BrightnessViewModel.java:1655-1660, BrightnessRepository.choiceLedMode). AREA picture 2026-10-30.
         */
        COLOR_TEMP(State.ON, null),
        /** Brightness levels and Boost: support_brightness_boost=2 (G0130). G0148 has 0: no Boost on its stock. */
        BOOST(State.ON, "support_brightness_boost", "0"),
        /**
         * User picture modes (stock image_mode_custom_base_*, CustomizeDataManager): not built in Lumen
         * (stock analysis, NEW). G0148 stock has support_custom_picture_mode=false, so it stays hidden there
         * even after a port (MODEL_SENSITIVE).
         */
        CUSTOM_PICTURE_MODE(State.PENDING, "support_custom_picture_mode", "false"),
        /** Sound modes: HB SoundEnum values equal the Z9X ones (S0). */
        SOUND_MODES(State.ON, null),
        /**
         * The 3.5 mm headphone / line output as a sound output row (2026-11-10, 4PDA: HDMI sound never reached a soundbar
         * or headphones on the jack). Stock GimiAudioService.getSupportWiredHeadphone: listed unless the feature
         * support_wired_headphone is "false" (absent in the G0130 file); the HB kernel reports no plug, so the row is
         * always there and choosing it connects the wired output (audio/HbWiredJack).
         */
        WIRED_HEADPHONE(State.ON, "support_wired_headphone", "false"),
        /** SoC PEQ equalizer: the HB audio path (Atmos, DTS DRC) is not traced; hidden until a device check. */
        EQUALIZER(State.PENDING, null),
        /** eARC, HDMI range, EDID: voice_earc_*, signal_*. */
        HDMI(State.ON, null),
        /**
         * Eye protection (stock "eye radiation protection": newsettings MoreRepository.setHumanDetectOnOff -> DISPLAY 61;
         * focusEvent 500 / 601 -> SystemUI HumanDetectManager -> com.xgimi.config EyeProjectionService: the mask, BACK =
         * 66(true), gear = 66(true) + 61(false); the vendor dims the light itself, 66(false) is only in a debug activity).
         * The same flow as the Z9X EyeGuard; the image runs the NUI fifo reader since hb-t6 (z9x_nui.rc,
         * sys.z9x.nui=ready), and EyeGuard still sends nothing while it is not ready. The vendor watchdog (99 / 100
         * uiHeartBeatSet("NativeUIWatch", 1|0, 1500) and uiHeartBeatDo every 1 s) stays refused (9.4, owner).
         * ON in the public 1.0.8 (2026-11-05: "Защита глаз должна быть в релизе"), not yet tested live on the HB.
         */
        EYE_PROTECTION(State.ON, "support_eye_protect_brightness_mode", "false"),
        /**
         * Game mode: display_gamemode_version=3, support_hfr=true. ON since 2026-11-04: the stock HB wires (newsettings
         * PictureViewModel, gmuiapi DisplayManager) are the Z9X ones; stock sends option 1 (standard) and 2 (top speed).
         */
        GAME_MODE(State.ON, "display_gamemode_version", "0"),
        /**
         * Game bar (stock analysis): gear long press on HDMI while the game mode runs (148 state 0), the stock
         * xhplayer GameMenuActivity with the Product_dapengse list. The items below are its rows; the bar is built
         * only with GAME_MODE ON.
         */
        GAME_BAR(State.ON, "display_gamemode_version", "0"),
        /** VRR (TV 145 / 146 per input), on only in top speed (support_vrr_only_at_top_speed=true) and 16:9. */
        GAME_VRR(State.ON, null),
        /** Game picture (TV 147 / 148): Standard, FPS, RPG, RTS, Sports. */
        GAME_PICTURE(State.ON, null),
        /** Dark enhance (TV 149 / 150, per game picture and input). */
        GAME_DARK_BOOST(State.ON, null),
        /** HDR settings (TV 174 / 175 tone mapping, 191 / 192 HGiG), only while HDR10 plays. hgig_mode=1. */
        GAME_HDR(State.ON, "hgig_mode", "0"),
        /** FPS overlay (DISPLAY 179 with VRR on, else the TV 38 frame rate). */
        GAME_FPS(State.ON, null),
        /** Screen shift (TV 41 / 151 / 171) in 21:9 and 32:9: the stDisplayArea layout is not read yet. */
        GAME_SCREEN_SHIFT(State.PENDING, null),
        /** Virtual crosshair (an overlay of our own, no HAL): not built yet. */
        GAME_CROSSHAIR(State.PENDING, null),
        /** AMD FreeSync Premium (TV 172 / 173): stock shows it only with support_amd_free_sync_premium=true; G0130 has no key. */
        GAME_FREESYNC(State.OFF, null),
        /** Menu theme: the bar's own vendor skins (Switch, PlayStation, Xbox, Mecha); nothing to copy. */
        GAME_MENU_THEME(State.OFF, null),
        /** Interface resolution row (Projector settings): the HB panel and UI sizes are not checked yet. */
        UI_RESOLUTION(State.PENDING, null),
        /**
         * Fit to my screen size: the quick panel row "Fit to screen size" and
         * the two reads it adds, FOCUS 46 getTofDistance and MOTION 2 getMotionDetection (allowed.tsv). PENDING until
         * design notes H1 passes on the device (both reads plausible: ToF against a tape, the
         * accelerometer axes and the sign of the tilt on a ceiling and a desk); then this line changes to ON.
         */
        FIT_SCREEN(State.PENDING, null);

        final State table;
        final String key;
        final String[] offValues;
        volatile boolean offByFile;
        volatile boolean offByModel;

        F(State table, String key, String... offValues) {
            this.table = table;
            this.key = key;
            this.offValues = offValues;
        }
    }

    /**
     * The features whose stock rows differ between the HB model codes and are ported from the G0130 tree only
     *: Boost (G0148 support_brightness_boost=0), user picture modes (G0148
     * support_custom_picture_mode=false) and the colour temperature list (dapengse 0 Standard / 16 Movie,
     * dapengfhm 16 Movie / 17 Balance: Lumen's 0 is not in the G0148 list, and the vendor stores it).
     */
    static final F[] MODEL_SENSITIVE = {F.BOOST, F.CUSTOM_PICTURE_MODE, F.COLOR_TEMP};

    /** Model codes whose stock tree the menu was checked against, and what each of them hides. */
    static F[] modelOff(String model) {
        if ("G0130".equals(model)) return new F[0];
        if ("G0148".equals(model)) return new F[] {F.BOOST, F.CUSTOM_PICTURE_MODE, F.COLOR_TEMP};
        return MODEL_SENSITIVE;                                     // unknown model: hide every model-dependent row
    }

    /** The stock feature.xml of a model code; null for anything that is not a model code (never a free path). */
    static String featureXml(String model) {
        if (model == null || !model.matches("G[0-9]{4}")) return null;
        return CONFIG_ROOT + model + "/feature/feature.xml";
    }

    private static volatile boolean sInit;
    private static volatile String sModel = "";

    private HbFeatures() {}

    /** The state for this process: the table, or OFF when feature.xml or the model rule says off. */
    public static State state(F f) {
        return f.offByFile || f.offByModel ? State.OFF : f.table;
    }

    /** The model code read at init() ("" before it or when the property is empty). */
    public static String model() {
        return sModel;
    }

    /** true only for ON (PENDING and OFF both hide the row). Any thread. */
    public static boolean on(F f) {
        return state(f) == State.ON;
    }

    /** NotOnHbException unless the feature is ON. Nothing is sent by the caller then. */
    static void require(F f, String what) throws AidlCaller.NotOnHbException {
        if (!on(f)) throw new AidlCaller.NotOnHbException(what + " (" + f + " is " + state(f) + ")");
    }

    /**
     * Reads the model's feature.xml once (App start, any thread). Never throws. An unreadable file keeps the table
     * but hides MODEL_SENSITIVE.
     */
    public static synchronized void init() {
        if (sInit) return;
        sInit = true;
        String model = "";
        try {
            model = SystemProperties.get(MODEL_PROP, "");
        } catch (Throwable t) {
            Log.w(TAG, MODEL_PROP + " not readable: " + t);
        }
        String path = featureXml(model);
        String xml = null;
        if (path != null) {
            try (InputStream in = new FileInputStream(path)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                xml = new String(out.toByteArray(), StandardCharsets.UTF_8);
            } catch (Throwable t) {
                Log.w(TAG, path + " not readable (" + t + ")");
            }
        }
        init(model, xml);
    }

    /** Package-private for the host test: the model rule, then one feature.xml text (null = no readable file). */
    static void init(String model, String xml) {
        sModel = model == null ? "" : model;
        for (F f : modelOff(model)) {
            f.offByModel = true;
            Log.w(TAG, f + ": not ported for model " + model + " (stock rows differ): OFF");
        }
        if (xml == null) {
            for (F f : MODEL_SENSITIVE) f.offByModel = true;
            Log.w(TAG, "no stock feature.xml for model '" + model + "': the table stands, "
                    + java.util.Arrays.toString(MODEL_SENSITIVE) + " OFF");
            return;
        }
        apply(xml);
    }

    /** Package-private for the host test: applies one feature.xml text. */
    static void apply(String xml) {
        Pattern p = Pattern.compile("<feature\\s+key=\"([^\"]+)\"\\s+value=\"([^\"]*)\"");
        for (F f : F.values()) {
            if (f.key == null) continue;
            Matcher m = p.matcher(xml);
            boolean seen = false;
            while (m.find()) {
                if (!f.key.equals(m.group(1))) continue;
                seen = true;
                String v = m.group(2).trim();
                boolean off = false;
                for (String o : f.offValues) if (o.equalsIgnoreCase(v)) off = true;
                if (off && f.table != State.OFF) {
                    f.offByFile = true;
                    Log.w(TAG, f + ": feature.xml " + f.key + "=" + v + " says off, table says " + f.table + ": OFF");
                } else if (!off && f.table == State.OFF) {
                    Log.i(TAG, f + ": feature.xml " + f.key + "=" + v + " does not say off, table keeps OFF");
                }
            }
            if (!seen) Log.i(TAG, f + ": feature.xml has no " + f.key);
        }
    }
}
