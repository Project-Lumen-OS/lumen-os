// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.ui;

import android.content.Context;

import org.lumen.menu.LampOff;

/** The app's one {@link LampOff}: the device's light calls and a boot mark in the app's preferences. */
public final class LampOffHost {
    private static final String PREFS = "lumen_lamp_off";
    private static LampOff sLampOff;

    private LampOffHost() {}

    /** The first call creates it and restores the state of this boot (App start), later calls return the same one. */
    public static synchronized LampOff get(Context c, LampOff.Light light) {
        if (sLampOff == null) {
            sLampOff = new LampOff(light, new BootMark(c.getApplicationContext(), PREFS));
            sLampOff.restore();
        }
        return sLampOff;
    }
}
