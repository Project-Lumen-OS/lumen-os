// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import java.util.Arrays;
import java.util.List;

/** Host tests of the Lumen game menu logic (GmModel). sh test/gamemenu/run.sh */
public final class GmModelTest {
    private static int pass, fail;

    private static void ok(boolean c, String what) {
        if (c) pass++; else { fail++; System.out.println("FAIL " + what); }
    }

    private static void eq(Object a, Object b, String what) {
        ok(a == null ? b == null : a.equals(b), what + " (got " + a + ", want " + b + ")");
    }

    private static String arr(int[] a) { return Arrays.toString(a); }

    private static GmModel.Caps allCaps() {
        GmModel.Caps c = new GmModel.Caps();
        c.onRead(GmModel.VRR, 0); c.onRead(GmModel.PIC, 0); c.onRead(GmModel.DARK, 0); c.onRead(GmModel.SND, 190);
        return c;
    }

    private static GmModel.State state(int lat, int vrr, int hfr, int pic, int dark, int sr, int ar, int snd) {
        GmModel.State s = new GmModel.State();
        s.set(GmModel.LAT, lat); s.set(GmModel.VRR, vrr); s.set(GmModel.HFR, hfr); s.set(GmModel.PIC, pic);
        s.set(GmModel.DARK, dark); s.set(GmModel.SR, sr); s.set(GmModel.AR, ar); s.set(GmModel.SND, snd);
        s.width = 3840; s.height = 2160;
        return s;
    }

    public static void main(String[] a) {
        items();
        caps();
        rawValues();
        rules();
        presets();
        focus();
        aim();
        shift();
        theme();
        frames();
        timers();
        System.out.println("GmModelTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    /**
     * Stock screen shift (xhplayer SCREEN_OFFSET / ScreenOffsetDialog): shown only with the aspect item, refused on
     * picking unless the aspect is 21:9 / 32:9, the band in UI lines from the panel area (vOffset = 1080f / disp_height),
     * 3 lines per key kept on screen, the panel line sent = (int) (top / vOffset).
     */
    static void shift() {
        GmModel.setWideWire(java.util.Arrays.asList(5, 6));
        GmModel.Caps c = new GmModel.Caps();
        c.allow(java.util.Arrays.asList(GmModel.AR, GmModel.SO));
        c.gate(java.util.Arrays.asList(GmModel.AR));
        ok(!c.visible(GmModel.SO) && !c.visible(GmModel.AR), "so: hidden while the aspect item is not proven");
        c.onRead(GmModel.AR, 5);
        ok(c.visible(GmModel.SO), "so: shown with the aspect item");
        GmModel.Caps noAr = new GmModel.Caps();
        noAr.allow(java.util.Arrays.asList(GmModel.SO));
        ok(!noAr.visible(GmModel.SO), "so: never without the aspect item in the profile");
        eq(GmModel.visibleOrder(c), java.util.Arrays.asList(GmModel.AR, GmModel.SO), "so: right after the aspect tile");
        eq(GmModel.count(GmModel.SO), 0, "so: no option list");
        GmModel.State s = new GmModel.State();
        for (int[] ar : new int[][] {{GmModel.BAD, GmModel.R_SO_RATIO}, {0, GmModel.R_SO_RATIO}, {1, GmModel.R_SO_RATIO},
                {2, GmModel.OK}, {3, GmModel.OK}}) {
            s.set(GmModel.AR, ar[0]);
            eq(GmModel.popupRefused(GmModel.SO, s), ar[1], "so refusal with aspect " + ar[0]);
        }
        int[] band = GmModel.shiftBand(new int[] {0, 257, 3840, 1646, 2160});
        eq(band[0] + "," + band[1], "128,823", "so: 21:9 on the 2160-line panel, band top 128 height 823 (stock casts)");
        int[] one = GmModel.shiftBand(new int[] {0, 129, 1920, 823, 0});
        eq(one[0] + "," + one[1], "129,823", "so: unknown panel lines, vOffset 1 (stock start value)");
        eq(GmModel.shiftNudge(128, 823, GmModel.UP), 125, "so: UP 3 lines");
        eq(GmModel.shiftNudge(128, 823, GmModel.DOWN), 131, "so: DOWN 3 lines");
        eq(GmModel.shiftNudge(2, 823, GmModel.UP), 0, "so: top kept at 0");
        eq(GmModel.shiftNudge(256, 823, GmModel.DOWN), 257, "so: bottom kept on screen (1080 - 823)");
        eq(GmModel.shiftNudge(257, 823, GmModel.DOWN), 257, "so: no move past the bottom");
        eq(GmModel.shiftNudge(128, 823, GmModel.LEFT), 128, "so: LEFT moves nothing");
        eq(GmModel.shiftWire(131, 2160), 262, "so: panel line (int) (131 / 0.5)");
        eq(GmModel.shiftWire(125, 1080), 125, "so: panel line on a 1080-line panel");
        eq(GmModel.shiftWire(101, 2000), 187, "so: panel line truncated as stock (101 / 0.54)");
    }

    static void items() {
        eq(GmModel.ORDER.length, 14, "14 tiles (v3: colour space and HDR of the HB bar; X50 stock FreeSync, screen shift)");
        eq(String.join(",", GmModel.ORDER), "lat,vrr,hfr,fs,pic,cs,dark,sr,ar,so,hdr,snd,aim,fps", "dock order of the prototype, FreeSync in Speed, screen shift after the aspect");
        eq(GmModel.GROUPS.length, 4, "4 groups");
        int n = 0;
        StringBuilder sb = new StringBuilder();
        for (String[] g : GmModel.GROUPS) { n += g.length; for (String k : g) sb.append(k).append(','); }
        eq(n, 14, "groups hold the 14 tiles");
        eq(sb.toString(), "lat,vrr,hfr,fs,pic,cs,dark,sr,ar,so,hdr,snd,aim,fps,", "groups in dock order");
        eq(GmModel.count(GmModel.PIC), 5, "5 game pictures");
        eq(GmModel.count(GmModel.SND), 3, "3 game sounds");
        eq(GmModel.count(GmModel.FPS), 4, "frame counter off/left/centre/right");
        ok(GmModel.isPerf(GmModel.LAT, 1) && GmModel.isPerf(GmModel.VRR, 1) && GmModel.isPerf(GmModel.HFR, 1), "turbo/VRR/HFR yellow");
        ok(!GmModel.isPerf(GmModel.LAT, 0) && !GmModel.isPerf(GmModel.PIC, 1) && !GmModel.isPerf(GmModel.SR, 1), "others not yellow");
        eq(String.join(",", GmModel.GATED), "vrr,pic,dark,snd,cs,hdr,fs", "the new vendor calls are gated");
        // colour space: all five by default (HB bar, stock COLOR_SPACE), the profile's list where stock has fewer (X50 V2)
        eq(GmModel.options(GmModel.CS).toString(), "[0, 1, 2, 3, 4]", "colour space: all five by default");
        GmModel.setCsOptions(java.util.Arrays.asList("native", "bt2020", "p3", "bt709"));
        eq(GmModel.options(GmModel.CS).toString(), "[0, 1, 3, 4]", "X50 COLOR_SPACE_V2: native, BT.2020, DCI-P3, Rec.709");
        ok(!GmModel.optionOffered(GmModel.CS, 2), "X50: Adobe RGB not offered");
        eq(GmModel.refuse(GmModel.CS, 2, new GmModel.State()), GmModel.R_NOT_OFFERED, "X50: Adobe RGB refused");
        eq(GmModel.refuse(GmModel.CS, 3, new GmModel.State()), GmModel.OK, "X50: DCI-P3 allowed (no stock refusal)");
        GmModel.setCsOptions(null);
        eq(GmModel.options(GmModel.CS).toString(), "[0, 1, 2, 3, 4]", "colour space: null = all five");
        boolean threw = false;
        try { GmModel.setCsOptions(java.util.Arrays.asList("cmyk")); } catch (IllegalArgumentException e) { threw = true; }
        ok(threw, "a colour space list without a known value is refused");
        eq(GmModel.options(GmModel.FS).toString(), "[0, 1]", "FreeSync off / on");
        eq(GmModel.plain(GmModel.FS, 1), 1, "173 true = on");
        eq(GmModel.plain(GmModel.FS, 2), GmModel.BAD, "FreeSync 2 bad");
        eq(GmModel.plain(GmModel.CS, 5), GmModel.BAD, "colour space 5 bad");
        for (String k : GmModel.PROVEN) ok(!GmModel.isGated(k), "proven item not gated: " + k);
        // aspect: only Auto and 16:9 until the wide wire values are known
        eq(GmModel.options(GmModel.AR).toString(), "[0, 1]", "aspect offers Auto and 16:9");
        eq(GmModel.wideCount(), 0, "21:9 / 32:9 hidden where the profile names no wire values (Z9X)");
        eq(GmModel.arWire(0), 2, "Auto = 665(2)");
        eq(GmModel.arWire(1), 1, "16:9 = 665(1)");
        eq(GmModel.arWire(2), -1, "21:9 not sent");
        eq(GmModel.arWire(3), -1, "32:9 not sent");
        eq(GmModel.arOf(2), 0, "664 2 = Auto");
        eq(GmModel.arOf(1), 1, "664 1 = 16:9");
        eq(GmModel.arOf(0), -1, "664 0 (4:3) not a menu value");
        eq(GmModel.arOf(4), -1, "664 4 (Original) not a menu value");
        eq(GmModel.options(GmModel.PIC).toString(), "[0, 1, 2, 3, 4]", "game pictures 0..4");
    }

    static void caps() {
        GmModel.Caps c = new GmModel.Caps();
        for (String k : GmModel.GATED) ok(!c.visible(k), "gated hidden before any read: " + k);
        for (String k : GmModel.PROVEN) ok(c.visible(k), "proven always visible: " + k);
        eq(GmModel.visibleOrder(c).toString(), "[lat, hfr, sr, ar, so, aim, fps]", "dock without the gated items");
        c.onRead(GmModel.VRR, 7);
        ok(!c.visible(GmModel.VRR), "VRR read 7 (outside the stock range) stays hidden");
        c.onRead(GmModel.VRR, -1);
        ok(c.visible(GmModel.VRR), "VRR read -1 (stock: off) shows the tile");
        c.onRead(GmModel.PIC, 5);
        ok(!c.visible(GmModel.PIC), "game picture 5 hidden");
        c.onRead(GmModel.PIC, 4);
        ok(c.visible(GmModel.PIC), "game picture 4 shown");
        c.onRead(GmModel.SND, 189);
        ok(!c.visible(GmModel.SND), "sound 189 hidden");
        c.onRead(GmModel.SND, 0);
        ok(c.visible(GmModel.SND), "sound 0 (stock: off) shown");
        c.onNoAnswer(GmModel.SND);
        ok(!c.visible(GmModel.SND), "no answer hides again");
        c.onRead(GmModel.SND, 191);
        ok(c.onReadBack(GmModel.SND, 2, 192), "sound read-back 192 = story ok");
        ok(c.visible(GmModel.SND), "still visible after a good read-back");
        ok(!c.onReadBack(GmModel.SND, 1, 192), "sound read-back mismatch");
        ok(!c.visible(GmModel.SND), "mismatch hides the item");
        eq(c.get(GmModel.SND), GmModel.CAP_FAILED, "mismatch = failed");
        c.onRead(GmModel.SND, 191);
        ok(!c.visible(GmModel.SND), "a failed item stays hidden even when the getter answers again");
        c.onNoAnswer(GmModel.SND);
        eq(c.get(GmModel.SND), GmModel.CAP_FAILED, "failed survives a later no-answer");
        ok(c.onReadBack(GmModel.SR, 1, 1), "proven item read-back ok");
        ok(!c.onReadBack(GmModel.SR, 1, 0), "proven item mismatch reported");
        ok(c.visible(GmModel.SR), "a proven item is never hidden by a mismatch");
        GmModel.Caps all = allCaps();
        eq(GmModel.visibleOrder(all).size(), 11, "all 11 after good reads");
        List<String[]> g = GmModel.visibleGroups(new GmModel.Caps());
        eq(g.size(), 3, "without the gated items the Sound group is empty and left out");
        StringBuilder sb = new StringBuilder();
        for (String[] r : g) sb.append(String.join("|", r)).append(' ');
        eq(sb.toString().trim(), "0|lat|hfr 1|sr|ar|so 3|aim|fps", "empty Sound group left out");
    }

    static void rawValues() {
        eq(GmModel.plain(GmModel.VRR, -1), 0, "300 -1 = off");
        eq(GmModel.plain(GmModel.VRR, 1), 1, "300 1 = on");
        eq(GmModel.plain(GmModel.VRR, 2), GmModel.BAD, "300 2 bad");
        eq(GmModel.plain(GmModel.PIC, -1), 0, "298 -1 = standard");
        eq(GmModel.plain(GmModel.PIC, 3), 3, "298 3 = RTS");
        eq(GmModel.plain(GmModel.PIC, -2), GmModel.BAD, "298 -2 bad");
        eq(GmModel.plain(GmModel.DARK, 1), 1, "296 true");
        eq(GmModel.plain(GmModel.DARK, 2), GmModel.BAD, "296 2 bad");
        eq(GmModel.plain(GmModel.SND, 0), 0, "1 -> 0 = off");
        eq(GmModel.plain(GmModel.SND, 190), 0, "190 off");
        eq(GmModel.plain(GmModel.SND, 191), 1, "191 surround");
        eq(GmModel.plain(GmModel.SND, 192), 2, "192 story");
        eq(GmModel.sndWire(0), 190, "off -> 190");
        eq(GmModel.sndWire(2), 192, "story -> 192");
        boolean threw = false;
        try { GmModel.sndWire(3); } catch (IllegalArgumentException e) { threw = true; }
        ok(threw, "sound 3 refused");
        eq(GmModel.plain(GmModel.SND, 193), GmModel.BAD, "193 not a value of a three-sound device");
        // X50 stock (xhplayer BaseConfigKt GAME_SOUND_EFFECT): also clarity 193 and balanced 194 (profile game_menu.sound)
        GmModel.setSounds(5);
        eq(GmModel.count(GmModel.SND), 5, "5 game sounds on a five-sound device");
        eq(GmModel.plain(GmModel.SND, 193), 3, "193 clarity");
        eq(GmModel.plain(GmModel.SND, 194), 4, "194 balanced");
        eq(GmModel.plain(GmModel.SND, 195), GmModel.BAD, "195 bad");
        eq(GmModel.plain(GmModel.SND, 189), GmModel.BAD, "189 bad");
        eq(GmModel.sndWire(3), 193, "clarity -> 193");
        eq(GmModel.sndWire(4), 194, "balanced -> 194");
        threw = false;
        try { GmModel.sndWire(5); } catch (IllegalArgumentException e) { threw = true; }
        ok(threw, "sound 5 refused");
        GmModel.Caps c5 = new GmModel.Caps();
        c5.onRead(GmModel.SND, 194);
        ok(c5.visible(GmModel.SND), "a unit left on 194 by stock keeps the tile");
        ok(c5.onReadBack(GmModel.SND, 3, 193), "read-back 193 = clarity ok");
        for (int bad : new int[] {2, 6}) {
            threw = false;
            try { GmModel.setSounds(bad); } catch (IllegalArgumentException e) { threw = true; }
            ok(threw, bad + " game sounds refused");
        }
        GmModel.setSounds(3);
        eq(GmModel.plain(GmModel.SND, 194), GmModel.BAD, "back to three: 194 bad");
        for (int o = 0; o <= 3; o++) eq(GmModel.option(GmModel.latOf(o), GmModel.hfrOf(o)), o, "option round trip " + o);
        eq(GmModel.option(1, 1), 2, "turbo + HFR = 2");
        eq(GmModel.option(1, 0), 3, "turbo = 3");
        eq(GmModel.option(0, 1), 1, "standard + HFR = 1");
        eq(GmModel.option(0, 0), 0, "standard = 0");
        eq(GmModel.latOf(4), GmModel.BAD, "647 4 bad");
        eq(GmModel.hfrOf(-1), GmModel.BAD, "647 -1 bad");
    }

    static void rules() {
        GmModel.State s = state(0, 0, 1, 0, 0, 0, 0, 0);
        eq(GmModel.refuse(GmModel.VRR, 1, s), GmModel.OK, "VRR on with Auto");
        s.set(GmModel.AR, 2);
        eq(GmModel.refuse(GmModel.VRR, 1, s), GmModel.R_VRR_WIDE, "VRR refused with 21:9");
        s.set(GmModel.AR, 3);
        eq(GmModel.refuse(GmModel.VRR, 1, s), GmModel.R_VRR_WIDE, "VRR refused with 32:9");
        eq(GmModel.refuse(GmModel.VRR, 0, s), GmModel.OK, "VRR off always allowed");
        s = state(0, 1, 1, 0, 0, 0, 0, 0);
        eq(GmModel.refuse(GmModel.AR, 1, s), GmModel.R_AR_VRR, "aspect refused while VRR on");
        eq(GmModel.refuse(GmModel.AR, 0, s), GmModel.R_AR_VRR, "the aspect item itself refused while VRR on (stock toast_close_vrr_retry)");
        eq(GmModel.refuse(GmModel.HFR, 0, s), GmModel.R_HFR_VRR, "HFR off refused while VRR on");
        eq(GmModel.refuse(GmModel.HFR, 1, s), GmModel.OK, "HFR on allowed while VRR on");
        s = state(0, 0, 0, 0, 0, 0, 0, 0);
        eq(GmModel.refuse(GmModel.HFR, 0, s), GmModel.OK, "HFR off allowed without VRR");
        eq(GmModel.refuse(GmModel.AR, 2, s), GmModel.R_NOT_OFFERED, "21:9 not offered on the Z9X yet");
        eq(GmModel.refuse(GmModel.PIC, 5, s), GmModel.R_NOT_OFFERED, "picture 5 not offered");
        eq(GmModel.refuse(GmModel.AIM, 0, s), GmModel.OK, "crosshair has no option list");
        // the RS30 (profile game_menu.vrr_top_speed, stock support_vrr_only_at_top_speed): VRR goes on only at top speed
        eq(GmModel.refuse(GmModel.VRR, 1, state(0, 0, 1, 0, 0, 0, 0, 0)), GmModel.OK, "VRR on below top speed without the rule");
        GmModel.setVrrTopSpeed(true);
        eq(GmModel.refuse(GmModel.VRR, 1, state(0, 0, 1, 0, 0, 0, 0, 0)), GmModel.R_VRR_SPEED, "VRR on refused below top speed");
        eq(GmModel.refuse(GmModel.VRR, 1, state(GmModel.BAD, 0, 1, 0, 0, 0, 0, 0)), GmModel.R_VRR_SPEED, "VRR on refused at an unknown speed");
        eq(GmModel.refuse(GmModel.VRR, 1, state(1, 0, 1, 0, 0, 0, 0, 0)), GmModel.OK, "VRR on at top speed");
        eq(GmModel.refuse(GmModel.VRR, 0, state(0, 1, 1, 0, 0, 0, 0, 0)), GmModel.OK, "VRR off below top speed allowed");
        eq(GmModel.refuse(GmModel.LAT, 0, state(1, 1, 1, 0, 0, 0, 0, 0)), GmModel.OK, "leaving top speed with VRR on (stock keeps VRR)");
        eq(GmModel.refuse(GmModel.HFR, 1, state(0, 1, 1, 0, 0, 0, 0, 0)), GmModel.OK, "the rule touches nothing but VRR on");
        GmModel.setVrrTopSpeed(false);
        eq(GmModel.refuse(GmModel.VRR, 1, state(0, 0, 1, 0, 0, 0, 0, 0)), GmModel.OK, "the rule off again");
        // FreeSync (stock X50 MutexManager on picking AMD_FREE_SYNC_PREMIUM, OtherFactory HIGH_FRAME_RATE_OFF)
        eq(GmModel.popupRefused(GmModel.FS, state(0, 0, 1, 0, 0, 0, 0, 0)), GmModel.OK, "FreeSync opens below top speed without the rule");
        GmModel.setVrrTopSpeed(true);
        eq(GmModel.popupRefused(GmModel.FS, state(0, 0, 1, 0, 0, 0, 0, 0)), GmModel.R_FS_SPEED, "FreeSync refused below top speed (rule)");
        eq(GmModel.popupRefused(GmModel.FS, state(GmModel.BAD, 0, 1, 0, 0, 0, 0, 0)), GmModel.R_FS_SPEED, "FreeSync refused at an unknown speed");
        eq(GmModel.refuse(GmModel.FS, 1, state(0, 0, 1, 0, 0, 0, 0, 0)), GmModel.R_FS_SPEED, "FreeSync value refused below top speed");
        eq(GmModel.popupRefused(GmModel.FS, state(1, 0, 1, 0, 0, 0, 0, 0)), GmModel.OK, "FreeSync opens at top speed");
        eq(GmModel.popupRefused(GmModel.FS, state(1, 0, 1, 0, 0, 0, 2, 0)), GmModel.R_FS_WIDE, "FreeSync refused with 21:9 (vrr_mutex)");
        eq(GmModel.popupRefused(GmModel.FS, state(0, 0, 1, 0, 0, 0, 3, 0)), GmModel.R_FS_SPEED, "speed is checked before the ratio (stock order)");
        GmModel.setVrrTopSpeed(false);
        eq(GmModel.popupRefused(GmModel.FS, state(0, 0, 1, 0, 0, 0, 3, 0)), GmModel.R_FS_WIDE, "FreeSync refused with 32:9");
        eq(GmModel.popupRefused(GmModel.FS, state(0, 0, 1, 0, 0, 0, 1, 0)), GmModel.OK, "FreeSync opens with 16:9");
        eq(GmModel.popupRefused(GmModel.VRR, state(0, 0, 1, 0, 0, 0, 3, 0)), GmModel.OK, "the FreeSync rule is the FreeSync item's only");
        GmModel.State fsOn = state(1, 0, 1, 0, 0, 0, 0, 0);
        fsOn.set(GmModel.FS, 1);
        eq(GmModel.refuse(GmModel.HFR, 0, fsOn), GmModel.R_HFR_FS, "HFR off refused while FreeSync is on");
        fsOn.set(GmModel.VRR, 1);
        eq(GmModel.refuse(GmModel.HFR, 0, fsOn), GmModel.R_HFR_FS, "FreeSync checked before VRR (stock order)");
        fsOn.set(GmModel.FS, 0);
        eq(GmModel.refuse(GmModel.HFR, 0, fsOn), GmModel.R_HFR_VRR, "VRR rule with FreeSync off");
        fsOn.set(GmModel.VRR, 0);
        eq(GmModel.refuse(GmModel.HFR, 0, fsOn), GmModel.OK, "HFR off allowed with both off");
        fsOn.set(GmModel.FS, 1);
        eq(GmModel.refuse(GmModel.HFR, 1, fsOn), GmModel.OK, "HFR on allowed with FreeSync on");
        GmModel.Caps fc = allCaps();
        fc.onRead(GmModel.FS, 1);
        boolean hfrOff = false;
        for (GmModel.Step st : GmModel.plan(GmModel.P_STORY, fsOn, fc)) if (st.key.equals(GmModel.SPEED) && GmModel.hfrOf(st.value) == 0) hfrOff = true;
        ok(!hfrOff, "a preset never turns HFR off while FreeSync is on");
        // the HDR item (stock X50 xhplayer HDR_SETTINGS, MutexManager: only while getHdrType reads HDR10)
        GmModel.State h = state(0, 0, 1, 0, 0, 0, 0, 0);
        eq(GmModel.popupRefused(GmModel.HDR, h), GmModel.R_HDR_SIGNAL, "HDR item refused with an unknown signal");
        for (int t : new int[] {GmModel.HDR_SDR, GmModel.HDR_DOLBY_VISION, GmModel.HDR_HLG, GmModel.HDR_HDR10_PLUS, GmModel.HDR_VIVID}) {
            h.hdr = t;
            eq(GmModel.popupRefused(GmModel.HDR, h), GmModel.R_HDR_SIGNAL, "HDR item refused with signal type " + t);
            for (int v = 0; v < 3; v++) eq(GmModel.refuse(GmModel.HDR, v, h), GmModel.R_HDR_SIGNAL, "HDR " + v + " refused with type " + t);
        }
        h.hdr = GmModel.HDR_HDR10;
        eq(GmModel.popupRefused(GmModel.HDR, h), GmModel.OK, "HDR item opens with HDR10");
        for (int v = 0; v < 3; v++) eq(GmModel.refuse(GmModel.HDR, v, h), GmModel.OK, "HDR " + v + " allowed with HDR10");
        eq(GmModel.refuse(GmModel.HDR, 3, h), GmModel.R_NOT_OFFERED, "HDR 3 not offered");
        h.hdr = GmModel.HDR_SDR;
        for (String k : new String[] {GmModel.VRR, GmModel.PIC, GmModel.SND, GmModel.DARK, GmModel.LAT})
            eq(GmModel.popupRefused(k, h), GmModel.OK, k + " opens without HDR10 (only the HDR item has the rule)");
        // 21:9 / 32:9 with the X50 family's wire values (profile ar_wide, TV 35: 5 / 6): offered without a 4K rule (stock
        // shows only the tip), refused while VRR is on (picking the item) and turning VRR / FreeSync on with a wide format
        GmModel.setWideWire(java.util.Arrays.asList(5, 6));
        eq(GmModel.options(GmModel.AR).toString(), "[0, 1, 2, 3]", "X50: Auto, 16:9, 21:9, 32:9");
        eq(GmModel.arWire(2), 5, "21:9 = TV 35(5)");
        eq(GmModel.arWire(3), 6, "32:9 = TV 35(6)");
        eq(GmModel.arOf(5), 2, "36 5 = 21:9");
        eq(GmModel.arOf(6), 3, "36 6 = 32:9");
        GmModel.State w = state(1, 0, 1, 0, 0, 0, 1, 0);
        w.width = 1920; w.height = 1080;
        eq(GmModel.refuse(GmModel.AR, 2, w), GmModel.OK, "21:9 with a 1080p signal: allowed (stock tip only)");
        w.set(GmModel.VRR, 1);
        eq(GmModel.popupRefused(GmModel.AR, w), GmModel.R_AR_VRR, "aspect item refused on picking while VRR is on");
        w.set(GmModel.VRR, 0); w.set(GmModel.AR, 3);
        eq(GmModel.popupRefused(GmModel.AR, w), GmModel.OK, "aspect item opens with VRR off");
        eq(GmModel.refuse(GmModel.VRR, 1, w), GmModel.R_VRR_WIDE, "VRR on refused with 32:9");
        boolean bad = false;
        try { GmModel.setWideWire(java.util.Arrays.asList(5)); } catch (IllegalArgumentException e) { bad = true; }
        ok(bad, "ar_wide must name both wide formats");
        GmModel.setWideWire(null);
        eq(GmModel.options(GmModel.AR).toString(), "[0, 1]", "no wide values: Auto and 16:9 only");
        ok(GmModel.askTurbo(0, 1, false), "turbo asks when the keystone is not the full frame");
        ok(GmModel.askTurbo(0, 1, null), "turbo asks when the keystone is unknown");
        ok(!GmModel.askTurbo(0, 1, true), "turbo does not ask at the full frame");
        ok(!GmModel.askTurbo(1, 1, false), "already turbo: no question");
        ok(!GmModel.askTurbo(1, 0, false), "back to standard: no question");
    }

    static String plan(int p, GmModel.State s, GmModel.Caps c) { return GmModel.plan(p, s, c).toString(); }

    static void presets() {
        GmModel.Caps c = allCaps();
        GmModel.State s = state(0, 0, 0, 0, 0, 0, 0, 0);
        eq(plan(GmModel.P_COMPETE, s, c), "[speed=2, vrr=1, pic=1, dark=1, snd=1]", "competitive from all off");
        s = state(1, 1, 1, 1, 1, 0, 0, 1);
        eq(plan(GmModel.P_COMPETE, s, c), "[]", "competitive already on: nothing sent");
        eq(GmModel.presetMatch(s, c), GmModel.P_COMPETE, "competitive matched");
        eq(plan(GmModel.P_STORY, s, c), "[vrr=0, speed=0, pic=2, dark=0, sr=1, snd=2]", "story: VRR off before HFR off");
        eq(plan(GmModel.P_BALANCE, s, c), "[vrr=0, speed=1, pic=0, dark=0, snd=0]", "balance from competitive");
        s = state(0, 0, 1, 0, 0, 0, 0, 0);
        eq(GmModel.presetMatch(s, c), GmModel.P_BALANCE, "balance matched");
        eq(plan(GmModel.P_BALANCE, s, c), "[]", "balance already on");
        s = state(0, 0, 1, 2, 1, 0, 0, 0);
        eq(plan(GmModel.P_BALANCE, s, c), "[pic=0, dark=0]", "dark scenes re-sent after the game picture");
        s = state(0, 0, 1, 2, 0, 0, 0, 0);
        eq(plan(GmModel.P_BALANCE, s, c), "[pic=0, dark=0]", "dark scenes of the new picture written too");
        // wide format goes to Auto before VRR on
        s = state(0, 0, 0, 0, 0, 0, 2, 0);
        eq(plan(GmModel.P_COMPETE, s, c).startsWith("[ar=0, speed=2, vrr=1"), true, "21:9 to Auto first: " + plan(GmModel.P_COMPETE, s, c));
        // gated items hidden: the preset skips them and never forces HFR for an invisible VRR
        GmModel.Caps none = new GmModel.Caps();
        s = state(0, 0, 0, 0, 0, 0, 0, 0);
        eq(plan(GmModel.P_COMPETE, s, none), "[speed=2]", "competitive with only proven items");
        eq(plan(GmModel.P_STORY, s, none), "[sr=1]", "story with only proven items");
        s = state(0, 0, 1, 0, 0, 0, 0, 0);
        eq(GmModel.presetMatch(s, none), GmModel.P_BALANCE, "balance matched on proven items only");
        s = state(0, 1, 1, 3, 1, 0, 0, 2);
        eq(GmModel.presetMatch(s, none), GmModel.P_BALANCE, "hidden items (VRR, picture, dark, sound) do not break a match");
        eq(GmModel.presetMatch(s, c), -1, "the same values with every item visible match no preset");
        // VRR stays on (hidden VRR read as on): HFR may not go off
        GmModel.Caps noVrr = allCaps();
        noVrr.onReadBack(GmModel.VRR, 1, 0);
        s = state(0, 1, 1, 0, 0, 0, 0, 0);
        eq(plan(GmModel.P_STORY, s, noVrr), "[pic=2, dark=0, sr=1, snd=2]", "HFR kept on while a hidden VRR reads on");
        s = state(1, 0, 0, 0, 0, 0, 0, 0);
        eq(GmModel.presetMatch(s, c), -1, "no preset matches turbo without HFR");
        boolean threw = false;
        try { GmModel.preset(3); } catch (IllegalArgumentException e) { threw = true; }
        ok(threw, "preset 3 refused");
        eq(GmModel.preset(GmModel.P_COMPETE).toString(), "{lat=1, vrr=1, hfr=1, pic=1, dark=1, snd=1}", "competitive values");
        eq(GmModel.preset(GmModel.P_BALANCE).toString(), "{lat=0, vrr=0, hfr=1, pic=0, dark=0, sr=0, snd=0}", "balance values");
        eq(GmModel.preset(GmModel.P_STORY).toString(), "{lat=0, vrr=0, hfr=0, pic=2, dark=0, sr=1, snd=2}", "story values");
    }

    static void focus() {
        int D = GmModel.Z_DOCK, P = GmModel.Z_PRESETS;
        eq(arr(GmModel.move(D, 0, 0, GmModel.LEFT, 10, 3)), "[0, 0, 0]", "dock LEFT at the first tile stays");
        eq(arr(GmModel.move(D, 9, 9, GmModel.RIGHT, 10, 3)), "[0, 9, 9]", "dock RIGHT at the last tile stays");
        eq(arr(GmModel.move(D, 4, 4, GmModel.RIGHT, 10, 3)), "[0, 5, 5]", "dock RIGHT");
        eq(arr(GmModel.move(D, 4, 4, GmModel.LEFT, 10, 3)), "[0, 3, 3]", "dock LEFT");
        eq(arr(GmModel.move(D, 0, 0, GmModel.UP, 10, 3)), "[1, 0, 0]", "UP from the first tile to Competitive");
        eq(arr(GmModel.move(D, 5, 5, GmModel.UP, 10, 3)), "[1, 1, 5]", "UP from the middle to Balance");
        eq(arr(GmModel.move(D, 9, 9, GmModel.UP, 10, 3)), "[1, 2, 9]", "UP from the last tile to Story");
        eq(arr(GmModel.move(D, 3, 3, GmModel.DOWN, 10, 3)), "[0, 3, 3]", "DOWN on the dock stays");
        eq(arr(GmModel.move(P, 1, 7, GmModel.DOWN, 10, 3)), "[0, 7, 7]", "DOWN from presets back to the remembered tile");
        eq(arr(GmModel.move(P, 2, 7, GmModel.RIGHT, 10, 3)), "[1, 2, 7]", "presets RIGHT at the end stays");
        eq(arr(GmModel.move(P, 0, 7, GmModel.LEFT, 10, 3)), "[1, 0, 7]", "presets LEFT at the start stays");
        eq(arr(GmModel.move(P, 0, 7, GmModel.RIGHT, 10, 3)), "[1, 1, 7]", "presets RIGHT");
        eq(arr(GmModel.move(P, 1, 7, GmModel.UP, 10, 3)), "[1, 1, 7]", "UP on presets stays");
        eq(arr(GmModel.move(P, 0, 12, GmModel.DOWN, 6, 3)), "[0, 5, 12]", "remembered tile clamped to a shorter dock");
        eq(GmModel.presetBelow(2, 6, 3), 1, "6 tiles: third tile under Balance");
        eq(GmModel.presetBelow(0, 1, 3), 0, "one tile: Competitive");
        eq(GmModel.popupMove(0, GmModel.UP, 4), 0, "popup UP at the top stays");
        eq(GmModel.popupMove(3, GmModel.DOWN, 4), 3, "popup DOWN at the bottom stays");
        eq(GmModel.popupMove(1, GmModel.DOWN, 4), 2, "popup DOWN");
        eq(GmModel.popupMove(1, GmModel.LEFT, 4), 1, "popup LEFT does nothing");
    }

    static void aim() {
        int S = GmModel.AIM_ROW_STYLE, C = GmModel.AIM_ROW_COLOUR;
        eq(arr(GmModel.aimMove(S, 0, 0, GmModel.LEFT)), "[1, 9, 0]", "style LEFT wraps to the last");
        eq(arr(GmModel.aimMove(S, 9, 0, GmModel.RIGHT)), "[1, 0, 0]", "style RIGHT wraps to the first");
        eq(arr(GmModel.aimMove(S, 2, 0, GmModel.DOWN)), "[1, 7, 0]", "DOWN to the second line");
        eq(arr(GmModel.aimMove(S, 7, 0, GmModel.DOWN)), "[2, 7, 0]", "DOWN from the second line to the colours");
        eq(arr(GmModel.aimMove(S, 7, 0, GmModel.UP)), "[1, 2, 0]", "UP to the first line");
        eq(arr(GmModel.aimMove(S, 2, 0, GmModel.UP)), "[0, 2, 0]", "UP from the first line to the switch");
        eq(arr(GmModel.aimMove(C, 0, 0, GmModel.LEFT)), "[2, 0, 4]", "colour LEFT wraps");
        eq(arr(GmModel.aimMove(C, 0, 4, GmModel.RIGHT)), "[2, 0, 0]", "colour RIGHT wraps");
        eq(arr(GmModel.aimMove(C, 0, 1, GmModel.DOWN)), "[3, 0, 1]", "colours DOWN to Move");
        eq(arr(GmModel.aimMove(GmModel.AIM_ROW_MOVE, 0, 1, GmModel.DOWN)), "[3, 0, 1]", "Move is the last row");
        eq(arr(GmModel.aimMove(GmModel.AIM_ROW_TOGGLE, 3, 1, GmModel.LEFT)), "[0, 3, 1]", "LEFT on the switch does nothing");
        eq(GmModel.AIM_COLOURS[0], 0xFF51E915, "stock default colour first");
        eq(GmModel.AIM_COLOURS.length, 5, "5 colours");
        eq(arr(GmModel.aimNudge(960, 540, GmModel.LEFT)), "[954, 540]", "nudge 6 px");
        eq(arr(GmModel.aimNudge(80, 80, GmModel.UP)), "[80, 80]", "top edge kept");
        eq(arr(GmModel.aimNudge(1840, 1000, GmModel.RIGHT)), "[1840, 1000]", "right edge kept");
        eq(arr(GmModel.aimNudge(1840, 1000, GmModel.DOWN)), "[1840, 1000]", "bottom edge kept");
        eq(arr(GmModel.aimNudge(83, 500, GmModel.LEFT)), "[80, 500]", "clamped at the left");
    }

    static void theme() {
        eq(GmModel.theme("PlayStation 5", 0, true), GmModel.TH_PS, "PlayStation 5");
        eq(GmModel.theme("PS5", 0, true), GmModel.TH_PS, "PS5");
        eq(GmModel.theme("PS4 Pro", 0, true), GmModel.TH_PS, "PS4");
        eq(GmModel.theme("Xbox Series X", 0, true), GmModel.TH_XBOX, "Xbox");
        eq(GmModel.theme("XBOX ONE", 0, true), GmModel.TH_XBOX, "XBOX upper case");
        eq(GmModel.theme("Nintendo Switch", 0, true), GmModel.TH_SWITCH, "Switch");
        eq(GmModel.theme("Switch", 0, true), GmModel.TH_SWITCH, "Switch alone");
        eq(GmModel.theme("NINTENDO", 0, true), GmModel.TH_SWITCH, "Nintendo");
        eq(GmModel.theme("", GmModel.VENDOR_SONY, true), GmModel.TH_PS, "nameless Sony playback = PS5");
        eq(GmModel.theme("", GmModel.VENDOR_SONY, false), GmModel.TH_COMPUTER, "nameless Sony non-playback");
        eq(GmModel.theme("BDP-S6700", GmModel.VENDOR_SONY, true), GmModel.TH_COMPUTER, "named Sony Blu-ray is no console");
        eq(GmModel.theme("MacBook Pro", 0, true), GmModel.TH_APPLE, "MacBook = Apple icon");
        eq(GmModel.theme("Guest's Mac", 0, true), GmModel.TH_APPLE, "Mac as a word");
        eq(GmModel.theme("iMac", 0, true), GmModel.TH_APPLE, "iMac");
        eq(GmModel.theme("Mac mini", 0, true), GmModel.TH_APPLE, "Mac mini");
        eq(GmModel.theme("Apple TV", 0, true), GmModel.TH_APPLE, "Apple");
        eq(GmModel.theme("Machine", 0, true), GmModel.TH_COMPUTER, "Mac only as a word");
        eq(GmModel.theme("Windows PC", 0, true), GmModel.TH_WINDOWS, "Windows");
        eq(GmModel.theme("GAMING PC", 0, true), GmModel.TH_WINDOWS, "PC as a word");
        eq(GmModel.theme("Microsoft Surface", 0, true), GmModel.TH_WINDOWS, "Microsoft");
        eq(GmModel.theme("Xbox Microsoft", 0, true), GmModel.TH_XBOX, "Xbox before Microsoft");
        eq(GmModel.theme("Topcon", 0, true), GmModel.TH_COMPUTER, "pc only as a word");
        eq(GmModel.theme("Chromecast", 0, true), GmModel.TH_COMPUTER, "other source = laptop icon");
        eq(GmModel.theme(null, 0, false), GmModel.TH_COMPUTER, "no CEC = laptop icon");
        // the framework's placeholder names are no names (PS5 on the Play 6: Playback_1 first, vendor 0xFFFFFF)
        eq(GmModel.realName("Playback_1"), null, "Playback_1 is a placeholder");
        eq(GmModel.realName(" Recorder_2 "), null, "Recorder_2 is a placeholder");
        eq(GmModel.realName("PlayStation 5"), "PlayStation 5", "a real name stays");
        eq(GmModel.realName("Playback 1"), null, "placeholder with a space");
        eq(GmModel.realName("Playback"), "Playback", "a word alone is a name");
        eq(GmModel.realName(""), null, "empty name");
        eq(GmModel.theme("Playback_1", GmModel.VENDOR_SONY, true), GmModel.TH_PS, "Sony playback with the placeholder = PS5");
        eq(GmModel.theme("Playback_1", 0xFFFFFF, true), GmModel.TH_COMPUTER, "placeholder, unknown vendor, no hint");
        eq(GmModel.theme("Playback_1", 0xFFFFFF, true, true), GmModel.TH_PAD, "placeholder, unknown vendor, ALLM = pad");
        eq(GmModel.theme("", 0x0050F2, true), GmModel.TH_XBOX, "nameless Microsoft block = Xbox");
        eq(GmModel.theme(null, 0x0009BF, true), GmModel.TH_SWITCH, "nameless Nintendo block = Switch");
        eq(GmModel.theme("Surface Hub", 0x0050F2, true), GmModel.TH_COMPUTER, "a named Microsoft device is no Xbox");
        eq(GmModel.theme(null, 0, false, true), GmModel.TH_PAD, "no CEC but a console hint = generic pad");
        eq(GmModel.theme("MacBook Pro", 0, false, true), GmModel.TH_APPLE, "a Mac never gets a pad, hint or not");
        eq(GmModel.theme("Windows PC", 0, false, true), GmModel.TH_WINDOWS, "a PC never gets a pad, hint or not");
        eq(GmModel.theme("Steam Deck", 0, true, true), GmModel.TH_PAD, "unknown named device with ALLM = pad");
        eq(GmModel.theme(new GmModel.Source("PS5", 0, true), false), GmModel.TH_PS, "Source overload");
        eq(GmModel.theme((GmModel.Source) null, true), GmModel.TH_PAD, "no source, hint");
        ok(GmModel.isConsole(GmModel.TH_PAD), "the generic pad plays frames");
        eq(String.join(",", GmModel.FRAME_THEMES), "ps,xbox,switch,pad", "frame themes");
        eq(GmModel.shownName("Playback_1", GmModel.TH_PS), "PlayStation", "brand word, never the placeholder");
        eq(GmModel.shownName("PlayStation 5", GmModel.TH_PS), "PlayStation 5", "own name first");
        eq(GmModel.shownName(null, GmModel.TH_PAD), null, "pad without a name: the port");
        eq(GmModel.shownName("Playback_1", GmModel.TH_COMPUTER), null, "placeholder on a computer: the port");
        ok(new GmModel.Source("A", 1, true).same(new GmModel.Source(" A ", 1, true)), "same source");
        ok(!new GmModel.Source("A", 1, true).same(new GmModel.Source("A", 2, true)), "vendor differs");
        eq(GmModel.glow(GmModel.TH_PS), 0xFF4C7DFF, "PS glow");
        eq(GmModel.glow(GmModel.TH_XBOX), 0xFF3FBF5B, "Xbox glow");
        eq(GmModel.glow(GmModel.TH_SWITCH), 0xFFFF5A51, "Switch glow");
        eq(GmModel.glow(GmModel.TH_COMPUTER), 0xFF3B78E7, "computer glow = accent");
        ok(GmModel.isConsole(GmModel.TH_PS) && GmModel.isConsole(GmModel.TH_XBOX) && GmModel.isConsole(GmModel.TH_SWITCH),
                "consoles play the controller frames");
        for (String c : new String[] {GmModel.TH_APPLE, GmModel.TH_WINDOWS, GmModel.TH_COMPUTER})
            ok(!GmModel.isConsole(c), "no gamepad for a computer: " + c);
        ok(GmModel.isKnownComputer(GmModel.TH_APPLE) && GmModel.isKnownComputer(GmModel.TH_WINDOWS)
                && !GmModel.isKnownComputer(GmModel.TH_COMPUTER) && !GmModel.isKnownComputer(GmModel.TH_PS), "known computers");
    }

    static void frames() {
        eq(GmModel.frameAt(0), 1, "turn starts after the hero frame");
        eq(GmModel.frameAt(-5), 1, "negative time = start");
        eq(GmModel.frameAt(GmModel.TURN_MS - 1), 0, "turn ends on the hero frame");
        int prev = -1, back = 0, seen = 0;
        boolean[] hit = new boolean[30];
        for (long t = 0; t < GmModel.TURN_MS; t += 5) {
            int f = GmModel.frameAt(t);
            ok(f >= 0 && f < 30, "turn frame in range at " + t);
            int lin = (f + 29) % 30;          // 1..29, 0 -> 0..29 in play order
            if (prev >= 0 && lin < prev) back++;
            prev = lin;
            if (!hit[f]) { hit[f] = true; seen++; }
        }
        eq(back, 0, "turn never steps back");
        ok(seen >= 28, "turn shows (nearly) every frame: " + seen);
        eq(GmModel.frameAt(GmModel.TURN_MS), GmModel.IDLE_BASE, "idle starts with idle_00");
        eq(GmModel.frameAt(GmModel.TURN_MS + 139), GmModel.IDLE_BASE, "idle_00 for 140 ms");
        eq(GmModel.frameAt(GmModel.TURN_MS + 140), GmModel.IDLE_BASE + 1, "idle_01 after 140 ms");
        eq(GmModel.frameAt(GmModel.TURN_MS + 140L * 24), GmModel.IDLE_BASE, "idle loops after 24 frames");
        eq(GmModel.frameAt(GmModel.TURN_MS + 140L * 23), GmModel.IDLE_BASE + 23, "idle_23 last");
        ok(GmModel.frameAt(GmModel.TURN_MS / 2) > 15, "ease-out: past half the turn at half time");
        eq(GmModel.frameAt(GmModel.TURN_MS / 2), 24, "ease-out 2.2: frame 24 at half time (linear would be 16)");
        eq(GmModel.frameAt(GmModel.TURN_MS / 4), 15, "ease-out 2.2: frame 15 at a quarter (linear would be 8)");
        eq(GmModel.framePath("ps", 0), "gamemenu/ps/turn_00.webp", "turn path");
        eq(GmModel.framePath("xbox", GmModel.IDLE_BASE + 7), "gamemenu/xbox/idle_07.webp", "idle path");
    }

    static void timers() {
        eq(GmModel.autoHideMs(true), 30_000L, "first time 30 s");
        eq(GmModel.autoHideMs(false), 10_000L, "then 10 s");
        eq(GmModel.fpsShown(5990), 60, "59.90 Hz shows 60");
        eq(GmModel.fpsShown(11988), 120, "119.88 Hz shows 120");
        eq(GmModel.fpsShown(2397), 24, "23.97 Hz shows 24");
        eq(GmModel.fpsShown(0), -1, "no signal");
        eq(GmModel.fpsShown(-3), -1, "negative");
        eq(GmModel.fpsShown(60000), -1, "implausible");
    }
}
