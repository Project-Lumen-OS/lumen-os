package androidx.localbroadcastmanager.content;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public final class LocalBroadcastManager {
    public static LocalBroadcastManager getInstance(Context context) { return null; }
    public void registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {}
    public void unregisterReceiver(BroadcastReceiver receiver) {}
}
