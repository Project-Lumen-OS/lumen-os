// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.ui;

import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;

/**
 * The key sounds of every Lumen menu, as the stock XGIMI menus play them (release_838_newsettings, cell framework
 * com.xgimi.view.cell gmc/gb): a focus step plays the navigation sound of its direction (UP, DOWN, LEFT, RIGHT) and an
 * OK on the focused item plays the click. Nothing else sounds: a slider or choice step that keeps the focus is silent.
 * The sound goes through View.playSoundEffect, so the system switch "Button sounds" (sound_effects_enabled) and the
 * system volume decide as for every Android view.
 *
 * <p>Screens whose own code moves the focus (the menu panels, Home's focus map) call these; a key the views leave to
 * Android's own focus search already sounds there (ViewRootImpl.performFocusNavigation) and must not call them.
 */
public final class KeySound {
    /** No sound for this key. */
    public static final int NONE = -1;

    private KeySound() {}

    /** The navigation sound of a D-pad key, NONE for any other key. */
    public static int forMove(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: return SoundEffectConstants.NAVIGATION_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return SoundEffectConstants.NAVIGATION_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return SoundEffectConstants.NAVIGATION_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return SoundEffectConstants.NAVIGATION_RIGHT;
            default: return NONE;
        }
    }

    /** True for the keys that confirm the focused item (OK, Enter). */
    public static boolean isOk(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    /** The focus moved by {@code keyCode} onto {@code to}: its navigation sound. */
    public static void move(View to, int keyCode) {
        int s = forMove(keyCode);
        if (to != null && s != NONE) to.playSoundEffect(s);
    }

    /** OK on {@code on}: the click. */
    public static void click(View on) {
        if (on != null) on.playSoundEffect(SoundEffectConstants.CLICK);
    }
}
