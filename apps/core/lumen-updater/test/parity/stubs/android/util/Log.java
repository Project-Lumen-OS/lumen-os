// SPDX-License-Identifier: Apache-2.0
package android.util;

/** Parity probe stand-in: log lines are dropped (the probe prints only results). */
public final class Log {
    private Log() {}
    public static int d(String t, String m) { return 0; }
    public static int i(String t, String m) { return 0; }
    public static int i(String t, String m, Throwable e) { return 0; }
    public static int w(String t, String m) { return 0; }
    public static int w(String t, String m, Throwable e) { return 0; }
    public static int w(String t, Throwable e) { return 0; }
    public static int e(String t, String m) { return 0; }
    public static int e(String t, String m, Throwable e) { return 0; }
}
