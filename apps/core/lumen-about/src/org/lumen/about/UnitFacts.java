// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.about;

import android.os.SystemProperties;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/** XgimiFacts of the running projector: its properties and its own config file, read only. */
public final class UnitFacts {
    private static final int MAX_INI = 64 * 1024;

    private UnitFacts() {}

    /** without the HAL line (the Back to the XGIMI system screen needs no FPGA firmware) */
    public static XgimiFacts read() {
        return read(() -> null);
    }

    /** dlpImage: the light engine line of the projector HAL, null when it cannot be read */
    static XgimiFacts read(Supplier<String> dlpImage) {
        return XgimiFacts.read(p -> SystemProperties.get(p, ""), UnitFacts::text, "", dlpImage);
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
