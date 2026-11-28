// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package androidx.preference;

import android.view.View;
import android.view.ViewGroup;

/**
 * Host stand-in for test/FocusRowsTest.java: androidx.preference 1.2 Preference as far as a row's focus and click go.
 * onBindViewHolder sets the click listener, the enabled state on the row view and all its children
 * (setEnabledStateOnViews) and focusable / clickable from isSelectable(); performClick skips a disabled or not
 * selectable preference. A switch row flips on a click.
 */
public class Preference {
    private final String title;
    private final boolean isSwitch;
    private boolean enabled = true, selectable = true, checked;

    public Preference(String title, boolean isSwitch, boolean checked) {
        this.title = title;
        this.isSwitch = isSwitch;
        this.checked = checked;
    }

    public String getTitle() { return title; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isSelectable() { return selectable; }
    public void setSelectable(boolean selectable) { this.selectable = selectable; }
    public boolean isChecked() { return checked; }

    public void onBindViewHolder(PreferenceViewHolder holder) {
        View itemView = holder.itemView;
        itemView.setOnClickListener(v -> performClick());
        setEnabledStateOnViews(itemView, isEnabled());
        final boolean selectable = isSelectable();
        itemView.setFocusable(selectable);
        itemView.setClickable(selectable);
    }

    private void setEnabledStateOnViews(View v, boolean enabled) {
        v.setEnabled(enabled);
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = vg.getChildCount() - 1; i >= 0; i--) setEnabledStateOnViews(vg.getChildAt(i), enabled);
        }
    }

    public void performClick() {
        if (!isEnabled() || !isSelectable()) return;
        if (isSwitch) checked = !checked;
    }
}
