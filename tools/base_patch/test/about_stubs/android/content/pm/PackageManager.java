package android.content.pm;

/** Host stand-in for the About rows test (test/AboutRowsTest.java): only what AboutFragment.LumenRows uses. */
public abstract class PackageManager {
    public abstract ProviderInfo resolveContentProvider(String authority, int flags);
}
