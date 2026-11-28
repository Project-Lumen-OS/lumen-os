// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package androidx.preference;

import java.util.ArrayList;
import java.util.List;

/** Host stand-in for test/FocusRowsTest.java: models only what the focus of a settings row depends on. */
public class PreferenceGroup extends Preference {
    private final List<Preference> list = new ArrayList<>();

    public PreferenceGroup() { super("screen", false, false); }
    public int getPreferenceCount() { return list.size(); }
    public Preference getPreference(int i) { return list.get(i); }
    public void addPreference(Preference p) { list.add(p); }
}
