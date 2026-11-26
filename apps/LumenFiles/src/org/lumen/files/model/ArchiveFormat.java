// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

import java.util.Locale;

/** Archives Files unpacks itself (java.util.zip and its own tar reader, no extra library). */
public enum ArchiveFormat {
    ZIP, TAR, TAR_GZ;

    /** The format of a file name, null when Files cannot unpack it (7z, rar ...). */
    public static ArchiveFormat of(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".zip")) return ZIP;
        if (n.endsWith(".tar")) return TAR;
        if (n.endsWith(".tgz") || n.endsWith(".tar.gz")) return TAR_GZ;
        return null;
    }
}
