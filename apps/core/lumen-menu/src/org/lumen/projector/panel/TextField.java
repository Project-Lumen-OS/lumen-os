// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import android.content.Context;

import org.lumen.projector.R;

/** One text the shared edit screen (TextEditActivity) edits: its texts, its value, its check and where it goes. */
interface TextField {
    /** The card title. */
    int title();

    /** The line under the field while nothing is wrong. */
    int hint();

    /** android.text.InputType of the field. */
    int inputType();

    /** The text now, null when it cannot be read. */
    String value(Context c);

    /** The message of what is wrong with {@code text}, 0 when it may be saved. */
    int problem(String text);

    /** Saves {@code text} (already checked); false when it was refused. */
    boolean save(Context c, String text);

    /** The toast after a save, 0 for none. */
    default int saved() { return R.string.lumen_m_hotspot_saved; }

    /** The line under the field when the save was refused. */
    default int failed() { return R.string.lumen_m_hotspot_failed; }

    /** What the field takes while typing (the stock input filters), none by default. */
    default android.text.InputFilter[] filters(Context c) { return new android.text.InputFilter[0]; }
}
