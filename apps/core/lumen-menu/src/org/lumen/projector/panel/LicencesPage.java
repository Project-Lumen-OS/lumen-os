// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;

import org.lumen.projector.R;
import org.lumen.projector.ui.Page;
import org.lumen.projector.ui.TextRow;

/**
 * Licences (settings.licences, General on every model): the credits the bundled works require (GmLicences.CREDITS), one
 * readable row per work in the menu's own page, so the D-pad walks and scrolls them and BACK returns.
 */
final class LicencesPage {
    private LicencesPage() {}

    static Page build(Context app) {
        Page page = new Page(app.getString(R.string.lumen_gm_licences));
        page.add(new TextRow(app, app.getString(R.string.lumen_gm_credits_intro)));
        for (String part : GmLicences.CREDITS.split("\n\n")) page.add(new TextRow(app, part).readable());
        page.setInitialFocus(1);
        return page;
    }
}
