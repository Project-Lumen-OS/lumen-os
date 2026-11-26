// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.source;

import java.util.regex.Pattern;

/**
 * The card's one line: the input's name, then the device's own name when it sent one ("HDMI 1 · PlayStation 5").
 * The names the framework gives a CEC device before its OSD name arrives ("Playback_1", "Recorder_2", "TV") are
 * not names. Pure.
 */
public final class OsdText {
    public static final String SEP = " \u00b7 ";
    private static final Pattern PLACEHOLDER =
            Pattern.compile("(?i)(tv|playback|recorder|tuner|audio ?system|audio|free ?use|reserved|unknown)([ _]?\\d+)?");

    private OsdText() {}

    /** {@code name} alone when {@code device} is empty, a placeholder or the same text. */
    public static String line(String name, String device) {
        String d = device == null ? "" : device.trim();
        if (d.isEmpty() || placeholder(d) || d.equalsIgnoreCase(name.trim())) return name;
        return name + SEP + d;
    }

    static boolean placeholder(String name) {
        return PLACEHOLDER.matcher(name.trim()).matches();
    }
}
