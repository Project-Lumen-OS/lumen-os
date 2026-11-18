package android.os;

public class Handler {
    public Handler(Looper l) { }

    public boolean post(Runnable r) { Looper.post(r, 0); return true; }

    public boolean postDelayed(Runnable r, long ms) { Looper.post(r, ms); return true; }

    public void removeCallbacks(Runnable r) { Looper.remove(r); }
}
