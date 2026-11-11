// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

/**
 * Puts RTP payloads back into sequence order. Wi-Fi Direct links reorder and drop UDP datagrams;
 * MPEG-TS needs its 188-byte packets in order. Out-of-order packets wait in a small window; when a
 * gap stays open longer than {@code maxHold} packets or {@code maxWaitMs}, it is given up as lost
 * ({@link Sink#onLoss}, so the caller can ask for a key frame) and delivery goes on. Latency stays
 * bounded by those two limits. Sequence numbers wrap at 65536.
 */
public final class RtpReorderer {
    public interface Sink {
        void onPayload(byte[] data, int off, int len);

        void onLoss(int packets);
    }

    private static final int WINDOW = 128;
    private final Sink sink;
    private final int maxHold;
    private final long maxWaitMs;
    private final byte[][] data = new byte[WINDOW][];
    private final int[] lens = new int[WINDOW];
    private final int[] seqs = new int[WINDOW];
    private final long[] times = new long[WINDOW];
    private int expected = -1;
    private int held;
    private long lost, delivered, dropped;

    public RtpReorderer(int maxHold, long maxWaitMs, Sink sink) {
        if (maxHold < 1 || maxHold >= WINDOW) throw new IllegalArgumentException("maxHold");
        this.maxHold = maxHold;
        this.maxWaitMs = maxWaitMs;
        this.sink = sink;
        for (int i = 0; i < WINDOW; i++) seqs[i] = -1;
    }

    static int diff(int a, int b) {
        return (short) ((a - b) & 0xffff);
    }

    public void push(int seq, byte[] buf, int off, int len, long nowMs) {
        seq &= 0xffff;
        if (expected < 0) expected = seq;
        int d = diff(seq, expected);
        if (d < 0) {
            if (d < -1000) { // the source restarted its sequence
                restart(seq);
            } else {
                dropped++;   // duplicate or too late (its gap was already given up)
                return;
            }
            d = 0;
        }
        if (d >= WINDOW / 2) { // a jump bigger than the window: deliver what we have, count the rest lost
            while (held > 0) skipGap();
            lost += diff(seq, expected);
            if (diff(seq, expected) > 0) sink.onLoss(diff(seq, expected));
            expected = seq;
            d = 0;
        }
        if (d == 0) {
            deliver(buf, off, len);
            expected = (expected + 1) & 0xffff;
            drain();
            return;
        }
        int slot = seq % WINDOW;
        if (seqs[slot] == seq) {
            dropped++;
            return;
        }
        if (data[slot] == null || data[slot].length < len) data[slot] = new byte[Math.max(len, 1500)];
        System.arraycopy(buf, off, data[slot], 0, len);
        lens[slot] = len;
        seqs[slot] = seq;
        times[slot] = nowMs;
        held++;
        if (held > maxHold) skipGap();
    }

    /** Call regularly (for example on a socket receive timeout) so a gap does not hold data forever. */
    public void flushStale(long nowMs) {
        while (held > 0 && nowMs - oldestTime() > maxWaitMs) skipGap();
    }

    public void reset() {
        for (int i = 0; i < WINDOW; i++) seqs[i] = -1;
        held = 0;
        expected = -1;
    }

    private void restart(int seq) {
        reset();
        expected = seq;
    }

    private long oldestTime() {
        long t = Long.MAX_VALUE;
        for (int i = 0; i < WINDOW; i++) {
            if (seqs[i] >= 0 && times[i] < t) t = times[i];
        }
        return t;
    }

    /** Gives up the missing packets before the first held one and delivers from there. */
    private void skipGap() {
        int best = -1, bestD = Integer.MAX_VALUE;
        for (int i = 0; i < WINDOW; i++) {
            if (seqs[i] < 0) continue;
            int d = diff(seqs[i], expected);
            if (d < bestD) {
                bestD = d;
                best = seqs[i];
            }
        }
        if (best < 0) {
            held = 0;
            return;
        }
        if (bestD > 0) {
            lost += bestD;
            sink.onLoss(bestD);
        }
        expected = best;
        drain();
    }

    private void drain() {
        while (true) {
            int slot = expected % WINDOW;
            if (seqs[slot] != expected) return;
            seqs[slot] = -1;
            held--;
            deliver(data[slot], 0, lens[slot]);
            expected = (expected + 1) & 0xffff;
        }
    }

    private void deliver(byte[] b, int off, int len) {
        delivered++;
        sink.onPayload(b, off, len);
    }

    public long lost() {
        return lost;
    }

    public long delivered() {
        return delivered;
    }

    public long dropped() {
        return dropped;
    }

    public int held() {
        return held;
    }
}
