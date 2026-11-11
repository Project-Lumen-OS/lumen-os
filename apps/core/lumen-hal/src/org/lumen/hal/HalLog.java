package org.lumen.hal;

import java.util.HashMap;
import java.util.Map;

/**
 * Pacing of the INFO line that every vendor call gets (AidlTransport; tag X50Proj in X50Projector, X50Eye in
 * X50EyeGuard): the method, its small arguments and its result or error. A call is logged when its text (arguments and
 * result) differs from the last line of that method; for a getter (get... / is...) at most once per
 * {@link #GETTER_GAP_MS} (the 500 ms angle reads change in the last digits all the time), and the same text again at
 * most once per {@link #HEARTBEAT_MS}; the number of calls skipped in between is added to the next line. So the 1 s
 * pollers and the angle reads cannot flood the log. Plain Java, host-tested. Until 2026-11-10 two copies of this class
 * lived in apps/X50EyeGuard and apps/X50Projector; lumen-hal holds the one class now.
 */
public final class HalLog {
    /** the same text of one method is logged again at most this often */
    public static final long HEARTBEAT_MS = 60_000;
    /** a getter whose result changed is logged at most this often */
    public static final long GETTER_GAP_MS = 5_000;
    /** argument words shown at most (the KstPoint grid of 81 points is never printed) */
    public static final int MAX_ARGS = 4;

    private final Map<String, String> last = new HashMap<>();
    private final Map<String, Long> at = new HashMap<>();
    private final Map<String, Integer> skipped = new HashMap<>();

    /** The line to log for this call now, or null to skip it. */
    public synchronized String line(String method, String args, String result, long now) {
        String text = "(" + args + ") -> " + result;
        String prev = last.get(method);
        Long t = at.get(method);
        int n = skipped.containsKey(method) ? skipped.get(method) : 0;
        boolean getter = method.startsWith("get") || method.startsWith("is");
        boolean same = text.equals(prev);
        if (t != null && (same ? now - t < HEARTBEAT_MS : getter && now - t < GETTER_GAP_MS)) {
            skipped.put(method, n + 1);
            return null;
        }
        last.put(method, text);
        at.put(method, now);
        skipped.put(method, 0);
        return "hal " + method + text + (n > 0 ? " [" + n + " skipped]" : "");
    }

    /** The argument words of a call (each parcel word an int), or ... for a long argument list. */
    public static String args(int[] words) {
        if (words == null) return "...";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) b.append(", ");
            b.append(words[i]);
        }
        return b.toString();
    }
}
