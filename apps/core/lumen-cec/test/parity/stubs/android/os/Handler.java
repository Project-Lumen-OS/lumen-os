// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.os;

import parity.World;

/** A queue of the parity world: posts run when the harness drains (World.drain), in order, delays ignored. */
public class Handler {
    private final String queue;
    public Handler(String queue) { this.queue = queue; }
    public final boolean post(Runnable r) { World.post(queue, r, 0); return true; }
    public final boolean postDelayed(Runnable r, long ms) { World.post(queue, r, ms); return true; }
    public final void removeCallbacks(Runnable r) { World.remove(queue, r); }
    public String queue() { return queue; }
}
