// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.fwcopy;

import java.io.Closeable;
import java.io.IOException;

/** The partitions of a projector by name (boot_a, xgimiconfig_b): the real block devices, or files in the host tests. */
public interface Blocks {
    /** the partition exists on this projector */
    boolean exists(String name);

    Handle open(String name, boolean write) throws IOException;

    /** One opened partition: positioned reads and writes, sync. */
    interface Handle extends Closeable {
        long size() throws IOException;

        void read(long off, byte[] b, int o, int n) throws IOException;

        void write(long off, byte[] b, int o, int n) throws IOException;

        void sync() throws IOException;
    }
}
