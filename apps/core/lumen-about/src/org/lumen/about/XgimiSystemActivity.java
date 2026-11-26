// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.about;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * The address of Back to the XGIMI system that Settings > About opens (AboutProvider names this class, so it stays
 * exported): the screen is a page of the projector menu on every projector (lumen-menu XgimiSystemPage), so this only
 * asks this app's menu for that place and closes. No screen of its own.
 */
public final class XgimiSystemActivity extends Activity {
    /** The menu request of the projector apps (Lumen Home's tiles send the same), the place as "section". */
    static final String SHOW_PANEL = "org.lumen.projector.action.SHOW_PANEL";
    /** lumen-menu MenuRequest.XGIMI_SYSTEM */
    static final String PLACE = "settings.xgimi_system";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sendBroadcast(new Intent(SHOW_PANEL).setPackage(getPackageName()).putExtra("section", PLACE)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND));
        finish();
    }
}
