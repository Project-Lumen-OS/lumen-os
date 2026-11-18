package android.bluetooth;
public class BluetoothDevice {
    public static final String ACTION_PAIRING_REQUEST = "android.bluetooth.device.action.PAIRING_REQUEST";
    public static final String ACTION_BOND_STATE_CHANGED = "android.bluetooth.device.action.BOND_STATE_CHANGED";
    public static final String ACTION_ACL_CONNECTED = "android.bluetooth.device.action.ACL_CONNECTED";
    public static final String ACTION_ACL_DISCONNECTED = "android.bluetooth.device.action.ACL_DISCONNECTED";
    public static final String ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED";
    public static final String EXTRA_DEVICE = "android.bluetooth.device.extra.DEVICE";
    public static final String EXTRA_PAIRING_VARIANT = "android.bluetooth.device.extra.PAIRING_VARIANT";
    public static final String EXTRA_BOND_STATE = "android.bluetooth.device.extra.BOND_STATE";
    public static final String EXTRA_PREVIOUS_BOND_STATE = "android.bluetooth.device.extra.PREVIOUS_BOND_STATE";
    public static final String EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL";
    public static final int BOND_NONE = 10, BOND_BONDING = 11, BOND_BONDED = 12;
    public static final int TRANSPORT_AUTO = 0, TRANSPORT_BREDR = 1, TRANSPORT_LE = 2;
    public static final int PAIRING_VARIANT_PIN = 0, PAIRING_VARIANT_PASSKEY_CONFIRMATION = 2, PAIRING_VARIANT_CONSENT = 3;
    public final String addr; public String name; public int bond = BOND_NONE; public boolean connected; public int battery = -1;
    public BluetoothDevice(String addr, String name) { this.addr = addr; this.name = name; }
    public String getAddress() { return addr; }
    public String getName() { return name; }
    public String getAlias() { return name; }
    public int getBondState() { return bond; }
    public boolean isConnected() { return connected; }
    public int getBatteryLevel() { return battery; }
    public int getType() { return 2; }
    public boolean createBond(int transport) { return lumen.fake.World.createBond(this, transport); }
    public boolean createBond() { return createBond(TRANSPORT_AUTO); }
    public boolean removeBond() { return lumen.fake.World.removeBond(this); }
    public boolean cancelBondProcess() { lumen.fake.World.event("cancelBond " + addr); return true; }
    public boolean setPairingConfirmation(boolean c) { lumen.fake.World.event("confirm " + addr + " " + c); return true; }
    @Override public boolean equals(Object o) { return o instanceof BluetoothDevice && ((BluetoothDevice) o).addr.equals(addr); }
    @Override public int hashCode() { return addr.hashCode(); }
    @Override public String toString() { return addr; }
}
