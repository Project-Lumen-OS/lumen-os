package android.os;

import java.util.ArrayList;
import java.util.List;

/** Test only: the recorder of every vendor transaction and the source of the scripted replies. */
public final class Wire {
    private Wire() {}

    public static final List<String> LOG = new ArrayList<>();
    public static final java.util.Map<String, String> PROPS = new java.util.HashMap<>();
    public static long clock = 1_000_000L;
    /** Scripted reply words (HIDL: one per read, AIDL: the reply parcel after the status); generator when empty. */
    public static final java.util.ArrayDeque<Object> SCRIPT = new java.util.ArrayDeque<>();
    public static int seed;
    public static boolean failNext, unimplementedNext;
    /** The last callback binder the client sent (HIDL HwBinder or AIDL Binder). */
    public static Object lastBinder;
    private static int n;

    public static void log(String s) { LOG.add(s); }

    public static void reset(int s) { SCRIPT.clear(); seed = s; n = 0; }

    /** The next reply word: scripted, else from the seed's pattern. */
    public static Object next(String kind) {
        if (!SCRIPT.isEmpty()) return SCRIPT.poll();
        int k = n++;
        int[] pat;
        switch (seed) {
            case 0: pat = new int[] {1}; break;
            case 1: pat = new int[] {0}; break;
            case 2: pat = new int[] {400, 3, 2, 1, 0, 6, 100, 50, 3840, 2160, -1, 12}; break;
            default: pat = new int[] {5, 10, 1, 16, 17, 2, 3, 4, 9, 11, 13, 44}; break;
        }
        return pat[k % pat.length];
    }

    public static int nextInt() {
        Object o = next("i32");
        if (o instanceof Boolean) return ((Boolean) o) ? 1 : 0;
        if (o instanceof Float) return Float.floatToIntBits((Float) o);
        return ((Number) o).intValue();
    }

    public static float nextFloat() {
        Object o = next("f32");
        if (o instanceof Float) return (Float) o;
        return ((Number) o).intValue();
    }

    public static String hex(byte[] b, int len) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < len; i++) s.append(String.format("%02x", b[i] & 0xff));
        return s.toString();
    }
}
