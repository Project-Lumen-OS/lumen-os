package android.os;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Host stand-in for the About rows test (test/AboutRowsTest.java): only what AboutFragment.LumenRows uses. */
public final class Bundle {
    private final Map<String, Object> map = new HashMap<>();

    public void putString(String key, String value) { map.put(key, value); }
    public String getString(String key) { Object v = map.get(key); return v instanceof String ? (String) v : null; }
    public void putParcelableArrayList(String key, ArrayList<?> value) { map.put(key, value); }

    @SuppressWarnings("unchecked")
    public <T> ArrayList<T> getParcelableArrayList(String key, Class<? extends T> clazz) {
        Object v = map.get(key);
        return v instanceof ArrayList ? (ArrayList<T>) v : null;
    }
}
