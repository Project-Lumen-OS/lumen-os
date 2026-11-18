package com.android.settingslib;

import android.content.Context;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public class Utils {
    public static boolean isWifiOnly(Context context) { return false; }
}
