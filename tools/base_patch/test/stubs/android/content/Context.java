package android.content;

import lumen.a2dp.World;

public class Context {
    public Intent registerReceiver(BroadcastReceiver r, IntentFilter f) {
        World.register(r, f);
        return null;
    }

    public void unregisterReceiver(BroadcastReceiver r) {
        World.unregister(r);
    }
}
