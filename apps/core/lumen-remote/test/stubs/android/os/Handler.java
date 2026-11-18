package android.os;
/** Host stub: every handler of the test runs on the one virtual-time loop of World. */
public class Handler {
    public Handler() {}
    public Handler(Looper l) {}
    public final boolean post(Runnable r) { lumen.fake.World.post(this, r, 0); return true; }
    public final boolean postDelayed(Runnable r, long ms) { lumen.fake.World.post(this, r, ms); return true; }
    public final void removeCallbacks(Runnable r) { lumen.fake.World.remove(this, r); }
    public final void removeCallbacksAndMessages(Object o) { lumen.fake.World.removeAll(this); }
    public final Looper getLooper() { return Looper.L; }
}
