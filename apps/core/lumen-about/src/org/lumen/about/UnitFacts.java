// SPDX-License-Identifier: Apache-2.0
package org.lumen.about;

import android.os.SystemProperties;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** XgimiFacts of the running projector: its properties and its own config file, read only. */
final class UnitFacts {
    private static final int MAX_INI = 64 * 1024;

    private UnitFacts() {}

    static XgimiFacts read() {
        return XgimiFacts.read(p -> SystemProperties.get(p, ""), UnitFacts::text);
    }

    /** the text of a small file, null when it cannot be read */
    static String text(String path) {
        File f = new File(path);
        if (!f.isFile() || f.length() > MAX_INI) return null;
        try (InputStream in = new FileInputStream(f)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | SecurityException e) {
            return null;
        }
    }
}
