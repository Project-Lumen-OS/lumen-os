// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.hdmi.sim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Virtual time for the host tests of HdmiAudio: every android.os.Handler stub posts here, every thread runs its tasks
 * one after the other, a vendor call made by a task moves that thread's clock by the time the vendor took. The audio
 * keys the AudioManager stub gets are kept with their time. Single threaded and deterministic.
 */
public final class Sim {
    private Sim() {}

    /** One looper thread: its tasks in (time, post order), free again at {@link #free}. */
    public static final class Thread {
        public final String name;
        final PriorityQueue<Task> tasks = new PriorityQueue<>();
        long free = Long.MIN_VALUE;
        public final List<Long> done = new ArrayList<>();

        Thread(String name) { this.name = name; }
    }

    static final class Task implements Comparable<Task> {
        final long at, seq;
        final Runnable r;

        Task(long at, long seq, Runnable r) { this.at = at; this.seq = seq; this.r = r; }

        @Override public int compareTo(Task o) { return at != o.at ? Long.compare(at, o.at) : Long.compare(seq, o.seq); }
    }

    /** An audio key the HAL got, at its virtual time. */
    public static final class Key {
        public final long at;
        public final String kv;

        Key(long at, String kv) { this.at = at; this.kv = kv; }

        @Override public String toString() { return at + " " + kv; }
    }

    private static final Map<String, Thread> THREADS = new LinkedHashMap<>();
    public static final List<Key> KEYS = new ArrayList<>();
    private static long sNow, sSeq;

    public static synchronized Thread thread(String name) {
        return THREADS.computeIfAbsent(name, Thread::new);
    }

    /** A fresh run: no task, no key, every thread free from any time on, the clock at 0. */
    public static void reset() {
        for (Thread t : THREADS.values()) { t.tasks.clear(); t.free = Long.MIN_VALUE; t.done.clear(); }
        KEYS.clear();
        sNow = 0;
    }

    public static long now() { return sNow; }

    public static void post(Thread t, Runnable r, long delayMs) { postAt(t, r, sNow + Math.max(0, delayMs)); }

    public static void postAt(Thread t, Runnable r, long at) { t.tasks.add(new Task(at, sSeq++, r)); }

    /** A vendor call of the running task: its thread's clock moves on by the time the vendor took. */
    public static void call(long ms) { sNow += ms; }

    public static void key(String kv) { KEYS.add(new Key(sNow, kv)); }

    /** Runs every task that starts before {@code until}, the earliest start first. */
    public static void run(long until) {
        while (true) {
            Thread next = null;
            long start = Long.MAX_VALUE, seq = Long.MAX_VALUE;
            for (Thread t : THREADS.values()) {
                Task h = t.tasks.peek();
                if (h == null) continue;
                long s = Math.max(h.at, t.free);
                if (s < start || s == start && h.seq < seq) { next = t; start = s; seq = h.seq; }
            }
            if (next == null || start >= until) return;
            Task task = next.tasks.poll();
            sNow = start;
            task.r.run();
            next.free = sNow;
            next.done.add(sNow);
        }
    }
}
