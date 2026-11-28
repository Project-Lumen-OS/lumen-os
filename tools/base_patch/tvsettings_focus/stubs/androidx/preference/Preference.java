package androidx.preference;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image.
 */
public class Preference {
    public CharSequence getTitle() { return null; }
    public boolean isEnabled() { return true; }
    public boolean isSelectable() { return true; }
    public void setVisible(boolean visible) {}
}
