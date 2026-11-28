package com.android.tv.twopanelsettings;

import androidx.fragment.app.Fragment;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public abstract class TwoPanelSettingsFragment extends Fragment {
    public interface PreviewableComponentCallback {
        void onArriveAtMainPanel(boolean forward);
    }

    public void addListenerForFragment(Fragment fragment) {}
    public void removeListenerForFragment(Fragment fragment) {}
}
