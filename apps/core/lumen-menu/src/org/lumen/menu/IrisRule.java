// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

import org.lumen.menu.ProjectorFeatures.Note;

/**
 * The light level and the lens iris (stock newsettings DisplayMutexManager.setDlpLumenLevel, the same code on every
 * model with an iris): while the iris reads Dynamic, f5.5 or f7 the vendor holds the light level (pq database chain
 * aperture_f_num to lumens_level) and stock refuses the slider with a note. The menu does the same before the slider
 * moves, for any model whose profile has both rows; an adapter refuses a light level write with {@link #heldNote} too.
 */
public final class IrisRule {
    private IrisRule() {}

    /** Iris options that hold the light level. */
    static final String DYNAMIC = "dynamic", F5_5 = "f5_5", F7 = "f7";
    /** The notes (string resource names of lumen-menu): the dynamic iris, a small fixed iris. */
    static final String NOTE_DYNAMIC = "lumen_m_iris_lamp_dynamic", NOTE_HELD = "lumen_m_iris_lamp_held";

    /** The note of an iris option that holds the light level, or null when the light level is free. */
    public static String heldNote(String iris) {
        if (DYNAMIC.equals(iris)) return NOTE_DYNAMIC;
        if (F5_5.equals(iris) || F7.equals(iris)) return NOTE_HELD;
        return null;
    }

    /** The refusal of a light level step in {@code now}, or null when the slider may move. */
    public static Note blocked(MenuProfile profile, FeatureId feature, MenuState now) {
        if (FeatureId.LAMP != feature || profile == null || now == null || !profile.has(FeatureId.PICTURE_IRIS)) return null;
        String note = heldNote(now.get(FeatureId.PICTURE_IRIS));
        return note == null ? null : Note.of(note);
    }
}
