package androidx.preference;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public abstract class PreferenceGroup extends Preference {
    public int getPreferenceCount() { return 0; }
    public Preference getPreference(int index) { return null; }
}
