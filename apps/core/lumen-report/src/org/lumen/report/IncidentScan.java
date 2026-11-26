// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.report;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which DropBox entries are incidents of the image itself (automatic reports): crashes and freezes of system_server
 * and of the image's own apps, watchdog and framework restarts, a kernel panic of the last start. Crashes of apps the
 * user installed (data_app_*) are never incidents. Plain Java.
 */
final class IncidentScan {
    static final String[] CRASH_TAGS = {"system_server_crash", "system_server_native_crash", "system_app_crash",
            "system_app_native_crash"};
    static final String[] ANR_TAGS = {"system_server_anr", "system_app_anr"};
    static final String[] BOOT_TAGS = {"system_server_watchdog", "SYSTEM_RESTART", "SYSTEM_LAST_KMSG"};
    /** The pseudo tag of a start after a kernel panic or a hardware watchdog (sys.boot.reason). */
    static final String BOOT_REASON = "bootreason";
    /** Entries older than this are history, not news (a scan after a long time off). */
    static final long MAX_AGE_MS = 2L * 24 * 3600 * 1000;

    private static final Pattern PROCESS = Pattern.compile("(?m)^Process: ([\\w.:-]{1,128})");
    private static final Pattern PANIC = Pattern.compile(
            "Kernel panic - not syncing|Internal error: Oops|Last boot reason: (kernel_panic|watchdog)");
    private static final Pattern BAD_BOOT = Pattern.compile("(panic|watchdog|wdt)");

    private IncidentScan() {}

    static String[] tags() {
        String[] all = new String[CRASH_TAGS.length + ANR_TAGS.length + BOOT_TAGS.length];
        System.arraycopy(CRASH_TAGS, 0, all, 0, CRASH_TAGS.length);
        System.arraycopy(ANR_TAGS, 0, all, CRASH_TAGS.length, ANR_TAGS.length);
        System.arraycopy(BOOT_TAGS, 0, all, CRASH_TAGS.length + ANR_TAGS.length, BOOT_TAGS.length);
        return all;
    }

    /** The tags an automatic report reads: the watched ones, and SYSTEM_BOOT too after an unexpected stop. */
    static String[] autoTags(boolean stop) {
        String[] t = tags();
        if (!stop) return t;
        String[] all = Arrays.copyOf(t, t.length + 1);
        all[t.length] = "SYSTEM_BOOT";
        return all;
    }

    /**
     * After an unexpected stop the start records of this start go in even without a panic: SYSTEM_BOOT and
     * SYSTEM_LAST_KMSG written since {@code bootStart} (the kernel log of the start before, when the kernel kept it).
     */
    static boolean bootRecord(String tag, long time, long bootStart) {
        return ("SYSTEM_BOOT".equals(tag) || "SYSTEM_LAST_KMSG".equals(tag)) && time >= bootStart;
    }

    static ReportKind kindOf(String tag) {
        for (String t : CRASH_TAGS) if (t.equals(tag)) return ReportKind.CRASH;
        for (String t : ANR_TAGS) if (t.equals(tag)) return ReportKind.ANR;
        for (String t : BOOT_TAGS) if (t.equals(tag)) return ReportKind.BOOT;
        return null;
    }

    /**
     * The incident of one DropBox entry ({@code text} = its start, for SYSTEM_LAST_KMSG the whole entry), or null
     * when automatic reports leave it out: not a watched tag, an app that is not the image's, a last kernel log of
     * a normal restart, an entry older than MAX_AGE_MS or not newer than {@code since}.
     */
    static Incident of(String tag, long time, String text, long since, long now) {
        ReportKind k = kindOf(tag);
        if (k == null || time <= since || now - time > MAX_AGE_MS) return null;
        if (tag.startsWith("system_server") || tag.equals("SYSTEM_RESTART")) return new Incident(tag, time, k, "system_server");
        if (tag.equals("SYSTEM_LAST_KMSG")) {
            return text != null && PANIC.matcher(text).find() ? new Incident(tag, time, k, "kernel") : null;
        }
        String p = process(text);
        return p != null && OwnApps.own(p) ? new Incident(tag, time, k, p) : null;
    }

    /** The process named in a crash or ANR header ("Process: org.lumen.home"), without a ":suffix". */
    static String process(String text) {
        if (text == null) return null;
        Matcher m = PROCESS.matcher(text);
        if (!m.find()) return null;
        String p = m.group(1);
        int colon = p.indexOf(':');
        return colon > 0 ? p.substring(0, colon) : p;
    }

    /** A start after a kernel panic or a watchdog reset (sys.boot.reason), once per start; else null. */
    static Incident bootReason(String reason, long bootTime) {
        if (reason == null || !BAD_BOOT.matcher(reason.toLowerCase(Locale.ROOT)).find()) return null;
        return new Incident(BOOT_REASON, bootTime, ReportKind.BOOT, "kernel");
    }
}
