package com.android.tv.settings;

import androidx.preference.PreferenceFragmentCompat;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public abstract class SettingsPreferenceFragment extends PreferenceFragmentCompat {
    protected int getPageId() { return 0; }
}
