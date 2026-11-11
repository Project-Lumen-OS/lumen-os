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
    static final class FakeIo implements GameMenu.Io {
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
            o.snd = s.snd;
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
        everyKeyRedraws();
        device();
        System.out.println("GmControllerTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    /** Every popup: opened by OK on its tile, focus on the value in use, UP / DOWN move the focus and redraw. */
    static void popups() {
        GmController probe = shown(false, GmModel.Source.NONE, false);
        List<String> tiles = probe.tiles();
        eq(tiles.size(), 10, "all ten tiles visible with every getter answering");
        for (int t = 0; t < tiles.size(); t++) {
            String k = tiles.get(t);
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
