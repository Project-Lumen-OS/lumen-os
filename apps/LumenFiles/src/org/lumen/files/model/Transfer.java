// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Copy or move rows into a folder (worker thread). A move on the same drive is a rename; anything else copies
 * byte by byte with progress, checks the free space before the first byte, gives clashing names a free name
 * ("name (2).ext"), and removes what it wrote when it is cancelled or fails. A move removes each source only
 * after its whole copy landed.
 */
public final class Transfer {
    /** Progress: bytes done of all, and the row being copied. */
    public interface Listener {
        void progress(long done, long total, String name);
    }

    static final int BUF = 1 << 16;
    private final Fs src, dst;
    private final TransferOp op;
    private final List<Entry> items;
    private final String into;
    private final BooleanSupplier stop;
    private final Listener listener;
    private long done, total;
    /** The paths this run created (top-level rows). */
    public final List<String> made = new ArrayList<>();

    public Transfer(Fs src, Fs dst, TransferOp op, List<Entry> items, String into, BooleanSupplier stop, Listener l) {
        this.src = src;
        this.dst = dst;
        this.op = op;
        this.items = new ArrayList<>(items);
        this.into = into;
        this.stop = stop;
        this.listener = l;
    }

    /** True when {@code dir} is {@code path} or lies inside it. */
    public static boolean inside(String path, String dir) {
        return dir.equals(path) || dir.startsWith(path.endsWith("/") ? path : path + "/");
    }

    public Outcome run() {
        if (!dst.writable() || (op == TransferOp.MOVE && !src.writable())) return Outcome.READ_ONLY;
        for (Entry e : items) if (e.dir() && src.same(dst) && inside(e.path, into)) return Outcome.INTO_ITSELF;
        if (op == TransferOp.MOVE && src.same(dst)) return renames();
        try {
            for (Entry e : items) total += bytes(src, e);
        } catch (IOException x) {
            return Outcome.FAILED;
        }
        long free = dst.free(into);
        if (free >= 0 && free < total) return Outcome.NO_SPACE;
        for (Entry e : items) {
            if (stop.getAsBoolean()) return Outcome.CANCELLED;
            String target = dst.child(into, Names.free(e.name, n -> exists(dst, dst.child(into, n))));
            try {
                copy(e, target);
            } catch (Cancelled c) {
                wipe(dst, target);
                return Outcome.CANCELLED;
            } catch (IOException | RuntimeException x) {
                wipe(dst, target);
                return Outcome.FAILED;
            }
            made.add(target);
            if (op == TransferOp.MOVE) {
                try {
                    removeAll(src, e.path);
                } catch (IOException x) {
                    return Outcome.FAILED;
                }
            }
        }
        return Outcome.DONE;
    }

    private Outcome renames() {
        for (Entry e : items) {
            if (stop.getAsBoolean()) return Outcome.CANCELLED;
            if (into.equals(src.parent(e.path))) continue;
            String target = dst.child(into, Names.free(e.name, n -> exists(dst, dst.child(into, n))));
            if (!src.rename(e.path, target)) return Outcome.FAILED;
            made.add(target);
        }
        return Outcome.DONE;
    }

    static boolean exists(Fs fs, String path) {
        try {
            return fs.stat(path) != null;
        } catch (IOException e) {
            return true;
        }
    }

    /** Bytes of a file or a whole folder. */
    public static long bytes(Fs fs, Entry e) throws IOException {
        if (!e.dir()) return Math.max(0, e.size);
        long n = 0;
        for (Entry c : fs.list(e.path)) n += bytes(fs, c);
        return n;
    }

    private static final class Cancelled extends IOException {
        Cancelled() {
            super("cancelled");
        }
    }

    private void copy(Entry e, String target) throws IOException {
        if (stop.getAsBoolean()) throw new Cancelled();
        if (e.dir()) {
            dst.mkdir(target);
            for (Entry c : src.list(e.path)) copy(c, dst.child(target, c.name));
            return;
        }
        byte[] buf = new byte[BUF];
        try (InputStream in = src.read(e.path); OutputStream out = dst.create(target)) {
            for (int n; (n = in.read(buf)) > 0; ) {
                out.write(buf, 0, n);
                done += n;
                if (listener != null) listener.progress(done, total, e.name);
                if (stop.getAsBoolean()) throw new Cancelled();
            }
        }
    }

    /** Removes a file or a whole folder; a failure leaves the rest as it was found. */
    public static void removeAll(Fs fs, String path) throws IOException {
        Entry e = fs.stat(path);
        if (e == null) return;
        if (e.dir()) for (Entry c : fs.list(path)) removeAll(fs, c.path);
        fs.remove(path);
    }

    static void wipe(Fs fs, String path) {
        try {
            removeAll(fs, path);
        } catch (IOException ignored) {
        }
    }

    /** Deletes rows (worker thread): stops between rows when asked. */
    public static Outcome delete(Fs fs, List<Entry> rows, BooleanSupplier stop) {
        if (!fs.writable()) return Outcome.READ_ONLY;
        for (Entry e : rows) {
            if (stop.getAsBoolean()) return Outcome.CANCELLED;
            try {
                removeAll(fs, e.path);
            } catch (IOException x) {
                return Outcome.FAILED;
            }
        }
        return Outcome.DONE;
    }
}
