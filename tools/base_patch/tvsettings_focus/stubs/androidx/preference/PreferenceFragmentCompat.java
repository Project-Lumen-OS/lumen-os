package androidx.preference;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public abstract class PreferenceFragmentCompat extends Fragment {
    public PreferenceScreen getPreferenceScreen() { return null; }
    public void setPreferenceScreen(PreferenceScreen preferenceScreen) {}
    protected RecyclerView.Adapter onCreateAdapter(PreferenceScreen preferenceScreen) { return null; }
    public abstract void onCreatePreferences(Bundle savedInstanceState, String rootKey);
}
