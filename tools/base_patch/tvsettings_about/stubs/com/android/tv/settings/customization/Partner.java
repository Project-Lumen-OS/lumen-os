package com.android.tv.settings.customization;

import android.content.Context;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public final class Partner {
    public static Partner getInstance(Context context) { return null; }
    public boolean isCustomizationPackageProvided() { return false; }
}
