package org.lumen.hal.gmpf.utils;

/** Argument checks of the vendor calls: a refused value throws IllegalArgumentException before anything is sent. */
public final class Args {
    private Args() {}

    public static int checkRange(String what, int v, int min, int max) {
        if (v < min || v > max) throw new IllegalArgumentException(what + " " + v + " not in " + min + ".." + max);
        return v;
    }

    public static boolean oneOf(int v, int... allowed) {
        for (int a : allowed) if (a == v) return true;
        return false;
    }
}
