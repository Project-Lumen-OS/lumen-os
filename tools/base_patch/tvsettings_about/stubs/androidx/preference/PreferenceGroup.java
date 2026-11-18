package androidx.preference;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image. Runnable for the host test of the Lumen rows: a list of preferences.
 */
public abstract class PreferenceGroup extends Preference {
    private final List<Preference> list = new ArrayList<>();

    public PreferenceGroup(Context context) { super(context); }
    public int getPreferenceCount() { return list.size(); }
    public Preference getPreference(int index) { return list.get(index); }
    public boolean addPreference(Preference preference) { return list.add(preference); }
    public boolean removePreference(Preference preference) { return list.remove(preference); }
}
