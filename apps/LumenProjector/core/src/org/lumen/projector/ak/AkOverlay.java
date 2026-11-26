// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.ak;

import android.content.Context;

import org.lumen.projector.Family;

/**
 * The auto-keystone overlay seam of every projector: the Hal facade hands it each AK event (105-118, 120), other
 * modules ask {@link #isActive()} before they open a panel, start a motor or take a key. What it does is the HAL
 * family's: the gmpf families draw the stock AK screens ({@link AkScreen}), the Play 6 vendor runs its real-time AK
 * with no app (never active, the events are only logged with their timing).
 */
public final class AkOverlay {
    private AkOverlay() {}

    /** App.onCreate (main thread). */
    public static void install(Context ctx) {
        Family.akInstall(ctx);
    }

    /** The UI language changed: the captions are drawn again. */
    public static void onLocaleChanged() {
        Family.akLocaleChanged();
    }

    /** The AK overlay is up (also for a vendor-started AK). Any thread. */
    public static boolean isActive() {
        return Family.akActive();
    }

    /** One AK event (main thread, before the other listeners); true = handled. */
    public static boolean onFocusEvent(int type, String value) {
        return Family.akEvent(type, value);
    }
}
