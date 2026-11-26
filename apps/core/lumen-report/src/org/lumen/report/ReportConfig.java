// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.report;

import android.os.SystemProperties;
import android.util.Log;

import java.util.List;

/** Switches and limits of problem reports (the address rules: ReportUrl). */
public final class ReportConfig {
    static final String TAG = "LumenReport";

    static final String PROP_URL = "ro.lumen.report.url";
    static final String PROP_URL_DEBUG = "persist.lumen.report.url";

    /** Logcat window of a manual report when the image's log helper gives no answer (RootLogs takes the whole buffer). */
    static final int LOG_MINUTES = 30;
    /** Logcat window of an automatic report: the minutes around the incident. */
    static final int AUTO_LOG_MINUTES = 10;
    /** Upper bound of a manual zip; the server takes up to 5 MiB (lumen_reports.MAX_BYTES). */
    static final long MAX_ZIP_BYTES = 5_000_000L;
    /** Upper bound of an automatic zip: a crash needs its record and a few minutes of log. */
    static final long AUTO_MAX_ZIP_BYTES = 1_000_000L;
    /** Raw (scrubbed, uncompressed) caps per section; ReportZip halves them while the zip is too big. */
    static final long CAP_LOGCAT = 6L << 20;
    static final long CAP_CRASH = 512L << 10;
    static final long CAP_KERNEL = 1L << 20;
    static final long CAP_PREV_BOOT = 1L << 20;
    static final long CAP_PSTORE = 512L << 10;
    static final long CAP_OTA = 256L << 10;
    static final long CAP_DIAG = 256L << 10;
    static final long CAP_DROPBOX_ENTRY = 256L << 10;
    static final long CAP_DROPBOX_TOTAL = 2L << 20;
    /** Dropbox and exit-reason window of a manual report; an automatic one looks at the last day only. */
    static final long HISTORY_MS = 7L * 24 * 3600 * 1000;
    static final long AUTO_HISTORY_MS = 24L * 3600 * 1000;
    static final long SHORT_EXEC_TIMEOUT_MS = 15_000;

    private ReportConfig() {}

    /** The upload addresses in order, empty when problem reports are off on this image. Any thread. */
    static List<String> urls() {
        return ReportUrl.choose(prop(PROP_URL_DEBUG), prop(PROP_URL));
    }

    /** True when this image sends problem reports at all. */
    public static boolean enabled() {
        return !urls().isEmpty();
    }

    static String prop(String k) {
        try {
            return SystemProperties.get(k, "");
        } catch (Throwable t) {
            Log.w(TAG, "prop " + k + ": " + t);
            return "";
        }
    }
}
