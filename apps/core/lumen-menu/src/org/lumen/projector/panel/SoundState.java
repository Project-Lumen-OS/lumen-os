package org.lumen.projector.panel;

/**
 * What the Sound page shows for "Sound enhancement" and the equalizer, from the vendor reads (plain Java, host tested
 * by sh test/sound/run.sh).
 *
 * Stock (settings SoundHelper.getDtsModeV5 over GmAudioManager.getSoundProcessTypeFusion): 43 on reads as 100 (the
 * old DTS switch, no row of the Z9X list), 26 == 5 is DTS Virtual:X, every other process is Harman Kardon; the row
 * shows what is STORED, whether or not it plays yet. Lumen showed DTS only once the output path was reported (59) and
 * Harman otherwise. On the owner's trial (no audio at all, so never reported) he chose DTS: the row went back to
 * "Harman Kardon" while the equalizer said "Off (DTS)", and choosing Harman Kardon was no change of the row, so
 * nothing was sent and he could not get out. Now the row follows stock: DTS stays DTS, Harman is a real choice, and a
 * hint line says when DTS is stored but not running (output not reported yet, or not the speaker).
 *
 * Lumen keeps one difference on purpose: a process that is neither 2 nor 5 (or 43 on, or a failed read) shows no
 * choice ("—") instead of stock's Harman, so choosing either option sends it.
 */
final class SoundState {
    private SoundState() {}

    /** IGmpf 26 process values (GmpfClient.SoundProcess). */
    static final int HARMAN = 2, DTS_VX = 5;
    /** Indexes of the Sound enhancement row (SoundProfiles.ORDER). */
    static final int ROW_HARMAN = 0, ROW_DTS = 1;

    /** The row's choice: ROW_HARMAN, ROW_DTS, or -1 ("—": unknown, 43 on, another process). */
    static int enhancementRow(Integer process, Boolean dts43) {
        if (dts43 == null || dts43 || process == null) return -1;
        if (process == DTS_VX) return ROW_DTS;
        if (process == HARMAN) return ROW_HARMAN;
        return -1;
    }

    /** DTS Virtual:X is stored (26 == 5, 43 off), running or not. */
    static boolean dtsStored(Integer process, Boolean dts43) {
        return Boolean.FALSE.equals(dts43) && process != null && process == DTS_VX;
    }

    static final int HINT_NONE = 0, HINT_NOT_READY = 1, HINT_SPEAKER_ONLY = 2, HINT_UNKNOWN = 3;

    /**
     * The hint line under the row: DTS stored but the output path not reported yet (it starts once the output is
     * ready), DTS stored on another output than the speaker (speaker only), the state unknown (choose to re-apply).
     */
    static int hint(Integer process, Boolean dts43, boolean pathActive, int path, int speakerPath) {
        if (process == null) return HINT_NONE;
        if (dtsStored(process, dts43)) {
            if (!pathActive) return HINT_NOT_READY;
            if (path != speakerPath) return HINT_SPEAKER_ONLY;
            return HINT_NONE;
        }
        return enhancementRow(process, dts43) < 0 ? HINT_UNKNOWN : HINT_NONE;
    }

    /** The equalizer works in Harman (AMP) mode only: in DTS the vendor's DTS tuning owns the same PEQ. */
    static boolean eqUsable(Integer process, Boolean dts43) {
        return Boolean.FALSE.equals(dts43) && process != null && process == HARMAN;
    }

    /**
     * The row after the user picked {@code row}: the other option is always a real change of the row (the stuck
     * state was a stored DTS shown as Harman, so picking Harman changed nothing). True when picking it reaches the
     * listener (ChoiceRow commits only a changed index).
     */
    static boolean canChoose(Integer process, Boolean dts43, int row) {
        return row != enhancementRow(process, dts43);
    }
}
