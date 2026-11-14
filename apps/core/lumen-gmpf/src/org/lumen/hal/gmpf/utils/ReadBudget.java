package org.lumen.hal.gmpf.utils;

import android.os.SystemClock;

/** Rate guard of a sensor read: at least the gap apart and at most so many per minute; a refusal throws, nothing sent. */
public final class ReadBudget {
    private static final long WINDOW_MS = 60_000;

    private final long[] stamps;
    private final long gapMs;
    private int next;

    public ReadBudget(int perMinute, long gapMs) {
        this.stamps = new long[perMinute];
        this.gapMs = gapMs;
    }

    /** Takes one read or throws IllegalStateException. */
    public synchronized void take(String what) {
        long now = SystemClock.elapsedRealtime();
        long last = stamps[(next + stamps.length - 1) % stamps.length];
        if (last != 0 && now - last < gapMs) throw new IllegalStateException(what + " within " + gapMs + " ms of the last one");
        long oldest = stamps[next];
        if (oldest != 0 && now - oldest < WINDOW_MS) {
            throw new IllegalStateException(what + ": " + stamps.length + " reads in the last minute already");
        }
        stamps[next] = now;
        next = (next + 1) % stamps.length;
    }
}
