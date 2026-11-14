// SPDX-License-Identifier: Apache-2.0
package org.lumen.updater;

import java.util.function.Consumer;

/**
 * The one worker thread of UpdateService. A new job asks the running one to stop and waits for it on its own
 * thread (up to {@link #JOIN_MS}), then runs. Only the newest job may end the service ({@link #mayEnd}): a job
 * that ends because a newer one replaced it must not stop the service under that newer job. Plain Java.
 */
final class JobRunner {
    static final long JOIN_MS = 10_000;

    private volatile Thread current;
    private volatile ServiceJob job = ServiceJob.OTHER;
    private volatile boolean stop;

    /** The running job was asked to end (Pause, Cancel, a newer job). */
    boolean stopRequested() { return stop; }

    void requestStop() { stop = true; }

    /** A job is started and has not ended (also in the moment before its thread runs). */
    boolean busy() {
        Thread t = current;
        return t != null && t.getState() != Thread.State.TERMINATED;
    }

    /** The request only repeats the job that runs now and is not ending: nothing to start. */
    boolean repeats(ServiceJob requested) {
        return busy() && !stop && requested.repeats(job);
    }

    /** No newer job than the calling thread is alive (any thread). */
    boolean mayEnd() {
        Thread t = current;
        return t == null || t == Thread.currentThread() || t.getState() == Thread.State.TERMINATED;
    }

    /** Runs {@code body} on a new thread once the previous job ended; a Throwable goes to {@code crash} there. */
    Thread start(ServiceJob kind, Runnable body, Consumer<Throwable> crash) {
        final Thread prev = current;
        Thread t = new Thread(() -> {
            if (prev != null && prev.isAlive()) {
                try {
                    prev.join(JOIN_MS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            stop = false;
            try {
                body.run();
            } catch (Throwable e) {
                crash.accept(e);
            }
        }, "updater");
        job = kind;
        current = t;                                         // first: the replaced job sees a newer one at once
        if (prev != null && prev.isAlive()) stop = true;
        t.start();
        return t;
    }
}
