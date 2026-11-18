package com.android.tv.settings.customization;

import android.content.Context;

import androidx.preference.PreferenceScreen;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public final class PartnerPreferencesMerger {
    public static void mergePreferences(Context context, PreferenceScreen screen, String screenName) {}
}
