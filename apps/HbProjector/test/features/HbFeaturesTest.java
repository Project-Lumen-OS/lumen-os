package org.lumen.projector.hal;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Host test of HbFeatures (sh test/features/run.sh): the compile-time table against the stock G0130 feature.xml,
 * the G0148 file and model rule (Boost, user picture modes and the colour temperature list hidden), an unknown model
 * and a missing file (the model-dependent rows hidden, the rest kept), "off wins", require() for every state.
 * Plain Java, android.util.Log, android.os.SystemProperties and AidlCaller stubbed.
 */
public final class HbFeaturesTest {
    private static int failed;

    private static void check(boolean ok, String what) {
        if (!ok) {
            failed++;
            System.out.println("FAIL " + what);
        }
    }

    private static void reset() {
        for (HbFeatures.F f : HbFeatures.F.values()) {
            f.offByFile = false;
            f.offByModel = false;
        }
    }

    private static HbFeatures.State[] snapshot() {
        HbFeatures.F[] v = HbFeatures.F.values();
        HbFeatures.State[] s = new HbFeatures.State[v.length];
        for (int i = 0; i < v.length; i++) s[i] = HbFeatures.state(v[i]);
        return s;
    }

    public static void main(String[] a) throws Exception {
        String xml = new String(Files.readAllBytes(Paths.get(a[0])), StandardCharsets.UTF_8);
        // 1. the stock file changes nothing: every switch agrees with the table
        reset();
        HbFeatures.State[] before = snapshot();
        HbFeatures.init("G0130", xml);
        HbFeatures.State[] after = snapshot();
        for (int i = 0; i < before.length; i++) {
            check(before[i] == after[i], "stock feature.xml changed " + HbFeatures.F.values()[i] + " " + before[i] + " -> " + after[i]);
        }
        check(android.util.Log.warnings == 0, "stock feature.xml logged " + android.util.Log.warnings + " off switches");
        // 2. the states the image relies on
        check(HbFeatures.on(HbFeatures.F.AUTO_KEYSTONE), "AUTO_KEYSTONE ON");
        check(HbFeatures.on(HbFeatures.F.MOVE_AK), "MOVE_AK ON");
        check(HbFeatures.on(HbFeatures.F.MANUAL_FOCUS), "MANUAL_FOCUS ON");
        check(HbFeatures.on(HbFeatures.F.PICTURE), "PICTURE ON");
        check(HbFeatures.on(HbFeatures.F.BOOST), "BOOST ON");
        // AREA picture (2026-10-30): the stock colour temperature list 0 / 16 (image_mode_4_color_temp_dapengse)
        check(HbFeatures.on(HbFeatures.F.COLOR_TEMP), "COLOR_TEMP ON");
        // 9.1 (2026-10-29): fit to screen with newAutoKst(11), and obstacle avoidance it switches on
        check(HbFeatures.on(HbFeatures.F.CURTAIN_FIT), "CURTAIN_FIT ON");
        check(HbFeatures.on(HbFeatures.F.OBSTACLE_AVOID), "OBSTACLE_AVOID ON");
        // hb-t8c: manual keystone after the 16 read / 64 + 15 one-step check on the device
        check(HbFeatures.on(HbFeatures.F.MANUAL_KEYSTONE), "MANUAL_KEYSTONE ON");
        // 2026-11-10 (4PDA): the 3.5 mm output row, stock getSupportWiredHeadphone (key absent in the G0130 file)
        check(HbFeatures.on(HbFeatures.F.WIRED_HEADPHONE), "WIRED_HEADPHONE ON");
        check(!xml.contains("support_wired_headphone"), "G0130 feature.xml has no support_wired_headphone (stock default true)");
        // 2026-11-04: game mode with the stock HB options 1 / 2 (display_gamemode_version=3)
        check(HbFeatures.on(HbFeatures.F.GAME_MODE), "GAME_MODE ON");
        check(HbFeatures.on(HbFeatures.F.EYE_PROTECTION), "EYE_PROTECTION ON");
        for (HbFeatures.F f : new HbFeatures.F[] {HbFeatures.F.ZOOM_SHIFT, HbFeatures.F.ROTATION,
                HbFeatures.F.EQUALIZER, HbFeatures.F.UI_RESOLUTION, HbFeatures.F.FIT_SCREEN}) {
            check(HbFeatures.state(f) == HbFeatures.State.PENDING, f + " PENDING");
            check(!HbFeatures.on(f), f + " not on");
        }
        // hb-t5: optical zoom (9.3 zoom case) and the stock keystone reset (9.7)
        check(HbFeatures.on(HbFeatures.F.OPTICAL_ZOOM), "OPTICAL_ZOOM ON");
        check(HbFeatures.on(HbFeatures.F.KEYSTONE_RESET), "KEYSTONE_RESET ON");
        check(HbFeatures.state(HbFeatures.F.THREE_D) == HbFeatures.State.OFF, "THREE_D OFF");
        // 3. require(): only ON passes
        for (HbFeatures.F f : HbFeatures.F.values()) {
            boolean threw = false;
            try {
                HbFeatures.require(f, "test");
            } catch (AidlCaller.NotOnHbException e) {
                threw = true;
            }
            check(threw == !HbFeatures.on(f), "require(" + f + ") threw=" + threw);
        }
        // 4. off wins: a file that switches auto keystone off turns AUTO_KEYSTONE and MOVE_AK OFF, nothing turns ON
        reset();
        HbFeatures.apply(xml.replace("support_kst_adjust_type\" value=\"2\"", "support_kst_adjust_type\" value=\"0\"")
                .replace("support_optical_zoom\" value=\"true\"", "support_optical_zoom\" value=\"false\"")
                .replace("display_gamemode_version\" value=\"3\"", "display_gamemode_version\" value=\"0\""));
        check(HbFeatures.state(HbFeatures.F.AUTO_KEYSTONE) == HbFeatures.State.OFF, "AUTO_KEYSTONE off by file");
        check(HbFeatures.state(HbFeatures.F.MOVE_AK) == HbFeatures.State.OFF, "MOVE_AK off by file");
        check(HbFeatures.state(HbFeatures.F.GAME_MODE) == HbFeatures.State.OFF, "GAME_MODE off by file");
        check(HbFeatures.state(HbFeatures.F.OPTICAL_ZOOM) == HbFeatures.State.OFF, "OPTICAL_ZOOM off by file");
        // 4b. support_Adaptived_AK=0 (no adaptive AK) turns fit to screen and obstacle avoidance OFF
        reset();
        HbFeatures.apply(xml.replace("support_Adaptived_AK\" value=\"5\"", "support_Adaptived_AK\" value=\"0\""));
        check(HbFeatures.state(HbFeatures.F.CURTAIN_FIT) == HbFeatures.State.OFF, "CURTAIN_FIT off by file");
        check(HbFeatures.state(HbFeatures.F.OBSTACLE_AVOID) == HbFeatures.State.OFF, "OBSTACLE_AVOID off by file");
        check(HbFeatures.on(HbFeatures.F.AUTO_KEYSTONE), "AUTO_KEYSTONE untouched by support_Adaptived_AK");
        // 5. a file without the keys changes nothing
        reset();
        HbFeatures.apply("<features/>");
        HbFeatures.State[] empty = snapshot();
        for (int i = 0; i < before.length; i++) check(before[i] == empty[i], "empty file changed " + HbFeatures.F.values()[i]);
        // 6. the config path is the unit's own model folder, never a free path
        check("/mnt/vendor/xgimiconfig/G0130/feature/feature.xml".equals(HbFeatures.featureXml("G0130")), "G0130 path");
        check("/mnt/vendor/xgimiconfig/G0148/feature/feature.xml".equals(HbFeatures.featureXml("G0148")), "G0148 path");
        for (String bad : new String[] {"", null, "G0130/../G0148", "g0130", "G013", "G01300", "../x"}) {
            check(HbFeatures.featureXml(bad) == null, "no path for model " + bad);
        }
        // 7. G0148 (V6.17.12, dapengfhm): its own file says Boost 0; the model rule hides Boost, user picture modes and
        //    the colour temperature row (dapengfhm list 16 / 17); everything else is the G0130 menu
        String xml148 = new String(Files.readAllBytes(Paths.get(a[1])), StandardCharsets.UTF_8);
        reset();
        HbFeatures.init("G0148", xml148);
        check("G0148".equals(HbFeatures.model()), "model G0148");
        check(!HbFeatures.on(HbFeatures.F.BOOST), "G0148: BOOST hidden");
        check(HbFeatures.F.BOOST.offByFile, "G0148: BOOST off by its feature.xml (support_brightness_boost=0)");
        check(!HbFeatures.on(HbFeatures.F.CUSTOM_PICTURE_MODE), "G0148: CUSTOM_PICTURE_MODE hidden");
        check(!HbFeatures.on(HbFeatures.F.COLOR_TEMP), "G0148: COLOR_TEMP hidden");
        boolean threw = false;
        try { HbFeatures.require(HbFeatures.F.BOOST, "setBoost(true)"); } catch (AidlCaller.NotOnHbException e) { threw = true; }
        check(threw, "G0148: require(BOOST) throws, setBoost(true) sends nothing");
        HbFeatures.State[] g148 = snapshot();
        for (int i = 0; i < before.length; i++) {
            HbFeatures.F f = HbFeatures.F.values()[i];
            if (f == HbFeatures.F.BOOST || f == HbFeatures.F.CUSTOM_PICTURE_MODE || f == HbFeatures.F.COLOR_TEMP) continue;
            check(before[i] == g148[i], "G0148 changed " + f + " " + before[i] + " -> " + g148[i]);
        }
        // 7b. the model rule alone hides them, even with the G0130 file (a G0148 that reads a wrong folder)
        reset();
        HbFeatures.init("G0148", xml);
        check(!HbFeatures.on(HbFeatures.F.BOOST) && !HbFeatures.on(HbFeatures.F.COLOR_TEMP), "G0148 model rule without its file");
        // 7c. the G0148 file on a G0130 hides Boost (off wins), the model keeps colour temperature
        reset();
        HbFeatures.init("G0130", xml148);
        check(!HbFeatures.on(HbFeatures.F.BOOST), "G0130 with Boost 0 in its file: BOOST off");
        check(HbFeatures.on(HbFeatures.F.COLOR_TEMP), "G0130: COLOR_TEMP kept");
        // 8. an unknown model (G0118, empty) or no readable file: the model-dependent rows hidden, the rest kept
        for (String m : new String[] {"G0118", "", "G0130"}) {
            reset();
            HbFeatures.init(m, m.equals("G0130") ? null : xml);
            for (HbFeatures.F f : HbFeatures.MODEL_SENSITIVE) check(!HbFeatures.on(f), "model '" + m + "' (file "
                    + (m.equals("G0130") ? "missing" : "G0130") + "): " + f + " hidden");
            check(HbFeatures.on(HbFeatures.F.AUTO_KEYSTONE) && HbFeatures.on(HbFeatures.F.PICTURE)
                    && HbFeatures.on(HbFeatures.F.MANUAL_FOCUS), "model '" + m + "': the rest of the table kept");
        }
        // 9. 2026-11-10: support_wired_headphone=false (stock getSupportWiredHeadphone) hides the 3.5 mm row
        reset();
        HbFeatures.init("G0130", xml.replace("<feature key=\"support_brightness_boost\"",
                "<feature key=\"support_wired_headphone\" value=\"false\"/>\n        <feature key=\"support_brightness_boost\""));
        check(!HbFeatures.on(HbFeatures.F.WIRED_HEADPHONE), "support_wired_headphone=false: WIRED_HEADPHONE off");
        reset();
        if (failed > 0) {
            System.out.println("HbFeaturesTest: " + failed + " failed");
            System.exit(1);
        }
        System.out.println("HbFeaturesTest: ok (" + HbFeatures.F.values().length + " features)");
    }
}
