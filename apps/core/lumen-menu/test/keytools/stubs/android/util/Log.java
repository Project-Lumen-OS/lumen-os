package android.util;

public final class Log {
    public static final java.util.List<String> LINES = new java.util.ArrayList<>();

    public static int i(String t, String m) { LINES.add(t + " " + m); return 0; }

    public static int w(String t, String m) { LINES.add(t + " W " + m); return 0; }

    public static int e(String t, String m) { LINES.add(t + " E " + m); return 0; }

    public static int e(String t, String m, Throwable e) { LINES.add(t + " E " + m + " " + e); return 0; }
}
