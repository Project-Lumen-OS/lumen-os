package android.os;

import lumen.a2dp.World;

/** Host stand-in: messages run on the World's virtual clock. */
public class Handler {
    public void handleMessage(Message msg) {
    }

    public final boolean sendEmptyMessageDelayed(int what, long delayMillis) {
        World.post(this, what, delayMillis);
        return true;
    }

    public final boolean hasMessages(int what) {
        return World.has(this, what);
    }

    public final void removeCallbacksAndMessages(Object token) {
        World.removeAll(this);
    }
}
