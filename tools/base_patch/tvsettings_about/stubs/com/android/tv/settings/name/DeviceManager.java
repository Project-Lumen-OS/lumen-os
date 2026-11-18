package com.android.tv.settings.name;

import android.content.Context;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public class DeviceManager {
    public static final String ACTION_DEVICE_NAME_UPDATE =
            "com.android.tv.settings.name.DeviceManager.DEVICE_NAME_UPDATE";

    public static String getDeviceName(Context context) { return null; }
}
