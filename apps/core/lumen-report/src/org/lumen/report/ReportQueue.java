// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.report;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reports waiting for the server, in the app's own files: {@code <id>.zip} plus {@code <id>.meta} ("kind attempts
 * nextAt created"). A new report pushes out the oldest automatic one (then the oldest one) beyond MAX_ENTRIES or
 * MAX_BYTES. A failed send waits longer every time (WAIT_MS); a report older than MAX_AGE_MS or tried MAX_ATTEMPTS
 * times is dropped. One instance per directory, used from one thread at a time. Plain Java.
 */
final class ReportQueue {
    static final int MAX_ENTRIES = 6;
    static final long MAX_BYTES = 8L << 20;
    static final long MAX_AGE_MS = 7L * 24 * 3600 * 1000;
    static final int MAX_ATTEMPTS = 12;
    static final long[] WAIT_MS = {60_000L, 5 * 60_000L, 15 * 60_000L, 3600_000L, 3 * 3600_000L, 6 * 3600_000L,
            12 * 3600_000L};

    static final class Entry {
        final String id;
        final ReportKind kind;
        final int attempts;
        final long nextAt;
        final long created;
        final File zip;

        Entry(String id, ReportKind kind, int attempts, long nextAt, long created, File zip) {
            this.id = id;
            this.kind = kind;
            this.attempts = attempts;
            this.nextAt = nextAt;
            this.created = created;
            this.zip = zip;
        }
    }

    private final File dir;

    ReportQueue(File dir) {
        this.dir = dir;
    }

    /** The wait after the n-th failed send (n >= 1). */
    static long waitMs(int attempts) {
        int i = Math.max(1, attempts) - 1;
        return WAIT_MS[Math.min(i, WAIT_MS.length - 1)];
    }

    /** Takes {@code zip} into the queue (moved, else copied), due at once; returns its entry. */
    Entry add(File zip, ReportKind kind, long now) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("no queue dir " + dir);
        String id = now + "-" + kind.wire;
        for (int n = 2; new File(dir, id + ".zip").exists(); n++) id = now + "-" + kind.wire + "-" + n;
        File dst = new File(dir, id + ".zip");
        try {
            Files.move(zip.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.copy(zip.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        Entry e = new Entry(id, kind, 0, now, now, dst);
        writeMeta(e);
        trim(id);
        return e;
    }

    /** Every entry, oldest first; damaged ones are removed. */
    List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        File[] fs = dir.listFiles();
        if (fs == null) return out;
        for (File f : fs) {
            String n = f.getName();
            if (!n.endsWith(".zip")) {
                if (n.endsWith(".meta") && !new File(dir, n.substring(0, n.length() - 5) + ".zip").isFile()) f.delete();
                continue;
            }
            Entry e = read(n.substring(0, n.length() - 4));
            if (e == null) remove(n.substring(0, n.length() - 4));
            else out.add(e);
        }
        Collections.sort(out, (a, b) -> a.created != b.created ? Long.compare(a.created, b.created) : a.id.compareTo(b.id));
        return out;
    }

    /** The entries to send now, oldest first; expired ones (age, attempts) are dropped on the way. */
    List<Entry> due(long now) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries()) {
            if (now - e.created > MAX_AGE_MS || e.attempts >= MAX_ATTEMPTS) {
                remove(e.id);
                continue;
            }
            if (e.nextAt <= now) out.add(e);
        }
        return out;
    }

    /** Milliseconds until the next entry is due (0 = now), or -1 when the queue is empty. */
    long nextDueIn(long now) {
        long best = -1;
        for (Entry e : entries()) {
            long in = Math.max(0, e.nextAt - now);
            if (best < 0 || in < best) best = in;
        }
        return best;
    }

    /** A send failed for a reason that may pass (network, server, too many reports): try again later. */
    void failed(Entry e, long now) throws IOException {
        int n = e.attempts + 1;
        writeMeta(new Entry(e.id, e.kind, n, now + waitMs(n), e.created, e.zip));
    }

    /** Sent, or refused for good by the server. */
    void remove(String id) {
        new File(dir, id + ".zip").delete();
        new File(dir, id + ".meta").delete();
    }

    int size() {
        return entries().size();
    }

    // ------------------------------------------------------------------ files
    private void trim(String keep) {
        List<Entry> all = entries();
        long bytes = 0;
        for (Entry e : all) bytes += e.zip.length();
        while (all.size() > MAX_ENTRIES || (bytes > MAX_BYTES && all.size() > 1)) {
            Entry drop = null;
            for (Entry e : all) if (e.kind.automatic() && !e.id.equals(keep)) { drop = e; break; }
            if (drop == null) for (Entry e : all) if (!e.id.equals(keep)) { drop = e; break; }
            if (drop == null) break;
            bytes -= drop.zip.length();
            all.remove(drop);
            remove(drop.id);
        }
    }

    private void writeMeta(Entry e) throws IOException {
        File tmp = new File(dir, e.id + ".meta.tmp");
        try (OutputStream o = new FileOutputStream(tmp)) {
            o.write((e.kind.wire + " " + e.attempts + " " + e.nextAt + " " + e.created + "\n").getBytes(StandardCharsets.UTF_8));
        }
        File meta = new File(dir, e.id + ".meta");
        if (!tmp.renameTo(meta)) throw new IOException("cannot write " + meta);
    }

    private Entry read(String id) {
        try {
            String[] p = new String(Files.readAllBytes(new File(dir, id + ".meta").toPath()), StandardCharsets.UTF_8)
                    .trim().split(" ");
            ReportKind k = p.length == 4 ? ReportKind.of(p[0]) : null;
            if (k == null) return null;
            return new Entry(id, k, Integer.parseInt(p[1]), Long.parseLong(p[2]), Long.parseLong(p[3]), new File(dir, id + ".zip"));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
