// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.os;

public final class Looper {
    private static final Looper MAIN = new Looper();
    public static Looper getMainLooper() { return MAIN; }
    public static Looper myLooper() { return null; }
    public boolean isCurrentThread() { return false; }
}
