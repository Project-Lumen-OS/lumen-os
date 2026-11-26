// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.miracast;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * The old address of the screen mirroring settings (action org.lumen.miracast.SETTINGS, opened by Lumen Home's tile,
 * the projector's quick menu and the receiver's notification): it only opens the casting screen of Lumen AirPlay
 * (action org.lumen.cast.SCREEN), where the Miracast rows live next to the AirPlay ones. The extra "edit" = "name"
 * goes along, so a rename still opens the name field at once.
 */
public final class SettingsActivity extends Activity {
    static final String EXTRA_EDIT = "edit";
    static final String CAST_SCREEN = "org.lumen.cast.SCREEN";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            startActivity(castScreen(this, getIntent().getStringExtra(EXTRA_EDIT)));
        } catch (RuntimeException e) {
            Log.w(MiracastService.TAG, "casting screen: " + e);
        }
        finish();
    }

    /** The casting screen, the one settings page of the receiver. */
    static Intent castScreen(Context c, String edit) {
        Intent i = new Intent(CAST_SCREEN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (edit != null) i.putExtra(EXTRA_EDIT, edit);
        return i;
    }
}
