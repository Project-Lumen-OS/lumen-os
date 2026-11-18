// SPDX-License-Identifier: Apache-2.0
package org.lumen.system;

import android.content.Context;
import android.media.AudioManager;
import android.provider.Settings;
import android.util.Log;

/**
 * Android's key sounds (Settings.System sound_effects_enabled): the stock sound page's key tone switch (newsettings
 * VoiceRepository.setKeyTone writes the same setting). A change loads or unloads the sound effects as TvSettings does,
 * so the next key press already follows it. Writing needs WRITE_SETTINGS (the projector apps run as system).
 */
public final class KeyTone {
    private static final String TAG = "LumenSystem";

    private KeyTone() { }

    public static boolean on(Context c) {
        return Settings.System.getInt(c.getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED, 1) != 0;
    }

    public static void set(Context c, boolean on) {
        try {
            Settings.System.putInt(c.getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED, on ? 1 : 0);
            AudioManager am = c.getSystemService(AudioManager.class);
            if (on) am.loadSoundEffects();
            else am.unloadSoundEffects();
        } catch (Throwable t) {
            Log.w(TAG, "key sounds " + on + ": " + t);
        }
    }
}
