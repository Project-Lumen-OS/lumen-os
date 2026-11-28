package androidx.leanback.preference;

import android.content.Context;

import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceFragmentCompat;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public abstract class BaseLeanbackPreferenceFragmentCompat extends PreferenceFragmentCompat {
    @Override
    public Context getContext() { return null; }
    public Fragment getCallbackFragment() { return null; }
}
