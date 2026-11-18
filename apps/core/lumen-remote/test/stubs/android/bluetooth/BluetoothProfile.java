package android.bluetooth;
public interface BluetoothProfile {
    int A2DP = 2, HID_HOST = 4, STATE_DISCONNECTED = 0, STATE_CONNECTING = 1, STATE_CONNECTED = 2;
    int CONNECTION_POLICY_ALLOWED = 100, CONNECTION_POLICY_FORBIDDEN = 0;
    String EXTRA_STATE = "android.bluetooth.profile.extra.STATE";
    int getConnectionState(BluetoothDevice d);
    interface ServiceListener { void onServiceConnected(int p, BluetoothProfile proxy); void onServiceDisconnected(int p); }
}
