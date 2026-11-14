package org.lumen.projector.display;

/** What the first start in a new resolution does with the change marker. */
enum UiResKeep {
    /** no change pending */
    NONE,
    /** still the boot that asked for it (the restart has not happened): keep the marker */
    SAME_BOOT,
    /** the new resolution runs: ask "Keep this resolution?" */
    ASK,
    /** another resolution runs (the boot fallback, or the image cannot apply it): drop, no question */
    DROP
}
