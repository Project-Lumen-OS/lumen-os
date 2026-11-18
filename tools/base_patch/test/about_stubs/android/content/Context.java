package android.content;

import android.content.pm.PackageManager;

/** Host stand-in for the About rows test (test/AboutRowsTest.java): only what AboutFragment.LumenRows uses. */
public abstract class Context {
    public abstract PackageManager getPackageManager();
    public abstract ContentResolver getContentResolver();
}
