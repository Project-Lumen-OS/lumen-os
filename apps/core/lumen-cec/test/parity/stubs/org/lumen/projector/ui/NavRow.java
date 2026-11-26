// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.ui;

import android.content.Context;

public class NavRow extends Row {
    private Runnable onClick;
    private CharSequence value;
    private boolean chevron = true;
    public NavRow(Context c, CharSequence title) { super(c, title); }
    public NavRow(Context c, CharSequence title, Runnable onClick) { super(c, title); this.onClick = onClick; }
    public NavRow setOnClick(Runnable r) { onClick = r; return this; }
    public NavRow setValue(CharSequence v) { value = v; return this; }
    public CharSequence getValue() { return value; }
    public NavRow setChevron(boolean show) { chevron = show; return this; }
    public boolean chevron() { return chevron; }
    public Runnable onClick() { return onClick; }
}
