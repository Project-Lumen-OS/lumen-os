// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;


import java.nio.charset.StandardCharsets;
import org.lumen.miracast.core.WfdQuality;

/** Receiver settings (device-protected storage: readable before the first unlock, at boot). */
final class Prefs {
    private Prefs() {}

    static final String ENABLED = "enabled";
    static final String ASK = "ask";
    static final String NAME = "name";
    static final String QUALITY = "quality";
    static final String NOTIF_ASKED = "notif_asked";

    static final boolean DEF_ENABLED = true;
    static final boolean DEF_ASK = true;

    /** RTP port offered to the source (even; RTCP would be +1). */
    static final int RTP_PORT = 19000;
    /** WFD session management control port we put in our WFD IE (the source's own port comes from its IE). */
    static final int CONTROL_PORT = 7236;

    static SharedPreferences get(Context c) {
        return c.createDeviceProtectedStorageContext().getSharedPreferences("miracast", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return get(c).getBoolean(ENABLED, DEF_ENABLED);
    }

    static boolean ask(Context c) {
        return get(c).getBoolean(ASK, DEF_ASK);
    }

    static WfdQuality quality(Context c) {
        return WfdQuality.parse(get(c).getString(QUALITY, WfdQuality.P1080_60.name()));
    }

    /**
     * The name phones and computers list: the user's own, else the system device name (Settings >
     * Device name), else "Lumen". Wi-Fi Direct device names are at most 32 bytes of UTF-8.
     */
    static String name(Context c) {
        String n = get(c).getString(NAME, null);
        if (n == null || n.trim().isEmpty()) n = systemName(c);
        if (n == null || n.trim().isEmpty()) n = "Lumen";
        return clip32(n.trim());
    }

    /** Settings > Device name (Settings.Global device_name, as AirPlay), clipped like a Wi-Fi Direct name; or null. */
    static String systemName(Context c) {
        try {
            String n = Settings.Global.getString(c.getContentResolver(), Settings.Global.DEVICE_NAME);
            return n == null || n.trim().isEmpty() ? null : clip32(n.trim());
        } catch (Throwable t) {
            return null;
        }
    }

    static String clip32(String s) {
        StringBuilder out = new StringBuilder();
        int bytes = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            int b = ch.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + b > 32) break;
            out.append(ch);
            bytes += b;
            i += Character.charCount(cp);
        }
        return out.toString();
    }
}
