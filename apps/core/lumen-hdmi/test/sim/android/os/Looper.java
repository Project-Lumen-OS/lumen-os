// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.os;

import org.lumen.hdmi.sim.Sim;

/** Host stub: a looper is one virtual thread of {@link Sim}. */
public final class Looper {
    private static final Looper MAIN = new Looper("main");
    final Sim.Thread thread;

    Looper(String name) { thread = Sim.thread(name); }

    public static Looper getMainLooper() { return MAIN; }
}
