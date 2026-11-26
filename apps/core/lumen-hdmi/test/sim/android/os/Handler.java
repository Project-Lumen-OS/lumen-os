// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.os;

import org.lumen.hdmi.sim.Sim;

/** Host stub: posts to the looper's virtual thread. */
public class Handler {
    private final Looper looper;

    public Handler(Looper looper) { this.looper = looper; }

    public final boolean post(Runnable r) { Sim.post(looper.thread, r, 0); return true; }

    public final boolean postDelayed(Runnable r, long delayMillis) { Sim.post(looper.thread, r, delayMillis); return true; }

    /** Host only: a task at a virtual time (the replay of a recorded session). */
    public final void postAt(Runnable r, long at) { Sim.postAt(looper.thread, r, at); }
}
