package android.os;

/** Host stub (test/gamemenu). */
public final class SystemClock {
    private SystemClock() {}

    public static long uptimeMillis() { return System.nanoTime() / 1_000_000L; }

    public static void sleep(long ms) { }
}
