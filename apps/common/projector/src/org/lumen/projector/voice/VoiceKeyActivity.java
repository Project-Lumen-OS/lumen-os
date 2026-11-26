// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.voice;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * The voice key's text field (All settings, General, Voice assistant): a see-through screen that shows only the key
 * dialog of VoiceSettings and closes with it. An overlay menu cannot hold a text field with the keyboard.
 */
public final class VoiceKeyActivity extends Activity {
    public static boolean open(Context c) {
        try {
            c.startActivity(new Intent(c, VoiceKeyActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Throwable t) {
            Log.w("VoiceKey", "open: " + t);
            return false;
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        VoiceSettings.editKey(this, this::finish);
    }
}
