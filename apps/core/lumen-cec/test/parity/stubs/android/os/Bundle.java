package android.os;

import java.util.Map;
import java.util.TreeMap;

public final class Bundle {
    private final Map<String, Object> m = new TreeMap<>();
    public boolean containsKey(String k) { return m.containsKey(k); }
    public boolean getBoolean(String k) { return getBoolean(k, false); }
    public boolean getBoolean(String k, boolean d) { Object v = m.get(k); return v instanceof Boolean ? (Boolean) v : d; }
    public void putBoolean(String k, boolean v) { m.put(k, v); }
    public int[] getIntArray(String k) { Object v = m.get(k); return v instanceof int[] ? (int[]) v : null; }
    public void putIntArray(String k, int[] v) { m.put(k, v); }
    @Override public String toString() { return m.toString(); }
}
