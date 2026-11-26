// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import org.lumen.projector.ui.Page;

/** A page that draws itself as a sheet (the sound output page, OutputSheet) instead of rows in the popup card. */
final class SheetPage extends Page {
    final OutputSheet sheet;

    SheetPage(CharSequence title, OutputSheet sheet) {
        super(title);
        this.sheet = sheet;
    }
}
