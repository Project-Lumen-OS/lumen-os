// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.cec;

import android.content.Context;
import android.os.Handler;
import parity.World;

/** The Z9X / HB power policy as the page sees it: its worker thread, its storm pause, install. */
public final class CecPolicy {
    private static final Handler WORKER = new Handler("worker");
    private CecPolicy() { }
    public static void install(Context c) { World.event("CecPolicy.install"); }
    static Handler worker() { return WORKER; }
    static boolean stormPaused() { World.event("CecPolicy.stormPaused()"); return World.paused; }
}
