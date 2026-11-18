package org.lumen.projector.ui;

import android.content.Context;
import android.view.View;

public class HeaderRow extends View {
    private CharSequence text;
    public HeaderRow(Context c, CharSequence text) { super(c); this.text = text; }
    public void setText(CharSequence t) { text = t; }
    public CharSequence text() { return text; }
}
