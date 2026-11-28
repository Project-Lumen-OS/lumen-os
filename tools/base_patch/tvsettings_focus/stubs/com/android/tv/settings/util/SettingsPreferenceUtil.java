package com.android.tv.settings.util;

import androidx.leanback.preference.LeanbackPreferenceFragmentCompat;
import androidx.preference.Preference;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public final class SettingsPreferenceUtil {
    public static String getCompoundKey(LeanbackPreferenceFragmentCompat fragment, Preference preference) { return null; }
}
