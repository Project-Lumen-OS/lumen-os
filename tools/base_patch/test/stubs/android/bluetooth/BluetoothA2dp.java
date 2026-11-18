package android.bluetooth;

import java.util.List;

import lumen.a2dp.World;

public final class BluetoothA2dp implements BluetoothProfile {
    public static final String ACTION_CONNECTION_STATE_CHANGED =
            "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED";

    public boolean setConnectionPolicy(BluetoothDevice device, int policy) {
        return World.setPolicy(device, policy);
    }

    @Override
    public List<BluetoothDevice> getConnectedDevices() {
        return World.connectedDevices();
    }
}
