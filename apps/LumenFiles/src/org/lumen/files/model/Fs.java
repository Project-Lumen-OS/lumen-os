// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * A store Files lists and copies through: a local drive ({@link LocalFs}), an SMB share, a DLNA server. Paths are
 * the store's own strings ({@link #child}, {@link #parent}); read-only stores throw on every write.
 */
public interface Fs {
    /** Stable id of the store: "local:/storage/1234-ABCD", "smb:<server id>", "dlna:<udn>". */
    String id();

    boolean writable();

    List<Entry> list(String dir) throws IOException;

    /** The entry at a path, null when nothing is there. */
    Entry stat(String path) throws IOException;

    InputStream read(String path) throws IOException;

    /** A new file (its folder exists); the store refuses to replace a folder. */
    OutputStream create(String path) throws IOException;

    void mkdir(String path) throws IOException;

    /** One file or one empty folder. */
    void remove(String path) throws IOException;

    /** A rename or move inside this store; false when the store cannot do it. */
    boolean rename(String from, String to);

    /** Free bytes where {@code dir} lies, -1 when unknown. */
    long free(String dir);

    String child(String dir, String name);

    /** The folder of a path, null at the store's root. */
    String parent(String path);

    /** True when a move between this store and {@code other} is a rename. */
    boolean same(Fs other);
}
