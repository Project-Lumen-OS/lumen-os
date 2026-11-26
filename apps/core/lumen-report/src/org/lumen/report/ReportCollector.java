// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.report;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.DropBoxManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Collects one problem report on a worker thread (never on the main thread) and writes the zip. Model-neutral: the
 * same sections on every projector, a file or property a model does not have is a line in summary.txt.
 * A manual report (the consent screen lists the same):
 *   summary.txt          kind, model, versions, build, slot, trial or installed, uptime, boot reason, memory, storage,
 *                        displays, thermal status, network TYPE only, section status
 *   props.txt            allow-listed system properties (PropFilter: build, boot, Lumen, display; never serial / MAC)
 *   logcat.txt           main + system + events of the whole buffer, system and image apps only (LogcatFilter)
 *   logcat_crash.txt     crash buffer of this boot (any app)
 *   logcat_kernel.txt    the kernel log of this start (dmesg)
 *   previous_boot/       logcat -L (pstore pmsg), pstore console, /proc/last_kmsg, z9x_diag summaries (best effort)
 *   ota_log.txt          updater / update_engine lines of the whole log buffer
 *   dropbox/             crash, ANR, watchdog, tombstone, last kmsg and boot entries of the last 7 days
 *   exit_reasons.txt     ApplicationExitInfo of the last 7 days (all apps with DUMP, else ours)
 *   hdmi.txt             dumpsys hdmi_control (CEC devices, power status, System Audio, ARC, the last CEC messages),
 *                        the CEC messages of logcat.txt with the power polls counted (CecJournal) and the HDMI / ARC /
 *                        System Audio lines of dumpsys audio (HdmiDump)
 *   power.txt            dumpsys power without the wake lock and uid lists (PowerDump)
 * An automatic report (ReportKind CRASH / ANR / BOOT) is smaller: summary.txt with the incidents, props.txt,
 * logcat.txt and logcat_crash.txt (the newest lines that fit its zip) and dropbox/ of the last day, all of them
 * limited to the system and the image's own apps (crash records of apps the user installed stay out too).
 * After an unexpected stop of the last start (Alive) it also holds unexpected_stop.txt (last sign of life, app in
 * front, input, boot reasons, in plain English), previous_boot/ (its log lines filtered the automatic way) and the
 * SYSTEM_BOOT and SYSTEM_LAST_KMSG records of this start even without a panic.
 * The logs, the kernel log and the dumpsys texts come from the image's root log helper (RootLogs, never a dialog
 * to the user); only when it gives no answer the app runs logcat itself (then logcat.txt may hold this app's lines
 * only, summary.txt says so).
 * Every line passes the Scrubber. Every section is best effort: a failure is a line in summary.txt
 * (Android 14 SELinux keeps pstore and /metadata from a platform app; that is expected, not an error).
 * Permissions used (README): READ_LOGS (logcat of other processes, DropBox), PACKAGE_USAGE_STATS (DropBox), DUMP
 * (exit reasons of other apps), ACCESS_WIFI_STATE (saved Wi-Fi names, only to remove them), BLUETOOTH_CONNECT
 * (paired device names, only to remove them).
 */
final class ReportCollector {
    private static final String TAG = ReportConfig.TAG;

    interface Progress {
        void onStep(int step, int total);

        boolean isCanceled();

        /** The running child process (logcat / getprop) so that a cancel can destroy it at once; null = none. */
        void setProcess(Process p);
    }

    static final int STEPS = 9;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** DropBox tags worth a report; binary entries (tombstone protos) are skipped by the IS_TEXT check. */
    private static final String[] DROPBOX_TAGS = {
            "system_server_crash", "system_server_native_crash", "system_server_anr", "system_server_watchdog",
            "system_server_wtf", "system_server_lowmem", "system_app_crash", "system_app_native_crash",
            "system_app_anr", "data_app_crash", "data_app_native_crash", "data_app_anr", "SYSTEM_TOMBSTONE",
            "SYSTEM_LAST_KMSG", "SYSTEM_BOOT", "SYSTEM_RESTART", "SYSTEM_RECOVERY_LOG", "SYSTEM_RECOVERY_KMSG",
            "SYSTEM_FSCK", "SYSTEM_AUDIT",
    };
    private static final int DROPBOX_PER_TAG = 5;
    /** Upper bound of entries walked per tag (DropBox itself keeps about 1000 files in all). */
    private static final int DROPBOX_SCAN_PER_TAG = 1000;

    private final Context c;
    private final File dir;
    private final Progress progress;
    private final ReportKind kind;
    private final boolean manual;
    private final List<Incident> incidents;
    /** The unexpected stop among the incidents, or null. */
    private final Incident stop;
    private final Scrubber scrubber = new Scrubber();
    private final List<ReportZip.Part> parts = new ArrayList<>();
    private final StringBuilder notes = new StringBuilder();
    private Set<Integer> imageAppIds = Collections.emptySet();
    private String bluetoothLine = "";
    /** The log helper's dumpsys texts by service (RootLogs.DUMPSYS), for hdmi(); empty without its answer. */
    private final java.util.Map<String, String> dumps = new java.util.HashMap<>();
    /** The CEC messages of logcat.txt for hdmi.txt (CecJournal), fed while logcat.txt is written. */
    private final CecJournal cec = new CecJournal();

    ReportCollector(Context c, File dir, Progress progress, ReportKind kind, List<Incident> incidents) {
        this.c = c.getApplicationContext();
        this.dir = dir;
        this.progress = progress;
        this.kind = kind;
        this.manual = !kind.automatic();
        this.incidents = incidents == null ? Collections.emptyList() : incidents;
        Incident s = null;
        for (Incident i : this.incidents) if (Alive.STOP_TAG.equals(i.tag)) s = i;
        this.stop = s;
    }

    /** Collects the sections of this kind and writes {@code dir/lumen-report.zip}; returns it. */
    File collect() throws IOException {
        final long now = System.currentTimeMillis();
        final int minutes = manual ? ReportConfig.LOG_MINUTES : ReportConfig.AUTO_LOG_MINUTES;
        final String since = String.format(Locale.US, "%d.000", (now - minutes * 60_000L) / 1000);

        step(1);
        knownPersonalValues();
        imageAppIds = imageAppIds();

        step(2);
        props();

        step(3);
        boolean root = rootLogs(since);

        if (manual) {
            step(4);
            if (!root) {
                logcat("logcat_kernel.txt", ReportConfig.CAP_KERNEL, ReportConfig.SHORT_EXEC_TIMEOUT_MS, false,
                        "-d", "-b", "kernel", "-v", "threadtime", "-v", "uid", "-t", since);
            }

            step(5);
            previousBoot();

            step(6);
            logcat("ota_log.txt", ReportConfig.CAP_OTA, ReportConfig.SHORT_EXEC_TIMEOUT_MS, false,
                    "-d", "-b", "main,system", "-v", "threadtime", "-v", "uid",
                    "-s", "z9x_ota:V", "LumenUpdater:V", "update_engine:V", "update_verifier:V");
            textFile("ota_gate_log.txt", "/metadata/z9x_ota/log.txt", ReportConfig.CAP_OTA);
            miracastDiag();
            hdmi();
        } else if (stop != null) {
            parts.add(ReportZip.Part.text("unexpected_stop.txt", scrubber.scrubText(stop.detail)));
            note("unexpected_stop.txt: the last start ended without a shutdown");
            previousBoot();
        }

        step(7);
        dropbox(now);

        if (manual) {
            step(8);
            exitReasons(now);
        }

        step(9);
        parts.add(0, ReportZip.Part.text("summary.txt", summary(now)));
        File zip = new File(dir, "lumen-report.zip");
        long max = manual ? ReportConfig.MAX_ZIP_BYTES : ReportConfig.AUTO_MAX_ZIP_BYTES;
        int shrink = ReportZip.writeWithin(zip, parts, max);
        if (shrink < 0) throw new IOException("report does not fit " + max + " bytes");
        Log.i(TAG, kind.wire + " report zip " + zip.length() + " bytes (shrink " + shrink + ", "
                + scrubber.replacedCount() + " replacements)");
        return zip;
    }

    // ------------------------------------------------------------------ steps
    private void step(int n) throws CanceledException {
        if (progress.isCanceled()) throw new CanceledException();
        progress.onStep(n, STEPS);
    }

    private void note(String s) {
        notes.append(s).append('\n');
    }

    /**
     * Values that identify the user and that the device itself knows: saved / current Wi-Fi names, the
     * projector's own name, paired Bluetooth device names, serial numbers. They are only fed to the Scrubber
     * (replaced by tokens); none of them is written anywhere.
     */
    @SuppressWarnings("deprecation")                     // WifiConfiguration: still the API for saved networks
    private void knownPersonalValues() {
        try {
            WifiManager wm = c.getSystemService(WifiManager.class);
            if (wm != null) {
                List<WifiConfiguration> saved = wm.getConfiguredNetworks();
                if (saved != null) for (WifiConfiguration w : saved) wifiName(w.SSID);
                WifiInfo wi = wm.getConnectionInfo();
                if (wi != null) wifiName(wi.getSSID());
            }
        } catch (Throwable t) {
            note("wifi names for scrubbing: " + t.getClass().getSimpleName() + " (regexes still apply)");
        }
        String model = Build.MODEL == null ? "" : Build.MODEL;
        String defaultBt = prop("bluetooth.device.default_name");
        try {
            ownName(Settings.Global.getString(c.getContentResolver(), Settings.Global.DEVICE_NAME), model, defaultBt);
            ownName(Settings.Secure.getString(c.getContentResolver(), "bluetooth_name"), model, defaultBt);
        } catch (Throwable t) {
            Log.w(TAG, "device name: " + t);
        }
        try {
            BluetoothManager bm = c.getSystemService(BluetoothManager.class);
            BluetoothAdapter a = bm == null ? null : bm.getAdapter();
            if (a != null) {
                ownName(a.getName(), model, defaultBt);
                StringBuilder sb = new StringBuilder();
                int n = 0;
                for (BluetoothDevice d : a.getBondedDevices()) {
                    String name = d.getName();
                    scrubber.addLiteral(name, "bt-device");
                    scrubber.addLiteral(d.getAlias(), "bt-device");
                    // only the token: a name too short to become a literal (< 3 chars) is not printed either
                    String shown = name == null ? null : scrubber.scrub(name);
                    sb.append(n++ == 0 ? "" : ", ").append(shown == null || shown.equals(name) ? "?" : shown)
                            .append(" (").append(btType(d.getType())).append(')');
                }
                bluetoothLine = n + " paired" + (n > 0 ? ": " + sb : "");
            }
        } catch (Throwable t) {
            bluetoothLine = "not readable (" + t.getClass().getSimpleName() + ")";
        }
        try {
            scrubber.addLiteral(Build.getSerial(), "serial");      // READ_PRIVILEGED_PHONE_STATE: usually refused
        } catch (Throwable ignored) {
            // the serial regexes still apply
        }
        for (String p : new String[] {"ro.serialno", "ro.boot.serialno"}) {
            String v = prop(p);
            if (!v.isEmpty() && !"unknown".equalsIgnoreCase(v)) scrubber.addLiteral(v, "serial");
        }
    }

    private void wifiName(String ssid) {
        if (ssid == null || WifiManager.UNKNOWN_SSID.equals(ssid)) return;
        scrubber.addLiteral(ssid, "wifi");
    }

    private void ownName(String name, String model, String defaultName) {
        if (name == null || name.trim().isEmpty()) return;
        String n = name.trim();
        if (n.equalsIgnoreCase(model) || n.equalsIgnoreCase(defaultName)) return;   // a factory name says nothing
        scrubber.addLiteral(n, "device-name");
    }

    private static String btType(int t) {
        switch (t) {
            case BluetoothDevice.DEVICE_TYPE_LE: return "LE";
            case BluetoothDevice.DEVICE_TYPE_CLASSIC: return "classic";
            case BluetoothDevice.DEVICE_TYPE_DUAL: return "dual";
            default: return "unknown";
        }
    }

    /** App ids of the image's own apps (system apps with an own prefix): their log lines may go in. */
    private Set<Integer> imageAppIds() {
        Set<Integer> ids = new HashSet<>();
        try {
            List<ApplicationInfo> apps = c.getPackageManager().getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_SYSTEM_ONLY));
            for (ApplicationInfo ai : apps) {
                if (OwnApps.own(ai.packageName)) ids.add(ai.uid % 100_000);
            }
        } catch (Throwable t) {
            note("image apps: " + t.getClass().getSimpleName() + " (only system uids in logcat.txt)");
        }
        ids.add(android.os.Process.myUid() % 100_000);
        return ids;
    }

    // ------------------------------------------------------------------ props
    private void props() throws IOException {
        File out = new File(dir, "props.txt");
        int kept = 0;
        Process p = null;
        Timeout kill = null;
        try {
            p = new ProcessBuilder("/system/bin/getprop").redirectErrorStream(true).start();
            progress.setProcess(p);
            kill = Timeout.start(p, ReportConfig.SHORT_EXEC_TIMEOUT_MS);
            try (BufferedReader r = reader(p); Writer w = writer(out)) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!PropFilter.keep(line)) continue;
                    w.write(scrubber.scrub(line));
                    w.write('\n');
                    kept++;
                }
            }
            parts.add(ReportZip.Part.file("props.txt", out, 256L << 10));
            note("props.txt: " + kept + " properties");
        } catch (IOException e) {
            if (progress.isCanceled()) throw new CanceledException();
            note("props.txt: failed (" + e.getMessage() + ")");
        } finally {
            finish(p, kill);
        }
    }

    // ------------------------------------------------------------------ logcat
    /** The root log helper's properties (RootLogs). */
    private static final RootLogs.Props PROPS = new RootLogs.Props() {
        @Override public String get(String k) { return ReportConfig.prop(k); }

        @Override public void set(String k, String v) { android.os.SystemProperties.set(k, v); }
    };

    /**
     * The logs of this start from the image's root log helper: logcat.txt and logcat_crash.txt, for a manual report
     * also the kernel log and the dumpsys summaries. Without an answer (an image without the helper, busy, a timeout)
     * the app runs logcat itself, which Android limits to this app's own lines on most models. True = the helper's.
     */
    private boolean rootLogs(String since) throws IOException {
        String token = RootLogs.token(SystemClock.uptimeMillis());
        RootLogs.Answer a = RootLogs.request(PROPS, new RootLogs.Clock() {
            @Override public long now() { return SystemClock.uptimeMillis(); }

            @Override public boolean sleep(long ms) {
                try {
                    Thread.sleep(ms);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                return !progress.isCanceled();
            }
        }, token, RootLogs.TIMEOUT_MS);
        if (a == RootLogs.Answer.CANCELED || progress.isCanceled()) throw new CanceledException();
        File root = new File(RootLogs.DIR);
        if (a == RootLogs.Answer.OK && RootLogs.fresh(root, token)) {
            try {
                filtered("logcat.txt", ReportConfig.CAP_LOGCAT, new File(root, RootLogs.LOGCAT), true);
                filtered("logcat_crash.txt", ReportConfig.CAP_CRASH, new File(root, RootLogs.CRASH), false);
                if (manual) {
                    textFile("logcat_kernel.txt", new File(root, RootLogs.KERNEL).getPath(), ReportConfig.CAP_KERNEL);
                    for (String s : RootLogs.DUMPSYS) dumps.put(s, readText(new File(root, s + ".txt"), HdmiDump.CAP * 4));
                }
            } finally {
                RootLogs.ack(PROPS, token);
            }
            note("logs: the image's log helper (full system log)");
            return true;
        }
        note("logs: the image's log helper gave no logs (" + (a == RootLogs.Answer.OK ? "old files" : a.name().toLowerCase(Locale.US))
                + "), the app's own logcat instead");
        logcat("logcat.txt", ReportConfig.CAP_LOGCAT, ReportConfig.SHORT_EXEC_TIMEOUT_MS,
                true, "-d", "-b", "main,system", "-v", "threadtime", "-v", "uid", "-t", since);
        logcat("logcat_crash.txt", ReportConfig.CAP_CRASH, ReportConfig.SHORT_EXEC_TIMEOUT_MS, false,
                "-d", "-b", "crash", "-v", "threadtime", "-v", "uid");
        return false;
    }

    /** A helper file through LogcatFilter and the Scrubber, like a logcat run of the app. */
    private void filtered(String name, long cap, File in, boolean expectOthers) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(in),
                StandardCharsets.UTF_8), 64 * 1024)) {
            keepLines(name, cap, r, expectOthers, null, 0);
        } catch (CanceledException e) {
            throw e;
        } catch (IOException e) {
            if (progress.isCanceled()) throw new CanceledException();
            note(name + ": failed (" + e.getMessage() + ")");
        }
    }

    /**
     * Runs logcat with {@code args}, keeps the lines LogcatFilter allows (crash and kernel buffers are not
     * uid-filtered by the filter itself: crash = any app by design; kernel = uid 0), scrubs them into a file.
     */
    private void logcat(String name, long cap, long timeoutMs, boolean expectOthers, String... args) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add("/system/bin/logcat");
        Collections.addAll(cmd, args);
        Process p = null;
        Timeout kill = null;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            progress.setProcess(p);
            kill = Timeout.start(p, timeoutMs);
            try (BufferedReader r = reader(p)) {
                keepLines(name, cap, r, expectOthers, kill, timeoutMs);
            }
        } catch (CanceledException e) {
            throw e;
        } catch (IOException e) {
            if (progress.isCanceled()) throw new CanceledException();
            note(name + ": failed (" + e.getMessage() + ")");
        } finally {
            finish(p, kill);
        }
    }

    /** The system log section, also the source of the CEC journal. */
    private static final String LOGCAT = "logcat.txt";

    private void keepLines(String name, long cap, BufferedReader r, boolean expectOthers, Timeout kill, long timeoutMs)
            throws IOException {
        File out = new File(dir, name.replace('/', '_'));
        LogcatFilter f = new LogcatFilter(imageAppIds, android.os.Process.myUid(), manual);
        long lines = 0;
        String odd = null;
        try (Writer w = writer(out)) {
            String line;
            while ((line = r.readLine()) != null) {
                if ((++lines & 1023) == 0 && progress.isCanceled()) throw new CanceledException();
                if (!f.keep(line)) {
                    // logcat's own error message (no log prefix) is worth a note, never the line itself
                    if (odd == null && !line.isEmpty() && !Character.isDigit(line.charAt(0))) {
                        odd = line.length() > 120 ? line.substring(0, 120) : line;
                    }
                    continue;
                }
                String kept = scrubber.scrub(line);
                w.write(kept);
                w.write('\n');
                if (LOGCAT.equals(name)) cec.add(kept);
            }
        }
        parts.add(ReportZip.Part.file(name, out, cap));
        StringBuilder n = new StringBuilder(name).append(": ").append(f.kept()).append(" lines kept, ")
                .append(f.dropped()).append(" left out");
        if (expectOthers) {
            n.append(f.sawOtherProcesses() ? ", full log access" : ", LIMITED log access (only this app's lines)");
        }
        if (kill != null && kill.fired) n.append(", stopped after ").append(timeoutMs / 1000).append(" s");
        if (f.kept() == 0 && odd != null) n.append(", logcat said: ").append(scrubber.scrub(odd));
        note(n.toString());
    }

    /** Destroys a child process that runs too long. */
    private static final class Timeout implements Runnable {
        private final Process p;
        volatile boolean fired;

        private Timeout(Process p) { this.p = p; }

        static Timeout start(Process p, long ms) {
            Timeout t = new Timeout(p);
            MAIN.postDelayed(t, ms);
            return t;
        }

        @Override public void run() {
            fired = true;
            p.destroy();
        }
    }

    private void finish(Process p, Timeout kill) {
        if (kill != null) MAIN.removeCallbacks(kill);
        progress.setProcess(null);
        if (p != null) p.destroy();
    }

    private static BufferedReader reader(Process p) {
        return new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8), 64 * 1024);
    }

    private static Writer writer(File f) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8), 64 * 1024);
    }

    /**
     * The screen mirroring receiver's stage report (org.lumen.miracast StateProvider call "diag", permission
     * org.lumen.miracast.permission.CONTROL, held for the quick menu tile): where the last attempt stopped. Best effort.
     */
    private void miracastDiag() {
        try {
            android.os.Bundle b = c.getContentResolver().call("org.lumen.miracast", "diag", null, null);
            if (b == null) {
                note("miracast_diag.txt: receiver too old (no diag call)");
                return;
            }
            String text = "verdict: " + b.getString("verdict") + "\n\n" + b.getString("report")
                    + "\nlast finished attempt:\n" + b.getString("last") + "\n";
            parts.add(ReportZip.Part.text("miracast_diag.txt", scrubber.scrubText(text)));
            note("miracast_diag.txt: " + text.length() + " chars");
        } catch (Throwable t) {
            note("miracast_diag.txt: not readable (" + t.getClass().getSimpleName() + ")");
        }
    }

    /**
     * hdmi.txt and power.txt (manual): the HDMI-CEC state and history of system_server and the HDMI lines of the audio
     * service (HdmiDump), the power state without the app lists (PowerDump), from the dumps of the image's log helper
     * (RootLogs, read in rootLogs), and the CEC journal fed while logcat.txt was written (log.tag.HDMI lines).
     */
    private void hdmi() {
        String cec = dumps.get("hdmi_control"), audio = dumps.get("audio"), power = dumps.get("power");
        String journal = this.cec.text(ReportConfig.prop(CecJournal.TAG_PROP));
        parts.add(ReportZip.Part.text("hdmi.txt", scrubber.scrubText(HdmiDump.text(cec, journal, audio))));
        parts.add(ReportZip.Part.text("power.txt", scrubber.scrubText(PowerDump.text(power))));
        note("hdmi.txt: hdmi_control " + (cec == null ? "not readable" : cec.length() + " chars")
                + ", system log CEC messages " + this.cec.messages()
                + ", audio " + (audio == null ? "not readable" : audio.length() + " chars")
                + "; power.txt: " + (power == null ? "not readable" : power.length() + " chars"));
    }

    /** A helper file as text, at most {@code max} characters; null when it is not readable. */
    private static String readText(File f, int max) {
        try (java.io.Reader r = new InputStreamReader(new java.io.FileInputStream(f), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while (sb.length() < max && (n = r.read(buf)) > 0) sb.append(buf, 0, n);
            return sb.toString();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** The start before this one, as far as the kernel and logd kept it (all best effort). */
    private void previousBoot() throws IOException {
        // -D: crash is mixed with main/system here; without dividers no "switch to main" follows the first
        // crash entry and LogcatFilter would take every later line (user apps too) as crash buffer
        logcat("previous_boot/logcat_pmsg.txt", ReportConfig.CAP_PREV_BOOT, ReportConfig.SHORT_EXEC_TIMEOUT_MS, false,
                "-L", "-d", "-D", "-b", "main,system,crash", "-v", "threadtime", "-v", "uid");
        String pstore = "/sys/fs/pstore/console-ramoops-0";
        if (!new File(pstore).exists()) pstore = "/sys/fs/pstore/console-ramoops";
        textFile("previous_boot/pstore_console.txt", pstore, ReportConfig.CAP_PSTORE);
        textFile("previous_boot/last_kmsg.txt", "/proc/last_kmsg", ReportConfig.CAP_PSTORE);
        textFile("previous_boot/z9x_diag_history.txt", "/data/misc/z9x_diag/history.txt", ReportConfig.CAP_DIAG);
        textFile("previous_boot/z9x_diag_prev_summary.txt", "/data/misc/z9x_diag/boot.1/prev_summary.txt", ReportConfig.CAP_DIAG);
        textFile("previous_boot/z9x_diag_pm_boot.txt", "/data/misc/z9x_diag/boot.1/pm_boot.txt", ReportConfig.CAP_DIAG);
    }

    // ------------------------------------------------------------------ plain files (best effort)
    private void textFile(String name, String path, long cap) {
        File f = new File(path);
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            long len = r.length();
            long start = Math.max(0, len - cap);
            r.seek(start);
            byte[] b = new byte[(int) Math.min(cap, len)];
            r.readFully(b);
            String text = new String(b, StandardCharsets.UTF_8);
            if (start > 0) {
                int nl = text.indexOf('\n');
                text = "[... " + start + " earlier bytes left out ...]\n" + (nl >= 0 ? text.substring(nl + 1) : text);
            }
            parts.add(ReportZip.Part.text(name, scrubber.scrubText(text)));
            note(name + ": " + len + " bytes");
        } catch (Throwable t) {
            // expected for pstore and /metadata (SELinux / root-only); the note says which one it was
            note(name + ": not readable (" + t.getClass().getSimpleName() + ")");
        }
    }

    // ------------------------------------------------------------------ DropBox
    private void dropbox(long now) throws CanceledException {
        DropBoxManager db = c.getSystemService(DropBoxManager.class);
        if (db == null) {
            note("dropbox: no service");
            return;
        }
        long since = now - (manual ? ReportConfig.HISTORY_MS : ReportConfig.AUTO_HISTORY_MS);
        final String[] tags = manual ? DROPBOX_TAGS : IncidentScan.autoTags(stop != null);
        final long bootStart = now - SystemClock.elapsedRealtime();
        // pass 1: timestamps only (the newest DROPBOX_PER_TAG of every tag), no data read. Entries come
        // oldest first; a crash loop can leave hundreds of one tag, so walk them all (bounded) and keep
        // only the last ones.
        List<long[]> found = new ArrayList<>();                         // {time, tag index}
        try {
            for (int i = 0; i < tags.length; i++) {
                long t = since;
                ArrayDeque<Long> times = new ArrayDeque<>(DROPBOX_PER_TAG + 1);
                for (int n = 0; n < DROPBOX_SCAN_PER_TAG; n++) {
                    DropBoxManager.Entry e = db.getNextEntry(tags[i], t);
                    if (e == null) break;
                    t = e.getTimeMillis();
                    e.close();
                    times.addLast(t);
                    if (times.size() > DROPBOX_PER_TAG) times.removeFirst();
                    if ((n & 63) == 63 && progress.isCanceled()) throw new CanceledException();
                }
                for (long x : times) found.add(new long[] {x, i});
                if (progress.isCanceled()) throw new CanceledException();
            }
        } catch (SecurityException e) {
            note("dropbox: not allowed (needs READ_LOGS and PACKAGE_USAGE_STATS)");
            return;
        } catch (RuntimeException e) {
            // best effort like every section: a DropBox failure must not cost the whole report
            note("dropbox: failed (" + e.getClass().getSimpleName() + ")");
            return;
        }
        // pass 2: newest first until the size cap
        Collections.sort(found, (a, b) -> Long.compare(b[0], a[0]));
        // milliseconds in the name: two entries of one tag in the same second (several apps dying at once)
        // would otherwise give a duplicate zip entry, and ZipOutputStream fails the whole report on that
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd-HHmmss.SSS", Locale.US);
        Set<String> names = new HashSet<>();
        long total = 0;
        int added = 0, skipped = 0;
        for (long[] f : found) {
            if (progress.isCanceled()) throw new CanceledException();
            if (total >= ReportConfig.CAP_DROPBOX_TOTAL) { skipped++; continue; }
            String tag = tags[(int) f[1]];
            DropBoxManager.Entry e = null;
            try {
                e = db.getNextEntry(tag, f[0] - 1);
                if (e == null || e.getTimeMillis() != f[0]) continue;
                String text = e.getText((int) ReportConfig.CAP_DROPBOX_ENTRY);
                if (text == null) continue;                              // binary entry (tombstone proto)
                // automatic: only the image's own incidents (no record of an app the user installed), after an
                // unexpected stop also the start records of this start
                boolean bootRecord = stop != null && IncidentScan.bootRecord(tag, f[0], bootStart);
                if (!manual && !bootRecord && IncidentScan.of(tag, f[0], text, 0, now) == null) continue;
                String base = "dropbox/" + fmt.format(new Date(f[0])) + "_" + tag;
                String name = base + ".txt";
                for (int k = 2; !names.add(name); k++) name = base + "_" + k + ".txt";
                parts.add(ReportZip.Part.text(name, scrubber.scrubText(text)));
                total += text.length();
                added++;
            } catch (Throwable t) {
                Log.w(TAG, "dropbox " + tag + ": " + t);
            } finally {
                if (e != null) e.close();
            }
        }
        note("dropbox: " + added + " entries of the last " + (manual ? "7 days" : "day") + (skipped > 0 ? ", " + skipped + " older ones left out" : ""));
    }

    // ------------------------------------------------------------------ exit reasons
    private void exitReasons(long now) throws CanceledException {
        ActivityManager am = c.getSystemService(ActivityManager.class);
        if (am == null) return;
        long since = now - ReportConfig.HISTORY_MS;
        List<ApplicationExitInfo> all = new ArrayList<>();
        boolean dump = true;
        try {
            // our own package first (always allowed), then every other one while DUMP is granted
            List<String> pkgs = new ArrayList<>();
            pkgs.add(c.getPackageName());
            for (ApplicationInfo ai : c.getPackageManager().getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(0))) {
                if (!c.getPackageName().equals(ai.packageName)) pkgs.add(ai.packageName);
            }
            for (String pkg : pkgs) {
                if (progress.isCanceled()) throw new CanceledException();
                try {
                    for (ApplicationExitInfo x : am.getHistoricalProcessExitReasons(pkg, 0, 10)) {
                        if (x.getTimestamp() >= since) all.add(x);
                    }
                } catch (SecurityException e) {
                    dump = false;                               // no DUMP: other packages are refused alike
                    break;
                } catch (IllegalArgumentException e) {
                    // package removed meanwhile
                }
            }
        } catch (CanceledException e) {
            throw e;
        } catch (Throwable t) {
            note("exit_reasons.txt: failed (" + t.getClass().getSimpleName() + ")");
            return;
        }
        Collections.sort(all, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (ApplicationExitInfo x : all) {
            if (n++ >= 300) break;
            sb.append(fmt.format(new Date(x.getTimestamp()))).append("  ").append(x.getPackageUid() % 100_000)
                    .append(' ').append(x.getProcessName()).append(" pid ").append(x.getPid()).append("  ")
                    .append(reason(x.getReason())).append(" status=").append(x.getStatus())
                    .append(" importance=").append(x.getImportance())
                    .append(" pss=").append(x.getPss() / 1024).append("M rss=").append(x.getRss() / 1024).append('M');
            String d = x.getDescription();
            if (d != null && !d.isEmpty()) sb.append("  ").append(d.replace('\n', ' '));
            sb.append('\n');
        }
        parts.add(ReportZip.Part.text("exit_reasons.txt", scrubber.scrubText(sb.toString())));
        note("exit_reasons.txt: " + Math.min(n, 300) + " exits of the last 7 days"
                + (dump ? "" : " (this app only: other apps need DUMP)"));
    }

    private static String reason(int r) {
        switch (r) {
            case ApplicationExitInfo.REASON_EXIT_SELF: return "EXIT_SELF";
            case ApplicationExitInfo.REASON_SIGNALED: return "SIGNALED";
            case ApplicationExitInfo.REASON_LOW_MEMORY: return "LOW_MEMORY";
            case ApplicationExitInfo.REASON_CRASH: return "CRASH";
            case ApplicationExitInfo.REASON_CRASH_NATIVE: return "CRASH_NATIVE";
            case ApplicationExitInfo.REASON_ANR: return "ANR";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE: return "INITIALIZATION_FAILURE";
            case ApplicationExitInfo.REASON_PERMISSION_CHANGE: return "PERMISSION_CHANGE";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE: return "EXCESSIVE_RESOURCE_USAGE";
            case ApplicationExitInfo.REASON_USER_REQUESTED: return "USER_REQUESTED";
            case ApplicationExitInfo.REASON_USER_STOPPED: return "USER_STOPPED";
            case ApplicationExitInfo.REASON_DEPENDENCY_DIED: return "DEPENDENCY_DIED";
            case ApplicationExitInfo.REASON_OTHER: return "OTHER";
            case ApplicationExitInfo.REASON_FREEZER: return "FREEZER";
            case ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE: return "PACKAGE_STATE_CHANGE";
            case ApplicationExitInfo.REASON_PACKAGE_UPDATED: return "PACKAGE_UPDATED";
            default: return "UNKNOWN(" + r + ")";
        }
    }

    // ------------------------------------------------------------------ summary
    private String summary(long now) {
        StringBuilder sb = new StringBuilder(4096);
        SimpleDateFormat utc = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));
        int off = TimeZone.getDefault().getOffset(now) / 60_000;
        sb.append("Lumen OS problem report (format 2)\n");
        line(sb, "kind", kind.wire);
        line(sb, "created", utc.format(new Date(now)) + String.format(Locale.US, " (local time is UTC%s%02d:%02d)",
                off < 0 ? "-" : "+", Math.abs(off) / 60, Math.abs(off) % 60));
        for (Incident i : incidents) {
            line(sb, "incident", i.kind.wire + " " + i.process + " (" + i.tag + ", " + utc.format(new Date(i.time)) + ")");
        }
        line(sb, "install id", ReportPrefs.installId(c));
        line(sb, "model", prop("ro.boot.xgimi.modelname") + " (device " + prop("ro.product.device") + ", lumen device "
                + prop("ro.lumen.device") + ")");
        line(sb, "lumen", prop("ro.lumen.version") + " (version code " + prop("ro.lumen.version_code") + ")");
        line(sb, "build", Build.DISPLAY + " / " + Build.ID + " / " + Build.VERSION.INCREMENTAL + " / " + Build.TYPE
                + " / " + Build.TAGS);
        line(sb, "fingerprint", Build.FINGERPRINT);
        line(sb, "android", Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + "), security patch "
                + Build.VERSION.SECURITY_PATCH);
        line(sb, "slot", prop("ro.boot.slot_suffix"));
        line(sb, "system", "1".equals(prop("ro.gsid.image_running")) ? "trial run (the stock system stays)" : "installed");
        line(sb, "uptime", duration(SystemClock.elapsedRealtime()) + " (awake " + duration(SystemClock.uptimeMillis()) + ")");
        line(sb, "boot reason", "sys.boot.reason=" + prop("sys.boot.reason") + " ro.boot.bootreason="
                + prop("ro.boot.bootreason") + " persist.sys.boot.reason=" + prop("persist.sys.boot.reason"));
        line(sb, "lumen apps", lumenApps());
        line(sb, "language", Locale.getDefault().toLanguageTag());
        memory(sb);
        displays(sb);
        try {
            PowerManager pm = c.getSystemService(PowerManager.class);
            line(sb, "thermal status", pm == null ? "?" : String.valueOf(pm.getCurrentThermalStatus()));
        } catch (Throwable t) {
            line(sb, "thermal status", "?");
        }
        line(sb, "network", network());
        line(sb, "bluetooth", bluetoothLine);
        sb.append("\nSections:\n").append(notes);
        line(sb, "\npersonal data replacements", String.valueOf(scrubber.replacedCount()));
        return scrubber.scrubText(sb.toString());
    }

    private static void line(StringBuilder sb, String k, String v) {
        sb.append(k).append(": ").append(v == null || v.isEmpty() ? "-" : v).append('\n');
    }

    private String lumenApps() {
        StringBuilder sb = new StringBuilder();
        try {
            List<PackageInfo> l = c.getPackageManager().getInstalledPackages(
                    PackageManager.PackageInfoFlags.of(PackageManager.MATCH_SYSTEM_ONLY));
            Collections.sort(l, (a, b) -> a.packageName.compareTo(b.packageName));
            for (PackageInfo pi : l) {
                if (!pi.packageName.startsWith("org.lumen.")) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append(pi.packageName.substring("org.lumen.".length())).append(' ').append(pi.versionName)
                        .append(" (").append(pi.getLongVersionCode()).append(')');
            }
        } catch (Throwable t) {
            return "? (" + t.getClass().getSimpleName() + ")";
        }
        return sb.toString();
    }

    private void memory(StringBuilder sb) {
        try {
            ActivityManager am = c.getSystemService(ActivityManager.class);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            line(sb, "memory", "total " + mb(mi.totalMem) + ", available " + mb(mi.availMem) + ", low "
                    + mi.lowMemory + " (threshold " + mb(mi.threshold) + ")");
        } catch (Throwable t) {
            line(sb, "memory", "?");
        }
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            line(sb, "storage /data", mb(fs.getAvailableBytes()) + " free of " + mb(fs.getTotalBytes()));
        } catch (Throwable t) {
            line(sb, "storage /data", "?");
        }
    }

    private void displays(StringBuilder sb) {
        try {
            DisplayManager dm = c.getSystemService(DisplayManager.class);
            for (Display d : dm.getDisplays()) {
                Display.Mode m = d.getMode();
                StringBuilder v = new StringBuilder();
                v.append('"').append(d.getName()).append("\" mode ").append(mode(m));
                StringBuilder modes = new StringBuilder();
                for (Display.Mode x : d.getSupportedModes()) {
                    if (modes.length() > 0) modes.append(", ");
                    modes.append(mode(x));
                }
                v.append(", supported ").append(modes);
                v.append(", HDR ").append(Arrays.toString(m.getSupportedHdrTypes()));
                line(sb, "display " + d.getDisplayId(), v.toString());
            }
            DisplayMetrics dmx = c.getResources().getDisplayMetrics();
            line(sb, "app window", dmx.widthPixels + "x" + dmx.heightPixels + " @ " + dmx.densityDpi + " dpi");
        } catch (Throwable t) {
            line(sb, "display", "? (" + t.getClass().getSimpleName() + ")");
        }
    }

    private static String mode(Display.Mode m) {
        return m.getPhysicalWidth() + "x" + m.getPhysicalHeight() + "@" + String.format(Locale.US, "%.2f", m.getRefreshRate());
    }

    /** Transport type only: no network name, no address. */
    private String network() {
        try {
            ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
            Network n = cm == null ? null : cm.getActiveNetwork();
            NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
            if (nc == null) return "none";
            String t = nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "ethernet"
                    : nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "wifi"
                    : nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ? "vpn" : "other";
            return t + (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? ", validated" : ", not validated")
                    + (nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ? ", vpn" : "");
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String mb(long bytes) {
        return (bytes >> 20) + " MB";
    }

    private static String duration(long ms) {
        long m = ms / 60_000;
        return (m / 60) + " h " + String.format(Locale.US, "%02d", m % 60) + " min";
    }

    private static String prop(String k) {
        try {
            return ReportConfig.prop(k);
        } catch (Throwable t) {
            return "";
        }
    }
}
