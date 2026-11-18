package android.os;
public class HandlerThread { public HandlerThread(String n) {} public void start() {} public Looper getLooper() { return Looper.L; }
    public boolean quitSafely() { return true; } }
