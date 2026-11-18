// SPDX-License-Identifier: Apache-2.0
package org.lumen.fwcopy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Where a copy keeps its state (plan id, state, the parts already verified), so a copy cut by a power loss is
 * recognised and resumed. The copy never trusts it for a decision: every run plans again from the bytes.
 */
public interface Journal {
    /** the last state written, null when there is none */
    String read() throws IOException;

    void write(String state) throws IOException;

    /** A file, replaced as a whole (temporary file, sync, rename). */
    final class OnFile implements Journal {
        private final File f;

        public OnFile(File f) { this.f = f; }

        @Override public String read() throws IOException {
            return f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : null;
        }

        @Override public void write(String state) throws IOException {
            File tmp = new File(f.getPath() + ".new");
            try (FileOutputStream o = new FileOutputStream(tmp)) {
                o.write(state.getBytes(StandardCharsets.UTF_8));
                o.getFD().sync();
            }
            if (!tmp.renameTo(f)) throw new IOException("cannot replace " + f);
        }
    }
}
