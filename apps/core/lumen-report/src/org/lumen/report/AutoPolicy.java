// SPDX-License-Identifier: Apache-2.0
package org.lumen.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * When an automatic report may go out (the user's switch, the same rules as the statistics: on by default, nothing
 * before the first-run setup ended, an image without a report server sends nothing) and how many: one report per
 * incident key a day, at most MAX_PER_DAY a day. The log of sent automatic reports is text, one "time key" per line.
 * Plain Java.
 */
final class AutoPolicy {
    static final boolean DEFAULT_ON = true;
    static final long DAY_MS = 24L * 3600 * 1000;
    static final int MAX_PER_DAY = 4;
    /** Incidents of one report at most (the newest ones). */
    static final int MAX_PER_REPORT = 5;

    private AutoPolicy() {}

    static boolean allowed(boolean configured, boolean on, boolean setupDone) {
        return configured && on && setupDone;
    }

    /**
     * DropBox is read with consent only, and only after a first look ({@code since} > 0, the first look after an
     * install only remembers the time) whose time is not ahead of the clock (it went back after a power cut).
     */
    static boolean dropBoxScan(boolean allowed, long since, long now) {
        return allowed && since > 0 && since <= now;
    }

    /** The unexpected stop of the last start goes in with consent only, whatever the clock says. */
    static boolean withStop(boolean allowed, Incident stop) {
        return allowed && stop != null;
    }

    /**
     * The incidents that get an automatic report now, newest first: not reported in the last day (by key), one per
     * key, as many as the day still allows. {@code log} = the sent log (see record).
     */
    static List<Incident> pick(List<Incident> found, String log, long now) {
        Map<String, Long> last = new HashMap<>();
        int today = 0;
        for (String[] e : entries(log)) {
            long t = Long.parseLong(e[0]);
            if (now - t >= DAY_MS || t > now) continue;
            today++;
            Long seen = last.get(e[1]);
            if (seen == null || t > seen) last.put(e[1], t);
        }
        List<Incident> sorted = new ArrayList<>(found);
        Collections.sort(sorted, (a, b) -> Long.compare(b.time, a.time));
        List<Incident> out = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        if (today >= MAX_PER_DAY) return out;
        for (Incident i : sorted) {
            if (out.size() >= MAX_PER_REPORT) break;
            if (last.containsKey(i.key()) || !keys.add(i.key())) continue;
            out.add(i);
        }
        return out;
    }

    /** The log after one automatic report with {@code sent}: entries older than a day go, one line per new key. */
    static String record(String log, List<Incident> sent, long now) {
        StringBuilder sb = new StringBuilder();
        for (String[] e : entries(log)) {
            long t = Long.parseLong(e[0]);
            if (now - t < DAY_MS && t <= now) sb.append(t).append(' ').append(e[1]).append('\n');
        }
        for (Incident i : sent) sb.append(now).append(' ').append(i.key()).append('\n');
        return sb.toString();
    }

    /** {time, key} of every well-formed line; a damaged line is skipped, never fatal. */
    private static List<String[]> entries(String log) {
        List<String[]> out = new ArrayList<>();
        if (log == null) return out;
        for (String line : log.split("\n")) {
            int sp = line.indexOf(' ');
            if (sp <= 0 || sp == line.length() - 1) continue;
            String t = line.substring(0, sp);
            boolean digits = true;
            for (int k = 0; k < t.length(); k++) if (!Character.isDigit(t.charAt(k))) digits = false;
            if (digits && t.length() < 19) out.add(new String[] {t, line.substring(sp + 1)});
        }
        return out;
    }
}
