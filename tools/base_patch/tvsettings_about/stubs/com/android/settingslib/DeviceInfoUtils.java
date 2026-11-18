package com.android.settingslib;

import android.content.Context;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public class DeviceInfoUtils {
    public static String getFeedbackReporterPackage(Context context) { return null; }
    public static String getFormattedKernelVersion(Context context) { return null; }
    public static String getMsvSuffix() { return null; }
    public static String getSecurityPatch() { return null; }
}
