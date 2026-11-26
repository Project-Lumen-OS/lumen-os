// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.os;

/** Host stub: a named virtual thread. */
public class HandlerThread {
    private final Looper looper;

    public HandlerThread(String name) { looper = new Looper(name); }

    public void start() {}

    public Looper getLooper() { return looper; }
}
