package org.lumen.projector;
public final class SafeHandler extends android.os.Handler {
    public static SafeHandler newThread(String name) { return new SafeHandler(); }
    public static SafeHandler main() { return new SafeHandler(); }
}
