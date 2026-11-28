package com.android.tv.settings.library.instrumentation;

import androidx.leanback.preference.LeanbackPreferenceFragmentCompat;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public abstract class InstrumentedPreferenceFragment extends LeanbackPreferenceFragmentCompat {
    @Override
    public void onResume() {}
    @Override
    public void onPause() {}
}
