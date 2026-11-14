// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.lumen.projector.panel.GmLicences;

/**
 * The shared Licences screen (GmLicences) as an activity of its own, for a device app whose menu has no Projector
 * settings screen to put it in (the X50: menu.json feature settings.licences, General). The device app declares it
 * (not exported, a translucent theme); it shows the Licences dialog and finishes when the dialog closes (OK or
 * BACK). Opens no window of its own past the dialog and sends nothing to the device.
 */
public final class LicencesActivity extends Activity {
    private static final String TAG = "LumenMenu";

    /** Starts the screen from any context; false when it could not start. */
    public static boolean open(Context c) {
        try {
            c.startActivity(new Intent(c, LicencesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "licences: " + t);
            return false;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        AlertDialog d = GmLicences.open(this);
        if (d == null) {
            finish();
            return;
        }
        d.setOnDismissListener(x -> finish());
    }
}
