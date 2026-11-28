package com.android.tv.settings.widget;

import androidx.lifecycle.ViewModel;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public class SettingsViewModel extends ViewModel {
    public PreferenceContentProviderLiveData getVisibilityLiveData(String key) { return null; }
}
