// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package androidx.preference;

/** Host stand-in for test/FocusRowsTest.java: binds row position of the group to a holder (androidx 1.2). */
public class PreferenceGroupAdapter {
    private final PreferenceGroup group;

    public PreferenceGroupAdapter(PreferenceGroup group) { this.group = group; }
    public int getItemCount() { return group.getPreferenceCount(); }
    public Preference getItem(int position) { return group.getPreference(position); }

    public void onBindViewHolder(PreferenceViewHolder holder, int position) {
        getItem(position).onBindViewHolder(holder);
    }
}
