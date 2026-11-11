package org.lumen.projector.panel;

import java.util.ArrayList;
import java.util.List;

/**
 * Host test of panel/SlideCoalescer: a held remote key (repeats every 50 ms, the Android key repeat; and 110 ms, an IR
 * repeat frame) against a fast and a slow vendor. The rules: one write in flight, the newest value wins, writes at
 * least 100 ms apart (stock 2-point white balance job + delay(100)), the final value always written last, one page
 * re-read after the slide, never during it.
 */
public final class SlideCoalescerTest {
    private static int fails, checks;

    private static void eq(String what, Object want, Object got) {
        checks++;
        if (want == null ? got != null : !want.equals(got)) {
            fails++;
            System.out.println("FAIL " + what + ": want " + want + ", got " + got);
        }
    }

    private static void ok(String what, boolean cond) { eq(what, true, cond); }

    /** Virtual time queue: post / cancel, run until a time. */
    static final class Q implements SlideCoalescer.Scheduler, SlideCoalescer.Clock {
        long now;
        long seq;
        final List<long[]> keys = new ArrayList<>();      // {due, seq}
        final List<Runnable> jobs = new ArrayList<>();

        @Override public long now() { return now; }

        @Override public void post(Runnable r, long delayMs) {
            keys.add(new long[] {now + Math.max(0, delayMs), seq++});
            jobs.add(r);
        }

        @Override public void cancel(Runnable r) {
            for (int i = jobs.size() - 1; i >= 0; i--) {
                if (jobs.get(i) == r) { jobs.remove(i); keys.remove(i); }
            }
        }

        void runUntil(long t) {
            while (true) {
                int best = -1;
                for (int i = 0; i < keys.size(); i++) {
                    long[] k = keys.get(i);
                    if (k[0] > t) continue;
                    if (best < 0 || k[0] < keys.get(best)[0] || (k[0] == keys.get(best)[0] && k[1] < keys.get(best)[1])) best = i;
                }
                if (best < 0) break;
                now = Math.max(now, keys.get(best)[0]);
                Runnable r = jobs.remove(best);
                keys.remove(best);
                r.run();
            }
            now = Math.max(now, t);
        }
    }

    /** A vendor that takes latencyMs per write (or never answers with latencyMs < 0, or answers false). */
    static final class Vendor implements SlideCoalescer.Writer {
        final Q q;
        long latencyMs;
        boolean answer = true;
        int inFlight, maxInFlight;
        final List<Integer> values = new ArrayList<>();
        final List<Long> starts = new ArrayList<>();
        SlideCoalescer.Done lastDone;

        Vendor(Q q, long latencyMs) { this.q = q; this.latencyMs = latencyMs; }

        @Override public void write(int value, SlideCoalescer.Done done) {
            values.add(value);
            starts.add(q.now);
            inFlight++;
            maxInFlight = Math.max(maxInFlight, inFlight);
            lastDone = done;
            if (firstDone == null) firstDone = done;
            if (latencyMs < 0) return;               // the result never comes back
            final boolean a = answer;
            q.post(() -> { inFlight--; done.done(a); }, latencyMs);
        }
    }

    static int idles;
    /** The done of the first write any Vendor got since the last reset (6b). */
    static SlideCoalescer.Done firstDone;

    /** Holds a key: steps value+1 every periodMs, count steps, starting at t0. Returns the last value. */
    static int hold(Q q, SlideCoalescer c, int from, int count, long periodMs, long t0, List<Long> idleDuring) {
        int v = from;
        for (int i = 0; i < count; i++) {
            q.runUntil(t0 + i * periodMs);
            int before = idles;
            v++;
            c.submit(v);
            if (idleDuring != null && i > 0 && idles != before) idleDuring.add(q.now);
        }
        return v;
    }

    static long minGap(List<Long> starts) {
        long g = Long.MAX_VALUE;
        for (int i = 1; i < starts.size(); i++) g = Math.min(g, starts.get(i) - starts.get(i - 1));
        return g;
    }

    static boolean increasing(List<Integer> v) {
        for (int i = 1; i < v.size(); i++) if (v.get(i) <= v.get(i - 1)) return false;
        return true;
    }

    public static void main(String[] a) {
        // 0. the stock wait: PictureViewModel 2-point white balance job.cancel() + delay(100)
        eq("stock gap 100 ms", 100L, SlideCoalescer.STOCK_GAP_MS);
        ok("settle read after the gap", SlideCoalescer.SETTLE_MS > SlideCoalescer.STOCK_GAP_MS);
        ok("stuck time well above a vendor write", SlideCoalescer.STUCK_MS >= 1_000L);

        // 1. key held 1 s, Android repeat 50 ms (20 steps), vendor 30 ms per write
        Q q = new Q();
        Vendor w = new Vendor(q, 30);
        idles = 0;
        SlideCoalescer c = new SlideCoalescer(q, q, w, () -> idles++);
        List<Long> during = new ArrayList<>();
        int last = hold(q, c, 50, 20, 50, 0, during);
        q.runUntil(950);
        eq("held 50 ms: no re-read during the slide", 0, idles);
        ok("held 50 ms: busy right after the last step", c.busy() || idles == 0);
        q.runUntil(5_000);
        eq("held 50 ms: last value written last", last, w.values.get(w.values.size() - 1));
        ok("held 50 ms: at most 11 writes for 20 steps (one per 100 ms + the last), got " + w.values.size(),
                w.values.size() <= 11);
        ok("held 50 ms: at least 8 writes (the picture follows the held key), got " + w.values.size(), w.values.size() >= 8);
        eq("held 50 ms: one write in flight at most", 1, w.maxInFlight);
        ok("held 50 ms: writes at least 100 ms apart, min " + minGap(w.starts), minGap(w.starts) >= SlideCoalescer.STOCK_GAP_MS);
        ok("held 50 ms: values only grow (no older value after a newer one)", increasing(w.values));
        eq("held 50 ms: one page re-read after the slide", 1, idles);
        eq("held 50 ms: none during", 0, during.size());

        // 2. slow vendor (400 ms per write, a full page read on the HAL thread): still never a queue
        q = new Q();
        w = new Vendor(q, 400);
        idles = 0;
        c = new SlideCoalescer(q, q, w, () -> idles++);
        last = hold(q, c, 0, 20, 50, 0, null);
        q.runUntil(10_000);
        eq("slow vendor: last value written last", last, w.values.get(w.values.size() - 1));
        ok("slow vendor: at most 4 writes for 20 steps, got " + w.values.size(), w.values.size() <= 4);
        eq("slow vendor: one in flight", 1, w.maxInFlight);
        ok("slow vendor: values only grow", increasing(w.values));
        eq("slow vendor: one re-read", 1, idles);

        // 3. IR repeat 110 ms (later than SliderRow's 100 ms commit, so every step commits): spaced, not queued
        q = new Q();
        w = new Vendor(q, 150);
        idles = 0;
        c = new SlideCoalescer(q, q, w, () -> idles++);
        last = hold(q, c, 10, 10, 110, 0, null);
        q.runUntil(10_000);
        eq("IR 110 ms: last value written last", last, w.values.get(w.values.size() - 1));
        ok("IR 110 ms: fewer writes than steps when the vendor is slower than the repeat, got " + w.values.size(),
                w.values.size() < 10);
        eq("IR 110 ms: one in flight", 1, w.maxInFlight);
        eq("IR 110 ms: one re-read", 1, idles);

        // 4. one slow step (the reported 'fine' case): written at once, one re-read
        q = new Q();
        w = new Vendor(q, 30);
        idles = 0;
        c = new SlideCoalescer(q, q, w, () -> idles++);
        c.submit(42);
        q.runUntil(1);
        eq("one step: written at once", 1, w.values.size());
        q.runUntil(2_000);
        eq("one step: one re-read", 1, idles);
        // the same value again (e.g. the final commit of a step already written): no write, only the settle read
        c.submit(42);
        q.runUntil(4_000);
        eq("same value: not written again", 1, w.values.size());
        eq("same value: settle read", 2, idles);
        // forget() (read-back moved the slider): the next equal value is written
        c.forget();
        c.submit(42);
        q.runUntil(6_000);
        eq("after forget: written again", 2, w.values.size());

        // 5. a failed write: the same value is written again on the next submit
        q = new Q();
        w = new Vendor(q, 30);
        w.answer = false;
        idles = 0;
        c = new SlideCoalescer(q, q, w, () -> idles++);
        c.submit(7);
        q.runUntil(1_000);
        w.answer = true;
        c.submit(7);
        q.runUntil(2_000);
        eq("failed write: retried with the same value", 2, w.values.size());

        // 6. a write that never answers: after STUCK_MS the newest waiting value goes out; the late answer is ignored
        q = new Q();
        w = new Vendor(q, -1);
        idles = 0;
        c = new SlideCoalescer(q, q, w, () -> idles++);
        c.submit(1);
        q.runUntil(10);
        c.submit(2);
        c.submit(3);
        q.runUntil(SlideCoalescer.STUCK_MS - 10);
        eq("stuck: nothing more while the first is in flight", 1, w.values.size());
        SlideCoalescer.Done stale = w.lastDone;
        w.latencyMs = 20;
        q.runUntil(SlideCoalescer.STUCK_MS + 50);
        eq("stuck: the newest waiting value after the timeout", java.util.Arrays.asList(1, 3), w.values);
        int idlesBefore = idles;
        stale.done(true);                            // the first write answers very late
        q.runUntil(SlideCoalescer.STUCK_MS + 2_000);
        eq("stuck: the late answer starts nothing", 2, w.values.size());
        eq("stuck: one re-read after the second write", idlesBefore + 1, idles);

        // 6b. a late answer of the stuck write while the next one is still in flight must not free the slot
        q = new Q();
        w = new Vendor(q, -1);
        idles = 0;
        firstDone = null;
        c = new SlideCoalescer(q, q, w, () -> idles++);
        c.submit(1);
        q.runUntil(10);
        c.submit(2);
        w.latencyMs = 500;                            // the second write is slow
        q.runUntil(SlideCoalescer.STUCK_MS + 20);     // timeout: the second write starts
        eq("late answer: second write started", java.util.Arrays.asList(1, 2), w.values);
        stale = null;
        w.inFlight = 1;                               // the stuck first write is the vendor's past; only #2 counts
        w.maxInFlight = 1;
        SlideCoalescer.Done first = firstDone;
        if (first != null) first.done(true);          // the first write answers now, #2 still running
        q.runUntil(SlideCoalescer.STUCK_MS + 150);
        c.submit(3);
        q.runUntil(SlideCoalescer.STUCK_MS + 200);
        eq("late answer: #3 waits for #2 (one in flight)", 1, w.maxInFlight);
        q.runUntil(SlideCoalescer.STUCK_MS + 3_000);
        eq("late answer: #3 written after #2", java.util.Arrays.asList(1, 2, 3), w.values);

        if (fails > 0) {
            System.out.println("SlideCoalescerTest: " + fails + " of " + checks + " FAILED");
            System.exit(1);
        }
        System.out.println("SlideCoalescerTest: " + checks + " checks passed");
    }
}
