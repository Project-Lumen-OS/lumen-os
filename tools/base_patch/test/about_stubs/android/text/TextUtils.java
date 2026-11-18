package android.text;

/** Host stand-in for the About rows test (test/AboutRowsTest.java): only what AboutFragment.LumenRows uses. */
public class TextUtils {
    public static boolean isEmpty(CharSequence s) { return s == null || s.length() == 0; }
}
