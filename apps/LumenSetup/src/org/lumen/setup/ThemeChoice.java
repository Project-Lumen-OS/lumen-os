package org.lumen.setup;

import org.lumen.ui.ThemeMode;

/**
 * The theme step of Setup 2.0 (right after the language): the three choices in their order and what the live preview
 * shows for each. The choice itself is the shared lumen_ui_theme of lumen-ui (LumenTheme), so Home, the quick menu and
 * the wizard switch together. Pure Java (test/flow).
 */
public final class ThemeChoice {
    private ThemeChoice() {}

    /** Светлая (the default), Тёмная, Авто: the rows from top to bottom. */
    public static final ThemeMode[] ORDER = {ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.AUTO};

    /** Part of the preview drawn from the light picture: all for Светлая, none for Тёмная, half (a diagonal) for Авто. */
    public static float lightShare(ThemeMode m) {
        switch (m) {
            case DARK: return 0f;
            case AUTO: return 0.5f;
            default: return 1f;
        }
    }

    /** The row focused when the step opens: the stored choice, Светлая when nothing is stored yet. */
    public static int initialRow(ThemeMode stored) {
        for (int i = 0; i < ORDER.length; i++) if (ORDER[i] == stored) return i;
        return 0;
    }

    /** True when the screen has to change its colours after a pick: the palette on screen is not the one chosen. */
    public static boolean needsRetheme(boolean darkOnScreen, boolean darkChosen) {
        return darkOnScreen != darkChosen;
    }
}
