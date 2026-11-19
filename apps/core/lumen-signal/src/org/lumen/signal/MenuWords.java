// SPDX-License-Identifier: Apache-2.0
package org.lumen.signal;

import android.content.Context;

/**
 * The menu's words for the rows the panel shares with the projector menu (picture mode, motion smoothing), given by
 * the app at install: lumen-menu MenuSignalText, the same strings and translations the menu shows. {@code c} is in
 * the panel's language.
 */
public interface MenuWords {
    /** The row name of a menu feature id ("picture.mode"), null when the menu has none. */
    String title(Context c, String feature);

    /** The name of one option token of that feature, null when the menu has none. */
    String option(Context c, String feature, String token);
}
