package android.os;
public final class Looper { static final Looper L = new Looper(); public static Looper getMainLooper() { return L; }
    public static Looper myLooper() { return L; } }
