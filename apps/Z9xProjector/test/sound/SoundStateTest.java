package org.lumen.projector.panel;

import org.lumen.projector.audio.AudioPortRulesProbe;

/**
 * Host tests of the Sound page state (SoundState) and the audio port rules (audio.AudioPortRules through a probe in
 * its package). sh test/sound/run.sh
 */
public final class SoundStateTest {
    private static int pass, fail;

    private static void ok(boolean c, String what) {
        if (c) pass++; else { fail++; System.out.println("FAIL " + what); }
    }

    private static void eq(Object a, Object b, String what) {
        ok(a == null ? b == null : a.equals(b), what + " (got " + a + ", want " + b + ")");
    }

    static final int SPK = 0, ARC = 1;

    public static void main(String[] a) {
        stock();
        ownerCase();
        canLeave();
        hints();
        eq();
        AudioPortRulesProbe.run(SoundStateTest::ok);
        System.out.println("SoundStateTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    /** Stock getDtsModeV5: 26 == 5 is DTS Virtual:X, 2 Harman, 43 on is no row of the list. */
    static void stock() {
        eq(SoundState.enhancementRow(5, false), SoundState.ROW_DTS, "26=5: DTS Virtual:X");
        eq(SoundState.enhancementRow(2, false), SoundState.ROW_HARMAN, "26=2: Harman Kardon");
        eq(SoundState.enhancementRow(5, true), -1, "43 on: no row (stock 100)");
        eq(SoundState.enhancementRow(2, true), -1, "43 on with Harman stored: no row");
        eq(SoundState.enhancementRow(null, false), -1, "26 not read");
        eq(SoundState.enhancementRow(5, null), -1, "43 not read");
        eq(SoundState.enhancementRow(0, false), -1, "another process: no row, choosing re-applies");
        eq(SoundState.ROW_HARMAN, 0, "row order Harman first (SoundProfiles.ORDER)");
        eq(SoundState.ROW_DTS, 1, "row order DTS second");
        eq(SoundState.HARMAN, 2, "Harman wire");
        eq(SoundState.DTS_VX, 5, "DTS Virtual:X wire");
    }

    /** The owner's trial: DTS chosen while the output path is never reported (no audio). */
    static void ownerCase() {
        int row = SoundState.enhancementRow(5, false);
        eq(row, SoundState.ROW_DTS, "DTS stored, path not reported: the row still says DTS (was Harman)");
        ok(!SoundState.eqUsable(5, false), "the equalizer is off in DTS");
        ok(SoundState.dtsStored(5, false), "equalizer value says Off (DTS)");
        eq(SoundState.hint(5, false, false, -1, SPK), SoundState.HINT_NOT_READY, "hint: DTS starts once the output is ready");
        ok(SoundState.canChoose(5, false, SoundState.ROW_HARMAN), "Harman Kardon is a real choice from there (the way out)");
    }

    /** From every stored state, the option that is not stored is a real change of the row. */
    static void canLeave() {
        Integer[] procs = {2, 5};
        for (Integer p : procs) {
            int other = p == 5 ? SoundState.ROW_HARMAN : SoundState.ROW_DTS;
            ok(SoundState.canChoose(p, false, other), "26=" + p + ": the other option can be chosen");
            ok(!SoundState.canChoose(p, false, SoundState.enhancementRow(p, false)), "26=" + p + ": the stored one is shown");
            // the row never contradicts the equalizer: Harman shown <=> equalizer usable
            ok((SoundState.enhancementRow(p, false) == SoundState.ROW_HARMAN) == SoundState.eqUsable(p, false),
                    "26=" + p + ": row and equalizer agree");
        }
        for (Integer p : new Integer[] {null, 0, 1, 3, 100}) {
            for (Boolean d : new Boolean[] {null, true, false}) {
                if (SoundState.enhancementRow(p, d) < 0) {
                    ok(SoundState.canChoose(p, d, 0) && SoundState.canChoose(p, d, 1), "unknown " + p + "/" + d + ": both choosable");
                }
            }
        }
    }

    static void hints() {
        eq(SoundState.hint(5, false, true, SPK, SPK), SoundState.HINT_NONE, "DTS running on the speaker: no hint");
        eq(SoundState.hint(5, false, true, ARC, SPK), SoundState.HINT_SPEAKER_ONLY, "DTS on another output: speaker only");
        eq(SoundState.hint(2, false, false, -1, SPK), SoundState.HINT_NONE, "Harman: no hint");
        eq(SoundState.hint(0, false, true, SPK, SPK), SoundState.HINT_UNKNOWN, "other process: unknown hint");
        eq(SoundState.hint(5, true, true, SPK, SPK), SoundState.HINT_UNKNOWN, "43 on: unknown hint");
        eq(SoundState.hint(null, false, true, SPK, SPK), SoundState.HINT_NONE, "no read: no hint");
    }

    static void eq() {
        ok(SoundState.eqUsable(2, false), "equalizer in Harman");
        ok(!SoundState.eqUsable(2, true), "no equalizer with 43 on");
        ok(!SoundState.eqUsable(null, false), "no equalizer without a read");
        ok(!SoundState.dtsStored(5, true), "43 on is not DTS stored");
        ok(!SoundState.dtsStored(2, false), "Harman is not DTS stored");
    }
}
