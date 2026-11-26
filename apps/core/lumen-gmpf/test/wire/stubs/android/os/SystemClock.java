// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.os;

public final class SystemClock {
    public static long elapsedRealtime() { return Wire.clock; }
    public static long uptimeMillis() { return Wire.clock; }
}
