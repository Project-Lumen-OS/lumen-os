// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.ui;

import android.content.Context;
import android.view.View;

public class TextRow extends View {
    private CharSequence text;
    public TextRow(Context c, CharSequence text) { super(c); this.text = text; }
    public void setText(CharSequence t) { text = t; }
    public CharSequence text() { return text; }
}
