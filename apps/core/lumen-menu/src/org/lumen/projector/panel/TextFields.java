// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.text.InputType;

import org.lumen.projector.R;
import org.lumen.system.Hotspot;
import org.lumen.system.HotspotProblem;
import org.lumen.system.HotspotRules;

/** The texts the shared edit screen knows, by key (the screen is started with the key, never with code). */
final class TextFields {
    static final String HOTSPOT_NAME = "hotspot_name", HOTSPOT_PASSWORD = "hotspot_password";
    /** The name of a new user picture mode (CustomPictures). */
    static final String PICTURE_MODE_NAME = "picture_mode_name";

    private TextFields() {}

    /** The field of {@code key}, null for an unknown key. */
    static TextField of(String key) {
        if (HOTSPOT_NAME.equals(key)) return hotspot(true);
        if (HOTSPOT_PASSWORD.equals(key)) return hotspot(false);
        if (PICTURE_MODE_NAME.equals(key)) return CustomPictures.nameField();
        return null;
    }

    /** The message of a refused hotspot name or password. */
    static int message(HotspotProblem p) {
        if (p == null) return 0;
        switch (p) {
            case NAME_EMPTY: return R.string.lumen_m_hotspot_name_empty;
            case NAME_LONG: return R.string.lumen_m_hotspot_name_long;
            case PASSWORD_SHORT: return R.string.lumen_m_hotspot_password_short;
            case PASSWORD_LONG: return R.string.lumen_m_hotspot_password_long;
            default: return R.string.lumen_m_hotspot_password_chars;
        }
    }

    /** The stock hotspot name or password: saved with the other one as it is now (a running hotspot restarts). */
    private static TextField hotspot(boolean name) {
        return new TextField() {
            @Override public int title() { return name ? R.string.lumen_m_hotspot_name_title : R.string.lumen_m_hotspot_password_title; }

            @Override public int hint() { return name ? 0 : R.string.lumen_m_hotspot_password_hint; }

            @Override public int inputType() {
                return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                        | (name ? 0 : InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            }

            @Override public String value(Context c) {
                String[] cfg = Hotspot.config(c);
                return cfg == null ? null : cfg[name ? 0 : 1];
            }

            @Override public int problem(String text) {
                return message(name ? HotspotRules.name(text) : HotspotRules.password(text));
            }

            @Override public boolean save(Context c, String text) {
                String[] cfg = Hotspot.config(c);
                if (cfg == null) return false;
                return Hotspot.save(c, name ? text : cfg[0], name ? cfg[1] : text, ModuleScreens.hotspotNotes(c));
            }
        };
    }
}
