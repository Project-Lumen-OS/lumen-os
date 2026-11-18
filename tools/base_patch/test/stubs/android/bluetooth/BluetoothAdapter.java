package android.bluetooth;

import android.content.Context;

import lumen.a2dp.World;

public final class BluetoothAdapter {
    private static final BluetoothAdapter ADAPTER = new BluetoothAdapter();

    public static BluetoothAdapter getDefaultAdapter() {
        return ADAPTER;
    }

    public boolean getProfileProxy(Context context, BluetoothProfile.ServiceListener listener, int profile) {
        return World.profileProxy(listener, profile);
    }

    public void closeProfileProxy(int profile, BluetoothProfile proxy) {
    }
}
