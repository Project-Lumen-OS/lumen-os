package android.os;

import java.util.ArrayList;
import java.util.List;

/** Host stub (test/keytools): one main looper with a virtual clock; {@link #advance} runs what is due. */
public final class Looper {
    private static final Looper MAIN = new Looper();
    public static long now = 1_000;
    private static final List<Object[]> QUEUE = new ArrayList<>();   // {when, runnable}

    public static Looper getMainLooper() { return MAIN; }

    public static Looper myLooper() { return MAIN; }

    public static void post(Runnable r, long delay) { QUEUE.add(new Object[] {now + delay, r}); }

    public static void remove(Runnable r) { QUEUE.removeIf(e -> e[1] == r); }

    /** Runs every task due within {@code ms}, in time order, the clock moving with them. */
    public static void advance(long ms) {
        long end = now + ms;
        while (true) {
            Object[] next = null;
            for (Object[] e : QUEUE) if ((Long) e[0] <= end && (next == null || (Long) e[0] < (Long) next[0])) next = e;
            if (next == null) break;
            QUEUE.remove(next);
            now = Math.max(now, (Long) next[0]);
            ((Runnable) next[1]).run();
        }
        now = end;
    }
}
