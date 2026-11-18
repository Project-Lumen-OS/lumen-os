package android.content;

import android.os.Bundle;

/** Host stand-in for the About rows test (test/AboutRowsTest.java): only what AboutFragment.LumenRows uses. */
public abstract class ContentResolver {
    public abstract Bundle call(String authority, String method, String arg, Bundle extras);
}
