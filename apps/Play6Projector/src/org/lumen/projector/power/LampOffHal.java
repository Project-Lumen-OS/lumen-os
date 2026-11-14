// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.power;

import android.content.Context;

import org.lumen.menu.LampOff;
import org.lumen.projector.ui.LampOffHost;

import java.util.function.Consumer;

/**
 * The Play 6 light calls of the shared "Turn off screen" ({@link LampOff}), as its stock sends them (xgimi.api.version
 * 324, below 492: GmDisplayManager.setScreenOnOffWithAction falls back to setScreenOnOff): off = IGmpf 195(false) read
 * back by 196 ({@link Lamp#off}), on = 195(true) as the stock kPPowerPress ({@link Lamp#on}).
 */
public final class LampOffHal implements LampOff.Light {
    private static final LampOffHal LIGHT = new LampOffHal();

    private LampOffHal() {}

    /** The app's LampOff. The first call restores the state of this boot (PowerKey.install). */
    public static LampOff get(Context c) { return LampOffHost.get(c, LIGHT); }

    @Override public void off(Consumer<Boolean> done) {
        Lamp.off("screen off", on -> done.accept(Boolean.FALSE.equals(on)));
    }

    @Override public void on() {
        Lamp.on("screen off ends", on -> { });
    }
}
