// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.ui;

import android.content.Context;

public class ToggleRow extends Row {
    public interface OnToggle { void onToggle(ToggleRow row, boolean wanted); }
    private OnToggle listener;
    private Boolean checked;
    public ToggleRow(Context c, CharSequence title, OnToggle l) { super(c, title); listener = l; }
    public ToggleRow setChecked(Boolean v) { checked = v; return this; }
    public Boolean isChecked() { return checked; }
    /** The user flips the switch (harness). */
    public void flip() { listener.onToggle(this, !Boolean.TRUE.equals(checked)); }
}
