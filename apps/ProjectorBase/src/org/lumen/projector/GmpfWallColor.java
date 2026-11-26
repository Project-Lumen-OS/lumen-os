// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector;

import android.content.Context;
import android.util.Log;

import org.lumen.wallcolor.WallColor;
import org.lumen.wallcolor.WallColorStep;
import org.lumen.wallcolor.WallColorHal;

/**
 * The gmpf apps' part of the wall colour adaptation (apps/core/lumen-wallcolor), Z9X and HB alike: the four stock
 * calls through the shared clients (GmpfBase / Gmpf2Base) on the HAL thread, the vendor's focus events (already
 * registered by the app) routed to the module.
 */
public final class GmpfWallColor implements WallColorHal {
    private static final String TAG = "LumenWallColor";

    /** App.onCreate: the module with this HAL. */
    public static void install(Context app) {
        WallColor.install(app, new GmpfWallColor());
        Hal.addFocusEventListener(WallColor::onFocusEvent);
    }

    @Override public void call(WallColorStep c, Done done) {
        boolean queued = Hal.run((g, g2) -> {
            boolean ok = false;
            try {
                switch (c) {
                    case AE_OFF:
                        g2.setAutoExposure(false);
                        ok = true;
                        break;
                    case START:
                        ok = g.startAutoColor();
                        break;
                    case USE_MATCHED:
                        ok = g.useAutoColorMatched();
                        break;
                    default:
                        ok = g.useAutoColorOriginal();
                }
            } catch (Exception e) {
                Log.w(TAG, c + ": " + e);
            } finally {
                done.done(ok);
            }
        });
        if (!queued) done.done(false);
    }
}
