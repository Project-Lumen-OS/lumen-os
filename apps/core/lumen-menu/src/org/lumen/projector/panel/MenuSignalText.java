// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import android.content.Context;

import org.lumen.signal.MenuWords;

/**
 * The menu's own words for the signal panel's picture mode and motion smoothing rows (lumen-signal MenuWords): the row
 * names and option names of {@link MenuLabels}, so the panel says exactly what the menu says, in every language the
 * menu has.
 */
public final class MenuSignalText implements MenuWords {
    @Override public String title(Context c, String feature) { return text(c, MenuLabels.title(feature)); }

    @Override public String option(Context c, String feature, String token) {
        return text(c, MenuLabels.option(feature, token));
    }

    private static String text(Context c, int res) { return res == 0 ? null : c.getString(res); }
}
