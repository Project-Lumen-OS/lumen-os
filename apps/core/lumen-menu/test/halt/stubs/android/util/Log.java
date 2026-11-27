// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.util;

/** Host stub: the log lines Halt writes. */
public final class Log {
    public static final java.util.List<String> LINES = new java.util.ArrayList<>();

    public static int i(String tag, String msg) { LINES.add(tag + ": " + msg); return 0; }
}
