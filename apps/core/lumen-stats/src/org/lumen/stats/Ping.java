// SPDX-License-Identifier: Apache-2.0
package org.lumen.stats;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The statistics protocol of every Lumen projector, plain Java (the host test runs it on a JVM): the body the server
 * (tools/stats/server/lumen_stats.py) takes, when a ping is due and what the previous version and the update method
 * of a ping are.
 */
public final class Ping {
    private Ping() {}

    /** Default of the statistics switch (the one place that sets it). */
    public static final boolean DEFAULT_ON = true;
    /**
     * Tried in order until one answers 2xx. Empty = no statistics server in this build: nothing is sent and every
     * statistics switch is hidden ("never show what does not work").
     */
    public static final String[] URLS = {"https://prompt.az/lumen/v1/ping"};
    /** A failed send is tried again at the next trigger, but not sooner than this. */
    public static final long RETRY_MS = 5 * 60 * 1000L;
    public static final int VERSION = 3;

    private static final Pattern PRERELEASE = Pattern.compile("-(beta|rc)[0-9]*$");
    private static final Pattern XYZ = Pattern.compile("([0-9]{1,3})\\.([0-9]{1,2})\\.([0-9]{1,2})(?:-(?:beta|rc)([0-9]{1,2}))?$");

    public static boolean configured() { return URLS.length > 0; }

    /**
     * May statistics be sent at all: a server is configured, the switch is on, the first-run setup is finished and
     * the host holds nothing back (the updater: the one-time notice after an update is due).
     */
    public static boolean allowed(boolean configured, boolean on, boolean setupDone, boolean hold) {
        return configured && on && setupDone && !hold;
    }

    /**
     * Send now: once per start of the projector (a new boot), at least once a day on a projector that only sleeps,
     * and at once after an update; a failed try waits RETRY_MS before the next one.
     */
    public static boolean due(boolean updated, boolean newBoot, boolean newDay, long sinceTryMs) {
        return (updated || newBoot || newDay) && sinceTryMs >= RETRY_MS;
    }

    /** The build's channel: beta for a pre-release version (x.y.z-betaN, -rcN, also behind a model prefix). */
    public static String channel(String version) {
        return version != null && PRERELEASE.matcher(version).find() ? "beta" : "stable";
    }

    /**
     * A version code for an image without one (ro.lumen.version_code missing): x*1e6 + y*1e4 + z*100 + n, n the
     * beta or rc number, 99 for a final (the HB scheme), from the x.y.z at the end of the version; 0 if none.
     */
    public static int versionCode(String version) {
        Matcher m = XYZ.matcher(version == null ? "" : version);
        if (!m.find()) return 0;
        int n = m.group(4) == null ? 99 : Integer.parseInt(m.group(4));
        return Integer.parseInt(m.group(1)) * 1000000 + Integer.parseInt(m.group(2)) * 10000
                + Integer.parseInt(m.group(3)) * 100 + n;
    }

    /**
     * The update fields of a ping: {prev_version, prev_version_code, method}. lastVc 0 = the first ping of this
     * install ("first"); another version than the last sent one = an update, its method as recorded for this build
     * at the install (ota_full, ota_delta, usb) or "installer" when no update of this app did it; else none.
     */
    public static String[] update(int lastVc, String lastVersion, int vc, String build, String methodBuild,
            String method) {
        if (lastVc == 0) return new String[] {"", "0", "first"};
        if (lastVc != vc) {
            boolean known = build != null && !build.isEmpty() && build.equals(methodBuild) && method != null
                    && !method.isEmpty();
            return new String[] {lastVersion == null ? "" : lastVersion, String.valueOf(lastVc),
                    known ? method : "installer"};
        }
        return new String[] {"", "0", ""};
    }

    /** The JSON body, written by hand (no org.json: the host test runs it on a plain JVM). */
    public static String body(String id, String serial, String model, String xgimiModel, String version,
            int versionCode, String build, boolean trial, String prevVersion, int prevVersionCode, String method) {
        return "{\"v\":" + VERSION + ",\"id\":" + str(id) + ",\"serial\":" + str(serial) + ",\"model\":"
                + str(model) + ",\"xgimi_model\":" + str(xgimiModel) + ",\"version\":" + str(version)
                + ",\"version_code\":" + versionCode + ",\"build\":" + str(build) + ",\"channel\":"
                + str(channel(version)) + ",\"trial\":" + trial + ",\"prev_version\":" + str(prevVersion)
                + ",\"prev_version_code\":" + prevVersionCode + ",\"method\":" + str(method) + "}";
    }

    static String str(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char ch : (s == null ? "" : s).toCharArray()) {
            if (ch == '"' || ch == '\\') b.append('\\').append(ch);
            else if (ch < 0x20) b.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
            else b.append(ch);
        }
        return b.append('"').toString();
    }

    /** The UTC day of a time (the server counts UTC days too). */
    public static String day(long t) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(t));
    }

    public static String newId() { return UUID.randomUUID().toString(); }
}
