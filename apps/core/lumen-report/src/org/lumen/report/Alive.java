// SPDX-License-Identifier: Apache-2.0
package org.lumen.report;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * The sign of life of a running projector (files/report_alive, one line) and the rules around it. While the picture
 * is on the record says UP with a fresh time (AliveWatch, AliveState); every normal way out (screen off, standby,
 * shutdown, reboot) turns it CLEAN first. A start that finds an UP record of another start and no orderly reboot
 * reason follows an unexpected stop: a freeze and a pulled plug, a hard reset, a kernel panic. That start leaves the
 * record in files/report_stop for one automatic report (AutoReports). Plain Java.
 *
 * Line: {@code v=1 state=up|clean why=<event> time=<wall ms> up=<ms since start> boot=<boot id> app=<component>
 * input=hdmi|app screensaver=0|1}.
 */
final class Alive {
    static final String UP = "up", CLEAN = "clean";
    /** The pseudo DropBox tag and the process of the incident (Incident key "boot:unexpected_stop"). */
    static final String STOP_TAG = "unexpected_stop";
    /** Lamp-only standby of Z9X and HB: this activity covers the picture while Android stays awake. */
    static final String STANDBY_ACTIVITY = "org.lumen.projector/org.lumen.projector.power.StandbyActivity";
    /** The HDMI viewer of every model. */
    static final String HDMI_VIEWER = "org.lumen.tvinput/org.lumen.tvinput.PassthroughActivity";
    /** Sub reasons bootstat gives a hardware event under "reboot," or "shutdown," (aliases of bootloader reasons). */
    private static final String[] HARDWARE = {"longkey", "undervoltage", "powerloss", "thermal"};
    /** bootstat knownReasons: a bootloader reason with one of these words is never echoed as "reboot,<reason>". */
    private static final String[] KNOWN = {"watchdog", "kernel_panic", "recovery", "bootloader", "cold", "hard", "warm",
            "shutdown", "reboot"};
    /** Keys of the boot reason lines of a pending stop, in the order of the report. */
    static final String[] REASON_PROPS = {"sys.boot.reason.last", "sys.boot.reason", "ro.boot.bootreason",
            "persist.sys.boot.reason"};

    final String state, why, boot, app;
    final long time, up;
    final boolean hdmi, screensaver;

    Alive(String state, String why, long time, long up, String boot, String app, boolean screensaver) {
        this.state = state;
        this.why = why;
        this.time = time;
        this.up = up;
        this.boot = boot == null ? "" : boot;
        this.app = app == null ? "" : app;
        this.hdmi = HDMI_VIEWER.equals(this.app);
        this.screensaver = screensaver;
    }

    String line() {
        return "v=1 state=" + state + " why=" + token(why) + " time=" + time + " up=" + up + " boot=" + token(boot)
                + " app=" + token(app) + " input=" + (hdmi ? "hdmi" : "app") + " screensaver=" + (screensaver ? 1 : 0);
    }

    /** The record of the first line of {@code text}, or null when it is missing or damaged. */
    static Alive parse(String text) {
        if (text == null) return null;
        int nl = text.indexOf('\n');
        Map<String, String> kv = new LinkedHashMap<>();
        for (String w : (nl < 0 ? text : text.substring(0, nl)).trim().split(" ")) {
            int eq = w.indexOf('=');
            if (eq > 0) kv.put(w.substring(0, eq), w.substring(eq + 1));
        }
        String state = kv.get("state"), why = kv.get("why");
        if (!"1".equals(kv.get("v")) || !(UP.equals(state) || CLEAN.equals(state)) || why == null || why.isEmpty()) {
            return null;
        }
        try {
            long time = Long.parseLong(kv.get("time")), up = Long.parseLong(kv.get("up"));
            if (up < 0) return null;
            return new Alive(state, why, time, up, untoken(kv.get("boot")), untoken(kv.get("app")),
                    "1".equals(kv.get("screensaver")));
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    /**
     * Both records belong to one start: the same boot id, or (no boot id on one side) a time since the start that
     * did not go back.
     */
    static boolean sameBoot(Alive m, String boot, long up) {
        if (!m.boot.isEmpty() && boot != null && !boot.isEmpty()) return m.boot.equals(boot);
        return m.up <= up;
    }

    /** The last start ended without any normal way out: its record still says UP and no orderly reason explains it. */
    static boolean stopped(Alive prev, String boot, long up, String last, String reason, String bootloader) {
        return prev != null && UP.equals(prev.state) && !sameBoot(prev, boot, up) && !orderly(last, reason, bootloader);
    }

    /**
     * The start follows a reboot or power off that init carried out on request (bootstat: sys.boot.reason.last,
     * else sys.boot.reason): "reboot,<why>", "shutdown,<why>", shutdown, bootloader, recovery, fastboot. Not orderly:
     * a kernel reason (watchdog, kernel_panic), the bare "reboot" of a power cut, the hardware sub reasons of
     * HARDWARE, and "reboot,<x>" that bootstat only made of an unknown bootloader reason x.
     */
    static boolean orderly(String last, String reason, String bootloader) {
        String r = norm(last).isEmpty() ? norm(reason) : norm(last);
        if (r.isEmpty() || echo(r, bootloader)) return false;
        if (r.equals("shutdown") || r.equals("bootloader") || r.equals("recovery") || r.equals("fastboot")) return true;
        int comma = r.indexOf(',');
        if (comma < 0) return false;
        String head = r.substring(0, comma), sub = r.substring(comma + 1);
        if (!head.equals("reboot") && !head.equals("shutdown")) return false;
        for (String h : HARDWARE) if (sub.startsWith(h)) return false;
        return true;
    }

    /** bootstat's last resort: an unknown bootloader reason x becomes "reboot,x" (it says nothing about init). */
    private static boolean echo(String r, String bootloader) {
        String b = norm(bootloader);
        if (b.isEmpty()) return false;
        for (String w : b.split("[,_-]")) for (String k : KNOWN) if (k.equals(w)) return false;
        if (b.startsWith("reboot")) {
            b = b.substring("reboot".length());
            while (b.startsWith(",") || b.startsWith("_")) b = b.substring(1);
        }
        return !b.isEmpty() && r.equals("reboot," + b);
    }

    /**
     * The first look of a start: an unexpected stop of the start before goes to {@code pending} (the record and the
     * boot reasons), then {@code marker} gets a record of this start, so a second look in it (the app process
     * started again) finds nothing. True when a stop was noticed. {@code reasons}: the values of REASON_PROPS.
     */
    static boolean bootCheck(File marker, File pending, String boot, long up, long now, String... reasons) {
        Alive prev = parse(AliveFile.read(marker));
        if (prev != null && sameBoot(prev, boot, up)) return false;
        boolean stop = stopped(prev, boot, up, at(reasons, 0), at(reasons, 1), at(reasons, 2));
        if (stop) {
            StringBuilder sb = new StringBuilder(prev.line()).append("\nfound=").append(now);
            for (int i = 0; i < REASON_PROPS.length; i++) sb.append('\n').append(REASON_PROPS[i]).append('=').append(token(at(reasons, i)));
            AliveFile.write(pending, sb.append('\n').toString());
        }
        AliveFile.write(marker, new Alive(CLEAN, "start", now, up, boot, "", false).line());
        return stop;
    }

    /** The incident of a pending stop (bootCheck), with its plain English text, or null. */
    static Incident incident(String pending, TimeZone tz) {
        Alive prev = parse(pending);
        if (prev == null) return null;
        Map<String, String> kv = new LinkedHashMap<>();
        for (String l : pending.split("\n")) {
            int eq = l.indexOf('=');
            if (eq > 0 && !l.startsWith("v=")) kv.put(l.substring(0, eq), untoken(l.substring(eq + 1)));
        }
        return new Incident(STOP_TAG, prev.time, ReportKind.BOOT, STOP_TAG, text(prev, kv, tz));
    }

    /** unexpected_stop.txt of the report: plain English, no semicolons or dashes. */
    static String text(Alive prev, Map<String, String> kv, TimeZone tz) {
        StringBuilder sb = new StringBuilder();
        sb.append("The projector stopped without shutting down. The last start ended in a freeze, a pulled plug, a hard\n")
                .append("reset or a kernel panic: its sign of life was still on and no orderly restart or power off followed.\n\n");
        sb.append("Last sign of life: ").append(when(prev.time, tz)).append(", ").append(duration(prev.up))
                .append(" after that start\n");
        sb.append("Written because of: ").append(prev.why).append('\n');
        sb.append("App in front: ").append(prev.app.isEmpty() ? "unknown" : prev.app).append('\n');
        sb.append("Input: ").append(prev.hdmi ? "HDMI" : "an app").append('\n');
        sb.append("Screensaver: ").append(prev.screensaver ? "on" : "off").append('\n');
        try {
            long found = Long.parseLong(kv.get("found"));
            sb.append("Noticed at the next start: ").append(when(found, tz)).append('\n');
        } catch (NumberFormatException ignored) {
            // missing or damaged: the rest still helps
        }
        sb.append("Boot reasons of that next start:");
        for (String p : REASON_PROPS) {
            String v = kv.get(p);
            sb.append(' ').append(p).append('=').append(v == null || v.isEmpty() ? "none" : v);
        }
        sb.append("\n\nAfter a power cut the clock may be wrong until the network sets it: then the times above are off,\n")
                .append("the time after the start is not.\n");
        return sb.toString();
    }

    private static String when(long t, TimeZone tz) {
        SimpleDateFormat utc = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US);
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat local = new SimpleDateFormat("HH:mm:ss", Locale.US);
        local.setTimeZone(tz);
        int off = tz.getOffset(t) / 60_000;
        return utc.format(t) + String.format(Locale.US, " (%s local time, UTC%s%02d:%02d)", local.format(t),
                off < 0 ? "-" : "+", Math.abs(off) / 60, Math.abs(off) % 60);
    }

    private static String duration(long ms) {
        long m = ms / 60_000;
        return (m / 60) + " h " + (m % 60) + " min";
    }

    private static String at(String[] a, int i) {
        return a != null && i < a.length && a[i] != null ? a[i] : "";
    }

    /** bootstat's transformReason: lower case, blanks become '_'. */
    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s", "_");
    }

    private static String token(String s) {
        return s == null || s.isEmpty() ? "-" : s.replaceAll("\\s", "_");
    }

    private static String untoken(String s) {
        return s == null || s.equals("-") ? "" : s;
    }
}
