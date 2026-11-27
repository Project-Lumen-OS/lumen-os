// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.report;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The image's root log helper (image/lumen_report.sh, lumen_report.rc): the report app is not the system uid on every
 * model and Android 13+ gives such an app only its own log lines unless the user answers a dialog, while a report must
 * always carry the system log without asking. The app sets {@link #PROP_REQ} to a fresh token, init starts the
 * helper, the helper writes the scrubbed and size-capped logs to {@link #DIR} (readable only by this app's group) and
 * answers "&lt;token&gt; ok|busy|none|refused" in {@link #PROP_DONE}; after reading, the app sets {@link #PROP_ACK}
 * to the token and the helper removes the files. Plain Java (the properties and the clock come from the caller),
 * host-tested in ReportTest.
 */
final class RootLogs {
    static final String PROP_REQ = "sys.lumen.report.req";
    static final String PROP_DONE = "sys.lumen.report.done";
    static final String PROP_ACK = "sys.lumen.report.ack";
    static final String DIR = "/dev/lumen_report";
    /** The helper's files and the zip names they go in as (the logcat ones through LogcatFilter). */
    static final String LOGCAT = "logcat.txt", CRASH = "crash.txt", KERNEL = "kernel.txt";
    static final String[] DUMPSYS = {"hdmi_control", "audio", "power"};
    /**
     * An automatic report waits this long for the helper, past it the report goes on with the app's own log. A full
     * collection takes 1 to 5 s on an idle unit and far longer on a busy one (evleo790 LR-9MEQZP, HB low on memory: no
     * answer in 30 s, the report had no system log, kernel log or dumpsys).
     */
    static final long TIMEOUT_MS = 30_000;
    /** A manual report waits longer: the user is at the progress screen and the system log is the whole point. */
    static final long MANUAL_TIMEOUT_MS = 90_000;
    static final long POLL_MS = 100;
    private static final int MAX_TOKEN = 24;

    enum Answer {
        OK, BUSY, NONE, REFUSED, TIMEOUT, CANCELED;

        /** The words of the helper's answer; null for anything else. */
        static Answer of(String word) {
            switch (word) {
                case "ok": return OK;
                case "busy": return BUSY;
                case "none": return NONE;
                case "refused": return REFUSED;
                default: return null;
            }
        }
    }

    interface Props {
        String get(String key);

        void set(String key, String value);
    }

    interface Clock {
        long now();

        /** False when the report was canceled meanwhile. */
        boolean sleep(long ms);
    }

    private static int seq;

    private RootLogs() {}

    /** A fresh token in the helper's alphabet [0-9a-z-], at most 24 characters. */
    static synchronized String token(long uptimeMs) {
        seq = (seq + 1) % 1_000_000;
        String t = Long.toString(Math.max(0, uptimeMs), 36) + "-" + Integer.toString(seq, 36);
        return t.length() <= MAX_TOKEN ? t : t.substring(t.length() - MAX_TOKEN);
    }

    /** True for a token the helper accepts (the same rule as lumen_report.sh valid()). */
    static boolean valid(String t) {
        if (t == null || t.isEmpty() || t.length() > MAX_TOKEN) return false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || c == '-')) return false;
        }
        return true;
    }

    /** The helper's answer to {@code token} in a done value, null while it answered another token or nothing. */
    static Answer parse(String done, String token) {
        if (done == null || token == null || !done.startsWith(token + " ")) return null;
        return Answer.of(done.substring(token.length() + 1).trim());
    }

    /** Sends the request and waits for its answer, at most {@code timeoutMs}. Never call it on the main thread. */
    static Answer request(Props p, Clock clock, String token, long timeoutMs) {
        if (!valid(token)) return Answer.NONE;
        try {
            p.set(PROP_REQ, token);
        } catch (RuntimeException e) {
            return Answer.NONE;
        }
        long end = clock.now() + timeoutMs;
        while (true) {
            Answer a = parse(p.get(PROP_DONE), token);
            if (a != null) return a;
            if (clock.now() >= end) return Answer.TIMEOUT;
            if (!clock.sleep(POLL_MS)) return Answer.CANCELED;
        }
    }

    /** How long a report of this kind waits for the helper. */
    static long timeout(boolean manual) {
        return manual ? MANUAL_TIMEOUT_MS : TIMEOUT_MS;
    }

    /** The helper answered {@code token} after the wait ran out and its files are still there. */
    static boolean late(Props p, File dir, String token) {
        return parse(p.get(PROP_DONE), token) == Answer.OK && fresh(dir, token);
    }

    /** True when the helper's folder holds the files of {@code token} (and not those of an older request). */
    static boolean fresh(File dir, String token) {
        try {
            byte[] b = Files.readAllBytes(new File(dir, "token").toPath());
            return new String(b, StandardCharsets.UTF_8).trim().equals(token);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** The app read the files: the helper may remove them now. Best effort. */
    static void ack(Props p, String token) {
        try {
            p.set(PROP_ACK, token);
        } catch (RuntimeException ignored) {
            // the helper removes them after its wait anyway
        }
    }
}
