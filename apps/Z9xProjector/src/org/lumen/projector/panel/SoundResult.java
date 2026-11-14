package org.lumen.projector.panel;

/** Outcome of {@link #apply}. */
enum SoundResult {
    /** The read-back shows the profile and it is running. */
    APPLIED,
    /** DTS stored; the vendor applies DTS on the built-in speaker only (current path is not 0). */
    SPEAKER_ONLY,
    /** DTS stored; output-path reporting is not active yet, so the amp still runs Harman. */
    OUTPUT_NOT_READY,
    /** The read-back does not show the profile (or is unknown). */
    NOT_CONFIRMED
}
