// SPDX-License-Identifier: Apache-2.0
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
            eq(set("hdr_vivid", "color_accuracy"), new LinkedHashSet<>(greyed(p, Signal.DOLBY, "standard")),
                    dev + ": Dolby Vision greys both");
            eq(set("hdr_vivid"), greyed(p, null, "standard"), dev + ": content not read counts as SDR");
            eq(set("hdr_vivid"), SignalRule.greyed(p, FeatureId.PICTURE_MODE, null), dev + ": nothing read counts as SDR");
            eq(Collections.emptySet(), greyed(p, Signal.SDR, "hdr_vivid"), dev + ": the mode set now is never greyed");
            eq(Collections.emptySet(), greyed(p, Signal.HDR_VIVID, "color_accuracy"), dev + ": current colour accuracy stays usable");
            eq(Collections.emptySet(), SignalRule.greyed(p, FeatureId.PICTURE_COLOR_TEMP, at(Signal.SDR, "standard")),
                    dev + ": a choice without support lists is never greyed");
        }
        // ---- the other stock trees: X50 / RS30 colour accuracy and filmmaker, Play 6 none
        MenuProfile x50 = profile(root, "g0111");
        eq(set("filmmaker", "color_accuracy"), new LinkedHashSet<>(greyed(x50, Signal.HDR_VIVID, "ai")), "X50: HDR Vivid content");
        eq(Collections.emptySet(), greyed(x50, Signal.SDR, "ai"), "X50: SDR greys nothing");
        eq(set("color_accuracy"), greyed(profile(root, "g0155"), Signal.DOLBY, "ai"), "RS30: Dolby greys colour accuracy");
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
