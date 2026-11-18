package android.bluetooth;

import lumen.a2dp.World;

public final class BluetoothDevice {
    public static final String ACTION_UUID = "android.bluetooth.device.action.UUID";
    public static final String EXTRA_DEVICE = "android.bluetooth.device.extra.DEVICE";

    private final String mAddress;

    public BluetoothDevice(String address) {
        mAddress = address;
    }

    public String getAddress() {
        return mAddress;
    }

    public boolean fetchUuidsWithSdp() {
        return World.fetchUuids(this);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BluetoothDevice && ((BluetoothDevice) o).mAddress.equals(mAddress);
    }

    @Override
    public int hashCode() {
        return mAddress.hashCode();
    }
}
