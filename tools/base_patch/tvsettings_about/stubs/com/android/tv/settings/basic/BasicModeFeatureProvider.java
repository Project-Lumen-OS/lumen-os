package com.android.tv.settings.basic;

import android.content.Context;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public interface BasicModeFeatureProvider {
    boolean isStoreDemoMode(Context context);
}
