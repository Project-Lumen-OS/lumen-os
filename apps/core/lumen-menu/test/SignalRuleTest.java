// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Picture modes that follow the content (stock tree support lists, SignalRule): java org.lumen.menu.SignalRuleTest
 * &lt;gsi root&gt;.
 */
public final class SignalRuleTest {
    private static int pass, fail;

    private static void check(boolean ok, String what) {
        if (ok) pass++;
        else { fail++; System.out.println("FAIL " + what); }
    }

    private static void eq(Object want, Object got, String what) {
        boolean ok = want == null ? got == null : want.equals(got);
        if (!ok) System.out.println("     want " + want + " got " + got);
        check(ok, what);
    }

    private static Set<String> set(String... s) { return new LinkedHashSet<>(Arrays.asList(s)); }

    private static MenuProfile profile(Path root, String dev) throws Exception {
        return MenuProfile.parse(new String(Files.readAllBytes(root.resolve("devices/" + dev + "/menu.json")), StandardCharsets.UTF_8));
    }

    private static MenuState at(Signal s, String mode) {
        return MenuState.builder().signal(s).put(FeatureId.PICTURE_MODE, mode).build();
    }

    private static Set<String> greyed(MenuProfile p, Signal s, String mode) {
        return SignalRule.greyed(p, FeatureId.PICTURE_MODE, at(s, mode));
    }

    /** Every picture mode of {@code p} except {@code keep}, in the profile's order. */
    private static Set<String> allBut(MenuProfile p, String keep) {
        Set<String> s = new LinkedHashSet<>(p.options(FeatureId.PICTURE_MODE));
        s.remove(keep);
        return s;
    }

    private static MenuProfile parse(String features) {
        return MenuProfile.parse("{\"schema\": 1, \"device\": \"t\", \"model\": \"T\", \"adapter\": \"a\", \"features\": {" + features
                + "}, \"keys\": {\"gear_short\": \"quick_menu\", \"gear_long\": \"none\"}, \"game_menu\": {\"enabled\": false}}");
    }

    public static void main(String[] a) throws Exception {
        Path root = Paths.get(a[0]);
        // ---- the vendor's getHdrType read, in the stock PictureUtils order
        eq(Signal.SDR, Signal.ofHdrType(0), "hdr type 0 = SDR");
        eq(Signal.HDR10, Signal.ofHdrType(1), "hdr type 1 = HDR10");
        eq(Signal.DOLBY, Signal.ofHdrType(2), "hdr type 2 = Dolby Vision");
        eq(Signal.HDR, Signal.ofHdrType(4), "HLG counts as HDR");
        eq(Signal.HDR, Signal.ofHdrType(5), "HDR10+ counts as HDR");
        eq(Signal.HDR_VIVID, Signal.ofHdrType(6), "hdr type 6 = HDR Vivid");
        eq(null, Signal.ofHdrType(null), "no read = unknown");
        eq(null, Signal.ofHdrType(-1), "a failed read = unknown");
        for (Signal s : Signal.values()) eq(s, Signal.of(s.token), "token " + s.token);

        // ---- Z9X HB and Z9X: the stock dapengse / dapeng lists (HDR Vivid only for HDR Vivid content, colour
        //      accuracy for SDR, HDR, HDR10)
        for (String dev : new String[]{"goat_elegant", "horse_popular"}) {
            MenuProfile p = profile(root, dev);
            check(p.ok() && p.hasSupport(FeatureId.PICTURE_MODE), dev + ": picture modes follow the content");
            eq(set("hdr_vivid"), greyed(p, Signal.SDR, "standard"), dev + ": SDR greys HDR Vivid");
            eq(set("hdr_vivid"), greyed(p, Signal.HDR10, "standard"), dev + ": HDR10 greys HDR Vivid");
            eq(set("hdr_vivid"), greyed(p, Signal.HDR, "standard"), dev + ": HLG / HDR10+ grey HDR Vivid");
            eq(set("color_accuracy"), greyed(p, Signal.HDR_VIVID, "standard"), dev + ": HDR Vivid content greys colour accuracy");
            eq(allBut(p, "standard"), greyed(p, Signal.DOLBY, "standard"),
                    dev + ": Dolby Vision closes every picture mode (stock handleDolbyVideo)");
            eq(new LinkedHashSet<>(p.options(FeatureId.PICTURE_MODE)), greyed(p, Signal.DOLBY, null),
                    dev + ": Dolby Vision with a vendor-only mode read closes the whole list");
            eq(set("hdr_vivid"), greyed(p, null, "standard"), dev + ": content not read counts as SDR");
            eq(set("hdr_vivid"), SignalRule.greyed(p, FeatureId.PICTURE_MODE, null), dev + ": nothing read counts as SDR");
            eq(Collections.emptySet(), greyed(p, Signal.SDR, "hdr_vivid"), dev + ": the mode set now is never greyed");
            eq(Collections.emptySet(), greyed(p, Signal.HDR_VIVID, "color_accuracy"), dev + ": current colour accuracy stays usable");
            eq(Collections.emptySet(), SignalRule.greyed(p, FeatureId.PICTURE_COLOR_TEMP, at(Signal.SDR, "standard")),
                    dev + ": a choice without support lists is never greyed");
        }
        // ---- the other stock trees: X50 / RS30 colour accuracy and filmmaker, Play 6 none
        MenuProfile x50 = profile(root, "g0111");
        eq(set("imax", "filmmaker", "color_accuracy", "dv_bright", "dv_dark"), new LinkedHashSet<>(greyed(x50, Signal.HDR_VIVID, "ai")),
                "X50: HDR Vivid content");
        eq(set("imax", "dv_bright", "dv_dark"), greyed(x50, Signal.SDR, "ai"), "X50: SDR greys IMAX Enhanced and the Dolby Vision modes");
        eq(set("dv_bright", "dv_dark"), greyed(x50, Signal.HDR10, "ai"), "X50: HDR10 greys only the Dolby Vision modes");
        eq(set("filmmaker", "color_accuracy", "dv_bright", "dv_dark"), new LinkedHashSet<>(greyed(x50, Signal.IMAX, "ai")),
                "X50: IMAX Enhanced content greys Filmmaker, colour accuracy and the Dolby Vision modes");
        // ---- the X50 family: Dolby Vision Bright and Dark inside the picture mode list (stock image_mode_4_state nodes
        //      image_mode_picture_dolby_bright / _soft, support DOLBY), the only open modes while Dolby Vision plays
        for (String dev : new String[]{"g0111", "g0100", "g0155", "g0170", "h0061", "h0062", "h0063"}) {
            MenuProfile p = profile(root, dev);
            Set<String> normal = allBut(p, "dv_bright");
            normal.remove("dv_dark");
            check(p.options(FeatureId.PICTURE_MODE).containsAll(set("dv_bright", "dv_dark")), dev + ": the Dolby Vision modes are in the list");
            eq(normal, greyed(p, Signal.DOLBY, "dv_bright"), dev + ": Dolby Vision opens only its Bright and Dark");
            eq(normal, greyed(p, Signal.DOLBY, null), dev + ": Dolby Vision with no mode read yet: the same");
            for (Signal k : new Signal[]{Signal.SDR, Signal.HDR10, Signal.HDR}) {
                Set<String> g = greyed(p, k, p.options(FeatureId.PICTURE_MODE).get(1));
                check(g.contains("dv_bright") && g.contains("dv_dark") && Collections.disjoint(g, set("sport", "standard", "movie")),
                        dev + " " + k.token + ": the Dolby Vision modes greyed, the normal ones open");
            }
        }
        check(!profile(root, "goat_elegant").options(FeatureId.PICTURE_MODE).contains("dv_bright")
                && !profile(root, "horse_popular").options(FeatureId.PICTURE_MODE).contains("dv_bright"),
                "Z9X HB and Z9X: no Dolby Vision modes (no Dolby Vision display)");
        MenuProfile rs30 = profile(root, "g0155");
        Set<String> rs30Normal = allBut(rs30, "ai");
        rs30Normal.removeAll(set("dv_bright", "dv_dark"));
        eq(rs30Normal, greyed(rs30, Signal.DOLBY, "ai"), "RS30: Dolby Vision closes every normal picture mode");

        // ---- Dolby Vision and the runtime list: user picture modes close too, other content leaves them open
        MenuProfile hb = profile(root, "goat_elegant");
        java.util.List<String> withUser = new java.util.ArrayList<>(hb.options(FeatureId.PICTURE_MODE));
        withUser.add(CustomModes.token(101));
        MenuState dvUser = MenuState.builder().signal(Signal.DOLBY).put(FeatureId.PICTURE_MODE, "standard")
                .options(FeatureId.PICTURE_MODE, withUser).build();
        check(SignalRule.greyed(hb, FeatureId.PICTURE_MODE, dvUser).contains(CustomModes.token(101)),
                "HB: Dolby Vision closes a user picture mode");
        MenuState sdrUser = MenuState.builder().signal(Signal.SDR).put(FeatureId.PICTURE_MODE, "standard")
                .options(FeatureId.PICTURE_MODE, withUser).build();
        eq(set("hdr_vivid"), SignalRule.greyed(hb, FeatureId.PICTURE_MODE, sdrUser), "HB: SDR leaves user picture modes open");
        // an option that names Dolby Vision in its support list stays open while Dolby Vision plays
        MenuProfile dv = parse("\"picture.mode\": {\"options\": [\"ai\", \"dv_bright\", \"hdr_vivid\"], \"support\": "
                + "{\"dv_bright\": [\"dolby\"], \"hdr_vivid\": [\"hdr_vivid\"]}}");
        check(dv.ok(), "a profile with a Dolby Vision mode parses");
        eq(set("ai", "hdr_vivid"), greyed(dv, Signal.DOLBY, null), "a Dolby Vision mode stays open in Dolby Vision");
        eq(set("dv_bright", "hdr_vivid"), greyed(dv, Signal.SDR, "ai"), "a Dolby Vision mode is closed on SDR");
        for (Signal s : new Signal[]{Signal.HDR10, Signal.HDR, Signal.HDR_VIVID}) {
            check(greyed(dv, s, "ai").contains("dv_bright") && !greyed(dv, s, "dv_bright").contains("ai"),
                    s.token + ": the Dolby Vision mode closed, the normal modes open");
        }
        // one data rule for every choice with support lists: an option without one is everything but Dolby Vision
        check(SignalRule.offered(Collections.emptyList(), Signal.SDR) && SignalRule.offered(Collections.emptyList(), Signal.HDR10)
                && !SignalRule.offered(Collections.emptyList(), Signal.DOLBY), "no list = every kind but Dolby Vision");
        check(SignalRule.offered(java.util.List.of(Signal.DOLBY), Signal.DOLBY)
                && !SignalRule.offered(java.util.List.of(Signal.DOLBY), Signal.SDR), "a Dolby Vision list = Dolby Vision only");
        // the content kind in the stock order (PictureUtils.getCurSourceType): Dolby Vision, IMAX Enhanced, the HDR kind
        HdrFormat sdr = HdrFormat.ofHdrType(0), hdr10 = HdrFormat.ofHdrType(1), dolby = HdrFormat.ofHdrType(2);
        eq(Signal.DOLBY, Signal.of(dolby, true), "Dolby Vision before IMAX Enhanced");
        eq(Signal.IMAX, Signal.of(hdr10, true), "IMAX Enhanced before HDR10");
        eq(Signal.IMAX, Signal.of(sdr, true), "IMAX Enhanced before SDR");
        eq(Signal.IMAX, Signal.of(null, true), "IMAX Enhanced with the format not read");
        eq(Signal.HDR10, Signal.of(hdr10, false), "no IMAX flag: the HDR kind");
        eq(null, Signal.of(null, false), "nothing read: no kind");
        MenuState imaxState = MenuState.builder().hdr(hdr10, true).build();
        check(imaxState.signal() == Signal.IMAX && imaxState.format() == hdr10, "IMAX content: kind IMAX, the page names HDR10");
        check(MenuState.builder().hdr(hdr10).build().signal() == Signal.HDR10, "one getHdrType read alone: its kind");
        // IMAX Enhanced (stock support IMAX, HDR10): offered only with IMAX Enhanced or HDR10 content
        MenuProfile im = parse("\"picture.mode\": {\"options\": [\"ai\", \"imax\", \"filmmaker\"], \"support\": "
                + "{\"imax\": [\"imax\", \"hdr10\"], \"filmmaker\": [\"sdr\", \"hdr\", \"hdr10\", \"filmmaker\"]}}");
        check(im.ok(), "a profile with IMAX Enhanced parses");
        eq(set("filmmaker"), greyed(im, Signal.IMAX, "ai"), "IMAX content: IMAX Enhanced open, Filmmaker closed");
        eq(Collections.emptySet(), greyed(im, Signal.HDR10, "ai"), "HDR10: IMAX Enhanced open");
        for (Signal s : new Signal[]{Signal.SDR, Signal.HDR, Signal.HDR_VIVID, Signal.FILMMAKER}) {
            check(greyed(im, s, "ai").contains("imax"), s.token + ": IMAX Enhanced closed");
        }
        eq(set("ai", "imax", "filmmaker"), greyed(im, Signal.DOLBY, null), "Dolby Vision: IMAX Enhanced closed");
        MenuProfile other = parse("\"sound.mode\": {\"options\": [\"movie\", \"night\"], \"support\": {\"night\": [\"sdr\", \"dolby\"]}}");
        check(other.ok(), "another choice with a support list parses");
        eq(set("movie"), SignalRule.greyed(other, FeatureId.SOUND_MODE, MenuState.builder().signal(Signal.DOLBY).build()),
                "another choice follows the same rule in Dolby Vision");
        eq(Collections.emptySet(), SignalRule.greyed(other, FeatureId.SOUND_MODE, MenuState.builder().signal(Signal.SDR).build()),
                "another choice keeps both on SDR");
        eq(Collections.emptySet(), SignalRule.greyed(dv, FeatureId.PICTURE_COLOR_TEMP, at(Signal.DOLBY, "ai")),
                "Dolby Vision greys no choice without support lists");
        MenuState hdr = MenuState.builder().hdr(HdrFormat.DOLBY_VISION).put(FeatureId.PICTURE_MODE, "dv_bright").build();
        eq(Signal.DOLBY, hdr.signal(), "an adapter's hdr read carries the content kind");
        eq(HdrFormat.DOLBY_VISION, hdr.format(), "and the format");
        eq(set("ai", "hdr_vivid"), SignalRule.greyed(dv, FeatureId.PICTURE_MODE, hdr), "the hdr read alone greys by the kind");
        MenuState unread = MenuState.builder().hdr(null).build();
        check(unread.signal() == null && unread.format() == null, "no hdr read = nothing read");
        MenuProfile p6 = profile(root, "dragon_healthy");
        check(!p6.hasSupport(FeatureId.PICTURE_MODE), "Play 6: its stock tree has no support lists");
        for (Signal s : Signal.values()) eq(Collections.emptySet(), greyed(p6, s, "ai"), "Play 6 greys nothing on " + s.token);

        // ---- a live part keeps the content kind unless it reads one
        MenuState base = at(Signal.HDR_VIVID, "standard");
        eq(Signal.HDR_VIVID, base.with(MenuState.builder().put(FeatureId.LAMP, "5").build()).signal(), "a live part keeps the kind");
        eq(Signal.SDR, base.with(MenuState.builder().signal(Signal.SDR).build()).signal(), "a live part with a kind replaces it");

        // ---- profile checks
        String pm = "\"picture.mode\": {\"options\": [\"ai\", \"hdr_vivid\"], \"support\": ";
        check(parse(pm + "{\"hdr_vivid\": [\"hdr_vivid\"]}}").ok(), "a valid support list");
        check(!parse(pm + "{\"vivid\": [\"hdr_vivid\"]}}").ok(), "support of an option the choice lacks");
        check(!parse(pm + "{\"hdr_vivid\": [\"hdr12\"]}}").ok(), "support with an unknown content kind");
        check(!parse(pm + "{\"hdr_vivid\": [\"hdr_vivid\", \"hdr12\"]}}").ok(), "an unknown content kind next to a known one");
        check(!parse(pm + "{\"hdr_vivid\": []}}").ok(), "an empty support list");
        check(!parse(pm + "{}}").ok(), "an empty support object");
        check(!parse("\"eye\": {\"options\": [\"off\", \"on\"]}, \"lamp.off\": {\"support\": {\"x\": [\"sdr\"]}}").ok(),
                "support on a feature that is not a choice");

        System.out.println("SignalRuleTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
