// SPDX-License-Identifier: Apache-2.0
package org.lumen.updater;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Host check of UpdateService's worker (JobRunner, ServiceJob). The a test Z9X 2026-11-12: a second "Install now"
 * press replaced the first install job, that job ended and stopped the service while the second one installed, so
 * the screen stayed at 0 % until the next onResume.
 */
public final class JobRunnerTest {
    private static int fails, runs;

    private static void eq(String what, boolean want, boolean got) {
        runs++;
        if (want != got) {
            fails++;
            System.out.println("FAIL " + what + ": want " + want + ", got " + got);
        }
    }

    private static void await(CountDownLatch l) throws InterruptedException {
        if (!l.await(5, TimeUnit.SECONDS)) throw new AssertionError("timeout");
    }

    /** A job that runs until it is asked to stop, then reports whether it may end the service. */
    private static Runnable untilStopped(JobRunner r, CountDownLatch started, AtomicBoolean mayEnd) {
        return () -> {
            started.countDown();
            while (!r.stopRequested()) sleep(5);
            mayEnd.set(r.mayEnd());
        };
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void repeatsTable() {
        ServiceJob D = ServiceJob.DOWNLOAD, DI = ServiceJob.DOWNLOAD_INSTALL, I = ServiceJob.INSTALL, O = ServiceJob.OTHER;
        eq("second Install now while it installs", true, I.repeats(I));
        eq("Install now while download-and-install runs", true, I.repeats(DI));
        eq("Install now after a plain download: new work", false, I.repeats(D));
        eq("download and install again", true, DI.repeats(DI));
        eq("download and install while it installs", true, DI.repeats(I));
        eq("download and install over a plain download: new work", false, DI.repeats(D));
        eq("automatic download next to the user's install", true, D.repeats(I));
        eq("automatic download next to download-and-install", true, D.repeats(DI));
        eq("download twice", true, D.repeats(D));
        eq("watch / cancel never repeat", false, O.repeats(I));
        eq("nothing running", false, I.repeats(O));
        eq("no job", false, I.repeats(null));
    }

    /** The incident: the second press is dropped, the first install job goes on and alone ends the service. */
    private static void secondPressDropped() throws InterruptedException {
        JobRunner r = new JobRunner();
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean mayEnd = new AtomicBoolean();
        Thread first = r.start(ServiceJob.INSTALL, untilStopped(r, started, mayEnd), e -> { });
        await(started);
        eq("second Install now is a repeat", true, r.repeats(ServiceJob.INSTALL));
        eq("Pause / Cancel of the service still reach it", false, r.stopRequested());
        eq("the callback thread may not end the service under the job", false, r.mayEnd());
        r.requestStop();
        first.join(5000);
        eq("the job may end the service once it is the newest", true, mayEnd.get());
        eq("idle: anyone may end the service", true, r.mayEnd());
        eq("idle: nothing repeats", false, r.repeats(ServiceJob.INSTALL));
    }

    /** A replaced job ends after its replacement started: it must leave the service to the newer job. */
    private static void replacedJobKeepsService() throws InterruptedException {
        JobRunner r = new JobRunner();
        CountDownLatch started = new CountDownLatch(1), second = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicBoolean oldMayEnd = new AtomicBoolean(true), newMayEnd = new AtomicBoolean();
        Thread old = r.start(ServiceJob.DOWNLOAD, untilStopped(r, started, oldMayEnd), e -> { });
        await(started);
        eq("adding the install is new work", false, r.repeats(ServiceJob.DOWNLOAD_INSTALL));
        Thread next = r.start(ServiceJob.DOWNLOAD_INSTALL, () -> {
            second.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            newMayEnd.set(r.mayEnd());
        }, e -> { });
        old.join(5000);
        eq("the replaced job must not end the service", false, oldMayEnd.get());
        await(second);
        eq("the new job starts with the stop cleared", false, r.stopRequested());
        eq("the new job repeats a second press", true, r.repeats(ServiceJob.INSTALL));
        release.countDown();
        next.join(5000);
        eq("the newest job ends the service", true, newMayEnd.get());
    }

    /** Resume right after Pause: the ending job is no repeat, so the new download is not dropped. */
    private static void endingJobIsNoRepeat() throws InterruptedException {
        JobRunner r = new JobRunner();
        CountDownLatch started = new CountDownLatch(1), hold = new CountDownLatch(1);
        Thread t = r.start(ServiceJob.DOWNLOAD, () -> {
            started.countDown();
            try {
                hold.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }, e -> { });
        await(started);
        r.requestStop();
        eq("Resume while the paused download still ends", false, r.repeats(ServiceJob.DOWNLOAD));
        hold.countDown();
        t.join(5000);
        eq("ended job: not busy", false, r.busy());
        Thread done = r.start(ServiceJob.INSTALL, () -> { }, e -> { });
        done.join(5000);
        eq("a job that ended by itself is no repeat", false, r.repeats(ServiceJob.INSTALL));
    }

    private static void crashReported() throws InterruptedException {
        JobRunner r = new JobRunner();
        AtomicReference<Throwable> got = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        r.start(ServiceJob.OTHER, () -> { throw new IllegalStateException("boom"); }, e -> {
            got.set(e);
            done.countDown();
        });
        await(done);
        eq("a crash of the job reaches the handler", true, got.get() instanceof IllegalStateException);
    }

    public static void main(String[] a) throws Exception {
        repeatsTable();
        secondPressDropped();
        replacedJobKeepsService();
        endingJobIsNoRepeat();
        crashReported();
        System.out.println(fails == 0 ? "job tests passed=" + runs + " failed=0" : "job tests failed=" + fails);
        if (fails != 0) System.exit(1);
    }
}
