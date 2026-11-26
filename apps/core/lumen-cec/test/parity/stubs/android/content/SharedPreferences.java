// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.content;

public interface SharedPreferences {
    boolean getBoolean(String k, boolean d);
    int getInt(String k, int d);
    Editor edit();
    interface Editor {
        Editor putBoolean(String k, boolean v);
        Editor putInt(String k, int v);
        void apply();
        boolean commit();
    }
}
