// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The stock picture settings page refusal (PictureSettingsRule, newsettings SettingMutexManager image_mode_setting,
 * image_mode_basic_setting, image_mode_prof_setting): java org.lumen.menu.PictureSettingsRuleTest &lt;gsi root&gt;.
 */
public final class PictureSettingsRuleTest {
    private static int pass, fail;

    private static void check(boolean ok, String what) {
        if (ok) pass++;
        else { fail++; System.out.println("FAIL " + what); }
    }

    private static void eq(Object want, Object got, String what) {
        check(want == null ? got == null : want.equals(got), what + ": want " + want + ", got " + got);
    }

    private static final PictureSettingsRule.Layout ONE = PictureSettingsRule.ONE_PAGE, Z9X = PictureSettingsRule.BASIC_AND_PRO;

    private static String why(Boolean game, Integer pm, Integer hdr) {
        return PictureSettingsRule.why(ONE, FeatureId.PQ_BRIGHTNESS, game, pm, hdr);
    }

    private static String note(PictureSettingsRule.Layout l, FeatureId f, Boolean game, Integer pm, Integer hdr) {
        ProjectorFeatures.Note n = PictureSettingsRule.blocked(l, f, game, pm, hdr);
        return n == null ? null : n.res;
    }

    public static void main(String[] a) throws Exception {
        final String G = PictureSettingsRule.NOTE_GAME, AI = PictureSettingsRule.NOTE_AI,
                PERF = PictureSettingsRule.NOTE_PERFORMANCE, AI_B = PictureSettingsRule.NOTE_AI_BASIC,
                PERF_B = PictureSettingsRule.NOTE_PERFORMANCE_BASIC;
        // the stock order: game, then AI (exactly 10), then Performance (5); AI / Performance not with Dolby Vision
        eq(null, why(false, 1, 0), "standard SDR opens");
        // TITAN testers 2026-11-19: in game mode only the rows that add delay stay closed (stock closes the whole page)
        eq(null, why(true, 1, 0), "game mode on: brightness opens");
        eq(AI, why(true, 10, 0), "game mode on in AI: the AI lock");
        eq(null, why(true, 5, 2), "game mode on in Performance with Dolby Vision: opens");
        FeatureId memc = FeatureId.PICTURE_MEMC;
        eq(G, PictureSettingsRule.why(ONE, memc, true, 1, 0), "game mode on: motion compensation refused");
        eq(G, PictureSettingsRule.why(ONE, memc, true, 10, 0), "game before AI for a row that adds delay");
        eq(G, PictureSettingsRule.why(ONE, memc, true, 5, 2), "game even with Dolby Vision for a row that adds delay");
        for (FeatureId f : FeatureId.values()) {
            boolean slow = f == memc || f == FeatureId.PICTURE_NOISE || f == FeatureId.PICTURE_SUPER_RES
                    || f == FeatureId.PICTURE_ULTRA120 || f.parent == memc;
            check(PictureSettingsRule.gameLatency(f) == slow, f + (slow ? " adds delay" : " adds no delay"));
        }
        eq(AI, why(false, 10, 0), "AI 10 refuses");
        eq(AI, why(null, 10, 0), "AI 10 with the game read missing");
        eq(AI, why(false, 10, null), "AI 10 with the content read missing");
        eq(AI, why(false, 10, 1), "AI 10 with HDR10");
        eq(null, why(false, 10, 2), "AI with Dolby Vision opens");
        for (int s = 11; s <= 17; s++) eq(null, why(false, s, 0), "raw " + s + " is not 10 (stock tests 10 only, the vendor reads 10 in every AI scene)");
        eq(PERF, why(false, 5, 0), "Performance refuses");
        eq(PERF, why(false, 5, 6), "Performance with HDR Vivid");
        eq(null, why(false, 5, 2), "Performance with Dolby Vision opens");
        for (int m : new int[]{1, 4, 9, 25, 26, 29}) eq(null, why(false, m, 0), "mode " + m + " opens");
        eq(null, why(null, null, null), "nothing read never refuses");
        eq(null, why(false, null, 0), "picture mode not read never refuses");

        // stock reads lazily: the game state first, the picture mode only with the game off, the content only in AI /
        // Performance; a row off the pages reads nothing
        List<String> log = new ArrayList<>();
        int[][] cases = {{1, 10, 0}, {0, 9, 0}, {0, 10, 0}, {0, 5, 2}};
        String[][] reads = {{"game"}, {"game", "mode"}, {"game", "mode", "hdr"}, {"game", "mode", "hdr"}};
        for (int i = 0; i < cases.length; i++) {
            int[] c = cases[i];
            log.clear();
            PictureSettingsRule.check(ONE, FeatureId.PICTURE_MEMC, () -> { log.add("game"); return c[0] == 1; },
                    () -> { log.add("mode"); return c[1]; }, () -> { log.add("hdr"); return c[2]; });
            eq(String.join(",", reads[i]), String.join(",", log), "reads of game " + c[0] + " mode " + c[1]);
        }
        log.clear();
        eq(null, PictureSettingsRule.check(ONE, FeatureId.LAMP, () -> { log.add("game"); return true; },
                () -> { log.add("mode"); return 10; }, () -> { log.add("hdr"); return 0; }), "a row off the page");
        check(log.isEmpty(), "a row off the page reads nothing: " + log);
        try {
            PictureSettingsRule.check(ONE, FeatureId.PICTURE_MEMC, () -> false, () -> { throw new Exception("34"); }, () -> 0);
            check(false, "a failed read reaches the adapter");
        } catch (Exception e) {
            eq("34", e.getMessage(), "a failed read reaches the adapter");
        }

        // one image_mode_setting page (HB and X50 family trees): every row with the professional texts
        for (FeatureId f : new FeatureId[]{FeatureId.PICTURE_PQ, FeatureId.PQ_BRIGHTNESS, FeatureId.PQ_CONTRAST,
                FeatureId.PQ_SATURATION, FeatureId.PQ_SHARPNESS, FeatureId.PICTURE_MEMC, FeatureId.PICTURE_COLOR_TEMP,
                FeatureId.PICTURE_PRO, FeatureId.PICTURE_RESET, FeatureId.PICTURE_WB2_RED_GAIN, FeatureId.PICTURE_GAMMA,
                FeatureId.PICTURE_NOISE, FeatureId.PICTURE_DYNAMIC_BLACK, FeatureId.PICTURE_AUTO_SWITCH,
                FeatureId.PICTURE_HDR_SCREEN_GAIN, FeatureId.PICTURE_CC_HUE}) {
            check(PictureSettingsRule.onPage(ONE, f), f + " is on the page");
            eq(AI, note(ONE, f, false, 10, 0), f + " refused in AI");
            eq(PERF, note(ONE, f, false, 5, 0), f + " refused in Performance");
            eq(null, note(ONE, f, false, 16, 0), f + " raw 16 is not 10 (never read: the vendor reads 10 in every AI scene)");
        }
        check(!PictureSettingsRule.onPage(ONE, FeatureId.PICTURE_ULTRA120), "Ultra 120 is not on image_mode_setting");

        // picture mode 3.0 (Z9X dapeng tree): the basic page and the professional page, each with its own texts
        FeatureId[] basic = {FeatureId.PICTURE_PQ, FeatureId.PQ_BRIGHTNESS, FeatureId.PQ_CONTRAST, FeatureId.PQ_SATURATION,
                FeatureId.PQ_SHARPNESS, FeatureId.PICTURE_COLOR_TEMP, FeatureId.PICTURE_MEMC, FeatureId.PICTURE_RESET,
                FeatureId.PICTURE_AUTO_SWITCH};
        FeatureId[] pro = {FeatureId.PICTURE_PRO, FeatureId.PICTURE_AI_CONTRAST, FeatureId.PICTURE_SUPER_RES,
                FeatureId.PICTURE_ULTRA120, FeatureId.PICTURE_WB2_RED_GAIN, FeatureId.PICTURE_GAMUT, FeatureId.PICTURE_GAMMA,
                FeatureId.PICTURE_NOISE, FeatureId.PICTURE_DYNAMIC_BLACK, FeatureId.PICTURE_CC_HUE};
        // the Z9X 1.0.10 PanelRules.basicOrPro cases (test/rules PanelRulesTest), the same decisions with the stock texts
        // (game mode on: the rows that add delay take the game text, the others decide as with the game off)
        Object[][] pins = {{false, 1, 0, null, null}, {true, 1, 0, G, G, null, null}, {true, 10, 0, G, G, AI_B, AI},
                {false, 10, 0, AI_B, AI}, {false, 16, 0, null, null}, {false, 10, 2, null, null}, {false, 5, 0, PERF_B, PERF},
                {false, 5, 2, null, null}, {null, 1, 0, null, null}, {false, null, 0, null, null}};
        for (Object[] p : pins) {
            String what = " game " + p[0] + " mode " + p[1] + " content " + p[2];
            for (FeatureId f : basic) eq(p.length > 5 && !PictureSettingsRule.gameLatency(f) ? p[5] : p[3],
                    note(Z9X, f, (Boolean) p[0], (Integer) p[1], (Integer) p[2]), f + what);
            for (FeatureId f : pro) eq(p.length > 5 && !PictureSettingsRule.gameLatency(f) ? p[6] : p[4],
                    note(Z9X, f, (Boolean) p[0], (Integer) p[1], (Integer) p[2]), f + what);
        }
        check(!PictureSettingsRule.onPage(Z9X, FeatureId.PICTURE_HDR), "the Z9X tree has no HDR enhancement page");

        for (PictureSettingsRule.Layout l : new PictureSettingsRule.Layout[]{ONE, Z9X}) {
            for (FeatureId f : new FeatureId[]{FeatureId.PICTURE_MODE, FeatureId.LAMP, FeatureId.BOOST,
                    FeatureId.PICTURE_RESET_ALL, FeatureId.PICTURE_ASPECT, FeatureId.PICTURE_3D, FeatureId.HDMI_EDID,
                    FeatureId.HDMI_RANGE, FeatureId.SOUND_MODE, FeatureId.GAME_MODE, FeatureId.PICTURE_ANTI_RBE,
                    FeatureId.PICTURE_STYLE_FILTER, FeatureId.PICTURE_DV_SCREEN, FeatureId.PICTURE_DV_SCREEN_SIZE}) {
                check(!PictureSettingsRule.onPage(l, f), f + " is not on a page");
                eq(null, note(l, f, true, 10, 0), f + " never refused by the rule");
            }
            check(!PictureSettingsRule.onPage(l, null), "null is not on a page");
        }
        aiParity(a.length > 0 ? new File(a[0]) : null, AI, AI_B);
        colorAccuracyParity(a.length > 0 ? new File(a[0]) : null, G);

        ProjectorFeatures.Note n = PictureSettingsRule.blocked(ONE, FeatureId.PQ_BRIGHTNESS, false, 5, 0);
        check(n != null && n.toast && n.title == null && PERF.equals(n.res), "the note is the stock plain toast, no row title");

        // a kept read (an adapter that checks the press before anything moves from its last state read): read as stock
        // reads, the game state first, the picture mode only with the game off, the content only in AI / Performance
        // (the picture mode is read with the game on too: the rows without delay open in game mode)
        String[][] kept = {{"game", "mode", "hdr"}, {"game", "mode"}, {"game", "mode", "hdr"}, {"game", "mode", "hdr"}};
        for (int i = 0; i < cases.length; i++) {
            int[] c = cases[i];
            log.clear();
            PictureSettingsRule.Reads r = PictureSettingsRule.Reads.read(() -> { log.add("game"); return c[0] == 1; },
                    () -> { log.add("mode"); return c[1]; }, () -> { log.add("hdr"); return c[2]; });
            eq(String.join(",", kept[i]), String.join(",", log), "kept reads of game " + c[0] + " mode " + c[1]);
            String want = PictureSettingsRule.why(ONE, FeatureId.PICTURE_COLOR_TEMP, c[0] == 1, c[1], c[2]);
            ProjectorFeatures.Note k = PictureSettingsRule.blocked(ONE, FeatureId.PICTURE_COLOR_TEMP, r);
            eq(want, k == null ? null : k.res, "kept reads decide as the fresh ones, game " + c[0] + " mode " + c[1]);
            check(k == null || k.toast, "a kept read refuses with the toast");
        }
        PictureSettingsRule.Reads ai = PictureSettingsRule.Reads.read(() -> false, () -> 10, () -> 0);
        eq(AI, PictureSettingsRule.blocked(ONE, FeatureId.PQ_SATURATION, ai).res, "kept AI read: a slider is locked");
        eq(AI_B, PictureSettingsRule.blocked(Z9X, FeatureId.PICTURE_COLOR_TEMP, ai).res, "kept AI read: the Z9X basic text");
        eq(null, PictureSettingsRule.blocked(ONE, FeatureId.PICTURE_MODE, ai), "kept AI read: the picture mode stays free");
        eq(null, PictureSettingsRule.blocked(ONE, FeatureId.PICTURE_RESET_ALL, ai), "kept AI read: reset all stays free");
        eq(null, PictureSettingsRule.blocked(ONE, FeatureId.LAMP, ai), "kept AI read: the light stays free");
        eq(null, PictureSettingsRule.blocked(ONE, FeatureId.PQ_BRIGHTNESS, (PictureSettingsRule.Reads) null),
                "no kept read never refuses");
        PictureSettingsRule.Reads scene = PictureSettingsRule.Reads.read(() -> false, () -> 16, () -> 0);
        eq(null, PictureSettingsRule.blocked(ONE, FeatureId.PQ_BRIGHTNESS, scene), "kept read of the AI film scene opens");
        try {
            PictureSettingsRule.Reads.read(() -> false, () -> 10, () -> { throw new Exception("78"); });
            check(false, "a failed kept read reaches the adapter");
        } catch (Exception e) {
            eq("78", e.getMessage(), "a failed kept read reaches the adapter");
        }

        // the texts: the stock English (values-en of the HB newsettings, what a stock HB shows in English) in the one
        // capitalisation rule of every English text (tools/i18n/text_style.py, 2026-11-24: game mode in sentence case),
        // every language folder of the module has all five
        if (a.length > 0) {
            File res = new File(a[0], "apps/core/lumen-menu/res");
            String[][] en = {{G, "Game mode in effect"},
                    {AI, "Professional settings cannot be used in AI quality picture mode"},
                    {PERF, "Professional settings are not supported in performance picture mode"},
                    {AI_B, "Standard settings cannot be used in AI quality picture mode"},
                    {PERF_B, "Standard settings are not supported in performance picture mode"}};
            // the stock Russian (values-ru of the same newsettings)
            String[][] ru = {{G, "Активирован режим игры"},
                    {AI, "Профессиональные настройки недоступны в режиме качества изображения ИИ"},
                    {PERF, "Профессиональные настройки не поддерживаются в режиме производительности изображения"},
                    {AI_B, "Стандартные настройки недоступны в режиме качества изображения ИИ"},
                    {PERF_B, "Стандартные настройки не поддерживаются в режиме производительности изображения"}};
            File[] dirs = res.listFiles((d, name) -> name.startsWith("values"));
            check(dirs != null && dirs.length >= 18, "language folders");
            for (File d : dirs) {
                File f = new File(d, "lumen_strings_lock.xml");
                String x = f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "";
                for (int i = 0; i < en.length; i++) {
                    String[] s = en[i];
                    Matcher m = Pattern.compile("<string name=\"" + s[0] + "\">([^<]+)</string>").matcher(x);
                    boolean has = m.find();
                    check(has, d.getName() + " has " + s[0]);
                    if (has && d.getName().equals("values")) eq(s[1], m.group(1), "stock English " + s[0]);
                    if (has && d.getName().equals("values-ru")) eq(ru[i][1], m.group(1), "stock Russian " + s[0]);
                    if (has) check(!m.group(1).contains(";") && !m.group(1).contains("\u2014") && !m.group(1).contains("\u2013"),
                            d.getName() + " " + s[0] + " without semicolon or dash");
                }
            }
        }
        System.out.println("PictureSettingsRuleTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    /**
     * AI parity per model: every row of devices/&lt;c&gt;/menu.json in the AI picture mode
     * as the vendor reports it (getPictureMode reads 10 while any AI scene runs, vendor logs of the Z9X, HB and X50),
     * game off, SDR. Refused exactly when the row sits on that model's stock picture settings page (tree
     * image_mode_setting, Z9X image_mode_basic_setting / image_mode_prof_setting), with that page's text. The rows
     * outside it (light, Boost, iris, picture mode, reset all, 3D, aspect, anti rainbow, Dolby curtain, colour filter)
     * and every sound row stay free: no stock newsettings has an AI sound rule. The Play 6 keeps its own dayao rule.
     */
    private static void aiParity(File g, String ai, String aiBasic) throws Exception {
        if (g == null || !new File(g, "devices").isDirectory()) return;
        java.util.Set<String> x50Off = new java.util.HashSet<>(java.util.Arrays.asList("picture.mode", "picture.3d",
                "picture.anti_rbe", "picture.aspect", "picture.dv_screen", "picture.dv_screen.gain", "picture.dv_screen.size",
                "picture.iris", "picture.reset_all", "picture.settings", "picture.style_filter", "picture.custom_new"));
        java.util.Set<String> hbOff = new java.util.HashSet<>(java.util.Arrays.asList("picture.mode", "picture.aspect",
                "picture.reset_all", "picture.anti_rbe", "picture.custom_new"));
        java.util.Set<String> z9xOff = new java.util.HashSet<>(java.util.Arrays.asList("picture.mode", "picture.3d",
                "picture.aspect", "picture.reset_all"));
        java.util.Set<String> z9xBasic = new java.util.HashSet<>(java.util.Arrays.asList("picture.pq", "picture.color_temp",
                "picture.memc", "picture.reset", "picture.auto_switch"));
        String[][] models = {{"g0111", "x50"}, {"g0100", "x50"}, {"g0155", "x50"}, {"g0170", "x50"}, {"h0061", "x50"}, {"h0062", "x50"}, {"h0063", "x50"},
                {"goat_elegant", "hb"}, {"horse_popular", "z9x"}};
        Pattern key = Pattern.compile("\"((?:picture|sound|lamp|boost|game)[a-z0-9_.]*)\"\\s*:\\s*\\{");
        for (String[] m : models) {
            File mj = new File(g, "devices/" + m[0] + "/menu.json");
            String s = new String(Files.readAllBytes(mj.toPath()), StandardCharsets.UTF_8);
            int end = s.indexOf("\"hints\"");
            Matcher k = key.matcher(end > 0 ? s.substring(0, end) : s);
            boolean z9x = "z9x".equals(m[1]);
            PictureSettingsRule.Layout l = z9x ? PictureSettingsRule.BASIC_AND_PRO : PictureSettingsRule.ONE_PAGE;
            java.util.Set<String> off = z9x ? z9xOff : "hb".equals(m[1]) ? hbOff : x50Off;
            int rows = 0;
            while (k.find()) {
                String t = k.group(1);
                FeatureId f = FeatureId.of(t);
                if (f == null) continue;
                rows++;
                String want = !t.startsWith("picture.") || off.contains(t) ? null
                        : z9x && z9xBasic.contains(t) ? aiBasic : ai;
                eq(want, note(l, f, false, 10, 0), m[0] + " " + t + " in AI");
                if (t.startsWith("sound.")) check(!PictureSettingsRule.onPage(l, f), m[0] + " " + t + " has no AI rule");
            }
            check(rows > 10, m[0] + " menu.json rows read: " + rows);
            Matcher pm = Pattern.compile("\"picture\\.mode\"\\s*:\\s*\\{\\s*\"options\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(s);
            boolean hasAi = pm.find() && pm.group(1).contains("\"ai\"");
            check(hasAi == !m[0].startsWith("h006"), m[0] + " AI picture mode offered as on stock (TITAN trees have none)");
        }
    }

    /** Picture mode wire of Colour accuracy (stock image_mode_prof_color_accuracy, other 29) on every model with it. */
    private static final int COLOR_ACCURACY = 29;

    /**
     * Colour accuracy parity per model: stock newsettings has no Colour accuracy rule on any picture row (HB G0130
     * v6.16.24 and G0148 v6.17.12, Z9X v6.15.62, X50 v1.2.64: SettingMutexManager image_mode_setting refuses only the
     * game mode, AI 10 and Performance 5; isProfColorAccurate feeds only the auto switch and motion compensation
     * visibility and, on v6.17.12 and the X50, the elderly colour row of More), the trees give brightness, contrast,
     * saturation and sharpness no support list, PictureViewModel writes setPictureItem with no mode test, and the
     * vendor xgimipq.db keeps them per picture mode for 29 like Standard (settings_field_info) with no interceptor.
     * The page reads no input source, so HDMI and apps share one answer: every row free for SDR, HDR10, HLG and HDR10+
     * (the content Colour accuracy is offered for), and the game mode text on the page rows while the game mode runs.
     */
    private static void colorAccuracyParity(File g, String game) throws Exception {
        if (g == null || !new File(g, "devices").isDirectory()) return;
        String[] models = {"g0111", "g0100", "g0155", "goat_elegant", "horse_popular"};
        int[] contents = {0, 1, 4, 5};                                  // SDR, HDR10, HLG, HDR10+ (getHdrType)
        Pattern key = Pattern.compile("\"((?:picture|sound|lamp|boost|game)[a-z0-9_.]*)\"\\s*:\\s*\\{");
        for (String model : models) {
            File mj = new File(g, "devices/" + model + "/menu.json");
            String s = new String(Files.readAllBytes(mj.toPath()), StandardCharsets.UTF_8);
            check(s.contains("\"color_accuracy\": [\"sdr\", \"hdr\", \"hdr10\"]"),
                    model + " offers Colour accuracy for SDR, HDR and HDR10 as the stock support list");
            PictureSettingsRule.Layout l = "horse_popular".equals(model) ? PictureSettingsRule.BASIC_AND_PRO
                    : PictureSettingsRule.ONE_PAGE;
            int end = s.indexOf("\"hints\"");
            Matcher k = key.matcher(end > 0 ? s.substring(0, end) : s);
            int rows = 0;
            while (k.find()) {
                FeatureId f = FeatureId.of(k.group(1));
                if (f == null) continue;
                rows++;
                java.util.List<FeatureId> ids = new java.util.ArrayList<>();
                if (f == FeatureId.PICTURE_PQ) {
                    ids.add(FeatureId.PQ_BRIGHTNESS);
                    ids.add(FeatureId.PQ_CONTRAST);
                    ids.add(FeatureId.PQ_SATURATION);
                    ids.add(FeatureId.PQ_SHARPNESS);
                } else {
                    ids.add(f);
                }
                for (FeatureId id : ids) {
                    for (int hdr : contents) {
                        String at = model + " " + id + " in Colour accuracy, hdr type " + hdr;
                        eq(null, note(l, id, false, COLOR_ACCURACY, hdr), at + ", game off");
                        eq(null, note(l, id, null, COLOR_ACCURACY, hdr), at + ", game not read");
                        eq(PictureSettingsRule.onPage(l, id) && PictureSettingsRule.gameLatency(id) ? game : null,
                                note(l, id, true, COLOR_ACCURACY, hdr),
                                at + ", game mode on (only the rows that add delay stay closed, TITAN testers 2026-11-19)");
                    }
                    PictureSettingsRule.Reads r = PictureSettingsRule.Reads.read(() -> false, () -> COLOR_ACCURACY,
                            () -> { throw new IllegalStateException("content read outside AI and Performance"); });
                    eq(null, PictureSettingsRule.blocked(l, id, r), model + " " + id + " kept Colour accuracy read");
                }
            }
            check(rows > 10, model + " menu.json rows read: " + rows);
        }
    }
}
