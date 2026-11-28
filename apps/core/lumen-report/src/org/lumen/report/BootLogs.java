// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.report;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The persistent log of the last starts (image/lumen_report.sh keep) in a report. The log helper hands the app hard
 * links of the kept chunks in {@link #DIR} and their list in persist.txt ("&lt;start&gt;.&lt;file&gt; &lt;bytes&gt;"):
 * per start (bNNNNNN, the newest is this start) log.N (logcat main, system, events, crash, radio), imp.N (its
 * important lines), kernel.N (dmesg), snap.N (dumpsys snapshots), pstore files of its hard stop and info.txt.
 * {@link Caps} says how much of each stream a report takes per start age (0 = this start, 1 = the one before, ...):
 * the whole stream when it fits, else its first chunks (the boot) and its last ones (the moments before the end), with
 * one line naming what was left out between them. Every line passes the report's filter (LogcatFilter for the logcat
 * streams: no lines of apps the user installed) and the Scrubber again, and goes gzipped into the report as
 * boots/start-K/&lt;stream&gt;.txt.gz (stored as is in the zip). Plain Java, host-tested in ReportTest.
 */
final class BootLogs {
    static final String DIR = "/data/misc/lumen_logs/export";
    static final String LIST = "persist.txt";
    /** The chunk sizes of lumen_report.sh (CHUNK_LOG, CHUNK_SMALL): a finished chunk holds about this much text. */
    static final long CHUNK_LOG = 4L << 20, CHUNK_SMALL = 1L << 20;
    static final String[] STREAMS = {"log", "imp", "kernel", "snap"};

    /** Raw bytes per stream and start age (index 0, 1, then 2 for every older start): head + tail. */
    static final class Caps {
        final int starts;
        final long[][] head = new long[4][3], tail = new long[4][3];

        Caps(int starts) {
            this.starts = starts;
        }

        Caps set(String stream, int age, long head, long tail) {
            int s = stream(stream);
            this.head[s][age] = head;
            this.tail[s][age] = tail;
            return this;
        }

        long head(int stream, int age) {
            return head[stream][Math.min(age, 2)];
        }

        long tail(int stream, int age) {
            return tail[stream][Math.min(age, 2)];
        }

        /**
         * A manual report: this start (its boot and its last hours: 8 + 40 MiB of log) and the one before (8 + 24 MiB:
         * its boot and the moments before it ended), the important lines, kernel log and snapshots of every start. At
         * most about 134 MiB of text: the app filters and scrubs every line again (Scrubber, about 10 MB/s on a desktop,
         * far less on a projector), zipped about a tenth of it.
         */
        static Caps manual() {
            Caps c = new Caps(6);
            c.set("log", 0, 8L << 20, 40L << 20).set("log", 1, 8L << 20, 24L << 20);
            for (int age = 0; age < 2; age++) c.set("imp", age, 2L << 20, 6L << 20).set("kernel", age, 1L << 20, 3L << 20);
            c.set("snap", 0, 0, 4L << 20).set("snap", 1, 0, 2L << 20);
            return c.set("imp", 2, 1L << 20, 3L << 20).set("kernel", 2, 0, 1L << 20).set("snap", 2, 0, 1L << 20);
        }

        /** An automatic report of an unexpected stop: the end of the start that stopped. */
        static Caps stop() {
            return new Caps(2).set("log", 1, 2L << 20, 14L << 20).set("imp", 1, 1L << 20, 3L << 20)
                    .set("kernel", 1, 0, 2L << 20).set("snap", 1, 0, 1L << 20).set("imp", 0, 1L << 20, 0)
                    .set("kernel", 0, 1L << 20, 0);
        }

        /** An automatic crash or ANR report: the minutes before it in this start. */
        static Caps crash() {
            return new Caps(1).set("log", 0, 0, 4L << 20).set("imp", 0, 0, 2L << 20).set("kernel", 0, 0, 1L << 20);
        }
    }

    static int stream(String name) {
        for (int i = 0; i < STREAMS.length; i++) if (STREAMS[i].equals(name)) return i;
        throw new IllegalArgumentException(name);
    }

    /** One kept file: its export name, start, stream (log, imp, kernel, snap, pstore, info, budget) and index. */
    static final class Item {
        final String name, start, stream;
        final int index;
        final long bytes;
        final boolean gz;

        Item(String name, String start, String stream, int index, long bytes) {
            this.name = name;
            this.start = start;
            this.stream = stream;
            this.index = index;
            this.bytes = bytes;
            this.gz = name.endsWith(".gz");
        }

        /** The text this chunk holds: a finished (compressed) chunk is about a chunk, an open one its size. */
        long raw() {
            long chunk = "log".equals(stream) ? CHUNK_LOG : CHUNK_SMALL;
            return gz ? chunk : bytes;
        }
    }

    /** One start's files, chunks in order. */
    static final class Start {
        final String id;
        final List<List<Item>> streams = new ArrayList<>();
        final List<Item> pstore = new ArrayList<>();
        Item info;

        Start(String id) {
            this.id = id;
            for (int i = 0; i < STREAMS.length; i++) streams.add(new ArrayList<>());
        }
    }

    /** The starts of persist.txt, newest first; lines that do not parse are skipped. budget gets budget.txt. */
    static List<Start> parse(List<String> lines, Item[] budget) {
        TreeMap<String, Start> by = new TreeMap<>(Collections.reverseOrder());
        for (String l : lines) {
            String[] f = l.trim().split(" ");
            if (f.length != 2 || f[0].contains("/") || f[0].startsWith(".")) continue;
            long bytes;
            try {
                bytes = Long.parseLong(f[1]);
            } catch (NumberFormatException e) {
                continue;
            }
            String n = f[0];
            if (n.equals("budget.txt")) {
                if (budget != null && budget.length > 0) budget[0] = new Item(n, "", "budget", 0, bytes);
                continue;
            }
            if (!n.matches("b\\d{6}\\.[A-Za-z0-9._-]+")) continue;
            String start = n.substring(0, 7), rest = n.substring(8);
            Start s = by.computeIfAbsent(start, Start::new);
            if (rest.startsWith("pstore.")) {
                s.pstore.add(new Item(n, start, "pstore", 0, bytes));
            } else if (rest.equals("info.txt")) {
                s.info = new Item(n, start, "info", 0, bytes);
            } else {
                String[] p = rest.split("\\.");
                if (p.length < 3 || !p[1].matches("\\d{6}")) continue;
                int st;
                try {
                    st = stream(p[0]);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                s.streams.get(st).add(new Item(n, start, p[0], Integer.parseInt(p[1]), bytes));
            }
        }
        List<Start> out = new ArrayList<>(by.values());
        for (Start s : out) for (List<Item> c : s.streams) c.sort((a, b) -> Integer.compare(a.index, b.index));
        return out;
    }

    /** The chunks a report takes of one stream: the first ones up to head, the last ones up to tail. */
    static final class Pick {
        final List<Item> head = new ArrayList<>(), tail = new ArrayList<>();
        int skipped;
        long skippedRaw;
    }

    static Pick pick(List<Item> chunks, long head, long tail) {
        Pick p = new Pick();
        int n = chunks.size(), i = 0, j = n;
        long h = 0, t = 0;
        while (i < n && head > 0 && (p.head.isEmpty() || h + chunks.get(i).raw() <= head)) {
            h += chunks.get(i).raw();
            p.head.add(chunks.get(i++));
        }
        while (j > i && tail > 0 && (p.tail.isEmpty() || t + chunks.get(j - 1).raw() <= tail)) {
            t += chunks.get(j - 1).raw();
            p.tail.add(0, chunks.get(--j));
        }
        for (int k = i; k < j; k++) {
            p.skipped++;
            p.skippedRaw += chunks.get(k).raw();
        }
        return p;
    }

    interface LineFilter {
        boolean keep(String line);
    }

    interface Scrub {
        String scrub(String line);
    }

    interface Cancel {
        boolean canceled();
    }

    /** A file for the report zip (taken as is) under its zip name. */
    static final class Out {
        final String zipName;
        final File file;

        Out(String zipName, File file) {
            this.zipName = zipName;
            this.file = file;
        }
    }

    /**
     * The chosen chunks of every start, filtered and scrubbed, as gz files in {@code outDir}. logFilters gives a
     * fresh filter for each logcat stream (null: no filter); notes gets one line per stream.
     */
    static List<Out> write(File exportDir, List<Start> starts, Caps caps, Supplier<LineFilter> logFilters, Scrub scrub,
                           File outDir, Cancel cancel, List<String> notes) throws IOException {
        List<Out> out = new ArrayList<>();
        for (int age = 0; age < Math.min(caps.starts, starts.size()); age++) {
            Start s = starts.get(age);
            String zipDir = "boots/start-" + age + "/";
            boolean any = false;
            for (int st = 0; st < STREAMS.length; st++) {
                long head = caps.head(st, age), tail = caps.tail(st, age);
                List<Item> chunks = s.streams.get(st);
                if (head + tail <= 0 || chunks.isEmpty()) continue;
                Pick p = pick(chunks, head, tail);
                File f = new File(outDir, "start" + age + "_" + STREAMS[st] + ".txt.gz");
                LineFilter lf = st <= 1 && logFilters != null ? logFilters.get() : null;
                long[] kept = new long[3];
                try (Writer w = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(f),
                        64 * 1024), StandardCharsets.UTF_8), 64 * 1024)) {
                    long budget = head + tail + (head + tail) / 4 + (64 << 10);
                    for (Item c : p.head) copy(new File(exportDir, c.name), w, lf, scrub, cancel, kept, budget);
                    if (p.skipped > 0) {
                        w.write(String.format(Locale.US, "[... %d chunks of this start (about %d MB) are left out here"
                                + " to keep the report small; imp.txt.gz has their important lines ...]\n",
                                p.skipped, p.skippedRaw >> 20));
                    }
                    for (Item c : p.tail) copy(new File(exportDir, c.name), w, lf, scrub, cancel, kept, budget);
                }
                out.add(new Out(zipDir + STREAMS[st] + ".txt.gz", f));
                any = true;
                notes.add(String.format(Locale.US, "%s%s.txt.gz: %s, chunks %d first + %d last of %d, %d lines, %d left out",
                        zipDir, STREAMS[st], s.id, p.head.size(), p.tail.size(), chunks.size(), kept[0], kept[1]));
            }
            if (!any && age > 0) continue;
            List<Item> plain = new ArrayList<>(s.pstore);
            if (s.info != null) plain.add(0, s.info);
            for (Item i : plain) {
                String name = i.stream.equals("info") ? "info.txt" : "pstore/" + i.name.substring(15);
                File f = new File(outDir, "start" + age + "_" + name.replace('/', '_'));
                try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
                    copy(new File(exportDir, i.name), w, null, scrub, cancel, new long[3], 4L << 20);
                }
                out.add(new Out(zipDir + name, f));
            }
        }
        return out;
    }

    /**
     * One chunk's lines through the filter and the scrub into w: kept[0] lines kept, kept[1] left out, kept[2] the
     * characters written to w so far; past budget characters the stream stops (a safety cap over the chunk choice).
     */
    private static void copy(File in, Writer w, LineFilter lf, Scrub scrub, Cancel cancel, long[] kept, long budget)
            throws IOException {
        InputStream raw = null;
        try {
            raw = new FileInputStream(in);
            InputStream is = in.getName().endsWith(".gz") ? new GZIPInputStream(raw, 64 * 1024) : raw;
            BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8), 64 * 1024);
            String line;
            long n = 0;
            while ((line = r.readLine()) != null) {
                if ((++n & 1023) == 0 && cancel != null && cancel.canceled()) throw new CanceledException();
                if (lf != null && !lf.keep(line)) {
                    kept[1]++;
                    continue;
                }
                if (kept[2] > budget) break;
                String s = scrub == null ? line : scrub.scrub(line);
                w.write(s);
                w.write('\n');
                kept[2] += s.length() + 1;
                kept[0]++;
            }
        } catch (java.io.EOFException e) {
            w.write("[... this chunk ends early: it was cut by a power loss ...]\n");
        } finally {
            if (raw != null) raw.close();
        }
    }
}
