package android.content;

/** Host stand-in for the About rows test (test/AboutRowsTest.java): only what AboutFragment.LumenRows uses. */
public class Intent {
    public String pkg, cls;

    public Intent setClassName(String packageName, String className) {
        pkg = packageName;
        cls = className;
        return this;
    }
}
