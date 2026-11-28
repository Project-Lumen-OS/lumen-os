// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.report;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes the report zip under a size limit. Text parts go in whole; file parts (logs) keep their LAST
 * {@code cap / shrink} bytes (the newest lines), cut at a line start. {@link #writeWithin} doubles
 * {@code shrink} until the zip fits. Raw parts (the kept starts, already gzipped and chosen by BootLogs) go in whole
 * and uncompressed, outside the limit. Plain Java.
 */
final class ReportZip {
    private ReportZip() {}

    static final class Part {
        final String name;
        final byte[] bytes;
        final File file;
        final long cap;
        final boolean raw;

        private Part(String name, byte[] bytes, File file, long cap, boolean raw) {
            this.name = name;
            this.bytes = bytes;
            this.file = file;
            this.cap = cap;
            this.raw = raw;
        }

        static Part text(String name, String text) {
            return new Part(name, text.getBytes(StandardCharsets.UTF_8), null, 0, false);
        }

        static Part file(String name, File f, long cap) {
            return new Part(name, null, f, cap, false);
        }

        /** The whole file as it is (a gz file of BootLogs), never cut and not counted against the limit. */
        static Part raw(String name, File f) {
            return new Part(name, null, f, 0, true);
        }
    }

    /** The bytes of the raw parts (they come on top of the limit). */
    static long rawBytes(List<Part> parts) {
        long n = 0;
        for (Part p : parts) if (p.raw && p.file != null && p.file.isFile()) n += p.file.length();
        return n;
    }

    /** @return the shrink factor used (1 = nothing cut beyond the caps), or -1 when even 1/16 is too big */
    static int writeWithin(File out, List<Part> parts, long maxBytes) throws IOException {
        long raw = rawBytes(parts);
        for (int shrink = 1; shrink <= 16; shrink *= 2) {
            if (write(out, parts, shrink) <= maxBytes + raw) return shrink;
        }
        return -1;
    }

    static long write(File out, List<Part> parts, int shrink) throws IOException {
        byte[] buf = new byte[32 * 1024];
        try (ZipOutputStream z = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out), 64 * 1024))) {
            for (Part p : parts) {
                if (p.bytes == null && (p.file == null || !p.file.isFile())) continue;
                z.setLevel(p.raw ? Deflater.NO_COMPRESSION : Deflater.DEFAULT_COMPRESSION);
                z.putNextEntry(new ZipEntry(p.name));
                if (p.bytes != null) {
                    z.write(p.bytes);
                } else if (p.raw) {
                    copyTail(p.file, Long.MAX_VALUE, z, buf);
                } else {
                    copyTail(p.file, Math.max(4096, p.cap / shrink), z, buf);
                }
                z.closeEntry();
            }
        }
        return out.length();
    }

    private static void copyTail(File f, long cap, ZipOutputStream z, byte[] buf) throws IOException {
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            long len = r.length();
            long start = Math.max(0, len - cap);
            if (start > 0) {
                r.seek(start);
                int c;
                while ((c = r.read()) >= 0 && c != '\n') start++;
                start++;                                     // the byte after '\n'
                String note = "[... " + start + " earlier bytes cut to keep the report under the size limit ...]\n";
                z.write(note.getBytes(StandardCharsets.UTF_8));
            }
            r.seek(Math.min(start, len));
            int n;
            while ((n = r.read(buf)) > 0) z.write(buf, 0, n);
        }
    }
}
