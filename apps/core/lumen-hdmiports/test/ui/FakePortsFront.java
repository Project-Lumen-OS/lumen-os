// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hdmiports.fake;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

/** Another app in front of Home for the walks (Home stopped, its port watch closed). */
public class FakePortsFront extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        View v = new View(this);
        v.setBackgroundColor(0xFF404040);
        setContentView(v);
    }
}
