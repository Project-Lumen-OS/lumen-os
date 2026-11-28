package com.android.tv.settings.overlay;

import android.content.Context;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public final class FlavorUtils {
    public static boolean isTwoPanel(Context context) { return false; }
}
