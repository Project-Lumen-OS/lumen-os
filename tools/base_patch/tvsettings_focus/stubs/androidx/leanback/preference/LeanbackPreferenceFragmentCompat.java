package androidx.leanback.preference;

import android.os.Bundle;
import android.view.View;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public abstract class LeanbackPreferenceFragmentCompat extends BaseLeanbackPreferenceFragmentCompat {
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {}
}
