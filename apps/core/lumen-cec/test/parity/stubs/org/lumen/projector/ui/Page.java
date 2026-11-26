// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.ui;

import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class Page {
    private CharSequence title;
    private final List<View> rows = new ArrayList<>();
    private Runnable onShown;
    public Page(CharSequence title) { this.title = title; }
    public CharSequence getTitle() { return title; }
    public <T extends View> T add(T row) { rows.add(row); return row; }
    public List<View> rows() { return rows; }
    public Page setOnShown(Runnable r) { onShown = r; return this; }
    public Page setInitialFocus(int i) { return this; }
    public Runnable onShown() { return onShown; }
}
