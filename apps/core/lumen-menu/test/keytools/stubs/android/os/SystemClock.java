package android.os;

public final class SystemClock {
    public static long uptimeMillis() { return Looper.now; }

    public static long elapsedRealtime() { return Looper.now; }
}
