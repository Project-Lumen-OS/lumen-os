// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.bluetooth;
public class BluetoothAdapter {
    public static final String ACTION_STATE_CHANGED = "android.bluetooth.adapter.action.STATE_CHANGED";
    public static final String EXTRA_STATE = "android.bluetooth.adapter.extra.STATE";
    public static final int STATE_OFF = 10, STATE_TURNING_ON = 11, STATE_ON = 12, STATE_TURNING_OFF = 13;
    public final android.bluetooth.le.BluetoothLeScanner scanner = new android.bluetooth.le.BluetoothLeScanner();
    public boolean isEnabled() { return lumen.fake.World.btOn; }
    public String getName() { return lumen.fake.World.ownName; }
    public android.bluetooth.le.BluetoothLeScanner getBluetoothLeScanner() { return lumen.fake.World.btOn ? scanner : null; }
    public java.util.Set<BluetoothDevice> getBondedDevices() { return lumen.fake.World.bonded(); }
    public BluetoothDevice getRemoteDevice(String a) { return lumen.fake.World.device(a); }
    public int getProfileConnectionState(int p) { return p == BluetoothProfile.A2DP && lumen.fake.World.audio ? 2 : 0; }
    public boolean isDiscovering() { return false; }
    public boolean cancelDiscovery() { return true; }
    public boolean getProfileProxy(android.content.Context c, BluetoothProfile.ServiceListener l, int p) { return lumen.fake.World.proxy(l, p); }
    public static BluetoothAdapter getDefaultAdapter() { return lumen.fake.World.adapter; }
}
