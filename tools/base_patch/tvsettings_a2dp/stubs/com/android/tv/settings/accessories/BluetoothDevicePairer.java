package com.android.tv.settings.accessories;

import android.bluetooth.BluetoothAdapter;

/**
 * Compile-time stand-in: only the two interfaces BluetoothA2dpConnector uses, with the signatures of the TvSettings
 * class in the base (LineageOS 21). The real class stays in the base dex; this file never reaches the image.
 */
public class BluetoothDevicePairer {
    public interface BluetoothConnector {
        void openConnection(BluetoothAdapter adapter);
        void dispose();
    }

    public interface OpenConnectionCallback {
        void succeeded();
        void failed();
    }
}
