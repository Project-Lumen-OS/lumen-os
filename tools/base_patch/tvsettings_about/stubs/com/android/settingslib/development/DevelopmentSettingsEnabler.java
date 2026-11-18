package com.android.settingslib.development;

import android.content.Context;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public class DevelopmentSettingsEnabler {
    public static boolean isDevelopmentSettingsEnabled(Context context) { return false; }
    public static void setDevelopmentSettingsEnabled(Context context, boolean enable) {}
}
