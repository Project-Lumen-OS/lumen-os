// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import org.lumen.menu.FeatureId;
import org.lumen.menu.PictureSettingsRule;
import org.lumen.menu.ProjectorFeatures.Note;

/**
 * The Z9X rows of the stock picture settings pages through the shared PictureSettingsRule (Z9xMenuRules.blocked): the
 * 1.0.10 PanelRules.basicOrPro decisions on every row that asked it, the stock text of the row's page (basic or
 * professional), and the per-row reads that answer before the rule. sh test/rules/run.sh
 */
public final class Z9xPictureSettingsTest {
    private static int failed, passed;

    private static void eq(String what, Object want, Object got) {
        if (want == got || (want != null && want.equals(got))) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL " + what + ": want " + want + ", got " + got);
        }
    }

    /** 2D, SDR, Standard, no game, every read of the rows present. */
    private static Z9xMenuRules.In in(Boolean game, Integer pm, Integer hdr) {
        Z9xMenuRules.In in = new Z9xMenuRules.In();
        in.gameActive55 = game;
        in.pictureMode = pm;
        in.hdr = hdr;
        in.lumensMode = 16;
        in.memc = 0;
        in.fmt3d = 1;
        in.is3dTo2d = false;
        in.nativeFps = false;
        in.source = Z9xMenuRules.SOURCE_HDMI1;
        in.hdmiFpsCenti = 6000;
        return in;
    }

    private static String note(FeatureId f, String value, Z9xMenuRules.In in) {
        Note n = Z9xMenuRules.blocked(f, value, in);
        return n == null ? null : n.res;
    }

    public static void main(String[] a) {
        eq("the Z9X dapeng tree: picture mode 3.0", PictureSettingsRule.BASIC_AND_PRO, Z9xMenuRules.PICTURE_PAGES);
        final String G = PictureSettingsRule.NOTE_GAME, AI = PictureSettingsRule.NOTE_AI,
                PERF = PictureSettingsRule.NOTE_PERFORMANCE, AI_B = PictureSettingsRule.NOTE_AI_BASIC,
                PERF_B = PictureSettingsRule.NOTE_PERFORMANCE_BASIC;
        // the rows that asked PanelRules.basicOrPro in 1.0.10, the value of the press (null = a step or a page)
        Object[][] basic = {{FeatureId.PQ_BRIGHTNESS, null}, {FeatureId.PQ_SHARPNESS, null},
                {FeatureId.PICTURE_COLOR_TEMP, "d65"}, {FeatureId.PICTURE_MEMC, "low"}, {FeatureId.PICTURE_RESET, null}};
        Object[][] pro = {{FeatureId.PICTURE_PRO, null}, {FeatureId.PICTURE_AI_CONTRAST, "on"},
                {FeatureId.PICTURE_SUPER_RES, "on"}, {FeatureId.PICTURE_ULTRA120, "off"}};
        // the 1.0.10 basicOrPro cases (game, 666, 620): NONE, GAME, GAME, AI, NONE, NONE, PERFORMANCE, NONE, NONE, NONE;
        // game mode on (TITAN testers 2026-11-19, PictureSettingsRule.GAME_LATENCY): only the rows that add delay keep
        // the game text, the others decide as with the game off (the last two columns)
        Object[][] pins = {{false, 1, 0, null, null}, {true, 1, 0, G, G, null, null}, {true, 10, 0, G, G, AI_B, AI},
                {false, 10, 0, AI_B, AI}, {false, 16, 0, null, null}, {false, 10, 2, null, null}, {false, 5, 0, PERF_B, PERF},
                {false, 5, 2, null, null}, {null, 1, 0, null, null}, {false, null, 0, null, null}};
        for (Object[] p : pins) {
            String what = " game " + p[0] + " 666 " + p[1] + " 620 " + p[2];
            for (Object[] r : basic) {
                Object want = p.length > 5 && !PictureSettingsRule.gameLatency((FeatureId) r[0]) ? p[5] : p[3];
                eq(r[0] + what, want, note((FeatureId) r[0], (String) r[1], in((Boolean) p[0], (Integer) p[1], (Integer) p[2])));
            }
            for (Object[] r : pro) {
                Object want = p.length > 5 && !PictureSettingsRule.gameLatency((FeatureId) r[0]) ? p[6] : p[4];
                eq(r[0] + what, want, note((FeatureId) r[0], (String) r[1], in((Boolean) p[0], (Integer) p[1], (Integer) p[2])));
            }
        }
        // the rows that add delay stay closed in game mode whatever the picture mode, the others are drawn open
        Note memc = Z9xMenuRules.locked(FeatureId.PICTURE_MEMC, in(true, 1, 0));
        eq("motion compensation drawn closed in game mode", G, memc == null ? null : memc.res);
        eq("brightness drawn open in game mode", null, Z9xMenuRules.locked(FeatureId.PQ_BRIGHTNESS, in(true, 1, 0)));
        Note n = Z9xMenuRules.blocked(FeatureId.PQ_BRIGHTNESS, null, in(false, 10, 0));
        eq("the stock plain toast, no row title", true, n != null && n.toast && n.title == null);

        // the closed look (Z9xFeatures.locked): the same page rule and nothing else, nothing read = open
        for (Object[] r : basic) {
            Note l = Z9xMenuRules.locked((FeatureId) r[0], in(false, 10, 0));
            eq(r[0] + " drawn closed in AI with the basic text", AI_B, l == null ? null : l.res);
        }
        for (Object[] r : pro) {
            Note l = Z9xMenuRules.locked((FeatureId) r[0], in(false, 10, 0));
            eq(r[0] + " drawn closed in AI with the professional text", AI, l == null ? null : l.res);
        }
        for (FeatureId f : new FeatureId[]{FeatureId.PICTURE_MODE, FeatureId.LAMP, FeatureId.PICTURE_RESET_ALL, FeatureId.PICTURE_ASPECT}) {
            eq(f + " drawn open in AI", null, Z9xMenuRules.locked(f, in(false, 10, 0)));
        }
        eq("nothing read: drawn open", null, Z9xMenuRules.locked(FeatureId.PQ_BRIGHTNESS, null));
        eq("sport: drawn open", null, Z9xMenuRules.locked(FeatureId.PQ_BRIGHTNESS, in(false, 9, 0)));

        // the reads each row asks first, as 1.0.10: a missing read of the row answers before the page rule
        Z9xMenuRules.In ai = in(false, 10, 0);
        ai.lumensMode = null;
        eq("colour temperature without 175: silent before the rule", Z9xMenuRules.SILENT, Z9xMenuRules.blocked(FeatureId.PICTURE_COLOR_TEMP, "d65", ai));
        ai = in(false, 10, 0);
        ai.memc = null;
        eq("motion compensation without its read: silent before the rule", Z9xMenuRules.SILENT, Z9xMenuRules.blocked(FeatureId.PICTURE_MEMC, "low", ai));
        for (FeatureId f : new FeatureId[]{FeatureId.PQ_BRIGHTNESS, FeatureId.PICTURE_AI_CONTRAST, FeatureId.PICTURE_SUPER_RES,
                FeatureId.PICTURE_PRO, FeatureId.PICTURE_RESET, FeatureId.PICTURE_ULTRA120}) {
            eq(f + " with no read at all", null, Z9xMenuRules.blocked(f, "on", null));
        }
        // the row's own rules come after the page rule
        Z9xMenuRules.In office = in(false, 25, 0);
        eq("colour temperature in Office: its own rule", "rules_not_office", note(FeatureId.PICTURE_COLOR_TEMP, "d65", office));
        Z9xMenuRules.In fps = in(false, 1, 0);
        fps.nativeFps = true;
        eq("motion compensation with the original frame rate", "pro_memc_blocked", note(FeatureId.PICTURE_MEMC, "low", fps));
        fps.pictureMode = 10;
        eq("motion compensation, AI first", AI_B, note(FeatureId.PICTURE_MEMC, "low", fps));
        Z9xMenuRules.In u = in(false, 1, 0);
        u.nativeFps = true;
        eq("Ultra 120 on with the original frame rate", "rules_not_native_fps", note(FeatureId.PICTURE_ULTRA120, "on", u));
        u.pictureMode = 5;
        eq("Ultra 120 on in Performance: the page first", PERF, note(FeatureId.PICTURE_ULTRA120, "on", u));
        // rows off the pages never get the page text
        for (FeatureId f : new FeatureId[]{FeatureId.PICTURE_MODE, FeatureId.PICTURE_RESET_ALL, FeatureId.PICTURE_ASPECT,
                FeatureId.HDMI_EDID}) {
            String r = note(f, null, in(true, 10, 0));
            eq(f + " is not refused with a page text", false, r != null && r.startsWith("lumen_m_ps_"));
        }
        System.out.println("Z9xPictureSettingsTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
