// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import java.util.ArrayDeque;

/**
 * The a test Z9X equalizer page (1.1.0-beta2): the preset blinks and cannot be changed. sh test/sound/run.sh
 *
 * A model of the chain with the real RepaintGate: MenuController.refresh reads, the read comes back on the main
 * thread (stateOf -> Z9xMenuPages.applyEq -> MenuController.refreshPage(eqPage), then apply), every QpdPanel render
 * runs the page's shown hook (eqPage.setOnShown(menu::refresh)). Old wiring: only apply() repainted quietly, so the
 * equalizer page read without end and every read put the saved preset back over the user's step before its commit.
 */
public final class RepaintGateTest {
    private static int pass, fail;

    private static void ok(boolean c, String what) {
        if (c) pass++; else { fail++; System.out.println("FAIL " + what); }
    }

    /** The menu, the panel and the equalizer page; {@code refreshPageQuiet}: refreshPage repaints through the gate. */
    static final class Menu {
        final RepaintGate gate = new RepaintGate();
        final boolean refreshPageQuiet;
        final ArrayDeque<Runnable> main = new ArrayDeque<>();   // read results posted back to the main thread
        int reads, renders;
        int saved = 0, shown = 0;                               // the stored preset, the preset row
        boolean pending;                                        // a step waits for its 300 ms commit
        final boolean keepPending;

        Menu(boolean refreshPageQuiet, boolean keepPending) {
            this.refreshPageQuiet = refreshPageQuiet;
            this.keepPending = keepPending;
        }

        void refresh() {                                        // MenuController.refresh
            if (gate.quiet()) return;
            reads++;
            main.add(this::readBack);
        }

        void readBack() {                                       // stateOf: the eq listener first, then apply()
            applyEq();
            gate.repaint(this::render);
        }

        void applyEq() {                                        // Z9xMenuPages.applyEq
            if (!SoundState.eqKeepRows(4, 4, false, keepPending && pending)) shown = saved;
            if (refreshPageQuiet) gate.repaint(this::render); else render();
        }

        void render() {                                         // QpdPanel.render: the shown hook of the top page
            renders++;
            refresh();
        }

        void step(int to) { shown = to; pending = true; }       // ChoiceRow.changeTo with a commit delay

        void commit() { pending = false; saved = shown; }       // ChoiceRow commit -> SoundEq.savePreset(shown)

        /** Runs the main thread until idle or {@code max} posted reads. */
        int drain(int max) {
            int n = 0;
            while (!main.isEmpty() && n < max) { main.poll().run(); n++; }
            return n;
        }
    }

    public static void main(String[] a) {
        gate();
        ownerBlink();
        System.out.println("RepaintGateTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    static void gate() {
        RepaintGate g = new RepaintGate();
        ok(!g.quiet(), "gate open at start");
        boolean[] in = new boolean[2];
        g.repaint(() -> { in[0] = g.quiet(); g.repaint(() -> in[1] = g.quiet()); });
        ok(in[0] && in[1], "quiet during a repaint, nested too");
        ok(!g.quiet(), "open again after nested repaints");
        try { g.repaint(() -> { throw new RuntimeException("x"); }); } catch (RuntimeException e) { /* expected */ }
        ok(!g.quiet(), "open again after a repaint that threw");
    }

    static void ownerBlink() {
        // the old wiring: the page reads again and again while it is open (the blink)
        Menu old = new Menu(false, false);
        old.refresh();                                          // the page is shown
        ok(old.drain(200) == 200 && !old.main.isEmpty(), "old wiring: the equalizer page reads without end");
        old.step(1);                                            // RIGHT: Flat -> Bass
        old.drain(1);                                           // one read lands before the 300 ms commit
        old.commit();
        ok(old.saved == 0, "old wiring: the step is put back and the old preset committed (owner case)");

        // the fixed wiring: one read per showing, the step stays and is committed
        Menu m = new Menu(true, true);
        m.refresh();
        ok(m.drain(200) == 1 && m.main.isEmpty() && m.reads == 1, "one read when the page is shown (got " + m.reads + ")");
        m.step(1);
        m.refresh();                                            // any read while the step waits (another trigger)
        m.drain(200);
        ok(m.shown == 1, "a read during the pending step keeps it on the row");
        m.commit();
        ok(m.saved == 1, "the step is committed");
        m.refresh();
        m.drain(200);
        ok(m.shown == 1 && m.reads == 3, "a later read shows the committed preset, no loop (reads " + m.reads + ")");

        // the repaint gate alone already ends the loop; the pending guard alone does not
        Menu g = new Menu(true, false);
        g.refresh();
        ok(g.drain(200) == 1, "the gate alone: one read");
        Menu p = new Menu(false, true);
        p.refresh();
        ok(p.drain(200) == 200, "the pending guard alone: still a loop");
    }
}
