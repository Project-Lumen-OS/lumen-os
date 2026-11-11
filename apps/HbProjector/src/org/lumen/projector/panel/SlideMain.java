/*
 * SlideMain: panel/SlideCoalescer on the device (HB-only file): the main-thread scheduler and the vendor write of one
 * slider value on the HAL thread. Used by panel/ProPicture and panel/QuickPanelController for their picture sliders.
 *
 * The write runs through Hal.query (the HAL thread, the result back on the main thread, null when the HAL is not
 * connected), so the coalescer always gets its done(ok) and never sends the next value of the same slider before the
 * vendor finished the previous one. No full page read here: the page reads once when the slide is over (onIdle).
 */
package org.lumen.projector.panel;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import org.lumen.projector.Hal;
import org.lumen.projector.R;
import org.lumen.projector.Ui;

final class SlideMain {
    private static final String TAG = "Z9xPanel";

    private SlideMain() {}

    private static final SlideCoalescer.Scheduler MAIN = new SlideCoalescer.Scheduler() {
        @Override public void post(Runnable r, long delayMs) { Ui.main().postDelayed(r, delayMs); }
        @Override public void cancel(Runnable r) { Ui.main().removeCallbacks(r); }
    };

    private static final SlideCoalescer.Clock CLOCK = SystemClock::uptimeMillis;

    /** One write, built on the main thread when its value goes out: the log line and the HAL work. */
    static final class Step {
        final String what;
        final Hal.Task task;

        Step(String what, Hal.Task task) {
            this.what = what;
            this.task = task;
        }
    }

    /** The write of one value (main thread; captures the page state of that moment, e.g. the 11-point level). */
    interface Write { Step at(int value); }

    /** A coalescer for one slider: writes through the HAL thread, onIdle (the page re-read) on the main thread. */
    static SlideCoalescer of(final Context app, final Write w, Runnable onIdle) {
        return new SlideCoalescer(MAIN, CLOCK, (value, done) -> write(app, w, value, done), onIdle);
    }

    private static void write(final Context app, final Write w, final int value, final SlideCoalescer.Done done) {
        if (!Hal.isReady()) {
            Ui.toast(app, R.string.toast_not_ready);
            done.done(false);
            return;
        }
        final Step step;
        try {
            step = w.at(value);
        } catch (Throwable e) {
            Log.w(TAG, "slider value " + value + ": " + e);
            done.done(false);
            return;
        }
        Hal.query((g, g2) -> {
            try {
                step.task.run(g, g2);
                Log.i(TAG, step.what);
                return Boolean.TRUE;
            } catch (Throwable e) {
                Log.w(TAG, step.what + ": " + e);
                return Boolean.FALSE;
            }
        }, r -> {
            boolean ok = Boolean.TRUE.equals(r);
            if (r == null) Ui.toast(app, R.string.toast_not_ready);
            else if (!ok) Ui.toast(app, R.string.toast_failed);
            done.done(ok);
        });
    }
}
