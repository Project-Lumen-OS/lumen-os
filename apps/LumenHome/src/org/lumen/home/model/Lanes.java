// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Home's background work split by what it waits for, one thread per lane, so a slow network answer never holds up the
 * screen's data. {@link #LOAD}: the snapshot and the data of the screen (apps, Continue, inputs, cached showcase), local
 * reads only. {@link #NET}: everything that waits for a server (weather, the TMDB showcase); a fetch already queued or
 * running under the same key is not stacked again ({@link #once}). Pure Java (host test test/home2 lanes()).
 */
public final class Lanes {
    public static final String LOAD = "load", NET = "net";

    /** Makes the thread of one lane (the app sets Android priorities: the screen's lane is not a background one). */
    public interface Threads {
        Thread make(String lane, Runnable body);
    }

    private final Threads mThreads;
    private final Map<String, ExecutorService> mLanes = new HashMap<>();
    private final Set<String> mBusy = new HashSet<>();

    public Lanes(Threads threads) {
        mThreads = threads;
    }

    /** Plain daemon threads named "home-" + lane (tests). */
    public static Lanes plain() {
        return new Lanes((lane, body) -> {
            Thread t = new Thread(body, "home-" + lane);
            t.setDaemon(true);
            return t;
        });
    }

    private synchronized ExecutorService lane(String name) {
        ExecutorService e = mLanes.get(name);
        if (e == null) {
            ThreadFactory f = r -> mThreads.make(name, r);
            e = Executors.newSingleThreadExecutor(f);
            mLanes.put(name, e);
        }
        return e;
    }

    /** Runs {@code r} on the lane's own thread after what is already queued there. */
    public void post(String lane, Runnable r) {
        lane(lane).execute(r);
    }

    /**
     * Runs {@code r} on the lane unless the same key is still queued or running there; true when queued. A blocked
     * server costs one waiting fetch, not one more per network change or Home start.
     */
    public boolean once(String lane, String key, Runnable r) {
        final String k = lane + "/" + key;
        synchronized (this) {
            if (!mBusy.add(k)) return false;
        }
        lane(lane).execute(() -> {
            try {
                r.run();
            } finally {
                synchronized (Lanes.this) {
                    mBusy.remove(k);
                }
            }
        });
        return true;
    }

    /** True while a fetch under this key is queued or running (tests, diagnostics). */
    public synchronized boolean busy(String lane, String key) {
        return mBusy.contains(lane + "/" + key);
    }
}
