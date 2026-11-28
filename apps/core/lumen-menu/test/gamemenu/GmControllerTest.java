// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.view.KeyEvent;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Host tests of the game menu keys and the device picture (real GmController + GmModel over the stubs in
 * test/gamemenu/stubs). sh test/gamemenu/run.sh
 *
 * The a test Z9X (2026-11-11): in the VRR popup UP / DOWN moved the focus without a redraw, because only the console
 * frame timer or a live ALLM chip repainted the menu; the render harness always had both (PS theme, ALLM on). These
 * tests run the menu as a computer without ALLM, where nothing repaints by itself, and require a redraw after every
 * handled key. They also feed the CEC list as the PS5 sends it (placeholder first, name later).
 */
public final class GmControllerTest {
    private static int pass, fail;

    private static void ok(boolean c, String what) {
        if (c) pass++; else { fail++; System.out.println("FAIL " + what); }
    }

    private static void eq(Object a, Object b, String what) {
        ok(a == null ? b == null : a.equals(b), what + " (got " + a + ", want " + b + ")");
    }

    /** A projector that answers at once (the real one answers on the HAL thread, the logic is the same). */
    static class FakeIo implements GameMenu.Io {
        final GameMenu.Snap s = new GameMenu.Snap();

        FakeIo(boolean allm) {
            s.opt = GmModel.option(0, 1);
            s.running = true;
            s.width = 3840; s.height = 2160; s.frameCenti = 5994;
            s.hdr = 1;
            s.allm = allm;
            s.sr = 0; s.ar = 2; s.vrr = 0; s.pic = 0; s.dark = 0; s.snd = 190;
        }

        GameMenu.Snap copy() {
            GameMenu.Snap o = new GameMenu.Snap();
            o.opt = s.opt; o.running = s.running; o.width = s.width; o.height = s.height; o.frameCenti = s.frameCenti;
            o.hdr = s.hdr; o.allm = s.allm; o.sr = s.sr; o.ar = s.ar; o.vrr = s.vrr; o.pic = s.pic; o.dark = s.dark;
            o.snd = s.snd; o.cs = s.cs; o.hdrMode = s.hdrMode; o.fs = s.fs;
            return o;
        }

        @Override public void read(int port, Consumer<GameMenu.Snap> cb) { cb.accept(copy()); }

        @Override public void fullFrame(Consumer<Boolean> cb) { cb.accept(false); }   // Turbo asks first

        @Override public void apply(String key, int value, int port, GameMenu.Snap now, GameMenu.ApplyResult cb) {
            int back;
            switch (key) {
                case GmModel.SPEED: s.opt = value; back = value; break;
                case GmModel.SR: s.sr = value; back = value; break;
                case GmModel.AR: s.ar = GmModel.arWire(value); back = s.ar; break;
                case GmModel.VRR: s.vrr = value; back = value; if (value == 1) s.opt = GmModel.option(GmModel.latOf(s.opt), 1); break;
                case GmModel.PIC: s.pic = value; back = value; break;
                case GmModel.DARK: s.dark = value; back = value; break;
                case GmModel.SND: back = GmModel.sndWire(value); s.snd = back; break;
                default: back = GmModel.BAD;
            }
            cb.done(back, copy());
        }
    }

    private static GmController shown(boolean allm, GmModel.Source src, boolean marked) {
        GameMenu.CAPS = new GmModel.Caps();
        GmController c = new GmController(new Context(), new FakeIo(allm), 1, false, src, marked);
        c.overlays = new GmOverlays();
        c.view();
        c.onShown();
        return c;
    }

    private static boolean key(GmController c, int code) {
        return c.key(new KeyEvent(KeyEvent.ACTION_DOWN, code));
    }

    private static int redraws(GmController c) { return c.gmView().invalidations; }

    public static void main(String[] a) {
        popups();
        hdrRule();
        freeSyncRule();
        shiftItem();
        everyKeyRedraws();
        device();
        System.out.println("GmControllerTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    /**
     * The X50 stock HDR item (xhplayer MutexManager.mutex on picking it): OK on its tile opens the popup only while the
     * signal is HDR10 (getHdrType 1); any other type, or none, shows the stock refusal text and opens nothing.
     */
    /** A FakeIo that answers the screen shift as the X50 vendor would (panel 2160, 21:9 frame) and logs each op. */
    static final class ShiftIo extends FakeIo {
        final java.util.List<String> ops = new java.util.ArrayList<>();
        int[] area = {0, 257, 3840, 1646, 2160};
        boolean refuse;

        ShiftIo() { super(false); }

        @Override public void shift(int op, int port, int top, Consumer<int[]> cb) {
            ops.add(op + ":" + top);
            if (op == GmModel.SHIFT_MOVE) area = new int[] {0, top, 3840, 1646, 2160};
            if (op == GmModel.SHIFT_RESET) area = new int[] {0, 257, 3840, 1646, 2160};
            cb.accept(refuse ? null : area.clone());
        }
    }

    /**
     * Stock screen shift (MutexManager on picking SCREEN_OFFSET, ScreenOffsetDialog keys): refused with the stock text
     * unless 21:9 / 32:9; then the band reads the area, UP / DOWN send one move each with the panel line, short OK does
     * nothing, a long OK resets once per press, BACK leaves the band and keeps the menu.
     */
    static void shiftItem() {
        GmModel.setWideWire(java.util.Arrays.asList(5, 6));
        String rule = new Context().getString(org.lumen.projector.R.string.lumen_gm_rule_so_ratio);
        GameMenu.CAPS = new GmModel.Caps();
        ShiftIo io = new ShiftIo();
        io.s.ar = 1;                                     // 16:9
        GmController c = new GmController(new Context(), io, 1, false, GmModel.Source.NONE, false);
        c.overlays = new GmOverlays();
        c.view();
        c.onShown();
        int at = c.tiles().indexOf(GmModel.SO);
        ok(at == c.tiles().indexOf(GmModel.AR) + 1, "so: the tile right after the aspect tile");
        for (int i = 0; i < at; i++) key(c, KeyEvent.KEYCODE_DPAD_RIGHT);
        eq(c.focusedTile(), GmModel.SO, "so: focus on the screen shift tile");
        key(c, KeyEvent.KEYCODE_DPAD_CENTER);
        ok(!c.shifting && io.ops.isEmpty(), "so with 16:9: no band, nothing sent");
        eq(c.toast, rule, "so with 16:9: the stock refusal text");
        io.s.ar = 5;                                     // 21:9
        c.refresh();
        c.toast = null;
        key(c, KeyEvent.KEYCODE_DPAD_CENTER);
        ok(c.shifting && c.popup == null, "so with 21:9: the band opens, no popup");
        eq(io.ops, java.util.Arrays.asList("0:0"), "so: one area read on opening");
        eq(c.shiftTop + "," + c.shiftH, "128,823", "so: the band of the area read");
        key(c, KeyEvent.KEYCODE_DPAD_DOWN);
        eq(c.shiftTop, 131, "so: DOWN moves the band 3 lines");
        eq(io.ops.get(io.ops.size() - 1), "1:262", "so: DOWN sends the panel line 262 at once");
        c.key(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 1));
        eq(c.shiftTop, 128, "so: a held UP (repeat) moves too");
        int n = io.ops.size();
        key(c, KeyEvent.KEYCODE_DPAD_LEFT);
        key(c, KeyEvent.KEYCODE_DPAD_CENTER);
        ok(io.ops.size() == n && c.shifting, "so: LEFT and a short OK send nothing and keep the band");
        c.key(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1, true));
        c.key(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 2, true));
        eq(io.ops.subList(n, io.ops.size()), java.util.Arrays.asList("2:0"), "so: a long OK resets once per press");
        int resets = io.ops.size();
        c.key(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
        c.key(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1, true));
        eq(io.ops.subList(resets, io.ops.size()), java.util.Arrays.asList("2:0"), "so: the next long OK resets again");
        eq(c.shiftTop, 128, "so: after the reset the band is the restored frame");
        ok(c.back() && !c.shifting && c.popup == null, "so: BACK leaves the band, the menu stays");
        ok(!c.back(), "so: the next BACK closes the menu");
        io.refuse = true;
        key(c, KeyEvent.KEYCODE_DPAD_CENTER);
        ok(!c.shifting, "so: a refused or unanswered area read closes the band");
        eq(c.toast, new Context().getString(org.lumen.projector.R.string.lumen_gm_failed_soft), "so: the could-not note");
        io.refuse = false;
        key(c, KeyEvent.KEYCODE_DPAD_CENTER);
        ok(c.shifting, "so: the band open again");
        c.onDismissed();
        ok(!c.shifting, "so: the menu closing ends the band");
    }

    static void hdrRule() {
        String refusal = new Context().getString(org.lumen.projector.R.string.lumen_gm_rule_hdr_signal);
        for (int type : new int[] {GmModel.HDR_SDR, GmModel.HDR_HDR10, GmModel.HDR_DOLBY_VISION, GmModel.HDR_HLG,
                GmModel.HDR_HDR10_PLUS, GmModel.HDR_VIVID, -1}) {
            GameMenu.CAPS = new GmModel.Caps();
            FakeIo io = new FakeIo(false);
            io.s.hdr = type;
            io.s.hdrMode = 0;
            GmController c = new GmController(new Context(), io, 1, false, GmModel.Source.NONE, false);
            c.overlays = new GmOverlays();
            c.view();
            c.onShown();
            int at = c.tiles().indexOf(GmModel.HDR);
            ok(at >= 0, "type " + type + ": the HDR tile is on the dock once its getter answered");
            for (int i = 0; i < at; i++) key(c, KeyEvent.KEYCODE_DPAD_RIGHT);
            eq(c.focusedTile(), GmModel.HDR, "type " + type + ": focus on the HDR tile");
            key(c, KeyEvent.KEYCODE_DPAD_CENTER);
            boolean hdr10 = type == GmModel.HDR_HDR10;
            eq(c.popup, hdr10 ? GmModel.HDR : null, "type " + type + ": OK opens the HDR popup only with HDR10");
            eq(c.toast, hdr10 ? null : refusal, "type " + type + ": the stock refusal text, nothing else");
        }
    }

    /**
     * The X50 stock FreeSync item (xhplayer MutexManager on picking AMD_FREE_SYNC_PREMIUM, OtherFactory HIGH_FRAME_RATE_OFF):
     * the tile shows once 173 answered; with 21:9 / 32:9 OK shows the 16:9 text and opens nothing; while FreeSync is on,
     * HFR off is refused with the FreeSync text and nothing is written.
     */
    static void freeSyncRule() {
        String wide = new Context().getString(org.lumen.projector.R.string.lumen_gm_rule_vrr_wide);
        String hfr = new Context().getString(org.lumen.projector.R.string.lumen_gm_rule_hfr_fs);
        GameMenu.CAPS = new GmModel.Caps();
        FakeIo io = new FakeIo(false);
        io.s.fs = 1;
        GmController c = new GmController(new Context(), io, 1, false, GmModel.Source.NONE, false);
        c.overlays = new GmOverlays();
        c.view();
        c.onShown();
        int at = c.tiles().indexOf(GmModel.FS);
        eq(at, c.tiles().indexOf(GmModel.HFR) + 1, "the FreeSync tile right after HFR once 173 answered");
        c.choose(GmModel.HFR, 0);
        eq(c.toast, hfr, "HFR off refused while FreeSync is on, with the FreeSync text");
        eq(GmModel.hfrOf(io.s.opt), 1, "HFR stays on, nothing written");
        c.toast = null;
        c.state.set(GmModel.AR, 2);
        for (int i = 0; i < at; i++) key(c, KeyEvent.KEYCODE_DPAD_RIGHT);
        eq(c.focusedTile(), GmModel.FS, "focus on the FreeSync tile");
        key(c, KeyEvent.KEYCODE_DPAD_CENTER);
        eq(c.popup, null, "21:9: OK on FreeSync opens nothing");
        eq(c.toast, wide, "21:9: the 16:9 text (stock vrr_mutex)");
        c.state.set(GmModel.AR, 1);
        c.toast = null;
        key(c, KeyEvent.KEYCODE_DPAD_CENTER);
        eq(c.popup, GmModel.FS, "16:9: OK opens the FreeSync popup");
    }

    /** Every popup: opened by OK on its tile, focus on the value in use, UP / DOWN move the focus and redraw. */
    static void popups() {
        GmController probe = shown(false, GmModel.Source.NONE, false);
        List<String> tiles = probe.tiles();
        eq(tiles.size(), 11, "all eleven tiles visible with every getter answering (screen shift with the aspect tile)");
        for (int t = 0; t < tiles.size(); t++) {
            String k = tiles.get(t);
            if (GmModel.SO.equals(k)) continue;           // no popup: its band (shiftItem)
            GmController c = shown(false, GmModel.Source.NONE, false);
            eq(c.theme, GmModel.TH_COMPUTER, k + ": the run without any repaint timer (computer, no ALLM)");
            for (int i = 0; i < t; i++) key(c, KeyEvent.KEYCODE_DPAD_RIGHT);
            eq(c.focusedTile(), k, "RIGHT x" + t + " reaches " + k);
            key(c, KeyEvent.KEYCODE_DPAD_CENTER);
            eq(c.popup, k, "OK opens the " + k + " popup");
            boolean aim = GmModel.AIM.equals(k);
            List<Integer> opts = GmModel.options(k);
            int start = aim ? c.aimRow : c.prow;
            if (!aim) eq(start, Math.max(0, opts.indexOf(c.state.get(k))), k + ": focus starts on the value in use");
            int rows = aim ? GmModel.AIM_ROWS : opts.size();
            int before = redraws(c);
            ok(key(c, KeyEvent.KEYCODE_DPAD_DOWN), k + ": DOWN handled");
            ok(redraws(c) > before, k + ": DOWN in the popup redraws");
            int down = aim ? c.aimRow : c.prow;
            eq(down, Math.min(rows - 1, start + 1), k + ": DOWN moves the focus one row");
            if (!aim && down != start) eq(c.gmView().popupRows, 1, k + ": the row change reaches the view (fade)");
            before = redraws(c);
            ok(key(c, KeyEvent.KEYCODE_DPAD_UP), k + ": UP handled");
            ok(redraws(c) > before, k + ": UP in the popup redraws");
            int up = aim ? c.aimRow : c.prow;
            eq(up, Math.max(0, down - 1), k + ": UP moves the focus one row back");
            before = redraws(c);
            key(c, KeyEvent.KEYCODE_DPAD_UP);
            ok(redraws(c) > before, k + ": UP again redraws (also on the first row)");
            eq(aim ? c.aimRow : c.prow, Math.max(0, up - 1), k + ": UP stops at the first row");
            if (!aim && opts.size() > 1) {
                // pick the other value: the dot (state) follows the focused row
                int target = c.prow == 0 ? 1 : 0;
                if (c.prow != target) key(c, target > c.prow ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP);
                before = redraws(c);
                key(c, KeyEvent.KEYCODE_DPAD_CENTER);
                ok(redraws(c) > before, k + ": OK in the popup redraws");
                if (c.confirmOk != null) key(c, KeyEvent.KEYCODE_DPAD_CENTER);   // the Turbo question
                if (GmModel.refuse(k, opts.get(target), c.state) == GmModel.OK || GmModel.FPS.equals(k)) {
                    eq(c.state.get(k), opts.get(target), k + ": OK picks the focused value");
                }
            }
        }
    }

    /** A random walk over every state (dock, presets, popups, the Turbo question): each handled key redraws. */
    static void everyKeyRedraws() {
        int[] keys = {KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BACK};
        Random r = new Random(11);
        int handled = 0, missed = 0, confirms = 0;
        for (int run = 0; run < 6; run++) {
            GmController c = shown(run % 2 == 1, GmModel.Source.NONE, false);
            for (int i = 0; i < 400; i++) {
                int code = keys[r.nextInt(keys.length)];
                int before = redraws(c);
                if (c.confirmOk != null) confirms++;
                if (code == KeyEvent.KEYCODE_BACK) {
                    if (c.back() && redraws(c) == before) missed++;
                    continue;
                }
                if (key(c, code)) {
                    handled++;
                    if (redraws(c) == before) missed++;
                }
            }
        }
        ok(handled > 1500, "random walk handled keys (" + handled + ")");
        ok(confirms > 0, "random walk reached the Turbo question (" + confirms + " keys there)");
        eq(missed, 0, "handled keys without a redraw");
    }

    /** The picture follows the CEC list and ALLM while the menu is open. */
    static void device() {
        GmController c = shown(false, new GmModel.Source("Playback_1", 0xFFFFFF, true), false);
        eq(c.theme, GmModel.TH_COMPUTER, "placeholder name, unknown vendor, no ALLM: laptop icon");
        eq(c.cecName, null, "the placeholder is never shown as a name");
        int before = redraws(c);
        c.onSource(new GmModel.Source("PlayStation 5", GmModel.VENDOR_SONY, true));
        eq(c.theme, GmModel.TH_PS, "the PS5 name arrives while the menu is open: PS pad");
        eq(c.cecName, "PlayStation 5", "its name on the card");
        ok(redraws(c) > before, "a new device redraws the card");

        c = shown(false, new GmModel.Source("Playback_1", GmModel.VENDOR_SONY, true), false);
        eq(c.theme, GmModel.TH_PS, "Sony playback device with the placeholder name: PS pad");
        eq(c.cecName, "PlayStation", "brand word on the card, not Playback_1");

        c = shown(true, GmModel.Source.NONE, false);
        eq(c.theme, GmModel.TH_PAD, "no CEC device but ALLM: generic gamepad");
        eq(c.cecName, null, "no name: the card shows the port");

        c = shown(false, GmModel.Source.NONE, true);
        eq(c.theme, GmModel.TH_PAD, "input marked as a console: generic gamepad");

        c = shown(true, new GmModel.Source("MacBook Pro", 0, false), false);
        eq(c.theme, GmModel.TH_APPLE, "a named computer keeps its icon even with ALLM");

        c = shown(false, GmModel.Source.NONE, false);
        eq(c.theme, GmModel.TH_COMPUTER, "nothing known: laptop icon");
        FakeIo io = (FakeIo) c.io;
        io.s.allm = true;
        c.refresh();
        eq(c.theme, GmModel.TH_PAD, "ALLM read while open turns the picture into the gamepad");
    }
}
