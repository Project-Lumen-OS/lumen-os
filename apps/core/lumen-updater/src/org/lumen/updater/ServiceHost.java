// SPDX-License-Identifier: Apache-2.0
package org.lumen.updater;

import android.content.Context;

/** What UpdateService lends an InstallWay while it runs one of its jobs on the worker thread. */
interface ServiceHost {
    Context context();

    /** Pause or Cancel was asked for (or a newer job replaces this one). */
    boolean stopRequested();

    /** Sleeps on the worker, waking early when a stop is asked. */
    void sleep(long ms);

    /** The progress notification (pct -1 = no percentage). */
    void progress(String title, int pct);

    void acquireWake();

    void releaseWake();

    /** The end of an update_engine install (Engine.Listener.onComplete). */
    void complete(int code);

    /** The request repeats the running job: nothing to start (logged). */
    boolean repeat(ServiceJob kind);

    /** A job runs. */
    boolean busy();

    /** The version of the stored manifest ("?" when none). */
    String version();
}
