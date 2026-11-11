/*
 * SlideCoalescer: the vendor writes of one picture slider while a remote key is held (HB-only file, no HAL access,
 * free of Android classes so test/slider/run.sh checks it on the host). Used by panel/ProPicture (white balance and
 * colour correction pages) and panel/QuickPanelController (brightness, contrast, saturation, sharpness).
 *
 * The report (4PDA, 2026-11-10): the picture flickers in the advanced picture settings when a slider moves fast over
 * several steps (key held for about a second); one or two slow steps are fine. Lumen before this class: SliderRow
 * committed every step whose key repeat came later than its commit delay (100 ms white balance / colour correction,
 * 200 ms the picture items), and every commit queued one write AND one full re-read of the page (ProPicture.read about
 * 25 vendor getters, the quick panel Snapshot more) on the single HAL thread. A held key filled that thread with
 * writes and reads: the vendor kept applying older values after the key was released, and every late re-read could
 * hide a row whose getter failed meanwhile and show it again.
 *
 * Stock HB (stock analysis, picture/PictureViewModel):
 *  - 2-point white balance (setImageModeItemValue, types 250 / 500 / 750 / 751 / 752 / 753): job.cancel() and a new
 *    job that waits 100 ms and writes the newest value (GmDisplayManager.setWbRedGain ...): one write per pause;
 *  - brightness, contrast, saturation, sharpness (setPictureItem), 11-point white balance (setOneOfMultiPointWB) and
 *    colour correction (ColorCorrection.setSaturation ...): one write per key on the main thread, a synchronous binder
 *    call, so a slow vendor holds the key events back instead of queueing writes;
 *  - nothing re-reads the whole page after a write (only the one item, or refreshTwoPointWhiteBalance 100 ms later).
 *
 * Here: one write in flight per slider, at most one waiting value (the newest replaces it: never every step), the next
 * write not earlier than minGapMs after the start of the previous one (the stock 100 ms), a value equal to the last
 * written one is not written again, and when the slider is idle (nothing in flight, nothing waiting) onIdle runs once,
 * settleMs after the last write ended: the single re-read of the page. A write whose result never comes back (HAL
 * gone) counts as failed after stuckMs, so the slider never stops writing for good. Main thread only.
 */
package org.lumen.projector.panel;

public final class SlideCoalescer {
    /** Delayed runnables on the caller's thread (the main thread on the device, a fake queue in the host test). */
    public interface Scheduler {
        void post(Runnable r, long delayMs);
        void cancel(Runnable r);
    }

    public interface Clock { long now(); }

    /** Writes one value; calls done(ok) exactly once on the scheduler's thread (late or repeated calls are ignored). */
    public interface Writer { void write(int value, Done done); }

    public interface Done { void done(boolean ok); }

    /** The stock 2-point white balance wait (job + delay(100)), also the gap between two writes of a held key. */
    public static final long STOCK_GAP_MS = 100L;
    /** The page is read again this long after the last write of a slide ended (one read, not one per step). */
    public static final long SETTLE_MS = 300L;
    /** A write that never reported back is treated as failed after this (HAL thread gone or wedged). */
    public static final long STUCK_MS = 3_000L;

    private final Scheduler sch;
    private final Clock clock;
    private final Writer writer;
    private final Runnable onIdle;
    private final long minGapMs, settleMs, stuckMs;

    private boolean inFlight, hasPending, waiting;
    private int pending;
    private long lastStart;
    private boolean started;
    private boolean lastKnown;
    private int lastWritten;
    /** Generation of the write in flight: a late done() of an older write (after stuckMs) is ignored. */
    private int gen;

    private final Runnable fire = this::fire;
    private final Runnable idle = this::idle;
    private final Runnable stuck = () -> finish(gen, false, true);

    public SlideCoalescer(Scheduler sch, Clock clock, Writer writer, Runnable onIdle) {
        this(sch, clock, writer, onIdle, STOCK_GAP_MS, SETTLE_MS, STUCK_MS);
    }

    public SlideCoalescer(Scheduler sch, Clock clock, Writer writer, Runnable onIdle, long minGapMs, long settleMs,
            long stuckMs) {
        this.sch = sch;
        this.clock = clock;
        this.writer = writer;
        this.onIdle = onIdle;
        this.minGapMs = Math.max(0, minGapMs);
        this.settleMs = Math.max(0, settleMs);
        this.stuckMs = Math.max(1, stuckMs);
    }

    /** A new slider value (every step, and the final value). Never blocks, never queues more than one value. */
    public void submit(int value) {
        sch.cancel(idle);
        if (!inFlight && !waiting && !hasPending && lastKnown && lastWritten == value) {
            sch.post(idle, settleMs);               // nothing new to write: only the settle read
            return;
        }
        pending = value;
        hasPending = true;
        if (inFlight || waiting) return;            // the newest value waits; older ones are gone
        schedule();
    }

    /** The slider was moved back by a read-back or the page was re-shown: the next value is written even if equal. */
    public void forget() {
        lastKnown = false;
    }

    /** Something is in flight or waiting (the page must not re-read now). */
    public boolean busy() {
        return inFlight || hasPending || waiting;
    }

    private void schedule() {
        long wait = started ? Math.max(0, lastStart + minGapMs - clock.now()) : 0;
        waiting = true;
        sch.post(fire, wait);
    }

    private void fire() {
        waiting = false;
        if (inFlight || !hasPending) return;
        final int v = pending;
        hasPending = false;
        inFlight = true;
        started = true;
        lastStart = clock.now();
        lastWritten = v;
        lastKnown = true;
        final int my = ++gen;
        sch.post(stuck, stuckMs);
        writer.write(v, ok -> finish(my, ok, false));
    }

    private void finish(int my, boolean ok, boolean timedOut) {
        if (my != gen || !inFlight) return;         // a late or repeated result of an older write
        sch.cancel(stuck);
        inFlight = false;
        if (!ok || timedOut) lastKnown = false;     // the vendor may not have the value: write the next one again
        if (hasPending) {
            schedule();
            return;
        }
        sch.cancel(idle);
        sch.post(idle, settleMs);
    }

    private void idle() {
        if (busy()) return;
        onIdle.run();
    }
}
