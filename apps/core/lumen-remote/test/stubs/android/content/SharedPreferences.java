// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.content;
public interface SharedPreferences {
    java.util.Set<String> getStringSet(String k, java.util.Set<String> d);
    boolean getBoolean(String k, boolean d);
    int getInt(String k, int d);
    long getLong(String k, long d);
    String getString(String k, String d);
    boolean contains(String k);
    Editor edit();
    interface Editor {
        Editor putStringSet(String k, java.util.Set<String> v);
        Editor putBoolean(String k, boolean v);
        Editor putInt(String k, int v);
        Editor putLong(String k, long v);
        Editor putString(String k, String v);
        Editor remove(String k);
        void apply();
        boolean commit();
    }
}
