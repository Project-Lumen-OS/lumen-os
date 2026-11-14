// SPDX-License-Identifier: Apache-2.0
package org.lumen.miracast.core;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Where screen mirroring got to. Every step of the receiver reports here (service up, Wi-Fi Direct,
 * WFD information element, accept prompt, discoverable, then per attempt: request, answer, group,
 * source address, RTSP, PLAY, picture), so one log line per step tells where a user's attempt stopped.
 *
 * Setup stages (SERVICE .. VISIBLE) live for the whole run; attempt stages (REQUEST .. END) start over
 * with {@link #newAttempt}. {@link #report} is the text for dumpsys and the hidden status page.
 * Pure Java (host tested); the Android side gives a clock and a line sink (logcat).
 */
public final class Diag {
    public interface Clock {
        /** Wall clock, ms since the epoch (log times). */
        long nowMs();
    }

    public interface Sink {
        void line(String line);
    }

    public enum Stage {
        SERVICE("receiver service started"),
        P2P("Wi-Fi Direct available"),
        NAME("Wi-Fi Direct name set"),
        WFD_IE("WFD information element set (primary sink)"),
        APPROVER("own accept prompt attached"),
        VISIBLE("discoverable for phones and computers"),
        REQUEST("connection request from a sender"),
        ANSWER("request answered"),
        GROUP("Wi-Fi Direct group formed"),
        SOURCE_IP("sender address found"),
        RTSP_TCP("RTSP control connection open"),
        RTSP("RTSP capability exchange (M1..M7)"),
        PLAYING("sender accepted PLAY"),
        SCREEN("mirror screen on top"),
        MEDIA("RTP media arriving"),
        DECODER("video decoder running"),
        FIRST_FRAME("first picture shown"),
        END("session ended");

        public final String meaning;

        Stage(String meaning) {
            this.meaning = meaning;
        }

        public boolean perAttempt() {
            return ordinal() >= REQUEST.ordinal();
        }
    }

    public enum Status { OK, WAIT, FAIL }

    public static final class Entry {
        public final Stage stage;
        public final Status status;
        public final String detail;
        public final long atMs;

        Entry(Stage stage, Status status, String detail, long atMs) {
            this.stage = stage;
            this.status = status;
            this.detail = detail == null ? "" : detail;
            this.atMs = atMs;
        }
    }

    public static final String TAG = "LumenMiracastDiag";
    static final int MAX_EVENTS = 80;

    private final Clock clock;
    private final Sink sink;
    private final Map<Stage, Entry> last = new EnumMap<>(Stage.class);
    private final ArrayDeque<Entry> events = new ArrayDeque<>();
    private int attempt;
    private long attemptStartMs;

    public Diag(Clock clock, Sink sink) {
        this.clock = clock;
        this.sink = sink;
    }

    public synchronized void ok(Stage s, String detail) {
        put(s, Status.OK, detail);
    }

    public synchronized void waiting(Stage s, String detail) {
        put(s, Status.WAIT, detail);
    }

    public synchronized void fail(Stage s, String detail) {
        put(s, Status.FAIL, detail);
    }

    /** A sender asks to connect: the attempt stages start over. */
    public synchronized void newAttempt(String detail) {
        for (Stage s : Stage.values()) if (s.perAttempt()) last.remove(s);
        attempt++;
        attemptStartMs = clock.nowMs();
        put(Stage.REQUEST, Status.OK, detail);
    }

    public synchronized int attempt() {
        return attempt;
    }

    public synchronized Entry get(Stage s) {
        return last.get(s);
    }

    /** The last stage reached with OK (setup stages count until the first request). */
    public synchronized Stage furthest() {
        Stage best = null;
        for (Stage s : Stage.values()) {
            Entry e = last.get(s);
            if (e != null && e.status == Status.OK && s != Stage.END) best = s;
        }
        return best;
    }

    /** The first stage that failed, setup stages first; null when none did. */
    public synchronized Entry firstFailure() {
        for (Stage s : Stage.values()) {
            Entry e = last.get(s);
            if (e != null && e.status == Status.FAIL) return e;
        }
        return null;
    }

    /** One line a person can act on: where it stopped and why. */
    public synchronized String verdict() {
        Entry f = firstFailure();
        if (f != null) return "stopped at " + f.stage + " (" + f.stage.meaning + "): " + f.detail;
        Entry end = last.get(Stage.END);
        Stage far = furthest();
        if (end != null) return "ended after " + far + ": " + end.detail;
        if (far == null) return "nothing yet";
        if (far == Stage.FIRST_FRAME) return "mirroring";
        Stage next = Stage.values()[far.ordinal() + 1];
        if (attempt == 0 && next == Stage.REQUEST) return "ready, no sender has asked yet";
        return "waiting for " + next + " (" + next.meaning + ")";
    }

    /** Multi-line report: verdict, one row per stage reached, then the recent events. */
    public synchronized String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("verdict: ").append(verdict()).append('\n');
        sb.append("attempts: ").append(attempt).append('\n');
        for (Stage s : Stage.values()) {
            Entry e = last.get(s);
            if (e == null) continue;
            String rel = s.perAttempt() && attemptStartMs > 0 ? String.format(Locale.ROOT, "+%dms", e.atMs - attemptStartMs) : time(e.atMs);
            sb.append(String.format(Locale.ROOT, "%-4s %-11s %-12s %s%n", e.status, s, rel, e.detail));
        }
        sb.append("recent:\n");
        for (Entry e : events) {
            sb.append("  ").append(time(e.atMs)).append(' ').append(e.stage).append(' ').append(e.status).append(' ')
                    .append(e.detail).append('\n');
        }
        return sb.toString();
    }

    /** The logcat line for one entry; also what the host tests parse. */
    public static String line(Entry e) {
        return "stage=" + e.stage + " status=" + e.status + (e.detail.isEmpty() ? "" : " detail=" + e.detail);
    }

    private void put(Stage s, Status st, String detail) {
        Entry e = new Entry(s, st, clip(detail), clock.nowMs());
        last.put(s, e);
        events.addLast(e);
        while (events.size() > MAX_EVENTS) events.removeFirst();
        if (sink != null) sink.line(line(e));
    }

    private static String clip(String s) {
        if (s == null) return "";
        String one = s.replace('\n', ' ').replace('\r', ' ');
        return one.length() > 300 ? one.substring(0, 300) + "..." : one;
    }

    private static String time(long ms) {
        long d = ms % 86_400_000L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d UTC", d / 3_600_000, d / 60_000 % 60, d / 1000 % 60, d % 1000);
    }
}
