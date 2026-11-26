// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.ui;

import android.content.Context;
import android.view.View;

public abstract class Row extends View {
    private CharSequence title;
    private boolean rowEnabled = true;
    protected Row(Context c, CharSequence title) { super(c); this.title = title; }
    public void setTitle(CharSequence t) { title = t; }
    public CharSequence getTitle() { return title; }
    public void setRowEnabled(boolean e) { rowEnabled = e; }
    public boolean isRowEnabled() { return rowEnabled; }
}
