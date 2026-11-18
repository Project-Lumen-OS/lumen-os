package com.android.tv.settings;

import android.content.Context;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public final class PreferenceUtils {
    public static void resolveSystemActivityOrRemove(Context context, PreferenceGroup parent, Preference preference,
            int flags) {}
}
