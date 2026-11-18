package androidx.preference;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public abstract class PreferenceFragmentCompat extends Fragment {
    public abstract void onCreatePreferences(Bundle savedInstanceState, String rootKey);
    public PreferenceManager getPreferenceManager() { return null; }
    public PreferenceScreen getPreferenceScreen() { return null; }
    public void setPreferencesFromResource(int preferencesResId, String key) {}
    public <T extends Preference> T findPreference(CharSequence key) { return null; }
    public boolean onPreferenceTreeClick(Preference preference) { return false; }
}
