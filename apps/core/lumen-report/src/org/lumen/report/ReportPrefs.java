// SPDX-License-Identifier: Apache-2.0
package org.lumen.report;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.io.File;
import java.security.SecureRandom;

/**
 * Report preferences (file lumen_report): the random install id of the reports (made on the first report, never
 * derived from a hardware id, not the statistics id), the last manual and the last automatic report code, the switch
 * of automatic reports (on by default), the log of sent automatic reports and the time of the last DropBox scan.
 * Turning the switch off forgets the automatic log and the install id.
 */
final class ReportPrefs {
    private static final String PREFS = "lumen_report";
    private static final String K_INSTALL_ID = "install_id";
    private static final String K_AUTO = "auto";
    private static final String K_AUTO_LOG = "auto_log";
    private static final String K_SCAN = "scan_since";
    private static final String K_BOOT = "boot_handled";

    private ReportPrefs() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 24 lowercase hex characters (96 random bits); the server accepts [0-9a-f]{16,64}. */
    static synchronized String installId(Context c) {
        SharedPreferences p = prefs(c);
        String id = p.getString(K_INSTALL_ID, null);
        if (id != null && id.matches("[0-9a-f]{24}")) return id;
        byte[] b = new byte[12];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder(24);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        id = sb.toString();
        p.edit().putString(K_INSTALL_ID, id).apply();
        return id;
    }

    static boolean autoOn(Context c) {
        return prefs(c).getBoolean(K_AUTO, AutoPolicy.DEFAULT_ON);
    }

    static void setAutoOn(Context c, boolean on) {
        SharedPreferences.Editor e = prefs(c).edit().putBoolean(K_AUTO, on);
        if (!on) e.remove(K_AUTO_LOG).remove(K_INSTALL_ID);
        e.apply();
    }

    /** The same first-run rule as the statistics (lumen-stats Device.setupDone). */
    static boolean setupDone(Context c) {
        try {
            return Settings.Secure.getInt(c.getContentResolver(), Settings.Secure.USER_SETUP_COMPLETE, 0) == 1;
        } catch (Throwable t) {
            return false;
        }
    }

    static String autoLog(Context c) {
        return prefs(c).getString(K_AUTO_LOG, "");
    }

    static void setAutoLog(Context c, String log) {
        prefs(c).edit().putString(K_AUTO_LOG, log).apply();
    }

    /** DropBox entries up to this time were looked at; 0 = never (the first scan starts now, no old history). */
    static long scanSince(Context c) {
        return prefs(c).getLong(K_SCAN, 0);
    }

    static void setScanSince(Context c, long t) {
        prefs(c).edit().putLong(K_SCAN, t).apply();
    }

    /** The boot count whose boot reason was looked at already. */
    static int bootHandled(Context c) {
        return prefs(c).getInt(K_BOOT, -1);
    }

    static void setBootHandled(Context c, int boot) {
        prefs(c).edit().putInt(K_BOOT, boot).apply();
    }

    static void setLast(Context c, ReportKind kind, String code, long timeMs) {
        String k = kind.automatic() ? "auto" : "manual";
        prefs(c).edit().putString("last_code_" + k, code).putLong("last_time_" + k, timeMs).apply();
    }

    static String lastCode(Context c, boolean automatic) {
        return prefs(c).getString("last_code_" + (automatic ? "auto" : "manual"), null);
    }

    static long lastTime(Context c, boolean automatic) {
        return prefs(c).getLong("last_time_" + (automatic ? "auto" : "manual"), 0);
    }

    /** Reports waiting for the server (files/report_queue). */
    static ReportQueue queue(Context c) {
        return new ReportQueue(new File(c.getApplicationContext().getFilesDir(), "report_queue"));
    }
}
