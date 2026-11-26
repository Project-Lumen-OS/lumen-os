// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.signal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Host tests of the pure signal panel logic: formatting, judder, the button, the HAL budget, row hiding, items. */
public final class SignalTest {
    private static int fails, oks;

    static void ok(String what, boolean cond) {
        if (cond) oks++;
        else { fails++; System.out.println("FAIL " + what); }
    }

    static void eq(String what, Object want, Object got) {
        ok(what + " (want " + want + ", got " + got + ")", want == null ? got == null : want.equals(got));
    }

    /** Words that print the key and its arguments, so a test sees which text was chosen. */
    static final SignalText.Words WORDS = new SignalText.Words() {
        @Override public String get(String key, Object... args) {
            StringBuilder b = new StringBuilder(key);
            for (Object a : args) b.append('|').append(a);
            return b.toString();
        }

        @Override public char decimal() { return ','; }
    };

    public static void main(String[] a) {
        format();
        place();
        judder();
        toggle();
        budget();
        sheet();
        hiding();
        items();
        look();
        steady();
        menuRows();
        System.out.println((fails == 0 ? "ok   " : "FAIL ") + "SignalTest: " + oks + " ok, " + fails + " failed");
        System.exit(fails == 0 ? 0 : 1);
    }

    static void format() {
        eq("rate 23.97", "23.97", SignalFormat.rate(2397, '.'));
        eq("rate 60", "60", SignalFormat.rate(6000, ','));
        eq("rate 59,94", "59,94", SignalFormat.rate(5994, ','));
        eq("rate trailing zero", "23.9", SignalFormat.rate(2390, '.'));
        eq("rate leading zero", "23.05", SignalFormat.rate(2305, '.'));
        eq("rate none", "", SignalFormat.rate(0, '.'));
        eq("size", "3840\u00d72160", SignalFormat.size(3840, 2160));
        eq("mode: the owner's format, never glued", "1920\u00d71080 @ 60 Hz", SignalFormat.mode(SignalFormat.size(1920, 1080), "60 Hz"));
        eq("mode without a rate", "1920\u00d71080", SignalFormat.mode("1920\u00d71080", null));
        eq("mode empty rate", "1920\u00d71080", SignalFormat.mode("1920\u00d71080", ""));
        eq("mode no size", null, SignalFormat.mode(null, "60 Hz"));
        eq("temp", Integer.valueOf(48), SignalFormat.temp(47.6));
        eq("temp no sensor", null, SignalFormat.temp(0));
        eq("temp too hot", null, SignalFormat.temp(400));
        eq("physical 1.0.0.0", "1.0.0.0", SignalFormat.physical(0x1000));
        eq("physical 2.1.0.0", "2.1.0.0", SignalFormat.physical(0x2100));
        eq("physical none", null, SignalFormat.physical(0));
        eq("dv bright", SignalText.DV_BRIGHT, SignalFormat.dvKey(0));
        eq("dv dark", SignalText.DV_DARK, SignalFormat.dvKey(1));
        eq("dv bad", null, SignalFormat.dvKey(7));
        eq("sound format 1", SignalFormat.FMT_PCM, SignalFormat.soundFormatToken(1));
        eq("sound format 2", SignalFormat.FMT_PASS, SignalFormat.soundFormatToken(2));
        eq("sound format bad", null, SignalFormat.soundFormatToken(9));
        eq("aspect 1080p", "16:9", SignalFormat.aspectOf(1920, 1080));
        eq("aspect 2160p", "16:9", SignalFormat.aspectOf(3840, 2160));
        eq("aspect 720p", "16:9", SignalFormat.aspectOf(1280, 720));
        eq("aspect 1440x1080", "4:3", SignalFormat.aspectOf(1440, 1080));
        eq("aspect 1024x768", "4:3", SignalFormat.aspectOf(1024, 768));
        eq("aspect 2560x1080", "21:9", SignalFormat.aspectOf(2560, 1080));
        eq("aspect 3440x1440", "21:9", SignalFormat.aspectOf(3440, 1440));
        eq("aspect 1920x1200", "16:10", SignalFormat.aspectOf(1920, 1200));
        eq("aspect 1280x1024", "5:4", SignalFormat.aspectOf(1280, 1024));
        eq("aspect SD 720x480 is not told by its size", null, SignalFormat.aspectOf(720, 480));
        eq("aspect SD 720x576", null, SignalFormat.aspectOf(720, 576));
        eq("aspect odd size", null, SignalFormat.aspectOf(1000, 1000));
        eq("aspect setting 2 auto", SignalFormat.ASPECT_AUTO, SignalFormat.aspectSettingToken(2));
        eq("aspect setting 0", "4:3", SignalFormat.aspectSettingToken(0));
        eq("aspect setting 5", "21:9", SignalFormat.aspectSettingToken(5));
        eq("aspect setting 3 unknown", null, SignalFormat.aspectSettingToken(3));
        for (int h : new int[] {0, 1, 2, 4, 5, 6}) ok("hdr kind named exactly " + h, !SignalText.HDR_OTHER.equals(SignalFormat.hdrKey(h)));
        eq("hdr 1", SignalText.HDR_HDR10, SignalFormat.hdrKey(1));
        eq("hdr 4", SignalText.HDR_HLG, SignalFormat.hdrKey(4));
        eq("hdr 6", SignalText.HDR_VIVID, SignalFormat.hdrKey(6));
        eq("edid 0", "1.4", SignalFormat.edidToken(0));
        eq("edid 1", "2.0", SignalFormat.edidToken(1));
        eq("edid 2", "auto", SignalFormat.edidToken(2));
        eq("edid 3", "2.1", SignalFormat.edidToken(3));
        eq("edid bad", null, SignalFormat.edidToken(7));
        eq("range 0", "auto", SignalFormat.rangeToken(0));
        eq("range 2", "limited", SignalFormat.rangeToken(2));
        eq("range bad", null, SignalFormat.rangeToken(-1));
        eq("hdr 2", SignalText.HDR_DOLBY, SignalFormat.hdrKey(2));
        eq("hdr 5", SignalText.HDR_HDR10_PLUS, SignalFormat.hdrKey(5));
        eq("hdr unknown kind", SignalText.HDR_OTHER, SignalFormat.hdrKey(3));
        eq("hdr negative", null, SignalFormat.hdrKey(-1));
        ok("sdr is not hdr", !SignalFormat.isHdr(0));
        ok("hdr10 is hdr", SignalFormat.isHdr(1));
        ok("plausible 4k", SignalFormat.plausible(3840, 2160, 2397));
        ok("all zero is no signal", !SignalFormat.plausible(0, 0, 0));
        eq("out earc", SignalText.OUT_EARC, SignalFormat.outKey(SignalFormat.OUT_EARC));
        eq("out unknown", SignalText.OUT_OTHER, SignalFormat.outKey("x"));
    }

    static void judder() {
        eq("24p on 60 Hz", Judder.JUDDER, Judder.of(2397, 6000));
        eq("24p on 24 Hz", Judder.SMOOTH, Judder.of(2397, 2400));
        eq("24p on 120 Hz", Judder.SMOOTH, Judder.of(2397, 12000));
        eq("59.94 on 60", Judder.SMOOTH, Judder.of(5994, 6000));
        eq("29.97 on 60", Judder.SMOOTH, Judder.of(2997, 6000));
        eq("50 on 60", Judder.JUDDER, Judder.of(5000, 6000));
        eq("120 on 60", Judder.JUDDER, Judder.of(12000, 6000));
        eq("25 on 50", Judder.SMOOTH, Judder.of(2500, 5000));
        eq("no source", Judder.UNKNOWN, Judder.of(0, 6000));
        eq("no chip", Judder.UNKNOWN, Judder.of(2397, 0));
    }

    static void toggle() {
        PanelToggle t = new PanelToggle();
        eq("starts hidden", PanelMode.HIDDEN, t.mode());
        eq("back while hidden", PanelMode.HIDDEN, t.back());
        eq("press 1 card", PanelMode.COMPACT, t.press());
        eq("back on the card is not ours", PanelMode.COMPACT, t.back());
        eq("popup over keeps the card", PanelMode.COMPACT, t.popupOver());
        eq("signal change keeps the card", PanelMode.COMPACT, t.signalChanged(true, true));
        eq("press 2 full panel", PanelMode.DETAIL, t.press());
        eq("press 3 hidden", PanelMode.HIDDEN, t.press());
        t.press();
        t.press();
        eq("back closes the full panel", PanelMode.HIDDEN, t.back());
        eq("option off (the default): no popup", PanelMode.HIDDEN, t.signalChanged(false, true));
        eq("option on, apps or Home on screen: no popup", PanelMode.HIDDEN, t.signalChanged(true, false));
        eq("option on, HDMI on screen: popup", PanelMode.POPUP, t.signalChanged(true, true));
        eq("popup ends", PanelMode.HIDDEN, t.popupOver());
        t.signalChanged(true, true);
        eq("press on the popup opens the full panel", PanelMode.DETAIL, t.press());
        eq("popup timer does not close the full panel", PanelMode.DETAIL, t.popupOver());
        eq("hide", PanelMode.HIDDEN, t.hide());
        ok("card look", PanelMode.POPUP.card() && PanelMode.COMPACT.card() && !PanelMode.DETAIL.card());
    }

    static void budget() {
        Set<SignalItem> all = EnumSet.noneOf(SignalItem.class);
        for (SignalItem i : SignalItem.values()) if (i.hal) all.add(i);
        for (PanelMode m : PanelMode.values()) {
            SignalBudget b = new SignalBudget(all);
            List<long[]> calls = new ArrayList<>();
            Map<SignalItem, Integer> count = new HashMap<>();
            for (long t = 0; t <= 60_000; t += 500) {
                for (SignalItem i : b.due(t, m, true)) {
                    calls.add(new long[] {t});
                    count.merge(i, 1, Integer::sum);
                    ok(m + ": only HAL items", i.hal);
                    ok(m + ": slow items only in the full panel", i.card() || m == PanelMode.DETAIL);
                }
            }
            if (m == PanelMode.HIDDEN) {
                eq("hidden: no call at all", 0, calls.size());
                continue;
            }
            int worst = 0;
            for (int s = 0; s < calls.size(); s++) {
                int n = 0;
                for (long[] c : calls) if (c[0] >= calls.get(s)[0] && c[0] < calls.get(s)[0] + 1000) n++;
                worst = Math.max(worst, n);
            }
            ok(m + ": at most 2 calls in any second (worst " + worst + ")", worst <= SignalBudget.MAX_PER_SECOND);
            for (SignalItem i : all) {
                if (!i.card() && m != PanelMode.DETAIL) continue;
                ok(m + ": " + i + " read", count.getOrDefault(i, 0) > 0);
            }
        }
        SignalBudget b = new SignalBudget(EnumSet.of(SignalItem.HDMI_INFO));
        eq("first tick reads it", 1, b.due(0, PanelMode.COMPACT, true).size());
        eq("not again within 2 s", 0, b.due(1500, PanelMode.COMPACT, true).size());
        eq("again after 2 s", 1, b.due(2000, PanelMode.COMPACT, true).size());
        b.reset();
        eq("reset reads at once", 1, b.due(2100, PanelMode.COMPACT, true).size());
        eq("apps on screen: HDMI items wait", 0, b.due(9000, PanelMode.COMPACT, false).size());
        SignalBudget t2 = new SignalBudget(EnumSet.of(SignalItem.TEMP_SYSTEM, SignalItem.PORT_HW));
        eq("apps on screen: a projector item is read", List.of(SignalItem.TEMP_SYSTEM), t2.due(0, PanelMode.DETAIL, false));
        eq("HDMI: the port once", List.of(SignalItem.PORT_HW), t2.due(5000, PanelMode.DETAIL, true));
        eq("the port not again", 0, (int) t2.due(60_000, PanelMode.DETAIL, true).stream().filter(i -> i == SignalItem.PORT_HW).count());
        t2.reset();
        eq("the port again after a reset (new input)", 1, (int) t2.due(61_000, PanelMode.DETAIL, true).stream()
                .filter(i -> i == SignalItem.PORT_HW).count());
        eq("not a model item: never", 0, new SignalBudget(EnumSet.of(SignalItem.HDR)).due(0, PanelMode.DETAIL, true).stream()
                .filter(i -> i != SignalItem.HDR).count() > 0 ? 1 : 0);
    }

    static SignalValues full() {
        SignalValues v = new SignalValues();
        v.port = 1;
        v.width = 3840; v.height = 2160; v.rateCenti = 2397; v.interlaced = false; v.dvi = false;
        v.hdrType = 2; v.chipWidth = 3840; v.chipHeight = 2160; v.chipRateCenti = 2400;
        v.range = "auto"; v.edid = "2.1"; v.allm = false; v.vrr = false; v.game = false;
        v.freesync = false; v.imax = false; v.filmmaker = false; v.cec = true; v.arc = true; v.physicalAddress = 0x1000;
        v.dvMode = 1; v.hgig = false; v.toneMap = true; v.tempLight = 52; v.tempSystem = 41;
        v.soundFormat = SignalFormat.FMT_PASS; v.aspectSetting = SignalFormat.ASPECT_AUTO;
        v.screenWidth = 1920; v.screenHeight = 1080; v.screenRateCenti = 6000; v.audioOut = SignalFormat.OUT_EARC;
        return v;
    }

    static Set<SignalItem> allItems() { return EnumSet.allOf(SignalItem.class); }

    static List<String> keys(SignalSheet s) {
        List<String> out = new ArrayList<>();
        for (SignalSheet.Row r : s.valueRows()) out.add(r.key);
        return out;
    }

    static void sheet() {
        SignalSheet s = SignalSheet.build(full(), allItems(), WORDS);
        eq("port", "port_hdmi|1", s.port);
        eq("big: size @ rate on one line", "3840\u00d72160 @ hz|23,97", s.big);
        eq("chips", "[*hdr_dolby, edid_21]", s.chips.toString());
        eq("smooth verdict", Judder.SMOOTH, s.judder);
        eq("verdict text", "verdict_smooth", s.verdict);
        eq("sound", "sound_line|out_earc", s.sound);
        eq("card rows in the owner's order", "[r_aspect = 16:9 (aspect_setting|aspect_auto), r_range = range_auto, "
                + "r_hdr = hdr_dolby]", s.cardRows.toString());
        eq("full panel rows (the owner's order: aspect, range, HDR, then the rest)", "[r_input, r_link, r_aspect, r_range, r_hdr, "
                + "r_imax, r_filmmaker, r_edid, r_port, r_allm, r_vrr, "
                + "r_freesync, r_chip, r_motion, r_game_mode, r_hgig, r_tone_map, r_dv_mode, r_temp_light, r_temp_system, "
                + "r_out, r_sound_format]", keys(s).toString());
        eq("input row: size @ rate, scan small", "r_input = 3840\u00d72160 @ hz|23,97 (progressive)", s.valueRows().get(0).toString());
        String chipRow = null, portRow = null;
        for (SignalSheet.Row r : s.valueRows()) {
            if (SignalText.R_CHIP.equals(r.key)) chipRow = r.value;
            if (SignalText.R_PORT.equals(r.key)) portRow = r.toString();
        }
        eq("chip row: size @ rate", "3840\u00d72160 @ hz|24", chipRow);
        eq("port row", "r_port = port_cec, port_arc (1.0.0.0)", portRow);
        int sections = 0;
        for (SignalSheet.Row r : s.rows) if (r.section) sections++;
        eq("three sections", 3, sections);
        SignalValues v = full();
        v.chipRateCenti = 6000;
        SignalSheet j = SignalSheet.build(v, allItems(), WORDS);
        eq("judder verdict", "verdict_judder|60|23,97", j.verdict);
        RowTone motion = null;
        for (SignalSheet.Row r : j.rows) if (SignalText.R_MOTION.equals(r.key)) motion = r.tone;
        eq("judder row warns", RowTone.WARN, motion);
        v = full();
        v.allm = true; v.vrr = true;
        s = SignalSheet.build(v, allItems(), WORDS);
        eq("game chips", "[*hdr_dolby, edid_21, ALLM, VRR]", s.chips.toString());
        v.freesync = true; v.imax = true; v.filmmaker = true;
        eq("every badge", "[*hdr_dolby, edid_21, IMAX Enhanced, Filmmaker Mode, ALLM, VRR, FreeSync]",
                SignalSheet.build(v, allItems(), WORDS).chips.toString());
        v = full();
        v.hdrType = 1;
        ok("Dolby Vision mode only with Dolby Vision", !keys(SignalSheet.build(v, allItems(), WORDS)).contains("r_dv_mode"));
        v = full();
        v.interlaced = true; v.width = 1920; v.height = 1080; v.rateCenti = 5000;
        eq("interlaced big", "1920\u00d71080i @ hz|50", SignalSheet.build(v, allItems(), WORDS).big);
        v = full();
        v.width = 720; v.height = 576; v.rateCenti = 5000; v.aspectSetting = "4:3";
        eq("SD: the setting alone", "[r_aspect = 4:3, r_range = range_auto, r_hdr = hdr_dolby]",
                SignalSheet.build(v, allItems(), WORDS).cardRows.toString());
        v.aspectSetting = null;
        eq("SD without the setting: no aspect line", "[r_range = range_auto, r_hdr = hdr_dolby]",
                SignalSheet.build(v, allItems(), WORDS).cardRows.toString());
        v = full();
        v.edid = "auto"; v.hdrType = 0;
        eq("SDR chip plain, auto mode no chip", "[hdr_sdr]", SignalSheet.build(v, allItems(), WORDS).chips.toString());
        v = new SignalValues();
        v.port = 2;
        s = SignalSheet.build(v, allItems(), WORDS);
        eq("no signal", "no_signal", s.big);
        eq("no signal row only", "[r_input]", keys(s).toString());
        eq("no verdict without a signal", null, s.verdict);
        v = full();
        v.port = 0;
        s = SignalSheet.build(v, allItems(), WORDS);
        eq("apps port", "port_apps", s.port);
        eq("apps big = Android screen, the live bug '1920x108060' cannot come back", "1920\u00d71080 @ hz|60", s.big);
        eq("apps rows", "[r_source, r_screen, r_chip, r_game_mode, r_hgig, r_tone_map, r_temp_light, r_temp_system, r_out, "
                + "r_sound_format]", keys(s).toString());
        eq("apps: no HDMI chips", 0, s.chips.size());
        eq("apps: no verdict", null, s.verdict);
        eq("apps: no card lines", 0, s.cardRows.size());
    }

    /** Each item off: exactly its rows go, the rest stays (the model hides what it cannot read). */
    static void hiding() {
        Map<SignalItem, List<String>> own = new HashMap<>();
        own.put(SignalItem.HDMI_INFO, List.of("r_input", "r_link", "r_aspect", "r_motion"));
        own.put(SignalItem.HDR, List.of("r_hdr"));
        own.put(SignalItem.CHIP, List.of());       // CHIP_RATE still gives the chip row
        own.put(SignalItem.CHIP_RATE, List.of());
        own.put(SignalItem.RANGE, List.of("r_range"));
        own.put(SignalItem.ASPECT, List.of());     // the size still tells 16:9
        own.put(SignalItem.EDID, List.of("r_edid"));
        own.put(SignalItem.ALLM, List.of("r_allm"));
        own.put(SignalItem.VRR, List.of("r_vrr"));
        own.put(SignalItem.FREESYNC, List.of("r_freesync"));
        own.put(SignalItem.IMAX, List.of("r_imax"));
        own.put(SignalItem.FILMMAKER, List.of("r_filmmaker"));
        own.put(SignalItem.PORT_HW, List.of("r_port"));
        own.put(SignalItem.DV_MODE, List.of("r_dv_mode"));
        own.put(SignalItem.HGIG, List.of("r_hgig"));
        own.put(SignalItem.TONE_MAP, List.of("r_tone_map"));
        own.put(SignalItem.TEMP_LIGHT, List.of("r_temp_light"));
        own.put(SignalItem.TEMP_SYSTEM, List.of("r_temp_system"));
        own.put(SignalItem.SOUND_FORMAT, List.of("r_sound_format"));
        own.put(SignalItem.GAME, List.of("r_game_mode"));
        own.put(SignalItem.SCREEN, List.of());
        own.put(SignalItem.AUDIO_OUT, List.of("r_out"));
        own.put(SignalItem.HDMI_FORMAT, List.of());   // no receiver report in full(): its rows are in HdmiRxTest
        own.put(SignalItem.PICTURE_MODE, List.of());  // WORDS has no menu words: those rows are in menuRows()
        own.put(SignalItem.MEMC, List.of());
        List<String> base = keys(SignalSheet.build(full(), allItems(), WORDS));
        for (SignalItem i : SignalItem.values()) {
            Set<SignalItem> items = allItems();
            items.remove(i);
            List<String> want = new ArrayList<>(base);
            want.removeAll(own.get(i));
            eq("without " + i, want.toString(), keys(SignalSheet.build(full(), items, WORDS)).toString());
        }
        Set<SignalItem> noChip = allItems();
        noChip.remove(SignalItem.CHIP);
        noChip.remove(SignalItem.CHIP_RATE);
        SignalSheet s = SignalSheet.build(full(), noChip, WORDS);
        ok("no chip read: no chip, no motion, no verdict", !keys(s).contains("r_chip") && !keys(s).contains("r_motion")
                && s.verdict == null);
        SignalValues v = full();
        v.hdrType = null; v.range = null; v.edid = null; v.allm = null; v.vrr = null; v.game = null; v.audioOut = null;
        v.dvi = null; v.freesync = null; v.imax = null; v.filmmaker = null; v.cec = null; v.dvMode = null; v.hgig = null;
        v.toneMap = null; v.tempLight = null; v.tempSystem = null; v.soundFormat = null; v.aspectSetting = null;
        eq("values not read: rows hidden", "[r_input, r_aspect, r_chip, r_motion]",
                keys(SignalSheet.build(v, allItems(), WORDS)).toString());
        v = full();
        v.width = 0;
        SignalValues fresh = new SignalValues();
        fresh.width = 1920; fresh.height = 1080; fresh.rateCenti = 6000;
        v.merge(fresh, List.of(SignalItem.HDR), 1);
        eq("merge takes only the items read", 0, v.width);
        v.merge(fresh, List.of(SignalItem.HDMI_INFO), 2);
        eq("merge input", 1920, v.width);
        ok("changed: new size", SignalValues.changed(full(), v));
        ok("changed: same", !SignalValues.changed(full(), full()));
    }

    /** WORDS plus the menu's words: row "M:feature", option "feature=token" ("x" has no word in the menu). */
    static final SignalText.Words MENU = new SignalText.Words() {
        @Override public String get(String key, Object... args) { return WORDS.get(key, args); }

        @Override public char decimal() { return ','; }

        @Override public String menuTitle(String feature) { return "M:" + feature; }

        @Override public String menuOption(String feature, String token) {
            return "x".equals(token) ? null : feature + "=" + token;
        }
    };

    /** Picture mode and motion smoothing (2026-11-19): the menu's reads and words, the card's last line. */
    static void menuRows() {
        SignalValues v = full();
        v.pictureMode = "standard";
        v.memc = "low";
        SignalSheet s = SignalSheet.build(v, allItems(), MENU);
        eq("card: picture mode last, after HDR (the stock RS30 card's order)", "[r_aspect = 16:9 (aspect_setting|aspect_auto), "
                + "r_range = range_auto, r_hdr = hdr_dolby, M:picture.mode = picture.mode=standard]", s.cardRows.toString());
        List<String> k = keys(s);
        int sec = -1;
        for (int i = 0; i < s.rows.size(); i++) if (s.rows.get(i).section && SignalText.SEC_PROJECTOR.equals(s.rows.get(i).key)) sec = i;
        eq("full panel: the projector section starts with picture mode and MEMC",
                "[[sec_projector], M:picture.mode = picture.mode=standard, M:picture.memc = picture.memc=low]",
                s.rows.subList(sec, sec + 3).toString());
        ok("no picture mode among the input rows", k.indexOf("picture_mode") > k.indexOf("r_freesync"));
        eq("without menu words: no rows (never a raw token)", false, keys(SignalSheet.build(v, allItems(), WORDS)).contains("picture_mode"));
        v.pictureMode = "x";
        v.memc = null;
        s = SignalSheet.build(v, allItems(), MENU);
        ok("a token the menu cannot name or a value not read: no row", !keys(s).contains("picture_mode") && !keys(s).contains("memc"));
        v = full();
        v.pictureMode = "ai";
        v.memc = "high";
        Set<SignalItem> items = allItems();
        items.remove(SignalItem.MEMC);
        s = SignalSheet.build(v, items, MENU);
        ok("a model without the MEMC item: picture mode only", keys(s).contains("picture_mode") && !keys(s).contains("memc"));
        v.port = 0;
        s = SignalSheet.build(v, allItems(), MENU);
        eq("over apps too: the card line and both rows", "[M:picture.mode = picture.mode=ai]", s.cardRows.toString());
        ok("apps: both rows in the projector section", keys(s).contains("picture_mode") && keys(s).contains("memc"));
        ok("picture mode on the card, MEMC only in the full panel", SignalItem.PICTURE_MODE.card() && !SignalItem.MEMC.card()
                && !SignalItem.PICTURE_MODE.hdmiOnly && SignalItem.PICTURE_MODE.hal && SignalItem.MEMC.hal);
        SignalValues fresh = new SignalValues();
        fresh.pictureMode = "sport";
        v.merge(fresh, List.of(SignalItem.PICTURE_MODE), 1);
        eq("merge picture mode", "sport", v.pictureMode);
        eq("merge leaves MEMC", "high", v.memc);
    }

    /** One read of the input: w x h at centi (VRR on or off), the shown rate back. */
    static int read(SignalValues v, int w, int h, int centi, Boolean vrr, long at) {
        SignalValues f = new SignalValues();
        f.width = w; f.height = h; f.rateCenti = centi; f.vrr = vrr;
        v.merge(f, List.of(SignalItem.VRR, SignalItem.HDMI_INFO), at);
        return v.rateCenti;
    }

    static String big(SignalValues v) {
        v.port = 1;
        return SignalSheet.build(v, EnumSet.of(SignalItem.HDMI_INFO), WORDS).big;
    }

    /** SteadyRate: the tester's 133 Hz in game mode (Z9X HB, 2026-11-19) and the rules around it. */
    static void steady() {
        eq("nominal 60", 6000, SteadyRate.nominal(6000));
        eq("nominal 59.94 measured 5995", 5994, SteadyRate.nominal(5995));
        eq("nominal 23.976", 2397, SteadyRate.nominal(2398));
        eq("nominal 120 measured 12030", 12000, SteadyRate.nominal(12030));
        eq("133 Hz is no table rate", 0, SteadyRate.nominal(13300));
        eq("133.4 Hz is no table rate", 0, SteadyRate.nominal(13342));
        eq("300 Hz is no table rate", 0, SteadyRate.nominal(30000));
        eq("20 Hz is no table rate", 0, SteadyRate.nominal(2000));
        // the bad sample: 60 Hz steady, one read of 133 Hz, 60 Hz again: 60 all along
        SignalValues v = new SignalValues();
        eq("first read 60: shown at once", 6000, read(v, 3840, 2160, 6000, false, 1));
        eq("133 Hz once: 60 stays", 6000, read(v, 3840, 2160, 13300, false, 2));
        ok("133 Hz once: a confirming read is asked", v.ratePending());
        eq("card line keeps 60", "3840×2160 @ hz|60", big(v));
        eq("60 again", 6000, read(v, 3840, 2160, 6000, false, 3));
        ok("60 again: nothing pending", !v.ratePending());
        // the same during the mode switch to 1080p 120 (size changed): size alone until two reads agree
        eq("switch: 133 Hz with the new size: no rate", 0, read(v, 1920, 1080, 13342, false, 4));
        eq("switch: size alone", "1920×1080", big(v));
        eq("switch: 120 once: not yet", 0, read(v, 1920, 1080, 12000, false, 5));
        eq("switch: 120 twice: 120", 12000, read(v, 1920, 1080, 12000, false, 6));
        eq("switch: card line", "1920×1080 @ hz|120", big(v));
        // a new table rate at the same size: the old one stays until confirmed
        eq("50 once at the same size: 120 stays", 12000, read(v, 1920, 1080, 5000, false, 7));
        eq("50 twice: 50", 5000, read(v, 1920, 1080, 5000, false, 8));
        // 133 Hz three times in a row (no VRR): a PC timing the table lacks shows after three reads
        SignalValues pc = new SignalValues();
        eq("pc first 133: nothing", 0, read(pc, 1920, 1080, 13300, false, 1));
        eq("pc second 133: nothing", 0, read(pc, 1920, 1080, 13300, false, 2));
        eq("pc third 133: shown", 13300, read(pc, 1920, 1080, 13300, false, 3));
        // VRR: moving reads say VRR, a rate that holds for two reads shows
        SignalValues g = new SignalValues();
        eq("vrr first read: no number", 0, read(g, 1920, 1080, 11988, true, 1));
        ok("vrr first read: VRR", g.vrrMoving);
        eq("vrr card line", "1920×1080 @ VRR", big(g));
        eq("vrr 119.88 twice: shown", 11988, read(g, 1920, 1080, 11988, true, 2));
        ok("vrr held: no VRR text", !g.vrrMoving);
        eq("vrr 133 once: VRR", 0, read(g, 1920, 1080, 13300, true, 3));
        ok("vrr 133 once: moving", g.vrrMoving);
        eq("vrr 97 once: still VRR", 0, read(g, 1920, 1080, 9700, true, 4));
        eq("vrr 133 twice (no table rate): still VRR", 0, read(g, 1920, 1080, 13300, true, 5));
        eq("vrr 120 twice: 120", 12000, read(g, 1920, 1080, 12000, true, 7) == 0 ? read(g, 1920, 1080, 12000, true, 8) : -1);
        // no signal resets; after it a new signal waits for two reads (mid-session switch)
        eq("no signal", 0, read(v, 0, 0, 0, false, 9));
        ok("no signal: no input", !v.hasInput());
        eq("back at 60 once: not yet", 0, read(v, 3840, 2160, 6000, false, 10));
        ok("back at 60 once: input shown (size alone)", v.hasInput() && "3840×2160".equals(big(v)));
        eq("back at 60 twice: 60", 6000, read(v, 3840, 2160, 6000, false, 11));
        // the budget asks the confirming read sooner than the card period
        SignalBudget b = new SignalBudget(EnumSet.of(SignalItem.HDMI_INFO));
        eq("budget: first read", List.of(SignalItem.HDMI_INFO), b.due(0, PanelMode.COMPACT, true));
        b.soon(SignalItem.HDMI_INFO, 100, SteadyRate.CONFIRM_MS);
        ok("budget: not due before the confirm time", b.due(100 + SteadyRate.CONFIRM_MS - 1, PanelMode.COMPACT, true).isEmpty());
        eq("budget: due at the confirm time", List.of(SignalItem.HDMI_INFO),
                b.due(100 + SteadyRate.CONFIRM_MS, PanelMode.COMPACT, true));
    }

    static void items() {
        Set<SignalItem> m = SignalItems.parse("# c\nitem\tcall\tnote\nhdmi_info\tIGmpf 662\tx\nhdr\tIGmpf 620\nbogus\tx\n"
                + "screen\tnone\n\n");
        eq("parse: HAL items only, unknown skipped", EnumSet.of(SignalItem.HDMI_INFO, SignalItem.HDR), m);
        eq("parse null", EnumSet.noneOf(SignalItem.class), SignalItems.parse(null));
        Set<SignalItem> shown = SignalItems.shown(m, EnumSet.of(SignalItem.HDMI_INFO, SignalItem.CHIP));
        eq("shown = model and reader, plus Android", EnumSet.of(SignalItem.HDMI_INFO, SignalItem.SCREEN,
                SignalItem.AUDIO_OUT, SignalItem.SOUND_FORMAT), shown);
        for (SignalItem i : SignalItem.values()) eq("token round trip " + i, i, SignalItem.of(i.token));
    }

    static void look() {
        org.lumen.ui.Palette lp = org.lumen.ui.Palette.of(false), dp = org.lumen.ui.Palette.of(true);
        SignalLook l = SignalLook.of(lp), d = SignalLook.of(dp);
        ok("Lumen dark: dark", d.dark);
        ok("Lumen light: light", !l.dark);
        eq("ink = the palette's panel text", lp.ptext1, l.ink);
        eq("accent = the palette's accent", dp.accent, d.accent);
        eq("card = the palette's panel, a little see-through", (SignalLook.CARD_ALPHA << 24) | (lp.panel & 0xFFFFFF), l.card);
        eq("tone ok", d.ok, d.tone(RowTone.OK));
        eq("tone plain", d.ink, d.tone(RowTone.PLAIN));
        ok("no old blue accent", l.accent != 0xFF3B78E7 && d.accent != 0xFF3B78E7);
    }

    /** The live bug of 2026-11-18: the card covered Lumen Home's header. Title safe, below the header, smaller. */
    static void place() {
        int[] card = SignalPlace.card(260);
        ok("card in the title safe area", SignalPlace.safe(card));
        ok("card below the Home header", card[1] >= SignalPlace.HEADER_BOTTOM + 16);
        ok("card on the right", card[2] == SignalPlace.SCREEN_W - SignalPlace.SAFE_X);
        ok("card smaller than the first one (600 px)", SignalPlace.CARD_W <= 460);
        ok("sheet in the title safe area", SignalPlace.safe(SignalPlace.sheet()));
        ok("sheet smaller than the first one (700 px)", SignalPlace.SHEET_W <= 580);
        eq("sheet height at 1080", 972, SignalPlace.sheetHeight(1080, 1f));
        eq("sheet height at 2160", 1944, SignalPlace.sheetHeight(2160, 2f));
        ok("a too tall card leaves the safe area", !SignalPlace.safe(SignalPlace.card(1000)));
    }
}
