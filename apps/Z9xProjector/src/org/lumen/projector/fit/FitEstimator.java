package org.lumen.projector.fit;

/** Which picture the lit area is (section 4.3). */
public enum FitEstimator {
    /** A: the corrected keystone corners, mapped onto the screen. */
    CORNERS,
    /** B, keystone off: the lit picture is the whole trapezoid of the panel. */
    FULL_PANEL,
    /** B, keystone active but corners unknown: the largest 16:9 rectangle inside the trapezoid. */
    INSCRIBED
}
