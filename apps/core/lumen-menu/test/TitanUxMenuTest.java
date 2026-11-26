// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * TITAN testers 2026-11-19 in the shared menu: the menu's side and size (MenuLook), the menu kept across the input's
 * own restart (Rehost), the picture settings in game mode (PictureSettingsRule.GAME_LATENCY), the focus rows on the
 * Image correction page with their smart focus sub page, and the level angles under Size and position.
 */
public final class TitanUxMenuTest {
    private static int fails;

    private static void ok(String what, boolean cond) {
        if (cond) {
            System.out.println("ok   " + what);
        } else {
            fails++;
            System.out.println("FAIL " + what);
        }
    }

    public static void main(String[] a) throws Exception {
        look();
        rehost();
        game();
        for (File d : new File(a[0], "devices").listFiles()) {
            File j = new File(d, "menu.json");
            if (j.isFile()) profile(d.getName(), MenuProfile.parse(new String(Files.readAllBytes(j.toPath()), StandardCharsets.UTF_8)));
        }
        if (fails > 0) {
            System.out.println("TitanUxMenuTest: " + fails + " FAILED");
            System.exit(1);
        }
        System.out.println("TitanUxMenuTest: all ok");
    }

    static void look() {
        ok("side: nothing stored = left, the place so far", MenuLook.LEFT.equals(MenuLook.side(null)) && !MenuLook.right(null));
        ok("side: right stored = right", MenuLook.right("right") && MenuLook.RIGHT.equals(MenuLook.side("right")));
        ok("side: junk = left", MenuLook.LEFT.equals(MenuLook.side("middle")));
        ok("size: nothing stored = normal at scale 1", MenuLook.NORMAL.equals(MenuLook.density(null)) && MenuLook.scale(null) == 1f);
        ok("size: compact draws smaller (more rows fit)", MenuLook.scale("compact") == MenuLook.COMPACT_SCALE
                && MenuLook.COMPACT_SCALE < 1f && MenuLook.COMPACT_SCALE >= 0.8f);
        ok("valid: only the options of each setting", MenuLook.valid(FeatureId.SETTINGS_MENU_SIDE, "right")
                && !MenuLook.valid(FeatureId.SETTINGS_MENU_SIDE, "compact") && MenuLook.valid(FeatureId.SETTINGS_MENU_DENSITY, "compact")
                && !MenuLook.valid(FeatureId.SETTINGS_MENU_DENSITY, "left") && !MenuLook.valid(FeatureId.LAMP, "left"));
        ok("the default comes first in each option list", MenuLook.SIDES.get(0).equals(MenuLook.LEFT)
                && MenuLook.DENSITIES.get(0).equals(MenuLook.NORMAL));
        ok("both are module settings (no device call)", MenuSpec.MODULE_SETTINGS.contains(FeatureId.SETTINGS_MENU_SIDE)
                && MenuSpec.MODULE_SETTINGS.contains(FeatureId.SETTINGS_MENU_DENSITY));
    }

    static void rehost() {
        String tv = "org.lumen.tvinput", own = "org.lumen.x50.projector";
        ok("the input restarting its screen, screen on = kept", Rehost.keep(true, tv, tv, own));
        ok("screen off (power off) = never kept", !Rehost.keep(false, tv, tv, own));
        ok("HOME or another app in front = closed", !Rehost.keep(true, tv, "org.lumen.home", own));
        ok("unknown front = closed", !Rehost.keep(true, null, null, own) && !Rehost.keep(true, tv, null, own)
                && !Rehost.keep(true, "", "", own));
        ok("the menu's own app in front at the show = closed", !Rehost.keep(true, own, own, own));
        ok("a lost focus waits only while kept", Rehost.waitFocus(true, 1000, 1000 + Rehost.FOCUS_GRACE_MS - 1)
                && !Rehost.waitFocus(true, 1000, 1000 + Rehost.FOCUS_GRACE_MS) && !Rehost.waitFocus(false, 1000, 1001));
    }

    static void game() {
        PictureSettingsRule.Layout one = PictureSettingsRule.ONE_PAGE;
        String g = PictureSettingsRule.NOTE_GAME;
        ok("game on: brightness opens", PictureSettingsRule.why(one, FeatureId.PQ_BRIGHTNESS, true, 1, 0) == null);
        ok("game on: colour temperature, gamma, tone mapping open", PictureSettingsRule.why(one, FeatureId.PICTURE_COLOR_TEMP, true, 1, 0) == null
                && PictureSettingsRule.why(one, FeatureId.PICTURE_GAMMA, true, 1, 0) == null
                && PictureSettingsRule.why(one, FeatureId.PICTURE_HDR_MAPPING, true, 1, 1) == null);
        for (FeatureId f : new FeatureId[]{FeatureId.PICTURE_MEMC, FeatureId.PICTURE_NOISE, FeatureId.PICTURE_SUPER_RES}) {
            ok("game on: " + f + " stays closed (adds delay)", g.equals(PictureSettingsRule.why(one, f, true, 1, 0)));
            ok("game off: " + f + " opens", PictureSettingsRule.why(one, f, false, 1, 0) == null);
        }
        ok("Ultra 120 stays closed in game mode", g.equals(PictureSettingsRule.why(PictureSettingsRule.BASIC_AND_PRO,
                FeatureId.PICTURE_ULTRA120, true, 1, 0)));
        ok("game on in AI: the AI note (the picture mode is read in game mode now)",
                PictureSettingsRule.NOTE_AI.equals(PictureSettingsRule.why(one, FeatureId.PQ_BRIGHTNESS, true, 10, 0)));
        int[] reads = {0};
        PictureSettingsRule.Reads r = PictureSettingsRule.Reads.read(() -> { reads[0]++; return true; },
                () -> { reads[0]++; return 1; }, () -> { reads[0]++; return 0; });
        ok("reads in game mode: game state, picture mode, no content outside AI and Performance", reads[0] == 2
                && Boolean.TRUE.equals(r.gameOn) && Integer.valueOf(1).equals(r.pictureMode));
    }

    static boolean in(List<MenuSpec.Group> gs, FeatureId f) {
        if (gs == null) return false;
        for (MenuSpec.Group g : gs) if (g.rows.contains(f)) return true;
        return false;
    }

    static List<FeatureId> rows(List<MenuSpec.Group> gs) {
        List<FeatureId> out = new ArrayList<>();
        if (gs != null) for (MenuSpec.Group g : gs) out.addAll(g.rows);
        return out;
    }

    static void profile(String dev, MenuProfile p) {
        if (!p.ok()) {
            ok(dev + ": profile reads (" + p.problems() + ")", false);
            return;
        }
        MenuSpec s = MenuSpec.of(p);
        List<MenuSpec.Group> ks = s.sections.get(MenuSpec.KEYSTONE), kp = s.popups.get(MenuSpec.KEYSTONE);
        ok(dev + ": auto and manual focus on the Image correction page", in(ks, FeatureId.FOCUS_AF_NOW) && in(ks, FeatureId.FOCUS_MANUAL));
        int manual = rows(ks).indexOf(FeatureId.KEYSTONE_MANUAL), af = rows(ks).indexOf(FeatureId.FOCUS_AF_NOW);
        ok(dev + ": right after manual keystone", manual >= 0 && af == manual + 1);
        ok(dev + ": smart focus on the focus.settings sub page of that page", in(ks, FeatureId.FOCUS_SETTINGS)
                && in(s.pages.get(FeatureId.FOCUS_SETTINGS), FeatureId.FOCUS_SMART) && !in(ks, FeatureId.FOCUS_SMART));
        ok(dev + ": All settings has no own Focus category any more", !s.allSettings.contains(MenuSpec.FOCUS)
                && s.allSettings.contains(MenuSpec.KEYSTONE));
        ok(dev + ": the keystone popup leaves the focus rows to the Focus tile (each row once on the card)",
                !in(kp, FeatureId.FOCUS_AF_NOW) && !in(kp, FeatureId.FOCUS_MANUAL) && !in(kp, FeatureId.FOCUS_SETTINGS));
        List<MenuSpec.Group> fp = s.popups.get(MenuSpec.FOCUS);
        ok(dev + ": the Focus tile popup keeps every focus row", in(fp, FeatureId.FOCUS_AF_NOW) && in(fp, FeatureId.FOCUS_MANUAL)
                && in(fp, FeatureId.FOCUS_SMART));
        List<MenuSpec.Group> gen = s.sections.get(MenuSpec.GENERAL);
        boolean menuGroup = false;
        if (gen != null) for (MenuSpec.Group g : gen) {
            if ("menu".equals(g.header) && g.rows.contains(FeatureId.SETTINGS_MENU_SIDE) && g.rows.contains(FeatureId.SETTINGS_MENU_DENSITY)) menuGroup = true;
        }
        ok(dev + ": General has the Menu group (side and size)", menuGroup);
        if (p.has(FeatureId.KEYSTONE_ANGLE_H)) {
            boolean sized = false;
            for (MenuSpec.Group g : ks) {
                if ("size_position".equals(g.header) && g.rows.contains(FeatureId.KEYSTONE_ANGLE_H) && g.rows.contains(FeatureId.KEYSTONE_ANGLE_V)) sized = true;
            }
            ok(dev + ": the level angles under Size and position", sized);
        }
    }
}
