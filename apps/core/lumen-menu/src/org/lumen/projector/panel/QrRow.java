// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.lumen.projector.ui.Theme;
import org.lumen.qr.QrView;

/** A QR code as a row of a menu page (XgimiSystemPage): the code on the row's start side, a phone opens it. */
final class QrRow extends FrameLayout {
    /** The code's side, design px (the old Back to the XGIMI system screen's 220 dp). */
    static final int SIDE = 330;
    private final QrView code;

    QrRow(Context c) {
        super(c);
        code = new QrView(c, "");
        int side = Theme.px(c, SIDE), pad = Theme.px(c, 12);
        setPadding(pad, pad, pad, pad);
        addView(code, new LayoutParams(side, side, Gravity.START | Gravity.CENTER_VERTICAL));
    }

    void setText(String text) {
        code.setText(text);
        setContentDescription(text);
    }
}
