// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

/**
 * The look of a row stock keeps closed now ({@link ProjectorFeatures#locked}: the picture settings page rows in the AI
 * or Performance picture mode or while the game mode runs). Stock draws a closed cell with its text at alpha 110 of
 * 255 (newsettings ChoiceCell.setThemeColor, the disabled colours of ArrowCellView), so the row looks off at once. The
 * row keeps its value and stays focusable: a press answers the stock toast through {@link ProjectorFeatures#blocked}
 * and nothing moves. Plain Java, host tested (test/LockedRowTest), used by MenuController.apply.
 */
public final class LockedRow {
    private LockedRow() {}

    /** The stock disabled text alpha (110 of 255). */
    public static final float ALPHA = 110f / 255f;

    /** Is {@code f} closed now? Null state (nothing read) or no feature never closes a row. */
    public static boolean locked(ProjectorFeatures dev, FeatureId f, MenuState now) {
        return dev != null && f != null && now != null && dev.locked(f, now) != null;
    }

    /** The alpha of an enabled row: dimmed while closed, full otherwise. */
    public static float alpha(boolean locked) { return locked ? ALPHA : 1f; }
}
