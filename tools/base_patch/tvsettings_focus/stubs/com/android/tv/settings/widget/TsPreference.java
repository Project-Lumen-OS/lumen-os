package com.android.tv.settings.widget;

import androidx.preference.Preference;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public class TsPreference extends Preference {
    public boolean updatableFromGoogleSettings() { return false; }
}
