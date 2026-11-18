// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import android.content.Context;

import org.lumen.menu.FeatureId;
import org.lumen.menu.MenuSpec;
import org.lumen.menu.MenuState;
import org.lumen.report.ReportScreen;
import org.lumen.signal.SignalPanel;

/**
 * The screens and settings of shared modules the menu handles itself (MenuSpec.MODULE_SCREENS, MODULE_SETTINGS): one
 * place, no device code.
 */
final class ModuleScreens {
    private static final String ON = "on", OFF = "off";

    private ModuleScreens() {}

    private static android.media.AudioManager audio(Context c) {
        return (android.media.AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
    }

    /** The top step of a number setting of the module (the volume: the music stream's maximum). */
    static int max(Context c, FeatureId f) {
        android.media.AudioManager a = f == FeatureId.SOUND_VOLUME ? audio(c) : null;
        return a == null ? 100 : Math.max(1, a.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC));
    }

    static boolean available(Context c, FeatureId f) {
        if (f == FeatureId.SOUND_VOLUME) return audio(c) != null;
        if (f == FeatureId.SIGNAL_PANEL || f == FeatureId.SIGNAL_PIN) return SignalPanel.available(c);
        if (f == FeatureId.SIGNAL_REFRESH) return SignalPanel.receiverInfo(c);
        return f == FeatureId.SETTINGS_REPORT && ReportScreen.available(c);
    }

    static boolean open(Context c, FeatureId f) {
        if (f == FeatureId.SIGNAL_PANEL) return SignalPanel.press(c);
        return f == FeatureId.SETTINGS_REPORT && ReportScreen.open(c);
    }

    /** The module setting's value: "on" / "off" (TOGGLE) or an option token (CHOICE). */
    static String get(Context c, FeatureId f) {
        if (f == FeatureId.SIGNAL_PIN) return SignalPanel.pinned(c) ? ON : OFF;
        if (f == FeatureId.SIGNAL_REFRESH) return SignalPanel.refresh(c);
        if (f == FeatureId.SOUND_VOLUME) {
            android.media.AudioManager a = audio(c);
            return a == null ? null : Integer.toString(a.getStreamVolume(android.media.AudioManager.STREAM_MUSIC));
        }
        return null;
    }

    /** Writes a module setting; false when {@code f} is not one. */
    static boolean set(Context c, FeatureId f, String value) {
        if (f == FeatureId.SIGNAL_PIN) SignalPanel.setPinned(c, ON.equals(value));
        else if (f == FeatureId.SIGNAL_REFRESH) SignalPanel.setRefresh(c, value);
        else if (f == FeatureId.SOUND_VOLUME) {               // the level the remote's volume keys move (the music stream)
            android.media.AudioManager a = audio(c);
            if (a == null) return false;
            try {
                a.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, Integer.parseInt(value.trim()), 0);
            } catch (RuntimeException e) {
                return false;
            }
        }
        else return false;
        return true;
    }

    /** {@code st} with the module settings' values on top (null stays null: no device state, no values). */
    static MenuState merge(Context c, MenuState st) {
        if (st == null) return null;
        MenuState.Builder b = MenuState.builder();
        java.util.Set<FeatureId> mine = new java.util.LinkedHashSet<>(MenuSpec.MODULE_SETTINGS);
        mine.addAll(MenuSpec.EVERY_MODEL);
        for (FeatureId f : mine) {
            String v = get(c, f);
            if (v != null) b.put(f, v);
        }
        return st.with(b.build());
    }
}
