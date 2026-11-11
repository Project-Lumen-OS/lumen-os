package android.content;

/** Host stub (test/gamemenu): only what GmController uses. */
public class Context {
    public String getString(int id) { return "s" + id; }

    public String getString(int id, Object... args) { return "s" + id + java.util.Arrays.toString(args); }

    public Context getApplicationContext() { return this; }
}
