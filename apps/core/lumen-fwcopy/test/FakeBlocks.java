// SPDX-License-Identifier: Apache-2.0
package org.lumen.fwcopy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Partitions in memory, with the faults a real projector can have: a read error, a write error, a power cut after a
 * number of written bytes, a source that changes on its n-th read, a write that lands corrupted. Records every open
 * for writing and every read, so a test can prove which partitions were touched and in what order.
 */
final class FakeBlocks implements Blocks {
    /** a power cut: the run stops in the middle of a write, the bytes written so far stay */
    static final class PowerCut extends RuntimeException {
        PowerCut() { super("power cut"); }
    }

    final Map<String, byte[]> parts = new LinkedHashMap<>();
    /** files of the running vendor (the first stage and its post-install step) */
    final Map<String, byte[]> files = new LinkedHashMap<>();
    /** a partition the kernel keeps read-only now (an eMMC boot area with force_ro 1): a write open fails */
    java.util.function.Predicate<String> locked;
    final List<String> events = new ArrayList<>();
    String readError, writeError, corrupt;
    /** a power cut once this many bytes were written (all partitions together); negative = never */
    long cutAfter = -1;
    long writtenBytes;
    /** called on every read of a partition with its offset (before the bytes are copied out) */
    final Map<String, BiConsumer<Long, byte[]>> onRead = new LinkedHashMap<>();
    final Map<String, Integer> reads = new LinkedHashMap<>();

    FakeBlocks put(String name, byte[] b) { parts.put(name, b); return this; }

    @Override public boolean exists(String name) { return parts.containsKey(name); }

    @Override public Handle open(String name, boolean write) throws IOException {
        byte[] b = parts.get(name);
        if (b == null) throw new IOException("no " + name);
        if (write && locked != null && locked.test(name)) throw new IOException(name + ": Operation not permitted (force_ro)");
        if (write) events.add("open-w " + name);
        return new Handle() {
            @Override public long size() { return b.length; }

            @Override public void read(long off, byte[] dst, int o, int n) throws IOException {
                if (name.equals(readError)) throw new IOException("read error on " + name);
                if (off == 0) reads.merge(name, 1, Integer::sum);
                BiConsumer<Long, byte[]> c = onRead.get(name);
                if (c != null) c.accept(off, b);
                if (off < 0 || off + n > b.length) throw new IOException("read outside " + name);
                System.arraycopy(b, (int) off, dst, o, n);
            }

            @Override public void write(long off, byte[] src, int o, int n) throws IOException {
                if (!write) throw new IOException(name + " opened read-only");
                if (name.equals(writeError)) throw new IOException("write error on " + name);
                if (off < 0 || off + n > b.length) throw new IOException("write outside " + name);
                events.add("write " + name + " " + off + "+" + n);
                for (int i = 0; i < n; i++) {
                    if (cutAfter >= 0 && writtenBytes >= cutAfter) throw new PowerCut();
                    b[(int) off + i] = src[o + i];
                    writtenBytes++;
                }
                if (name.equals(corrupt) && off > 0) b[(int) off] ^= 1;
            }

            @Override public void sync() { events.add("sync " + name); }

            @Override public void close() {}
        };
    }
}
