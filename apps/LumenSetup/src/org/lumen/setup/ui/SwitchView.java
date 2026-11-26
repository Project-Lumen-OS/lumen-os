// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.setup.ui;

import android.content.Context;

/**
 * On/off mark at the end of a row: the Lumen icons toggle_on / toggle_off (stock Android TV pattern),
 * in the row's colours. Give it 48 px.
 */
public class SwitchView extends Icon {
    private boolean mOn;

    public SwitchView(Context c) {
        super(c, TOGGLE_OFF);
    }

    public boolean isOn() { return mOn; }

    public void setOn(boolean on, boolean animate) {
        mOn = on;
        type(on ? TOGGLE_ON : TOGGLE_OFF);
    }
}
