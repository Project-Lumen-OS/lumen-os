package android.os;

public final class SystemClock {
    public static long elapsedRealtime() { return Wire.clock; }
    public static long uptimeMillis() { return Wire.clock; }
}
