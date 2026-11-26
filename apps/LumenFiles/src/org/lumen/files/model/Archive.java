// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Unpacks zip, tar and tar.gz into a folder of a store (worker thread). Every name is checked first: one that
 * points out of the folder (absolute, "..", a drive letter) stops the whole unpack (UNSAFE); links are skipped.
 * Stops at {@code room} written bytes (NO_SPACE) so a small archive cannot fill the drive. The caller removes the
 * folder when the result is not DONE.
 */
public final class Archive {
    static final int BLOCK = 512;

    private Archive() {}

    /** The entry's path inside the target folder, null when it would leave it. */
    public static String safe(String name) {
        if (name == null) return null;
        String n = name.replace('\\', '/');
        if (n.startsWith("/") || n.matches("^[A-Za-z]:.*")) return null;
        StringBuilder b = new StringBuilder();
        for (String part : n.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..") || part.indexOf('\0') >= 0) return null;
            if (b.length() > 0) b.append('/');
            b.append(part);
        }
        return b.length() == 0 ? null : b.toString();
    }

    /** Bytes read so far of the archive file (progress). */
    static final class Counting extends FilterInputStream {
        long n;

        Counting(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int c = super.read();
            if (c >= 0) n++;
            return c;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int r = super.read(b, off, len);
            if (r > 0) n += r;
            return r;
        }
    }

    private static final class Stop extends IOException {
        final Outcome why;

        Stop(Outcome why) {
            super(why.name());
            this.why = why;
        }
    }

    /** One unpack run. */
    private static final class Run {
        final Fs fs;
        final String into;
        final BooleanSupplier stop;
        final Transfer.Listener l;
        final Counting in;
        final long size, room;
        long written;

        Run(Fs fs, String into, BooleanSupplier stop, Transfer.Listener l, Counting in, long size, long room) {
            this.fs = fs;
            this.into = into;
            this.stop = stop;
            this.l = l;
            this.in = in;
            this.size = size;
            this.room = room;
        }

        String dirs(String rel, boolean last) throws IOException {
            String cur = into;
            String[] parts = rel.split("/");
            for (int i = 0; i < parts.length - (last ? 0 : 1); i++) {
                cur = fs.child(cur, parts[i]);
                Entry e = fs.stat(cur);
                if (e == null) fs.mkdir(cur);
                else if (!e.dir()) throw new Stop(Outcome.UNSAFE);
            }
            return last ? cur : fs.child(cur, parts[parts.length - 1]);
        }

        void file(String rel, InputStream data, long len) throws IOException {
            String path = dirs(rel, false);
            Entry old = fs.stat(path);
            if (old != null && old.dir()) throw new Stop(Outcome.UNSAFE);
            if (old != null) fs.remove(path);
            byte[] buf = new byte[Transfer.BUF];
            try (OutputStream out = fs.create(path)) {
                long left = len;
                while (left != 0) {
                    int want = left < 0 ? buf.length : (int) Math.min(buf.length, left);
                    int n = data.read(buf, 0, want);
                    if (n <= 0) {
                        if (left > 0) throw new IOException("short archive");
                        break;
                    }
                    if (left > 0) left -= n;
                    written += n;
                    if (written > room) throw new Stop(Outcome.NO_SPACE);
                    out.write(buf, 0, n);
                    tick(rel);
                }
            }
        }

        void tick(String name) throws Stop {
            if (stop.getAsBoolean()) throw new Stop(Outcome.CANCELLED);
            if (l != null) l.progress(Math.min(in.n, size), size, name);
        }
    }

    public static Outcome extract(InputStream raw, ArchiveFormat f, Fs fs, String into, long size, long room,
                                  BooleanSupplier stop, Transfer.Listener l) {
        Counting in = new Counting(raw);
        Run r = new Run(fs, into, stop, l, in, Math.max(1, size), room);
        try {
            if (f == ArchiveFormat.ZIP) zip(r);
            else tar(r, f == ArchiveFormat.TAR_GZ ? new GZIPInputStream(in, Transfer.BUF) : in);
            return Outcome.DONE;
        } catch (Stop s) {
            return s.why;
        } catch (IOException | RuntimeException e) {
            return Outcome.FAILED;
        }
    }

    private static void zip(Run r) throws IOException {
        ZipInputStream z = new ZipInputStream(r.in, StandardCharsets.UTF_8);
        for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
            String rel = safe(e.getName());
            if (rel == null) throw new Stop(Outcome.UNSAFE);
            r.tick(rel);
            if (e.isDirectory()) r.dirs(rel, true);
            else r.file(rel, z, -1);
        }
    }

    private static void tar(Run r, InputStream in) throws IOException {
        byte[] h = new byte[BLOCK];
        String longName = null;
        while (true) {
            if (!full(in, h)) return;
            if (zero(h)) return;
            long len = number(h, 124, 12);
            char type = (char) h[156];
            String name = longName != null ? longName : name(h);
            longName = null;
            if (type == 'L' || type == 'x') {
                byte[] d = new byte[(int) Math.min(len, 1 << 20)];
                if (!full(in, d)) return;
                skip(in, len - d.length);
                pad(in, len);
                String s = new String(d, StandardCharsets.UTF_8);
                longName = type == 'L' ? s.replace("\0", "") : pax(s);
                continue;
            }
            String rel = safe(name);
            if (rel == null) throw new Stop(Outcome.UNSAFE);
            r.tick(rel);
            if (type == '5') {
                r.dirs(rel, true);
            } else if (type == '0' || type == 0 || type == '7') {
                r.file(rel, new Bounded(in, len), len);
                pad(in, len);
                continue;
            } else {
                skip(in, len);
            }
            pad(in, len);
        }
    }

    /** The "path" record of a pax header, null without one. */
    static String pax(String s) {
        for (String rec : s.split("\n")) {
            int sp = rec.indexOf(' ');
            if (sp > 0 && rec.startsWith("path=", sp + 1)) return rec.substring(sp + 6);
        }
        return null;
    }

    static String name(byte[] h) {
        String n = cstr(h, 0, 100);
        if ("ustar".equals(cstr(h, 257, 5))) {
            String p = cstr(h, 345, 155);
            if (!p.isEmpty()) n = p + "/" + n;
        }
        return n;
    }

    static String cstr(byte[] b, int off, int len) {
        int e = off;
        while (e < off + len && b[e] != 0) e++;
        return new String(b, off, e - off, StandardCharsets.UTF_8);
    }

    static long number(byte[] b, int off, int len) {
        if ((b[off] & 0x80) != 0) {
            long v = b[off] & 0x7f;
            for (int i = off + 1; i < off + len; i++) v = (v << 8) | (b[i] & 0xff);
            return v;
        }
        String s = cstr(b, off, len).trim();
        return s.isEmpty() ? 0 : Long.parseLong(s, 8);
    }

    static boolean zero(byte[] b) {
        for (byte x : b) if (x != 0) return false;
        return true;
    }

    static boolean full(InputStream in, byte[] b) throws IOException {
        int got = 0;
        while (got < b.length) {
            int n = in.read(b, got, b.length - got);
            if (n < 0) {
                if (got == 0) return false;
                throw new IOException("short archive");
            }
            got += n;
        }
        return true;
    }

    static void skip(InputStream in, long n) throws IOException {
        byte[] buf = new byte[Transfer.BUF];
        while (n > 0) {
            int r = in.read(buf, 0, (int) Math.min(buf.length, n));
            if (r < 0) throw new IOException("short archive");
            n -= r;
        }
    }

    static void pad(InputStream in, long len) throws IOException {
        long rest = (BLOCK - len % BLOCK) % BLOCK;
        skip(in, rest);
    }

    /** The data of one tar entry. */
    private static final class Bounded extends InputStream {
        private final InputStream in;
        private long left;

        Bounded(InputStream in, long len) {
            this.in = in;
            this.left = len;
        }

        @Override
        public int read() throws IOException {
            byte[] b = new byte[1];
            return read(b, 0, 1) < 0 ? -1 : b[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (left <= 0) return -1;
            int n = in.read(b, off, (int) Math.min(len, left));
            if (n > 0) left -= n;
            return n;
        }
    }
}
