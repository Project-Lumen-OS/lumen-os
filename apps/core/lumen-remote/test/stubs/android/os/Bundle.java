// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.os;
public class Bundle {
    private final java.util.TreeMap<String, Object> m = new java.util.TreeMap<>();
    public Bundle() {}
    public Bundle(Bundle b) { m.putAll(b.m); }
    public void putBoolean(String k, boolean v) { m.put(k, v); }
    public void putInt(String k, int v) { m.put(k, v); }
    public void putLong(String k, long v) { m.put(k, v); }
    public void putString(String k, String v) { m.put(k, v); }
    public boolean getBoolean(String k) { Object o = m.get(k); return o instanceof Boolean && (Boolean) o; }
    public boolean getBoolean(String k, boolean d) { Object o = m.get(k); return o instanceof Boolean ? (Boolean) o : d; }
    public int getInt(String k) { Object o = m.get(k); return o instanceof Integer ? (Integer) o : 0; }
    public int getInt(String k, int d) { Object o = m.get(k); return o instanceof Integer ? (Integer) o : d; }
    public String getString(String k) { Object o = m.get(k); return o == null ? null : String.valueOf(o); }
    public String getString(String k, String d) { String s = getString(k); return s == null ? d : s; }
    public boolean containsKey(String k) { return m.containsKey(k); }
    public void remove(String k) { m.remove(k); }
    public java.util.Set<String> keySet() { return m.keySet(); }
    public Object get(String k) { return m.get(k); }
    @Override public String toString() { return m.toString(); }
}
