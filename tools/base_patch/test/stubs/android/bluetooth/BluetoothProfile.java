package android.bluetooth;

import java.util.List;

public interface BluetoothProfile {
    int STATE_DISCONNECTED = 0;
    int STATE_CONNECTING = 1;
    int STATE_CONNECTED = 2;
    int STATE_DISCONNECTING = 3;
    int A2DP = 2;
    int CONNECTION_POLICY_ALLOWED = 100;
    String EXTRA_STATE = "android.bluetooth.profile.extra.STATE";
    String EXTRA_PREVIOUS_STATE = "android.bluetooth.profile.extra.PREVIOUS_STATE";

    List<BluetoothDevice> getConnectedDevices();

    interface ServiceListener {
        void onServiceConnected(int profile, BluetoothProfile proxy);
        void onServiceDisconnected(int profile);
    }
}
