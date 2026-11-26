// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.report;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 * Small state files written so that a power cut leaves the old or the new content, never a mix or nothing: a temp
 * file, fsync, rename, fsync of the folder. Plain Java.
 */
final class AliveFile {
    private static final int MAX_BYTES = 64 * 1024;

    private AliveFile() {}

    /** The content, or null when the file is missing, too big or unreadable. */
    static String read(File f) {
        try {
            if (!f.isFile() || f.length() > MAX_BYTES) return null;
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Reads and removes the file: what it held is handled once. */
    static String take(File f) {
        String s = read(f);
        if (f.exists() && !f.delete()) return null;          // never hand out what would come back next time
        return s;
    }

    static boolean write(File f, String text) {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (IOException | RuntimeException e) {
            tmp.delete();
            return false;
        }
        if (!tmp.renameTo(f)) {
            tmp.delete();
            return false;
        }
        try (FileChannel dir = FileChannel.open(f.getAbsoluteFile().getParentFile().toPath(), StandardOpenOption.READ)) {
            dir.force(true);                                    // the rename itself survives a power cut
        } catch (IOException | RuntimeException e) {
            // best effort: some file systems refuse a folder sync, the file content is synced already
        }
        return true;
    }
}
