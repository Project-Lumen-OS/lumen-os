// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.signal;

import java.util.ArrayList;
import java.util.List;

/**
 * Host test of holding OK on a developer card (OkHold, PanelToggle.expand / collapse): a short press passes exactly
 * once and unchanged, a held one opens the panel once, the timing edges, the full panel's own OK, and with no card on
 * screen nothing at all is taken.
 */
public final class OkHoldTest {
    private static int fails, checks;
    private static final int OK = 23, ENTER = 66, UP_KEY = 19, BACK = 4;
    private static final OkHold.Shown NONE = OkHold.Shown.NONE, CARD = OkHold.Shown.CARD, SHEET = OkHold.Shown.SHEET;

    private static void ok(String what, boolean cond) {
        checks++;
        if (!cond) {
            fails++;
            System.out.println("FAIL " + what);
        }
    }

    /** One key event as the filter sees it. */
    static final class Ev {
        final int code, repeat;
        final boolean down;
        final long downTime;

        Ev(int code, boolean down, int repeat, long downTime) {
            this.code = code;
            this.down = down;
            this.repeat = repeat;
            this.downTime = downTime;
        }

        @Override public String toString() { return code + (down ? "v" : "^") + repeat + "@" + downTime; }
    }

    /** Feeds the events like the filter does; returns those it let through (the app's view), acts into {@code acts}. */
    static List<Ev> run(OkHold h, OkHold.Shown shown, List<Ev> evs, List<OkHold.Act> acts) {
        List<Ev> passed = new ArrayList<>();
        for (Ev e : evs) {
            OkHold.Step s = h.key(e.code == OK || e.code == ENTER, e.down, e.repeat, shown, e.downTime);
            if (!s.consume) passed.add(e);
            if (s.act != OkHold.Act.NONE) acts.add(s.act);
        }
        return passed;
    }

    public static void main(String[] a) {
        long t0 = 10_000;
        // nothing on screen: every key of every code passes, nothing is armed, the timer never acts
        {
            OkHold h = new OkHold();
            boolean all = true;
            for (int code = 0; code < 320; code++) {
                OkHold.Step d = h.key(code == OK || code == ENTER, true, 0, NONE, t0);
                OkHold.Step r = h.key(code == OK || code == ENTER, true, 1, NONE, t0);
                OkHold.Step u = h.key(code == OK || code == ENTER, false, 0, NONE, t0);
                all &= !d.consume && !r.consume && !u.consume && d.due < 0 && d.act == OkHold.Act.NONE
                        && u.act == OkHold.Act.NONE;
            }
            ok("card hidden: no key of codes 0..319 is taken or armed", all);
            h.key(true, true, 0, NONE, t0);
            ok("card hidden: OK held 5 s opens nothing", h.timer(NONE, t0 + 5_000) == OkHold.Act.NONE && h.due() < 0);
            h.key(true, false, 0, NONE, t0);
        }
        // a short OK on the small card: both events pass, in order, unchanged, once; nothing opens
        for (long held : new long[] {1, 80, 250, 599}) {
            OkHold h = new OkHold();
            List<OkHold.Act> acts = new ArrayList<>();
            List<Ev> evs = List.of(new Ev(OK, true, 0, t0), new Ev(OK, false, 0, t0));
            OkHold.Step d = h.key(true, true, 0, CARD, t0);
            ok("short " + held + " ms: DOWN passes at once (not held back)", !d.consume && d.act == OkHold.Act.NONE);
            ok("short " + held + " ms: the timer is due at DOWN + 600", d.due == t0 + OkHold.LONG_MS);
            ok("short " + held + " ms: an early timer does nothing", h.timer(CARD, t0 + held) == OkHold.Act.NONE);
            OkHold.Step u = h.key(true, false, 0, CARD, t0);
            ok("short " + held + " ms: UP passes", !u.consume && u.act == OkHold.Act.NONE && !h.busy());
            ok("short " + held + " ms: the timer after the UP does nothing",
                    h.timer(CARD, t0 + OkHold.LONG_MS) == OkHold.Act.NONE);
            List<Ev> passed = run(new OkHold(), CARD, evs, acts);
            ok("short " + held + " ms: the app sees exactly DOWN, UP", passed.equals(evs) && acts.isEmpty());
        }
        // ten quick presses in a row (a console menu walk): all twenty events pass, nothing opens
        {
            OkHold h = new OkHold();
            List<Ev> evs = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                evs.add(new Ev(OK, true, 0, t0 + i * 150));
                evs.add(new Ev(OK, false, 0, t0 + i * 150));
            }
            List<OkHold.Act> acts = new ArrayList<>();
            List<Ev> passed = run(h, CARD, evs, acts);
            ok("10 quick presses: 20 events through, same order, none twice", passed.equals(evs) && acts.isEmpty());
            ok("10 quick presses: no timer left", h.due() < 0 && h.timer(CARD, t0 + 10_000) == OkHold.Act.NONE);
        }
        // a held OK on the small card opens the panel once, at 600 ms; its events still pass (DOWN went on at once)
        {
            OkHold h = new OkHold();
            ok("long: DOWN passes", !h.key(true, true, 0, CARD, t0).consume);
            ok("long: 599 ms opens nothing", h.timer(CARD, t0 + 599) == OkHold.Act.NONE && h.due() == t0 + 600);
            ok("long: 600 ms opens the panel", h.timer(CARD, t0 + 600) == OkHold.Act.EXPAND);
            ok("long: only once", h.timer(SHEET, t0 + 900) == OkHold.Act.NONE && h.due() < 0);
            ok("long: a repeat of that press passes", !h.key(true, true, 3, SHEET, t0).consume);
            OkHold.Step u = h.key(true, false, 0, SHEET, t0);
            ok("long: its UP passes (stops Android's repeats) and does not act in the panel",
                    !u.consume && u.act == OkHold.Act.NONE && !h.busy());
        }
        // the same edge both ways: the UP and the timer at 600 ms, whichever the main loop runs first wins
        {
            OkHold h = new OkHold();
            h.key(true, true, 0, CARD, t0);
            h.key(true, false, 0, CARD, t0);
            ok("edge: UP before the timer at 600 ms = a short press", h.timer(CARD, t0 + 600) == OkHold.Act.NONE);
            h = new OkHold();
            h.key(true, true, 0, CARD, t0);
            ok("edge: the timer before the UP at 600 ms = the panel", h.timer(CARD, t0 + 600) == OkHold.Act.EXPAND);
            ok("edge: that UP passes", !h.key(true, false, 0, SHEET, t0).consume);
        }
        // ENTER (a keyboard) is OK too
        {
            OkHold h = new OkHold();
            h.key(true, true, 0, CARD, t0);
            ok("ENTER held opens the panel", h.timer(CARD, t0 + 700) == OkHold.Act.EXPAND);
        }
        // another key while OK is held: no panel
        {
            OkHold h = new OkHold();
            h.key(true, true, 0, CARD, t0);
            ok("another key passes", !h.key(false, true, 0, CARD, t0 + 100).consume);
            ok("another key while OK is held: no panel", h.timer(CARD, t0 + 600) == OkHold.Act.NONE);
            ok("its OK UP passes", !h.key(true, false, 0, CARD, t0).consume);
        }
        // the card goes away while OK is held (the popup's time, the pin): nothing opens, the UP passes
        {
            OkHold h = new OkHold();
            h.key(true, true, 0, CARD, t0);
            ok("card gone at 600 ms: nothing opens", h.timer(NONE, t0 + 600) == OkHold.Act.NONE);
            ok("card gone: the UP passes", !h.key(true, false, 0, NONE, t0).consume);
        }
        // a press that began before the filter ran: its UP passes
        ok("a lone UP passes", !new OkHold().key(true, false, 0, CARD, t0).consume);
        // in the full panel OK is the panel's: short = its own OK, held = fold back; the app sees none of it
        {
            OkHold h = new OkHold();
            OkHold.Step d = h.key(true, true, 0, SHEET, t0);
            ok("panel: DOWN taken, timer armed", d.consume && d.due == t0 + 600);
            ok("panel: a repeat taken", h.key(true, true, 1, SHEET, t0).consume);
            OkHold.Step u = h.key(true, false, 0, SHEET, t0);
            ok("panel: short OK = its own OK, UP taken", u.consume && u.act == OkHold.Act.SHEET_OK);
            ok("panel: no fold after a short OK", h.timer(SHEET, t0 + 600) == OkHold.Act.NONE);
            h.key(true, true, 0, SHEET, t0 + 1000);
            ok("panel: 599 ms folds nothing", h.timer(SHEET, t0 + 1599) == OkHold.Act.NONE);
            ok("panel: OK held 600 ms folds back", h.timer(SHEET, t0 + 1600) == OkHold.Act.COLLAPSE);
            ok("panel: the folding press's repeats taken", h.key(true, true, 2, CARD, t0 + 1000).consume);
            OkHold.Step u2 = h.key(true, false, 0, CARD, t0 + 1000);
            ok("panel: the folding press's UP taken, no second action", u2.consume && u2.act == OkHold.Act.NONE);
            ok("panel: other keys pass (BACK, arrows go to the panel window)",
                    !h.key(false, true, 0, SHEET, t0).consume && !h.key(false, false, 0, SHEET, t0).consume);
            h = new OkHold();
            h.key(true, true, 0, SHEET, t0);
            OkHold.Step gone = h.key(true, false, 0, NONE, t0);
            ok("panel closed while its OK is held: the UP still taken, nothing runs",
                    gone.consume && gone.act == OkHold.Act.NONE);
        }
        // the panel window's own OK (filter not running yet)
        ok("window: the UP of the press that opened the panel does nothing", OkHold.windowUp(false, false, 900) == OkHold.Act.NONE);
        ok("window: a cancelled press does nothing", OkHold.windowUp(true, true, 100) == OkHold.Act.NONE);
        ok("window: short OK = its own OK", OkHold.windowUp(true, false, 599) == OkHold.Act.SHEET_OK);
        ok("window: held OK folds back", OkHold.windowUp(true, false, 600) == OkHold.Act.COLLAPSE);
        // the hint: the first three cards, never after the user held OK once
        ok("hint: cards 1 to 3", OkHold.hint(0, false) && OkHold.hint(1, false) && OkHold.hint(2, false));
        ok("hint: not the fourth", !OkHold.hint(3, false));
        ok("hint: never once learned", !OkHold.hint(0, true));
        // opened by the remote key the user gave it: every time until the user expanded a card once
        ok("hint by key: the fourth card too", OkHold.hint(3, false, true) && OkHold.hint(40, false, true));
        ok("hint by key: never once learned", !OkHold.hint(3, true, true) && !OkHold.hint(0, true, true));
        ok("hint not by key: the old rule", !OkHold.hint(3, false, false) && OkHold.hint(2, false, false));
        // PanelToggle: open by holding, fold back into the card it came from
        {
            PanelToggle t = new PanelToggle();
            ok("toggle: nothing to open without a card", t.expand() == PanelMode.HIDDEN && !t.held());
            t.press();
            ok("toggle: card -> panel", t.expand() == PanelMode.DETAIL && t.held());
            ok("toggle: held again -> the card", t.collapse() == PanelMode.COMPACT && !t.held());
            t.expand();
            ok("toggle: BACK folds a held-open panel back", t.back() == PanelMode.COMPACT);
            ok("toggle: the key from the card still opens the panel", t.press() == PanelMode.DETAIL && !t.held());
            ok("toggle: fold does nothing on a key-opened panel", t.collapse() == PanelMode.DETAIL);
            ok("toggle: BACK still closes a key-opened panel", t.back() == PanelMode.HIDDEN);
            t.press();
            t.expand();
            ok("toggle: the key on a held-open panel hides all", t.press() == PanelMode.HIDDEN && !t.held());
            t = new PanelToggle();
            t.signalChanged(true, true);
            t.expand();
            ok("toggle: the popup's time does not close the open panel", t.popupOver() == PanelMode.DETAIL);
            ok("toggle: a popup card folds back into a popup card", t.collapse() == PanelMode.POPUP);
            t = new PanelToggle();
            t.pin(true, true);
            t.expand();
            ok("toggle: pinned card -> panel -> pinned card", t.pinTick(true, 1) == PanelMode.DETAIL
                    && t.collapse() == PanelMode.COMPACT && t.pinTick(true, 2) == PanelMode.COMPACT);
            t.expand();
            t.hide();
            ok("toggle: hide forgets the hold", !t.held() && t.collapse() == PanelMode.HIDDEN);
        }
        System.out.println((fails == 0 ? "ok   " : "FAIL ") + "OkHoldTest: " + (checks - fails) + "/" + checks);
        if (fails > 0) System.exit(1);
    }
}
