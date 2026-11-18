package android.content;

import java.util.HashMap;
import java.util.Map;

/**
 * Host stub: the views ask their context for strings and pictures. The projection test sets none (no focus picture on
 * the host); the screens test (test/screens) fills both maps from the module res of the language it draws.
 */
public class Context {
    public static final Map<Integer, String> STRINGS = new HashMap<>();
    public static final Map<Integer, android.graphics.drawable.Drawable> DRAWABLES = new HashMap<>();

    public android.graphics.drawable.Drawable getDrawable(int id) { return DRAWABLES.get(id); }

    public String getString(int id) {
        String s = STRINGS.get(id);
        if (s == null) throw new IllegalStateException("no host string " + id);
        return s;
    }
}
