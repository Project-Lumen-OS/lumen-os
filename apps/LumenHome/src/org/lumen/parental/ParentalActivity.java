// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.parental;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * The old address of the parental controls (action org.lumen.parental.SETTINGS, kept for anything that still opens
 * it): the parent page is the Parental controls page of the projector menu on every model (lumen-menu ParentalPage,
 * the state through {@link ParentalProvider}), so this only asks the projector app to show that page and closes.
 */
public final class ParentalActivity extends Activity {
    /** The projector apps' menu request (Lumen Home's tiles send the same), the menu place as "section". */
    static final String SHOW_PANEL = "org.lumen.projector.action.SHOW_PANEL";
    static final String PAGE = "settings.parental";
    private static final String[] PROJECTOR_APPS = {"org.lumen.projector", "org.lumen.x50.projector"};

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        for (String pkg : PROJECTOR_APPS) {
            sendBroadcast(new Intent(SHOW_PANEL).setPackage(pkg).putExtra("section", PAGE)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND));
        }
        finish();
    }
}
