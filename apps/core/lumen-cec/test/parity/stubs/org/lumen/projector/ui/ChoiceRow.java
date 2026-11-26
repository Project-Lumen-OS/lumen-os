// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.ui;

import android.content.Context;

public class ChoiceRow extends Row {
    public interface OnChoice { void onChoice(ChoiceRow row, int index); }
    private CharSequence[] options;
    private int index = -1;
    private long commitDelay;
    private final OnChoice listener;
    public ChoiceRow(Context c, CharSequence title, CharSequence[] options, OnChoice l) {
        super(c, title); this.options = options; listener = l;
    }
    public ChoiceRow setSelected(int i) { index = i; return this; }
    public int getSelected() { return index; }
    public ChoiceRow setCommitDelay(long ms) { commitDelay = ms; return this; }
    public long commitDelay() { return commitDelay; }
    public CharSequence[] options() { return options; }
    /** The user commits a choice (harness). */
    public void choose(int i) { listener.onChoice(this, i); }
}
