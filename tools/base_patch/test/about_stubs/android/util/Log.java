package android.util;

/** Host stand-in for the About rows test (test/AboutRowsTest.java): only what AboutFragment.LumenRows uses. */
public final class Log {
    public static String last;

    public static int w(String tag, String msg) {
        last = tag + ": " + msg;
        return 0;
    }
}
