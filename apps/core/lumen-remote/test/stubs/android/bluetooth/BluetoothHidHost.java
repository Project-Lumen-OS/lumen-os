// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.bluetooth;
public final class BluetoothHidHost implements BluetoothProfile {
    public static final String ACTION_CONNECTION_STATE_CHANGED = "android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED";
    public boolean setConnectionPolicy(BluetoothDevice d, int policy) { return lumen.fake.World.hidAllow(d, policy); }
    public boolean connect(BluetoothDevice d) { return lumen.fake.World.hidAllow(d, CONNECTION_POLICY_ALLOWED); }
    @Override public int getConnectionState(BluetoothDevice d) { return lumen.fake.World.hidState(d); }
}
