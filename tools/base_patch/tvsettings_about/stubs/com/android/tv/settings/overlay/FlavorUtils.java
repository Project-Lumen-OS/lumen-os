package com.android.tv.settings.overlay;

import android.content.Context;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public final class FlavorUtils {
    public static final int FLAVOR_UNDEFINED = 0b00000000;
    public static final int FLAVOR_CLASSIC = 0b00000001;
    public static final int FLAVOR_TWO_PANEL = 0b00000010;
    public static final int FLAVOR_X = 0b00000100;
    public static final int FLAVOR_VENDOR = 0b00001000;

    public static int getFlavor(Context context) { return FLAVOR_UNDEFINED; }
    public static FeatureFactory getFeatureFactory(Context context) { return null; }
}
