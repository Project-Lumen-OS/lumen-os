package org.lumen.projector.dream;

/** What happens to the active screensaver (screensaver on). */
enum DreamActive {
    /** unset or a previous default: the sky becomes the active screensaver */
    SKY,
    /** unset, and the sky is not an installed dream: the v1 default (our clock) */
    CLOCK,
    /** the sky already */
    ALREADY,
    /** the user's choice (or a previous default while the sky is missing): kept */
    KEEP
}
