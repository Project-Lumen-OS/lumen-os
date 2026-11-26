// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.text.format;

import android.content.Context;

/** Parity probe stand-in: plain bytes. */
public final class Formatter {
    private Formatter() {}
    public static String formatShortFileSize(Context c, long n) { return n + " B"; }
    public static String formatFileSize(Context c, long n) { return n + " B"; }
}
